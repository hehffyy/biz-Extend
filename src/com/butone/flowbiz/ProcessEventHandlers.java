package com.butone.flowbiz;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.Node;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.butone.extend.BizLogicPluginEx;
import com.butone.extend.BizRuleExt;
import com.butone.extend.CacheManager;
import com.butone.extend.ProcessInfo;
import com.butone.extend.TaskUtils;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.butone.model.BizRule;
import com.butone.spi.FlowControlUtils;
import com.butone.spi.JsonUtilsUtils;
import com.butone.utils.ContextUtils;
import com.butone.utils.ModelExtUtils;
import com.justep.exception.BusinessException;
import com.justep.model.Activity;
import com.justep.model.Config;
import com.justep.model.Model;
import com.justep.model.ModelObject;
import com.justep.model.ModelUtils;
import com.justep.model.Procedure;
import com.justep.model.Process;
import com.justep.system.action.ActionUtils;
import com.justep.system.context.ActionContext;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.BizData;
import com.justep.system.data.DatabaseProduct;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.SQL;
import com.justep.system.data.Table;
import com.justep.system.data.TableUtils;
import com.justep.system.process.ProcessContext;
import com.justep.system.process.ProcessControl;
import com.justep.system.process.ProcessControlItem;
import com.justep.system.process.ProcessEngine;
import com.justep.system.process.ProcessUtils;
import com.justep.system.process.Task;
import com.justep.system.process.TaskDB;
import com.justep.system.process.TaskStatus;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class ProcessEventHandlers {
	private static Log logger = LogFactory.getLog(ProcessEventHandlers.class);

	/**
	 * 执行事件外部。监听实现的model路径位于/base/core/flow/logic/action/
	 * externalProcessEventHandler.config.m。执行优先于组件规则和流程组件
	 * 
	 * @param event
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InstantiationException
	 */
	@SuppressWarnings("unchecked")
	private static void invokeExternalProcessEventHandler(String event) throws Exception {
		String activity = ContextHelper.getActionContext().getActivity().getFullName();
		Config config = ModelUtils.getModel("/base/core/flow/logic/action").getUseableConfig("listener-model");
		if (config == null)
			return;
		Set<String> names = config.getNames();
		Map<Method, Integer> methods = new HashMap<Method, Integer>();
		Map<Method, String> methodDescs = new HashMap<Method, String>();
		for (String name : names) {
			Model m = ModelUtils.getModel(config.getValue(name));
			List<ModelObject> objList = m.getLocalObjectsByType(Procedure.TYPE);
			for (ModelObject obj : objList) {
				Procedure p = (Procedure) obj;
				List<String> targetActivities = (List<String>) p.getExtAttributeValue(ModelExtUtils.MODEL_EXT_URI, "targetActivities");
				List<String> targetEvents = (List<String>) p.getExtAttributeValue(ModelExtUtils.MODEL_EXT_URI, "targetEvents");
				if (targetActivities != null && !targetActivities.contains(activity)) {
					continue;
				}
				if (targetEvents != null && !targetEvents.contains(event)) {
					continue;
				}
				String desc = "【" + name + ":" + m.getFullName() + "】下的流程监听处理器【" + p.getCodeModelName() + "/" + p.getCode() + "】";
				try {
					Method localMethod = ModelUtils.getModel(p.getCodeModelName()).getModelMethod(p.getCode());
					methodDescs.put(localMethod, desc);
					methods.put(localMethod, (Integer) p.getExtAttributeValue(ModelExtUtils.MODEL_EXT_URI, "order"));
				} catch (Exception e) {
					throw new InvocationTargetException(e, "加载" + desc + "失败");
				}
			}
		}
		List<Entry<Method, Integer>> list = new ArrayList<Entry<Method, Integer>>(methods.entrySet());

		// 然后通过比较器来实现排序
		Collections.sort(list, new Comparator<Entry<Method, Integer>>() {
			// 升序排序
			public int compare(Entry<Method, Integer> m1, Entry<Method, Integer> m2) {
				Integer o1 = m1.getValue();
				Integer o2 = m2.getValue();
				if (o1 == null && o2 == null) {
					return 0;
				} else if (o1 != null && o2 != null) {
					return o1.compareTo(o2);
				} else if (o1 == null) {
					return 1;
				} else {
					return -1;
				}
			}
		});

		for (Entry<Method, Integer> e : list) {
			Method m = e.getKey();
			int modifiers = m.getModifiers();
			try {
				if (Modifier.isStatic(modifiers)) {
					m.invoke(null, new Object[0]);
				} else {
					m.invoke(m.getDeclaringClass().newInstance(), new Object[0]);
				}
			} catch (Exception ex) {
				throw new InvocationTargetException(ex, "执行" + methodDescs.get(m) + "失败");
			}
		}
	}

	/**
	 * action提交后处理器
	 * 
	 * @param actionContext
	 */
	public static void actionAfterCommitHandler(ActionContext actionContext) {
		Process p = actionContext.getProcess();
		if (!ModelExtUtils.isBizCoopProcess(p)) {
			String event = "commit/", actionName = actionContext.getAction().getName();
			if (actionName.equals("transferTaskAction")) {
				event += "移交";
			} else if (actionName.equals("finishProcessAction")) {
				event += "办结";
			} else if (actionName.equals("specialProcessAction")) {
				event += "跳转";
			} else if (actionName.equals("resumeProcessAction")) {
				event += "解挂";
			} else if (actionName.equals("suspendProcessAction")) {
				event += "挂起";
			} else if (actionName.equals("abortProcessAction")) {
				event += "作废";
			} else if (actionName.equals("backProcessAction")) {
				event += "回退";
			} else if (actionName.equals("advanceProcessAction")) {
				if (actionContext.containsKey(ModelExtUtils.Activity_asyncAdvance)) {
					return;
				}
				event += "流转";
			} else if (actionName.equals("startProcessAction")) {
				event += "启动";
			} else {
				logger.error(actionName + "不支持提交后的业务组件");
				return;
			}
			ProcessContext processContext = (ProcessContext) actionContext.get("process-context");
			String bizRecId = null;
			if (processContext != null) {
				if (processContext.getTask() != null)
					bizRecId = processContext.getTask().getData1();
				else if (processContext.getPI() != null)
					bizRecId = processContext.getPI().getTask().getData1();
				ProcessLogicPluginContext context = ProcessLogicPluginContext.createLogicPluginContext(actionContext.getProcess(),
						actionContext.getActivity(), bizRecId, actionContext);
				try {
					executeBizLogicPlugin(event, bizRecId, context);
				} finally {
					ProcessLogicPluginContext.removeLogicPluginContext(context, true);
				}
			}

		}
	}

	public static void updateStatus(String concept, String id, String status) {
		HashMap<String, Object> keyValues = new HashMap<String, Object>();
		keyValues.put("fStatus", status);
		updateConceptById(concept, id, keyValues, FlowBizConsts.DATA_MODEL_CORE_FLOW);
	}

	/**
	 * 更新概念数据
	 * 
	 * @param conpect
	 * @param id
	 * @param keyValues
	 * @return
	 */
	public static int updateConceptById(String conpect, Object id, Map<String, Object> keyValues, String dataModel) {
		if (keyValues == null || keyValues.size() == 0) {
			return 0;
		}
		HashMap<String, Object> varMap = new HashMap<String, Object>();
		StringBuffer updateFields = new StringBuffer("update ").append(conpect).append(" t ").append(" set ");
		Iterator<Entry<String, Object>> iKeyValue = keyValues.entrySet().iterator();
		while (iKeyValue.hasNext()) {
			Entry<String, Object> keyV = iKeyValue.next();
			String fldName = keyV.getKey();
			updateFields.append("t.").append(fldName).append(" = :").append(fldName);
			if (iKeyValue.hasNext()) {
				updateFields.append(",");
			}
		}
		varMap.putAll(keyValues);
		updateFields.append(" where ").append("t").append("=").append(":").append("id");
		varMap.put("id", id);
		return KSQL.executeUpdate(updateFields.toString(), varMap, dataModel, null);
	}

	private static boolean isBizCoopProcess() {
		Process p = ContextHelper.getActionContext().getProcess();
		return ModelExtUtils.isBizCoopProcess(p);
	}

	/**
	 * 为业务动作准备限办管理器
	 * 
	 * @return
	 */
	private static ProcessLimitManager prepareProcessLimitManager() {
		ActionContext actionContext = ContextHelper.getActionContext();
		ProcessLogicPluginContext context = ProcessLogicPluginContext.findLogicPluginContext(actionContext.getActivity(),
				ProcessUtils.getProcessData1());
		if (context == null)
			context = ProcessLogicPluginContext.createLogicPluginContext(actionContext.getProcess(), actionContext.getActivity(),
					ProcessUtils.getProcessData1());
		context.loadFlowSysTables();
		return new ProcessLimitManager(context.getProcess());
	}

	/**
	 * -------------------------------------------------------------------流程启动--
	 * ------------------------------------------------------
	 */

	/**
	 * 流程启动前事件 1.如果是业务流程且设置了coopProcess，则启动业务协同
	 */
	public static void beforeProcessStartHandler() throws Exception {
		if (!isBizCoopProcess()) {
			invokeExternalProcessEventHandler("before/启动");

			Process p = ContextHelper.getActionContext().getProcess();
			// 1.判断是否包含coopProcess属性
			JSONObject param = ModelExtUtils.getProcessCoopProcess(p);
			if (param != null) {
				FlowControlUtils.startBizCooperationFlow(param.getString("process"), param.getString("activity"));
			}
			ActionContext actionContext = ContextHelper.getActionContext();
			@SuppressWarnings("unchecked")
			String bizRecId = (String) ((Map<String, Object>) actionContext.getParameter("attributes")).get("sData1");
			executeBizLogicPlugin("before/启动", bizRecId);
			checkBizRule("启动", bizRecId);
		}
	}

	/**
	 * 流程启动后事件 1.如果不是业务协同过程，更新业务案卷表的流程ID
	 */
	public static void afterProcessStartHandler() throws Exception {
		if (!isBizCoopProcess()) {
			ActionContext actionContext = ContextHelper.getActionContext();
			ProcessLogicPluginContext context = ProcessLogicPluginContext.findLogicPluginContext(actionContext.getActivity(),
					ProcessUtils.getProcessData1());
			Object obj = actionContext.getActionResult();
			if (obj instanceof Document) {
				// 5.2
				String taskId = ((Document) obj).selectSingleNode("/items/item/task").getText();
				context.setParameter("@task", taskId);
			} else if (obj instanceof List) {
				// 5.3
				@SuppressWarnings("rawtypes")
				Map ret = (Map) ((List) actionContext.getActionResult()).get(0);
				context.setParameter("@task", ret.get("task"));
			}
			ProcessLimitManager limitManager = new ProcessLimitManager(context.getProcess());
			limitManager.start(actionContext);
			//
			executeBizLogicPlugin("after/启动", ProcessUtils.getProcessData1());
			executeBizRuleAction("启动", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/启动");
		}
	}

	/**
	 * -----------------------------------------------------------------------办结
	 * --------------------------------------------------------
	 */
	/**
	 * 办结前事件
	 */
	public static void beforeProcessFinishHandler() throws Exception {
		if (!isBizCoopProcess()) {
			invokeExternalProcessEventHandler("before/办结");

			ProcessControl control = ProcessUtils.getProcessContext().getProcessControl();
			Object finishInfo = control.getExt("finishInfo");

			FinishRuntime runtime = new FinishRuntime(ProcessUtils.getProcessData1());
			if (finishInfo == null) {
				Map<String, Table> tables = new HashMap<String, Table>();
				tables.put(FinishRuntime.CONCEPT_B_BJJLB, runtime.createDefaultBJJLBTable());
				runtime.applyFinish(BizRecStatus.bsFinished, FinishKind.fkNormal, tables);
			} else {
				runtime.applyFinish(BizRecStatus.bsFinished, (JSONObject) JsonUtilsUtils.toFastJSON(finishInfo));
			}

			executeBizLogicPlugin("before/办结", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 流程办结后事件 1.业务办结后驱动业务协同进行流转 2.同步状态
	 */
	public static void afterProcessFinishHandler() throws Exception {
		Task currTask = ProcessUtils.getTaskInProcessContext();
		// 1.获取更新状态参数
		if (isBizCoopProcess()) {
			updateStatus(FlowBizConsts.CONCEPT_BizCooperation, currTask.getData1(), FlowBizConsts.BizCoop_Status_Finish);
		} else {
			deleteFlowCanceledExecutor();

			// 业务流程
			Task t = ProcessUtils.getTaskInProcessContext();
			String suspendTaskId = t.getData3();
			if (suspendTaskId != null) {
				// 只有入口流程才有suspendTaskId
				FlowControlUtils.advanceBizCooperationProcess(suspendTaskId);
				updateStatus(FlowBizConsts.CONCEPT_BizApprove, currTask.getData2(), FlowBizConsts.BizApprove_Status_Finish);
			}
			//
			ProcessLimitManager limitManager = prepareProcessLimitManager();
			limitManager.finish(ContextHelper.getActionContext());
			//
			executeBizLogicPlugin("after/办结", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/办结");

		}

	}

	/**
	 * -----------------------------------------------------------------------挂起
	 * --------------------------------------------------------
	 */

	/**
	 * 挂起查询后
	 */
	public static void afterProcessSuspendQueryHandler() {
		if (!isBizCoopProcess()) {
			checkBizRule("挂起", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 挂起前
	 */
	public static void beforeProcessSuspendHandler() throws Exception {
		if (!isBizCoopProcess()) {
			invokeExternalProcessEventHandler("before/挂起");

			ProcessControl control = ProcessUtils.getProcessContext().getProcessControl();
			Object suspendInfo = control.getExt("suspendInfo");
			Utils.check(suspendInfo != null, "非法的服务请求,不存在挂起信息");

			SuspendRuntime suspend = new SuspendRuntime(ProcessUtils.getProcessData1());
			suspend.applySuspend((JSONObject) JsonUtilsUtils.toFastJSON(suspendInfo));

			executeBizLogicPlugin("before/挂起", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 挂起后
	 */
	public static void afterProcessSuspendHandler() throws Exception {
		if (!isBizCoopProcess()) {
			ProcessLimitManager limitManager = prepareProcessLimitManager();
			ProcessControl control = ProcessUtils.getProcessContext().getProcessControl();
			Object suspendInfo = control.getExt("suspendInfo");
			Utils.check(suspendInfo != null, "非法的服务请求,不存在挂起信息");
			limitManager.suspend(ContextHelper.getActionContext(), (JSONObject) JsonUtilsUtils.toFastJSON(suspendInfo));

			//
			executeBizLogicPlugin("after/挂起", ProcessUtils.getProcessData1());
			executeBizRuleAction("挂起", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/挂起");
		}
	}

	/**
	 * -------------------------------------------------------解挂----------------
	 * -----------------------------------------
	 */
	/**
	 * 解挂前事件
	 */
	public static void beforeProcessResumeHandler() throws Exception {
		if (!isBizCoopProcess()) {
			ActionContext actionContext = ContextHelper.getActionContext();
			String task = (String) actionContext.getParameter("task");
			ProcessEngine localProcessEngine = new ProcessEngine(task, null);
			ProcessUtils.addProcessContext(localProcessEngine);
			try {
				invokeExternalProcessEventHandler("before/解挂");
				executeBizLogicPlugin("before/解挂", ProcessUtils.getProcessData1());
			} finally {
				ProcessUtils.removeProcessContext();
			}
		}
	}

	/**
	 * 解挂后事件
	 */
	public static void afterProcessResumeHandler() throws Exception {
		if (!isBizCoopProcess()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> suspendInfo = (Map<String, Object>) ContextHelper.getRequestContext().get(
					FlowBizConsts.RequestContext_BizRecSuspendInfo);
			SuspendRuntime suspend = new SuspendRuntime(ProcessUtils.getProcessData1());

			Integer suspendDays = suspend.resume(suspendInfo);
			// 转报办结的挂起天数为null,无需改变限办日期
			if (suspendDays != null) {
				ProcessLimitManager limitManager = prepareProcessLimitManager();
				limitManager.resume(ContextHelper.getActionContext(), suspendDays);
			}

			//
			executeBizLogicPlugin("after/解挂", ProcessUtils.getProcessData1());
			executeBizRuleAction("解挂", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/解挂");
		}
	}

	/**
	 * -----------------------------------------------------------------------回退
	 * --------------------------------------------------------
	 */

	/**
	 * 回退查询后
	 */
	public static void afterProcessBackQueryHandler() {
		if (!isBizCoopProcess()) {
			checkBizRule("回退", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 回退前
	 */
	public static void beforeProcessBackHandler() throws Exception {
		if (!isBizCoopProcess()) {
			invokeExternalProcessEventHandler("before/回退");
			executeBizLogicPlugin("before/回退", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 回退后
	 */
	public static void afterProcessBackHandler() throws Exception {
		if (!isBizCoopProcess()) {
			deleteTaskCanceledExecutor();

			ProcessLimitManager limitManager = prepareProcessLimitManager();
			limitManager.back(ContextHelper.getActionContext());
			//
			executeBizLogicPlugin("after/回退", ProcessUtils.getProcessData1());
			executeBizRuleAction("回退", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/回退");
		}
	}

	/**
	 * -----------------------------------------------------------------------作废
	 * (终止)--------------------------------------------------------
	 */

	/**
	 * 终止查询后
	 */
	public static void afterProcessAbortQueryHandler() {
		if (!isBizCoopProcess()) {
			checkBizRule("作废", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 终止前
	 */
	public static void beforeProcessAbortHandler() throws Exception {
		if (!isBizCoopProcess()) {
			Task piTask = ProcessUtils.getPI().getTask();
			if (TaskStatus.SUSPEND.equals(piTask.getStatus())) {
				// 补交不来办结，欺骗AbortProcessEngine，流程时活动的
				piTask.setStatus(TaskStatus.EXECUTING);
				List<Task> aiTasks = piTask.getChildren();
				for (Task aiTask : aiTasks) {
					if (TaskStatus.SUSPEND.equals(aiTask.getStatus())) {
						aiTask.setStatus(TaskStatus.EXECUTING);
						List<Task> executorTasks = aiTask.getChildren();
						for (Task executorTask : executorTasks) {
							if (TaskStatus.SUSPEND.equals(executorTask.getStatus()))
								executorTask.setStatus(TaskStatus.EXECUTING);
						}
					}
				}
			}
			invokeExternalProcessEventHandler("before/作废");

			ProcessControl control = ProcessUtils.getProcessContext().getProcessControl();
			Object finishInfo = control.getExt("finishInfo");
			Utils.check(finishInfo != null, "非法的服务请求,不存在挂起信息");
			new FinishRuntime(ProcessUtils.getProcessData1()).applyFinish(BizRecStatus.bsAborted, (JSONObject) JsonUtilsUtils.toFastJSON(finishInfo));
			executeBizLogicPlugin("before/作废", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * 1.普通业务流程终止 2.业务协同回退，终止当前入口流程
	 */
	public static void afterProcessAbortHandler() throws Exception {
		ActionContext context = ActionUtils.getRequestContext().getActionContext();
		Task currTask = ProcessUtils.getTaskInProcessContext();
		if (isBizCoopProcess()) {
			// 业务协同
			updateStatus(FlowBizConsts.CONCEPT_BizCooperation, currTask.getData1(), FlowBizConsts.BizCoop_Status_Abort);
		} else {
			deleteFlowCanceledExecutor();

			String processAction = (String) context.getParameter(FlowBizConsts.PROCESS_ACTION);
			if ("backBizCoopProcess".equals(processAction)) {
				// 业务协同回退：终止入口流程
				updateStatus(FlowBizConsts.CONCEPT_BizApprove, currTask.getData2(), FlowBizConsts.BizApprove_Status_Returned);
			} else {
				updateStatus(FlowBizConsts.CONCEPT_BizApprove, currTask.getData2(), FlowBizConsts.BizApprove_Status_Abort);
			}

			//
			ProcessLimitManager limitManager = prepareProcessLimitManager();
			limitManager.abort(context);
			//
			executeBizLogicPlugin("after/作废", ProcessUtils.getProcessData1());
			executeBizRuleAction("作废", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/作废");
		}

	}

	/**
	 * -----------------------------------------------------------------------流转
	 * --------------------------------------------------------
	 */

	/**
	 * process流转查询时，检查此环节是否存在业务规则
	 */
	public static void afterProcessAdvanceQueryHandler() {
		if (!isBizCoopProcess()) {
			Process p = ContextHelper.getActionContext().getProcess();
			ProcessControl processControl = (ProcessControl) ContextHelper.getActionContext().getActionResult();
			for (ProcessControlItem item : processControl.getFlowTos()) {
				if (item.isEnd()) {
					processControl.getExts().put("finishKind", ModelExtUtils.getProcessFinishKind(p));
					break;
				}
			}
			boolean triggerRule = checkBizRule("流转", ProcessUtils.getProcessData1());
			if (!triggerRule) {
				boolean disable = false;
				if (processControl.getFlowTos().isEmpty()) {
					disable = true;
				} else if (processControl.getFlowTos().size() == 1) {
					// 未触发业务规则，如果是静默办结 或者 执行者及范围只有一个且完全相同，那么关闭对话框，直接办结或流转
					disable = processControl.getFlowTos().isEmpty()
							|| processControl.getFlowTos().get(0).isEnd()
							&& (ModelExtUtils.isSilenceFinish(p) && FinishKind.fkNormal.name().equals(ModelExtUtils.getProcessFinishKind(p)))
							|| processControl.getFlowTos().get(0).getExecutors().size() == 1
							&& (processControl.getFlowTos().get(0).getExecutorRange().size() == 0 || processControl.getFlowTos().get(0)
									.getExecutorRange().size() == 1
									&& processControl.getFlowTos().get(0).getExecutorRange().get(0)
											.equals(processControl.getFlowTos().get(0).getExecutors().get(0)));
				}
				processControl.setDialogEnabled(!disable);
			}
		}
	}

	private static void deleteTaskCanceledExecutor() {
		ProcessEngine engine = (ProcessEngine) ProcessUtils.getProcessContext();
		Task task = engine.getTask();
		int n = 0;
		if (task.executorIsPerson()) {
			task = task.getParent();
		}
		Iterator<Task> itorExecutor = task.getChildren().iterator();
		while (itorExecutor.hasNext()) {
			Task executor = itorExecutor.next();
			if (TaskStatus.CANCELED.equals(executor.getStatus())) {
				task.removeChild(executor);
				n++;
			}
		}
		logger.debug("删除" + n + "条已放弃的任务消息");
	}

	private static void deleteFlowCanceledExecutor() {
		ProcessEngine engine = (ProcessEngine) ProcessUtils.getProcessContext();
		Task rootTask = engine.getRootTask();
		Iterator<Task> itorTask = rootTask.getChildren().iterator();
		int n = 0;
		while (itorTask.hasNext()) {
			Task task = itorTask.next();
			Iterator<Task> itorExecutor = task.getChildren().iterator();
			while (itorExecutor.hasNext()) {
				Task executor = itorExecutor.next();
				if (TaskStatus.CANCELED.equals(executor.getStatus())) {
					task.removeChild(executor);
					n++;
				}

			}
		}
		logger.debug("删除" + n + "条已放弃的任务消息");
	}

	/**
	 * 流程流转后事件<br>
	 * 1.如果当前过程是业务协同，那么启动业务审批过程 2.如果是业务流程，且包含流程协同的接收环节，那么检查流入的环节是否接收环节
	 * 
	 * @throws JSONException
	 */
	public static void afterProcessAdvanceHandler() throws Exception {
		ActionContext context = ContextHelper.getActionContext();
		if (isBizCoopProcess()) {
			// 如果是业务协同过程,启动环节对应的业务审批入口流程
			startBizCoopActivityProcess(context.getProcess());
		} else {
			// 忽略异步批转
			if (context.containsKey(ModelExtUtils.Activity_asyncAdvance)) {
				return;
			}

			deleteTaskCanceledExecutor();
			// 如果是业务流程
			// 1.流程协作接收检查
			if (ModelExtUtils.isIncludeReceiveActivity(context.getProcess())) {
				checkFlowToIsCoopReceiver(context.getProcess());
			}
			// 2.时限计算
			ProcessControl processControl = (ProcessControl) ContextHelper.getActionContext().getParameter("control");
			ProcessLimitManager limitManager = prepareProcessLimitManager();
			limitManager.advance(context);

			// 3.准备组件环境
			ProcessLogicPluginContext pluginContext = ProcessLogicPluginContext.findLogicPluginContext(context.getActivity(),
					ProcessUtils.getProcessData1());
			pluginContext.setParameter("@batchSrcBizRecID", processControl.getExt("batchSrcBizRecID"));

			// 4. 执行业务组件
			executeBizLogicPlugin("after/流转", ProcessUtils.getProcessData1());
			// 5. 执行业务规则动作
			executeBizRuleAction("流转", ProcessUtils.getProcessData1());
			// 6. 执行外部监听器
			invokeExternalProcessEventHandler("after/流转");

			// 7. 最后批量处理
			Object batchData = processControl.getExts().remove("batchData");
			if (batchData != null) {
				processControl.getExts().clear();
				// 将批量操作的源案卷编号压入流程扩展属性
				processControl.addExt("batchSrcBizRecID", ProcessUtils.getProcessData1());
				JSONObject json = (JSONObject) processControl.writerToJson(null);
				String batchGUID = doProcessBatchData((JSONObject) JsonUtilsUtils.toFastJSON(batchData), "advance", json.toJSONString());
				Map<String, Object> contextData = new HashMap<String, Object>();
				contextData.put("@batchGUID", batchGUID);
				executeBizLogicPlugin("after/批量流转", ProcessUtils.getProcessData1(), contextData);
			}
		}
		// 如果后续环节状态是处理中，更新成未处理
		Set<Task> nextTasks = ProcessUtils.getNextTasks();
		// List<Task> nextTasks =
		// ProcessUtils.getTaskInProcessContext().getAllNextActiveTask();
		for (Task task : nextTasks) {
			if (task.getStatus().equals(TaskStatus.READY))
				continue;
			HashMap<String, Object> keyValues = new HashMap<String, Object>();
			keyValues.put("sStatusID", TaskStatus.READY);
			keyValues.put("sStatusName", TaskStatus.getReadyName());
			updateConceptById("SA_Task", task.getId(), keyValues, FlowBizConsts.DATA_MODEL_SYSTEM);
		}
	}

	/**
	 * 批处理
	 * 
	 * @param batchData
	 * @param action
	 */
	private static String doProcessBatchData(JSONObject batchData, String action, String parameter) {
		//
		JSONArray tasks = batchData.getJSONArray("tasks");
		if (tasks != null) {
			Task t = ProcessUtils.getTaskInProcessContext();
			// 插入批量操作sql
			String boGUID = CommonUtils.createGUID();
			Table boTable = TableUtils.createTable("B_BatchOperation", FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
			boTable.getMetaData().setKeyColumn("FGUID");
			Row bor = boTable.appendRow(boGUID);
			bor.setInteger("version", 0);
			bor.setString("fOperation", action);
			bor.setString("fOperationName", "");
			bor.setDateTime("fCreateTime", CommonUtils.getCurrentDateTime());
			bor.setString("fCreatorID", ContextHelper.getOperator().getID());
			bor.setString("fCreatorName", ContextHelper.getPersonMemberNameWithAgent());
			bor.setString("fCreatorFID", ContextHelper.getPersonMember().getFID());
			bor.setString("fSrcTaskID", t.getId());
			bor.setString("fSrcBizRecID", t.getData1());
			bor.setValue("fParameter", parameter);
			boTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);

			Table botTable = TableUtils.createTable("B_BatchOperationTask", FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
			botTable.getMetaData().setKeyColumn("FGUID");

			String taskSql = "update sa_task t set sstatusid='tesSleeping',sstatusName = '批处理中',version=version+1,sLock=? where sid=? and sstatusid in ('tesExecuting','tesReady')";
			for (int i = 0; i < tasks.size(); i++) {
				String task = tasks.getString(i);
				if (task.equals(t.getId()))
					continue;
				// 插入批量任务sql
				Row botr = botTable.appendRow(CommonUtils.createGUID());
				botr.setInteger("version", 0);
				botr.setString("fTaskId", task);
				botr.setString("fStatus", "等待中");
				botr.setString("fBatchGuid", boGUID);

				// 修改任务状态
				Map<String, String> batchStatusSql = new HashMap<String, String>();
				List<Object> binds = new ArrayList<Object>();
				binds.clear();
				binds.add(CommonUtils.createGUID());
				binds.add(task);
				batchStatusSql.clear();
				batchStatusSql.put(DatabaseProduct.DEFAULT.name(), taskSql);
				SQL.executeUpdate(batchStatusSql, binds, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
			}
			botTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
			return boGUID;
		}
		return null;
	}

	/**
	 * 流转前,执行组件
	 */
	public static void beforeProcessAdvanceHandler() throws Exception {
		if (!isBizCoopProcess()) {
			// 忽略异步批转
			ProcessControl control = (ProcessControl) ContextHelper.getActionContext().getParameter("control");
			if (control != null && control.getExts().containsKey(ModelExtUtils.Activity_asyncAdvance)) {
				return;
			}
			invokeExternalProcessEventHandler("before/流转");
			executeBizLogicPlugin("before/流转", ProcessUtils.getProcessData1());
		}
	}

	/**
	 * -----------------------------------------------------------------------跳转
	 * --------------------------------------------------------
	 */
	public static void afterProcessSpecialQueryHandler() {
		if (!isBizCoopProcess()) {
			checkBizRule("跳转", ProcessUtils.getProcessData1());
		}

	}

	public static void beforeProcessSpecialHandler() {
		if (!isBizCoopProcess()) {
			executeBizLogicPlugin("before/跳转", ProcessUtils.getProcessData1());
		}
	}

	public static void afterProcessSpecialHandler() throws Exception {
		if (!isBizCoopProcess()) {
			invokeExternalProcessEventHandler("before/跳转");

			ProcessLimitManager limitManager = prepareProcessLimitManager();
			limitManager.special(ContextHelper.getActionContext());
			//
			executeBizLogicPlugin("after/跳转", ProcessUtils.getProcessData1());
			executeBizRuleAction("跳转", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/跳转");
		}
	}

	/**
	 * -----------------------------------------------------------------------移交
	 * --------------------------------------------------------
	 */
	public static void afterTaskTransferQueryHandler() {
		if (!isBizCoopProcess()) {
			ProcessControl processControl = (ProcessControl) ContextHelper.getActionContext().getActionResult();
			boolean triggerRule = checkBizRule("移交", ProcessUtils.getProcessData1());
			if (!triggerRule && processControl.getFlowTos().size() == 1) {
				// 未触发业务规则，如果是静默办结 或者 执行者及范围只有一个且完全相同，那么关闭对话框，直接办结或流转
				boolean disable = processControl.getFlowTos().get(0).getExecutors().size() == 1
						&& (processControl.getFlowTos().get(0).getExecutorRange().size() == 0 || processControl.getFlowTos().get(0)
								.getExecutorRange().size() == 1
								&& processControl.getFlowTos().get(0).getExecutorRange().get(0)
										.equals(processControl.getFlowTos().get(0).getExecutors().get(0)));
				processControl.setDialogEnabled(!disable);
			}
		}
	}

	public static void beforeTaskTransferHandler() {
		if (!isBizCoopProcess()) {
			executeBizLogicPlugin("before/移交", ProcessUtils.getProcessData1());
		}
	}

	public static void afterTaskTransferHandler() throws Exception {
		if (!isBizCoopProcess()) {
			deleteTaskCanceledExecutor();

			ProcessLimitManager limitManager = prepareProcessLimitManager();
			limitManager.transform(ContextHelper.getActionContext());
			//
			executeBizLogicPlugin("after/移交", ProcessUtils.getProcessData1());
			executeBizRuleAction("移交", ProcessUtils.getProcessData1());
			invokeExternalProcessEventHandler("after/移交");
		}
	}

	/**
	 * ---------------------------------------------------------------案卷表------
	 * --流程协同--------------------------------------------------------
	 */

	/**
	 * 业务协同过程批转后，启动目标业务审批过程
	 */
	private static void startBizCoopActivityProcess(Process p) {
		Document doc = (Document) ContextHelper.getActionContext().getActionResult();
		Task ct = ProcessUtils.getTaskInProcessContext();
		Map<String, Task> tasks = TaskDB.loadFlowByTask(ct.getId());
		@SuppressWarnings("unchecked")
		List<Node> list = doc.selectNodes("/items/item");
		for (Node n : list) {
			String activity = n.selectSingleNode("activity").getText();
			String task = n.selectSingleNode("task").getText();
			Task t = tasks.get(task);
			Activity act = p.getActivity(activity);
			JSONObject param = ModelExtUtils.getActivityApproveEntryProcess(act);
			if (param == null) {
				String lang = ContextUtils.getRequestLanguage();
				throw new RuntimeException("业务协同" + p.getLabel(lang) + "的环节" + act.getLabel(lang) + "未设置入口流程");
			}
			FlowControlUtils.startBizCoopActivityProcess(param.getString("process"), param.getString("activity"), t);
		}
	}

	/**
	 * 那么检查流入的环节是否接收环节
	 */
	private static void checkFlowToIsCoopReceiver(Process p) {
		Document doc = (Document) ContextHelper.getActionContext().getActionResult();
		Task ct = ProcessUtils.getTaskInProcessContext();
		Map<String, Task> tasks = TaskDB.loadFlowByTask(ct.getId());
		@SuppressWarnings("unchecked")
		List<Node> list = doc.selectNodes("/items/item");
		for (Node n : list) {
			String activity = n.selectSingleNode("activity").getText();
			String task = n.selectSingleNode("task").getText();
			Task t = tasks.get(task);
			Activity act = p.getActivity(activity);
			if (ModelExtUtils.isFlowCoopReceiveActivity(act)) {
				// 如果是接收环节
				FlowControlUtils.checkFlowCoopReceiveRecord(p, act, t);
			}
		}
	}

	/**
	 * -公共方法：------------------------------------业务规则查询---
	 * 业务规则动作-----业务插件事件-----------------------------------------------
	 */

	private static boolean isIgnoreRuleWhen(Activity activity, String event) {
		// 如果批量
		Map<String, Object> batchOperationOption = ModelExtUtils.getActivityBatchOperationOption(activity);
		if (batchOperationOption != null) {
			String ignoreRuleWhen = (String) batchOperationOption.get("ignoreRuleWhen");
			return ("," + ignoreRuleWhen + ",").contains("," + event + ",");
		}
		return false;
	}

	/**
	 * 关闭监管规则，只规不管，不产生监管消息
	 * @return
	 */
	private static boolean isSuperviseDisabled() {
		Config c = ModelUtils.getModel("/system/config").getUseableConfig("disableSupervise");
		return c != null && "true".equals(c.getValue());
	}

	/**
	 * 业务规则查询
	 * 
	 * @param event
	 */
	private static boolean checkBizRule(String event, String bizRecId) {
		Object actionResult = ContextHelper.getActionContext().getActionResult();
		// 执行环境判断
		ProcessLogicPluginContext pluginContext = ProcessLogicPluginContext.findLogicPluginContext(ContextHelper.getActionContext().getActivity(),
				bizRecId);
		Utils.check(Utils.isNotNull(pluginContext), ProcessEventHandlers.class, "不存在流程代码逻辑插件运行环境");

		List<Map<String, Object>> processControlExt = new ArrayList<Map<String, Object>>();
		ProcessInfo processInfo = CacheManager.getProcessInfo(ContextHelper.getActionContext().getProcess());

		List<BizRuleExt> bizRules = processInfo.getBizRules(ContextHelper.getActionContext().getActivity().getName(), event);

		// 如果不存在业务规则,则返回
		if (bizRules == null)
			return false;

		BizRuleRuntime bizRuleRuntime = new BizRuleRuntime(pluginContext);
		Task currentTask = TaskUtils.loadTask(ProcessUtils.getTaskInProcessContext().getId());
		boolean isBatch = TaskStatus.SLEEPING.equals(currentTask.getStatus());
		boolean throwErr = event.equals("启动") || isBatch && !isIgnoreRuleWhen(ContextHelper.getActionContext().getActivity(), event);

		for (BizRuleExt ext : bizRules) {
			bizRuleRuntime.setBizRuleEx(ext);
			if (bizRuleRuntime.checkBizRule(event)) {
				BizRule rule = ext.getBizRule();
				if ("禁止规则".equals(rule.getKind())) {
					if (throwErr) {
						throw new BusinessException(bizRuleRuntime.getBizRuleTipInfo());
					}
				}
				Map<String, Object> ruleRsult = new HashMap<String, Object>();
				ruleRsult.put("guid", rule.getGuid());
				ruleRsult.put("type", rule.getKind());
				ruleRsult.put("name", rule.getName());
				if (Boolean.TRUE.equals(rule.getSupervise()) && !isSuperviseDisabled()) {
					ruleRsult.put("supervise", 1);
					if ("禁止规则".equals(rule.getKind())) {
						//TODO 强制保存禁止的监管消息
						saveForbidSuperviseMessage(currentTask.getId(), bizRecId, rule.getGuid(), bizRuleRuntime.getBizRuleTipInfo());
					}
				}
				boolean stop = "提示规则".equals(rule.getKind()) ? false : true;
				ruleRsult.put("stop", stop);
				ruleRsult.put("message", bizRuleRuntime.getBizRuleTipInfo());

				processControlExt.add(ruleRsult);
			}
		}
		if (processControlExt.size() > 0) {
			ProcessControl processControl = (ProcessControl) actionResult;
			processControl.getExts().put("rules", processControlExt);
		}
		return processControlExt.size() > 0;
	}

	/**
	 * 保存禁止监管消息
	 * @param taskId
	 * @param bizRecId
	 * @param ruleId
	 * @param ruleContent
	 */

	private static void saveForbidSuperviseMessage(String taskId, String bizRecId, String ruleId, String ruleContent) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("taskId", taskId);
		varMap.put("bizRecId", bizRecId);
		varMap.put("ruleId", ruleId);
		varMap.put("personId", ContextHelper.getPerson().getID());
		Table t = KSQL.select("select t.* from " + FlowBizConsts.CONCEPT_SuperviseMsg
				+ " t where t.fTaskId=:taskId and t.fBizRecId=:bizRecId and t.fRuleId=:ruleId and t.fCreatePerson=:personId", varMap,
				FlowBizConsts.DATA_MODEL_CORE_FLOW, ModelUtils.getModel(FlowBizConsts.FN_MODEL_CORE));
		t.getMetaData().setKeyColumn("FGUID");
		if (t.size() == 0) {
			t = BizData.create(t, FlowBizConsts.CONCEPT_SuperviseMsg, new HashMap<String, String>(), FlowBizConsts.FN_MODEL_CORE);
			Row r = t.iterator().next();
			r.setString("fTaskId", taskId);
			r.setString("fBizRecId", bizRecId);
			r.setString("fRuleId", ruleId);
			r.setString("fRuleContent", ruleContent);
			t.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		}
	}

	/**
	 * 添加业务操作日志
	 * 
	 * @param bizRecId
	 * @param bizRuleTipInfo
	 */
	private static void addFlowOperationLog(String operationName, String bizRecId, String bizRuleTipInfo) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("operationName", operationName);
		varMap.put("bizRecId", bizRecId);
		varMap.put("remark", bizRuleTipInfo);
		KSQL.executeUpdate(
				"insert into "
						+ FlowBizConsts.CONCEPT_FlowOperationLog
						+ " t(t, t.version, t.fPersonID, t.fPersonName, t.fOperationName, t.fCreateTime, t.fRemark, t.fBizRecId)"
						+ " values(:guid(), 0, :currentPersonID(), :currentPersonMemberNameWithAgent(), :operationName, :currentDateTime(), :remark, :bizRecId)",
				varMap, ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
	}

	/**
	 * 执行业务规则动作,并记录操作日志
	 * 
	 * @param event
	 * @throws JSONException
	 */

	private static void executeBizRuleAction(String event, String bizRecId) {
		// 执行环境判断
		ProcessLogicPluginContext pluginContext = ProcessLogicPluginContext.findLogicPluginContext(ContextHelper.getActionContext().getActivity(),
				bizRecId);
		Utils.check(Utils.isNotNull(pluginContext), ProcessEventHandlers.class, "不存在流程代码逻辑插件运行环境");

		ProcessInfo processInfo = CacheManager.getProcessInfo(ContextHelper.getActionContext().getProcess());
		List<BizRuleExt> bizRules = processInfo.getBizRules(ContextHelper.getActionContext().getActivity().getName(), event);

		// 如果不存在业务规则,则返回
		if (bizRules == null) {
			addFlowOperationLog(event, bizRecId, null);
			return;
		}

		// 重新计算业务规则，防止业务组件修改数据，导致违反竞争规则
		BizRuleRuntime bizRuleRuntime = new BizRuleRuntime(pluginContext);
		StringBuffer bizRuleTipInfo = new StringBuffer();
		for (BizRuleExt ext : bizRules) {
			bizRuleRuntime.setBizRuleEx(ext);
			if (bizRuleRuntime.checkBizRule(event)) {
				BizRule rule = ext.getBizRule();
				// TODO 不动产预查封转查封会触发禁止规则，要求advance操作前必须调用query方法触发规则检查。
				//				if ("禁止规则".equals(rule.getKind())) {
				//					throw new BusinessException(bizRuleRuntime.getBizRuleTipInfo());
				//				}
				if (Boolean.TRUE.equals(rule.getSupervise()) && !isSuperviseDisabled()) {
					Task task = null;
					try {
						task = ProcessUtils.getTaskInProcessContext();
					} catch (Exception e) {
					}
					if (task != null && !existsSuperviseExplain(task.getId(), bizRecId, rule.getGuid(), ContextHelper.getPerson().getID())) {
						throw new BusinessException("监管规则【" + rule.getName() + "】未答复");
					}
				}

				String tip = bizRuleRuntime.getBizRuleTipInfo();
				if (tip != null)
					bizRuleTipInfo.append(tip);
				else
					bizRuleTipInfo.append("触发业务规则:" + rule.getName());
				bizRuleTipInfo.append("\n");

				bizRuleRuntime.executeActions();
			}
		}
		addFlowOperationLog(event, bizRecId, bizRuleTipInfo.toString());
	}

	private static boolean existsSuperviseExplain(String taskId, String bizRecId, String ruleId, String personId) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("taskId", taskId);
		varMap.put("bizRecId", bizRecId);
		varMap.put("ruleId", ruleId);
		varMap.put("personId", personId);

		Table t = KSQL.select("select t.fExplainContent as fExplainContent,t.fExplainFiles as fExplainFiles from "
				+ FlowBizConsts.CONCEPT_SuperviseMsg
				+ " t where t.fTaskId=:taskId and t.fBizRecId=:bizRecId and t.fRuleId=:ruleId and t.fCreatePerson=:personId", varMap,
				FlowBizConsts.DATA_MODEL_CORE_FLOW, ModelUtils.getModel(FlowBizConsts.FN_MODEL_CORE));
		if (t.size() == 0)
			return false;
		Row r = t.iterator().next();
		if (Utils.isNotEmptyString(r.getString("fExplainContent")))
			return true;
		JSONArray files = JSON.parseArray(r.getString("fExplainFiles"));
		return files != null && files.size() > 0;
	}

	/**
	 * 执行业务插件事件
	 * 
	 * @param eventName
	 *            before/流转 、before/办结、after/流转、after/办结 等
	 * @throws JSONException
	 */
	private static void executeBizLogicPlugin(String eventName, String bizRecId) {
		executeBizLogicPlugin(eventName, bizRecId, null, null);
	}

	/**
	 * 
	 * @param eventName
	 * @param bizRecId
	 * @param contextData 组件环境中的变量
	 */
	private static void executeBizLogicPlugin(String eventName, String bizRecId, Map<String, Object> contextData) {
		executeBizLogicPlugin(eventName, bizRecId, null, contextData);
	}

	/**
	 * 
	 * @param eventName
	 * @param bizRecId
	 * @param pluginContext 采用相同的ProcessLogicPluginContext
	 */
	private static void executeBizLogicPlugin(String eventName, String bizRecId, ProcessLogicPluginContext pluginContext) {
		executeBizLogicPlugin(eventName, bizRecId, pluginContext, null);
	}

	/**
	 * 
	 * @param eventName
	 * @param bizRecId
	 * @param pluginContext 谁创建谁释放的原则。
	 * @param contextData
	 */
	private static void executeBizLogicPlugin(String eventName, String bizRecId, ProcessLogicPluginContext pluginContext,
			Map<String, Object> contextData) {
		if (pluginContext == null) {
			pluginContext = ProcessLogicPluginContext.findLogicPluginContext(ContextHelper.getActionContext().getActivity(), bizRecId);
		}
		Utils.check(Utils.isNotNull(pluginContext), ProcessEventHandlers.class, "不存在流程代码逻辑插件运行环境");
		if (contextData != null) {
			for (String k : contextData.keySet()) {
				pluginContext.setParameter(k, contextData.get(k));
			}
		}

		ProcessInfo processInfo = CacheManager.getProcessInfo(pluginContext.getProcess());
		List<BizLogicPluginEx> triggerEvents = processInfo.getEventTriggerLogicPlugins().get(pluginContext.getActivity().getName() + "/" + eventName);
		if (triggerEvents == null)
			return;

		for (BizLogicPluginEx pluginEx : triggerEvents) {
			String relBizDatas = pluginEx.getBizLogicPlugin().getRelBizDatas();
			if (relBizDatas == null) {
				// TODO 兼容老的资源
				pluginContext.loadAllBizTable();
			} else {
				String[] tableNames = relBizDatas.split(",");
				for (String tableName : tableNames) {
					pluginContext.loadBizTable(tableName);
				}
			}
			pluginContext.execute(pluginEx.getCalcLogicConfig(), null, null);
		}
	}
}
