package com.butone.flowbiz;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.flowbiz.ActivityGroupInstance.GroupInstanceTask;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.butone.utils.ModelExtUtils;
import com.butone.workdate.WorkDayUtils;
import com.justep.message.SystemMessages;
import com.justep.model.BusinessActivity;
import com.justep.model.ModelObject;
import com.justep.model.ModelUtils;
import com.justep.model.Process;
import com.justep.system.context.ActionContext;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.process.ActivityInstance;
import com.justep.system.process.ExpressEngine;
import com.justep.system.process.ProcessControl;
import com.justep.system.process.ProcessInstance;
import com.justep.system.process.ProcessUtils;
import com.justep.system.process.Task;
import com.justep.system.process.TaskKind;
import com.justep.system.util.BizSystemException;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class ProcessLimitManager {
	private Process process;

	private boolean includeStartDate;

	public ProcessLimitManager(Process process) {
		this.process = process;
	}

	public void setIncludeStartDate(boolean includeStartDate) {
		this.includeStartDate = includeStartDate;
	}

	public boolean isIncludeStartDate() {
		return includeStartDate;
	}

	private Date getFlowLimitDate(Date startDate) {

		String expr = ModelExtUtils.getModelObjectLimitDateExpr(this.process);
		if (Utils.isNotEmptyString(expr)) {
			return (Date) ExpressEngine.calculate(expr, null, ModelUtils.getModel("/base/core/logic/fn"));
		} else {
			return WorkDayUtils.calcDateAfterDays(startDate, ModelExtUtils.getModelObjectLimitDays(this.process),
					ModelExtUtils.getModelObjectLimitKind(this.process), this.includeStartDate);
		}
	}

	private Table loadBizRecTable(String bizRecId) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("bizRecId", bizRecId);
		return KSQL.select("select b.* from B_BizRec b where b=:bizRecId", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOW, null);
	}

	/**
	 * 获得FlowTo的环节分组
	 * 
	 * @param control
	 * @return @
	 */
	private Map<String, JSONObject> getFlowToActivityGroups(Collection<ActivityInstance> flowTos) {
		Map<String, JSONObject> ret = new HashMap<String, JSONObject>();
		for (ActivityInstance next : flowTos) {
			if (!(next.getActivity() instanceof BusinessActivity))
				continue;
			JSONObject group = ModelExtUtils.getActivityGroup(this.process.getActivity(next.getActivityID()));
			if (group == null) {
				// 为环节创建一个默认分组
				String limitKind = ModelExtUtils.getModelObjectLimitKind(next.getActivity());
				if (Utils.isEmptyString(limitKind))
					limitKind = ModelExtUtils.getModelObjectLimitKind(this.process);
				group = new JSONObject();
				group.put("id", next.getActivityID());
				group.put("name", next.getActivityName());
				String limitDateExpr = ModelExtUtils.getModelObjectLimitDateExpr(next.getActivity());
				if (Utils.isNotEmptyString(limitDateExpr))
					group.put("limitDateExpr", limitDateExpr);
				BigDecimal limitDays = ModelExtUtils.getModelObjectLimitDays(next.getActivity());
				if (limitDays != null)
					group.put("limitDays", limitDays);
				group.put("limitKind", limitKind);
			}
			String groupId = group.getString("id");
			JSONArray tasks;
			if (ret.containsKey(groupId)) {
				group = ret.get(groupId);
				tasks = group.getJSONArray("tasks");
			} else {
				ret.put(groupId, group);
				tasks = new JSONArray();
				group.put("tasks", tasks);
			}
			tasks.add(next.getTask());
		}
		return ret;
	}

	/**
	 * 设置案卷限办日期
	 * 
	 * @param pi
	 * @param bizRec
	 * @param currentDateTime
	 */
	private void setBizRecLimitDate(ProcessInstance pi, Row bizRec, Timestamp currentDateTime) {
		// 计算限办开始时间
		String limitStartExpr = ModelExtUtils.getProcessLimitStartDateExpr(this.process);
		Timestamp limitStartDate = null;
		if (Utils.isNotEmptyString(limitStartExpr)) {
			Date date = (Date) ExpressEngine.calculate(limitStartExpr, null, ModelUtils.getModel("/base/core/logic/fn"));
			if (date != null)
				limitStartDate = new Timestamp(date.getTime());
		}
		// 检查限办开始时间，如果为空默认为当前
		if (limitStartDate == null)
			limitStartDate = currentDateTime;
		bizRec.setDateTime("fLimitStartDate", limitStartDate);
		// 由限办开始时间计算流程时限
		Date date = this.getFlowLimitDate(limitStartDate);
		Timestamp limitDate = date != null ? new Timestamp(date.getTime()) : null;
		bizRec.setDateTime("fLimitDate", limitDate);
		if (limitDate != null) {
			Double days = WorkDayUtils.remainedDays(limitStartDate, limitDate, "工作日", false);
			bizRec.setInt("fRemainingDays", days.intValue());
		} else {
			bizRec.setValue("fRemainingDays", null);
		}
		// 更新流程记录限办时间
		pi.getTask().setLimitTime(limitDate);
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("limitDate", limitDate);
		varMap.put("id", pi.getId());
		KSQL.executeUpdate("update SA_Task t set t.sLimitTime=:limitDate where t = :id", varMap, FlowBizConsts.DATA_MODEL_SYSTEM, null);
	}

	/**
	 * 计算环节实例时限，用于FlowTos 的ActivityInstance任务的时限计算更新
	 * 
	 * @param task
	 *            ActivityInstance
	 * @param startTime
	 * @param forceLimitKind
	 *            强制的限办天数类型，由组的时限类型决定。组的时限类型 = 组定义 || 环节定义 || 流程定义 || '工作日'
	 */
	private void calcAndUpdateTaskLimitTime(Task aiTask, Date startTime, String forceLimitKind, Timestamp groupLimit, String groupLimitKind) {
		ModelObject model = process.getActivity(aiTask.getActivity());
		String expr = ModelExtUtils.getModelObjectLimitDateExpr(model);
		Date date;
		String limitKind = null;
		if (Utils.isNotEmptyString(expr)) {
			date = (Date) ExpressEngine.calculate(expr, null, ModelUtils.getModel("/base/core/logic/fn"));
		} else {
			limitKind = Utils.isNotEmptyString(forceLimitKind) ? forceLimitKind : ModelExtUtils.getModelObjectLimitKind(model);
			if (Utils.isEmptyString(limitKind))
				limitKind = ModelExtUtils.getModelObjectLimitKind(this.process);
			date = WorkDayUtils.calcDateAfterDays(startTime, ModelExtUtils.getModelObjectLimitDays(model),
					ModelExtUtils.getModelObjectLimitKind(model), this.includeStartDate);
		}
		Timestamp limitDate = date != null ? new Timestamp(date.getTime()) : null;
		updateTaskLimitTime(aiTask, limitDate, limitKind, groupLimit, groupLimitKind);
	}

	/**
	 * 更新环节时限，用于FlowTos 的ActivityInstance任务的时限计算更新或者解挂延迟限办
	 * 
	 * @param aiTask
	 * @param limitTime
	 */
	private void updateTaskLimitTime(Task aiTask, Timestamp limitTime, String actLimitKind, Timestamp groupLimit, String groupLimitKind) {
		// 更新任务限办时间
		aiTask.setLimitTime(limitTime);
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("id", aiTask.getId());
		varMap.put("limitDate", limitTime);
		varMap.put("actLimitKind", actLimitKind);
		varMap.put("groupLimit", groupLimit);
		varMap.put("groupLimitKind", groupLimitKind);
		if (limitTime == null) {
			varMap.put("remaindingDays", null);
		} else {
			Timestamp now = CommonUtils.getCurrentDateTime();
			Double days = WorkDayUtils.remainedDays(now, limitTime, actLimitKind, true);
			varMap.put("remaindingDays", new BigDecimal(days));
		}
		if (groupLimit == null) {
			varMap.put("groupRemaindingDays", null);
		} else {
			Timestamp now = CommonUtils.getCurrentDateTime();
			Double days = WorkDayUtils.remainedDays(now, groupLimit, groupLimitKind, true);
			varMap.put("groupRemaindingDays", new BigDecimal(days));
		}
		// update tkTask
		KSQL.executeUpdate("update SA_Task t set t.sLimitTime=:limitDate,t." + TaskExtendRelation.FlowTask_ActLimitKind + "=:actLimitKind,t."
				+ TaskExtendRelation.FlowTask_GroupLimit + "=:groupLimit,t." + TaskExtendRelation.FlowTask_GroupLimitKind + "=:groupLimitKind,t."
				+ TaskExtendRelation.FlowTask_RemaindingDays + "=:remaindingDays,t." + TaskExtendRelation.FlowTask_GroupRemaindingDays
				+ "=:groupRemaindingDays" + " where t = :id", varMap, FlowBizConsts.DATA_MODEL_SYSTEM, null);
		// update tkExecutor
		KSQL.executeUpdate("update SA_Task t set t.sLimitTime=:limitDate,t." + TaskExtendRelation.FlowTask_ActLimitKind + "=:actLimitKind,t."
				+ TaskExtendRelation.FlowTask_GroupLimit + "=:groupLimit,t." + TaskExtendRelation.FlowTask_GroupLimitKind + "=:groupLimitKind,t."
				+ TaskExtendRelation.FlowTask_RemaindingDays + "=:remaindingDays,t." + TaskExtendRelation.FlowTask_GroupRemaindingDays
				+ "=:groupRemaindingDays" + " where t.sParent = :id", varMap, FlowBizConsts.DATA_MODEL_SYSTEM, null);
	}

	/**
	 * 设置任务耗时，半天计时
	 * 
	 * @param task
	 *            ActivityInstance
	 * @param endTime
	 * @param limitKind
	 */
	private void setTaskLostDays(Task task, Date endTime) {
		String limitKind = ModelExtUtils.getModelObjectLimitKind(this.process.getActivity(task.getActivity()));
		if (Utils.isEmptyString(limitKind))
			limitKind = ModelExtUtils.getModelObjectLimitKind(this.process);
		Double lostDays = WorkDayUtils.calcLostDaysBetween((Timestamp) task.getCreateTime(), endTime, limitKind, true, true);
		task.setRelationValue(TaskExtendRelation.FlowTask_LostDays, new BigDecimal(lostDays));
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("id", task.getId());
		varMap.put("value", new BigDecimal(lostDays));
		KSQL.executeUpdate("update SA_Task t set t." + TaskExtendRelation.FlowTask_LostDays + "=:value where t=:id", varMap,
				FlowBizConsts.DATA_MODEL_SYSTEM, null);
	}

	/**
	 * 启动
	 * 
	 * @param actionContext
	 *            @
	 */
	public void start(ActionContext actionContext) {
		Timestamp now = CommonUtils.getCurrentDateTime();
		String bizId = ModelExtUtils.getProcessBizId(ContextHelper.getActionContext().getProcess());
		ProcessInstance pi = ProcessUtils.getPI();
		String flowId = pi.getId();
		// 流程时限
		Table table = ProcessLogicPluginContext.findTableControlObjectTarget(FlowBizConsts.CONCEPT_BizRec);
		boolean saveBizRec = table == null;
		if (saveBizRec) {
			table = loadBizRecTable(pi.getTask().getData1());
		}
		Row bizRec = table.iterator().next();
		bizRec.setString("fFlowId", flowId);
		if (bizRec.getDateTime("fReceiveTime") == null) {
			bizRec.setDateTime("fReceiveTime", now);
		}
		bizRec.setString("fBizId", bizId);
		bizRec.setString("fProcess", pi.getProcessFullName());
		BigDecimal flowLimitDays = ModelExtUtils.getModelObjectLimitDays(this.process);
		bizRec.setInteger("fLimitDays", flowLimitDays == null ? null : flowLimitDays.intValue());
		bizRec.setString("fLimitKind", ModelExtUtils.getModelObjectLimitKind(this.process));

		String limitEffectActivity = ModelExtUtils.getProcessLimitEffectActivity(this.process);
		if (Utils.isEmptyString(limitEffectActivity)) {
			// 未指定限办生效环节，立即计时
			setBizRecLimitDate(pi, bizRec, now);
		}

		// 分组时限(如果环节没有分组，生成一个默认分组)
		Map<String, JSONObject> flowToGroups = getFlowToActivityGroups(pi.getActiveAIs());

		for (JSONObject group : flowToGroups.values()) {
			BigDecimal limitDays = null;
			String limitKind = null;
			Date date;
			if (group.containsKey("limitDateExpr")) {
				date = (Date) ExpressEngine.calculate(group.getString("limitDateExpr"), null, ModelUtils.getModel("/base/core/logic/fn"));
			} else {
				if (group.containsKey("limitDays"))
					limitDays = group.getBigDecimal("limitDays");
				if (group.containsKey("limitKind"))
					limitKind = group.getString("limitKind");
				date = WorkDayUtils.calcDateAfterDays(now, limitDays, limitKind, this.includeStartDate);
			}
			if (Utils.isEmptyString(limitKind))
				limitKind = "工作日";
			Timestamp limitDate = date != null ? new Timestamp(date.getTime()) : null;
			ActivityGroupInstance instance = new ActivityGroupInstance();
			instance.setBizId(bizId);
			instance.setBizRecId(bizRec.getString("fBizRecId"));
			instance.setFlowId(flowId);
			instance.setGroupId(group.getString("id"));
			instance.setGroupName(group.getString("name"));
			instance.setLimitDays(limitDays);
			instance.setLimitKind(limitKind);
			instance.setLimitTime(limitDate);
			if (limitDate == null) {
				instance.setRemainingDays(null);
			} else {
				Double days = WorkDayUtils.remainedDays(now, limitDate, limitKind, true);
				instance.setRemainingDays(days.floatValue());
			}

			instance.setStartCount(1);
			instance.setStartTime(now);
			JSONArray groupTasks = group.getJSONArray("tasks");
			// 设置组内环节时限
			for (int i = 0; i < groupTasks.size(); i++) {
				Task aiTask = (Task) groupTasks.get(i);
				calcAndUpdateTaskLimitTime(aiTask, now, instance.getLimitKind(), instance.getLimitTime(), instance.getLimitKind());
			}
			instance.newTasks(groupTasks);
			instance.save();
		}
	}

	/**
	 * 完成，案卷剩余、分组剩余
	 * 
	 * @param actionContext
	 */
	public void finish(ActionContext actionContext) {
		ActivityInstance currentAI = ProcessUtils.getAI();
		Timestamp now = CommonUtils.getCurrentDateTime();

		Table bizRecTable = ProcessLogicPluginContext.findTableControlObjectTarget(FlowBizConsts.CONCEPT_BizRec);
		boolean saveBizRec = bizRecTable == null;
		if (saveBizRec) {
			bizRecTable = loadBizRecTable(currentAI.getTask().getData1());
		}
		Row bizRec = bizRecTable.iterator().next();
		String bizRecLimitKind = bizRec.getString("fLimitKind");
		// 1.计算案卷耗时 = fLimitStartDate || fReceiveTime
		{
			Date startDate = bizRec.getDateTime("fLimitStartDate");
			if (startDate == null)
				startDate = bizRec.getDateTime("fReceiveTime");
			if (startDate != null) {
				Double lostDays = WorkDayUtils.calcLostDaysBetween(startDate, now, bizRecLimitKind, this.includeStartDate, false);
				bizRec.setInteger("fLostDays", lostDays.intValue());
			}
		}
		// 计算案卷剩余天数工作日
		Date bizRecLimitDate = bizRec.getDateTime("fLimitDate");
		if (bizRecLimitDate != null) {
			Double lostDays = WorkDayUtils.calcLostDaysBetween(now, bizRecLimitDate, "工作日", false, false);
			bizRec.setInteger("fRemainingDays", lostDays.intValue());
		}

		ProcessInstance pi = currentAI.getPI();
		// 2.计算环节分组的耗时和剩余天数
		List<ActivityGroupInstance> groupInstances = ActivityGroupInstance.loadByFlow(currentAI.getTask().getFlow(), true);
		for (ActivityGroupInstance groupInstance : groupInstances) {
			if (groupInstance.getEndTime() != null) {
				continue;
			}
			String limitKind = groupInstance.getLimitKind();
			groupInstance.setEndTime(now);
			Double lostDays = WorkDayUtils.calcLostDaysBetween(groupInstance.getStartTime(), now, limitKind, true, true);

			Float nums = groupInstance.getLostDays();
			if (nums == null)
				groupInstance.setLostDays(lostDays.floatValue());
			else
				groupInstance.setLostDays(nums + lostDays.floatValue());

			if (groupInstance.getLimitTime() != null) {
				lostDays = WorkDayUtils.remainedDays(now, groupInstance.getLimitTime(), limitKind, true);
				groupInstance.setRemainingDays(lostDays.floatValue());
			} else {
				groupInstance.setRemainingDays(null);
			}
			// 3.设置组内任务(ActivityInstance)时限、耗时
			Iterator<GroupInstanceTask> itor = groupInstance.getTasks().iterator();
			while (itor.hasNext()) {
				GroupInstanceTask task = itor.next();
				try {
					ActivityInstance ai = pi.getAI(task.getTaskId());
					Task aiTask = ai.getTask();
					setTaskLostDays(aiTask, now);
					// 4.设置环节实例下的执行者任务（任务消息）时限、耗时
					for (Task executorTask : aiTask.getChildren()) {
						if (TaskKind.EXECUTOR.equals(executorTask.getKind())) {
							if (executorTask.getRelationValue(TaskExtendRelation.FlowTask_LostDays) == null)
								setTaskLostDays(executorTask, now);
						}
					}
				} catch (BizSystemException e) {
					if (SystemMessages.NO_AI2.equals(e.getCode())) {
						itor.remove();
					} else {
						throw e;
					}
				}
			}
			groupInstance.save();
		}
		if (saveBizRec)
			bizRecTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
	}

	/**
	 * 作废，案卷剩余、分组剩余，同办结
	 * 
	 * @param actionContext
	 */
	public void abort(ActionContext actionContext) {
		finish(actionContext);
	}

	/**
	 * 源于流程控件的
	 * 
	 * @param processControl
	 * @param checkBizRecLimitDate
	 *            是否检查案卷限办日期(仅流转、跳转进行检查，即FlowOut类型操作) @
	 */
	private void derivedFromProcessControl(ProcessControl processControl, boolean checkBizRecLimitDate) {
		ActivityInstance currentAI = ProcessUtils.getAI();
		Timestamp now = CommonUtils.getCurrentDateTime();
		Table bizRecTable = ProcessLogicPluginContext.findTableControlObjectTarget(FlowBizConsts.CONCEPT_BizRec);
		boolean saveBizRec = bizRecTable == null;
		if (saveBizRec) {
			bizRecTable = loadBizRecTable(currentAI.getTask().getData1());
		}
		Row bizRec = bizRecTable.iterator().next();
		// 检查案卷启动，且是FlowTo而不是waiting
		if (checkBizRecLimitDate && !currentAI.activation()) {
			String limitEffectActivity = ModelExtUtils.getProcessLimitEffectActivity(this.process);
			// 当前环节未生效环节
			if (currentAI.getActivity().getName().equals(limitEffectActivity))
				setBizRecLimitDate(currentAI.getPI(), bizRec, now);
		}

		Task currentTask = ProcessUtils.getTaskInProcessContext();
		// 1. 计算当前任务(任务消息)的耗时
		setTaskLostDays(currentTask, now);
		// 2. 计算任务实例的耗时
		if (!currentAI.getId().equals(currentTask.getId()) && !currentAI.activation()) {
			// 如果任务实例 不等于任务,AI为分组任务
			setTaskLostDays(currentAI.getTask(), now);
		}
		// FlowTo的环节分组,判断新的任务实例是否在同组内
		Map<String, JSONObject> flowToGroups = getFlowToActivityGroups(currentAI.getAllNextAIs());

		// 3.计算当前环节分组的耗时
		ActivityGroupInstance currentGroup = ActivityGroupInstance.loadByTask(currentAI.getId());
		if (currentGroup != null) { // 兼容老版本无时限
			if (!currentAI.activation() && !flowToGroups.containsKey(currentGroup.getGroupId())) {
				// 如果当前AI已结束 且 flowTo中不含当前分组，判断是否group内的其他AI是否全部结束
				ProcessInstance pi = currentAI.getPI();
				boolean groupFinished = true;
				for (GroupInstanceTask groupTask : currentGroup.getTasks()) {
					ActivityInstance ai = pi.getAI(groupTask.getTaskId());
					if (ai.activation()) {
						groupFinished = false;
						break;
					}
				}
				// 分组结束,半天计时
				if (groupFinished) {
					String limitKind = currentGroup.getLimitKind();
					currentGroup.setEndTime(now);
					Double lostDays = WorkDayUtils.calcLostDaysBetween(currentGroup.getStartTime(), now, limitKind, true, true);
					Float nums = currentGroup.getLostDays();
					if (nums == null)
						currentGroup.setLostDays(lostDays.floatValue());
					else
						currentGroup.setLostDays(nums + lostDays.floatValue());
					if (currentGroup.getLimitTime() != null) {
						lostDays = WorkDayUtils.remainedDays(now, currentGroup.getLimitTime(), limitKind, true);
						currentGroup.setRemainingDays(lostDays.floatValue());
					} else {
						currentGroup.setRemainingDays(null);
					}
					currentGroup.save();
				}
			}
		}

		// 4.计算flowTo环节分组
		for (JSONObject group : flowToGroups.values()) {
			ActivityGroupInstance flowToGroup = ActivityGroupInstance.findFlowActivityGroup(currentAI.getTask().getFlow(), group.getString("id"));
			if (flowToGroup != null) {
				// 分组已存在
				if (flowToGroup.getEndTime() != null) {
					// 已结束，二次进入
					flowToGroup.setStartCount(flowToGroup.getStartCount() + 1);
					flowToGroup.setStartTime(now);
					flowToGroup.setEndTime(null);
				}
			} else {
				// 新的分组
				BigDecimal limitDays = null;
				String limitKind = null;
				Date date;
				if (group.containsKey("limitDateExpr")) {
					date = (Date) ExpressEngine.calculate(group.getString("limitDateExpr"), null, ModelUtils.getModel("/base/core/logic/fn"));
				} else {
					if (group.containsKey("limitDays"))
						limitDays = group.getBigDecimal("limitDays");
					if (group.containsKey("limitKind"))
						limitKind = group.getString("limitKind");
					date = WorkDayUtils.calcDateAfterDays(now, limitDays, limitKind, this.includeStartDate);
				}
				if (Utils.isEmptyString(limitKind))
					limitKind = "工作日";
				Timestamp limitDate = date != null ? new Timestamp(date.getTime()) : null;
				flowToGroup = new ActivityGroupInstance();
				flowToGroup.setBizId(bizRec.getString("fBizId"));
				flowToGroup.setBizRecId(bizRec.getString("fBizRecId"));
				flowToGroup.setFlowId(bizRec.getString("fFlowId"));
				flowToGroup.setGroupId(group.getString("id"));
				flowToGroup.setGroupName(group.getString("name"));
				flowToGroup.setLimitDays(limitDays);
				flowToGroup.setLimitKind(limitKind);
				flowToGroup.setLimitTime(limitDate);
				if (limitDate == null) {
					flowToGroup.setRemainingDays(null);
				} else {
					Double days = WorkDayUtils.remainedDays(now, limitDate, limitKind, true);
					flowToGroup.setRemainingDays(days.floatValue());
				}
				flowToGroup.setStartCount(1);
				flowToGroup.setStartTime(now);
			}
			// 设置组内环节时限
			JSONArray groupTasks = group.getJSONArray("tasks");
			for (int i = 0; i < groupTasks.size(); i++) {
				Task aiTask = (Task) groupTasks.get(i);
				calcAndUpdateTaskLimitTime(aiTask, now, flowToGroup.getLimitKind(), flowToGroup.getLimitTime(), flowToGroup.getLimitKind());
			}
			flowToGroup.newTasks(groupTasks);
			flowToGroup.save();
		}
		if (saveBizRec)
			bizRecTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
	}

	/**
	 * 流转
	 * 
	 * @param actionContext
	 *            @
	 */
	public void advance(ActionContext actionContext) {
		try {
			derivedFromProcessControl(ProcessUtils.getProcessContext().getProcessControl(), true);
		} catch (Exception e) {
			throw new BizSystemException("发送下一环节计算流程时限异常", e);
		}

	}

	/**
	 * 回退
	 * 
	 * @param actionContext
	 *            @
	 */
	public void back(ActionContext actionContext) {
		try {
			derivedFromProcessControl(ProcessUtils.getProcessContext().getProcessControl(), false);

		} catch (Exception e) {
			throw new BizSystemException("发送下一环节计算流程时限异常", e);
		}
	}

	/**
	 * 移交
	 * 
	 * @param actionContext
	 */
	public void transform(ActionContext actionContext) {
		try {
			derivedFromProcessControl(ProcessUtils.getProcessContext().getProcessControl(), false);
		} catch (Exception e) {
			throw new BizSystemException("发送下一环节计算流程时限异常", e);
		}
	}

	/**
	 * 跳转
	 * 
	 * @param actionContext
	 */
	public void special(ActionContext actionContext) {
		try {
			derivedFromProcessControl(ProcessUtils.getProcessContext().getProcessControl(), true);
		} catch (Exception e) {
			throw new BizSystemException("发送下一环节计算流程时限异常", e);
		}
	}

	/**
	 * 挂起
	 * 
	 * @param actionContext
	 *            @
	 */
	public void suspend(ActionContext actionContext, JSONObject suspendInfo) {
		try {
			SuspendKind kind = SuspendKind.valueOf((String) suspendInfo.get("suspendKind"));
			if (kind.equals(SuspendKind.skSubmit)) {
				finish(actionContext);
			}
		} catch (Exception e) {
			throw new BizSystemException("解除挂起计算流程时限异常", e);
		}

	}

	/**
	 * 解挂，转报办结不会进入限办处理
	 * 
	 * @param actionContext
	 * @param suspendDays
	 *            挂起天数
	 */
	public void resume(ActionContext actionContext, Integer suspendDays) {
		ActivityInstance currentAI = ProcessUtils.getAI();

		Table bizRecTable = ProcessLogicPluginContext.findTableControlObjectTarget(FlowBizConsts.CONCEPT_BizRec);
		boolean saveBizRec = bizRecTable == null;
		if (saveBizRec) {
			bizRecTable = loadBizRecTable(currentAI.getTask().getData1());
		}

		// 1.案卷限办推迟
		{
			Row bizRec = bizRecTable.iterator().next();
			bizRec.setInt("fSuspendDays", (bizRec.getValue("fSuspendDays") == null ? 0 : bizRec.getInt("fSuspendDays")) + suspendDays.intValue());
			if (bizRec.getDateTime("fLimitDate") != null) {
				String limitKind = bizRec.getString("fLimitKind");
				Date newLimitDate = WorkDayUtils.calcDateAfterDays(bizRec.getDateTime("fLimitDate"), new BigDecimal(suspendDays), limitKind, false);
				bizRec.setDateTime("fLimitDate", new Timestamp(newLimitDate.getTime()));
			}
		}

		// 2.任务分组推迟
		List<ActivityGroupInstance> groupInstances = ActivityGroupInstance.loadByFlow(currentAI.getTask().getFlow(), true);
		ProcessInstance pi = currentAI.getPI();
		for (ActivityGroupInstance groupInstance : groupInstances) {
			// 分组挂起天数
			if (groupInstance.getSuspendDays() == null) {
				groupInstance.setSuspendDays(suspendDays);
			} else {
				groupInstance.setSuspendDays(groupInstance.getSuspendDays() + suspendDays);
			}

			// 分组限办日期
			if (groupInstance.getLimitTime() != null) {
				String limitKind = groupInstance.getLimitKind();
				Date limitTime = WorkDayUtils.calcDateAfterDays(groupInstance.getLimitTime(), new BigDecimal(suspendDays), limitKind, false);
				groupInstance.setLimitTime(new Timestamp(limitTime.getTime()));
			}
			for (GroupInstanceTask groupTask : groupInstance.getTasks()) {
				// 3.任务实例推迟
				Task aiTask = pi.getAI(groupTask.getTaskId()).getTask();
				if (aiTask.getLimitTime() != null) {
					String limitKind = groupInstance.getLimitKind();
					Date limitTime = WorkDayUtils.calcDateAfterDays((Date) aiTask.getLimitTime(), new BigDecimal(suspendDays), limitKind, false);
					updateTaskLimitTime(aiTask, new Timestamp(limitTime.getTime()), limitKind, groupInstance.getLimitTime(),
							groupInstance.getLimitKind());
				}
			}
			groupInstance.save();
		}
		if (saveBizRec)
			bizRecTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
	}

}
