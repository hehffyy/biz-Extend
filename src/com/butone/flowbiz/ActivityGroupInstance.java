package com.butone.flowbiz;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.justep.model.ModelUtils;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.process.Task;
import com.justep.system.util.CommonUtils;

public class ActivityGroupInstance {
	private String guid;
	private String groupId;
	private String groupName;
	private String bizId;
	// private String bizName;
	private String bizRecId;
	private String flowId;
	private Timestamp startTime;
	private Timestamp endTime;
	private Integer startCount;
	private String limitKind;
	private BigDecimal limitDays;
	private Timestamp limitTime;
	private Float lostDays;
	private Float remainingDays;
	private Integer suspendDays;
	private List<GroupInstanceTask> tasks = new ArrayList<GroupInstanceTask>();

	public List<GroupInstanceTask> getTasks() {
		return tasks;
	}

	public ActivityGroupInstance() {
		this.guid = CommonUtils.createGUID();
	}

	public String getGuid() {
		return guid;
	}

	public void setGuid(String guid) {
		this.guid = guid;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getBizId() {
		return bizId;
	}

	public void setBizId(String bizId) {
		this.bizId = bizId;
	}

	// public String getBizName() {
	// return bizName;
	// }
	//
	// public void setBizName(String bizName) {
	// this.bizName = bizName;
	// }

	public String getBizRecId() {
		return bizRecId;
	}

	public void setBizRecId(String bizRecId) {
		this.bizRecId = bizRecId;
	}

	public String getFlowId() {
		return flowId;
	}

	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}

	public Timestamp getStartTime() {
		return startTime;
	}

	public void setStartTime(Timestamp startTime) {
		this.startTime = startTime;
	}

	public Timestamp getEndTime() {
		return endTime;
	}

	public void setEndTime(Timestamp endTime) {
		this.endTime = endTime;
	}

	public Integer getStartCount() {
		return startCount;
	}

	public void setStartCount(Integer startCount) {
		this.startCount = startCount;
	}

	public String getLimitKind() {
		return limitKind;
	}

	public void setLimitKind(String limitKind) {
		this.limitKind = limitKind;
	}

	public BigDecimal getLimitDays() {
		return limitDays;
	}

	public void setLimitDays(BigDecimal limitDays) {
		this.limitDays = limitDays;
	}

	public Timestamp getLimitTime() {
		return limitTime;
	}

	public void setLimitTime(Timestamp limitTime) {
		this.limitTime = limitTime;
	}

	public Float getLostDays() {
		return lostDays;
	}

	public void setLostDays(Float lostDays) {
		this.lostDays = lostDays;
	}

	public Integer getSuspendDays() {
		return suspendDays;
	}

	public void setSuspendDays(Integer suspendDays) {
		this.suspendDays = suspendDays;
	}

	public Float getRemainingDays() {
		return remainingDays;
	}

	public void setRemainingDays(Float remainingDays) {
		this.remainingDays = remainingDays;
	}

	public void assign(Row row) {
		this.guid = row.getString("FGUID");
		this.bizId = row.getString("fBizId");
		// this.bizName = row.getString("fBizName");
		this.bizRecId = row.getString("fBizRecId");
		this.endTime = row.getDateTime("fEndTime");
		this.flowId = row.getString("fFlowId");
		this.guid = row.getString("FGUID");
		this.groupName = row.getString("fGroupName");
		this.groupId = row.getString("fGroupId");
		this.limitDays = row.getDecimal("fLimitDays");
		this.limitKind = row.getString("fLimitKind");
		this.limitTime = row.getDateTime("fLimitDate");
		this.startCount = row.getInteger("fStartCount");
		this.startTime = row.getDateTime("fStartTime");
		this.lostDays = (Float) row.getValue("fLostDays");
		this.remainingDays = (Float) row.getValue("fRemainingDays");
		this.suspendDays = row.getInteger("fSuspendDays");
	}

	public void assignTo(Row row) {
		row.setString("FGUID", this.guid);
		row.setString("fBizId", this.bizId);
		// row.setString("fBizName", this.bizName);
		row.setString("fBizRecId", this.bizRecId);
		row.setDateTime("fEndTime", this.endTime);
		row.setString("fFlowId", this.flowId);
		row.setString("FGUID", this.guid);
		row.setString("fGroupName", this.groupName);
		row.setString("fGroupId", this.groupId);
		row.setDecimal("fLimitDays", this.limitDays);
		row.setString("fLimitKind", this.limitKind);
		row.setDateTime("fLimitDate", this.limitTime);
		row.setInteger("fStartCount", this.startCount);
		row.setDateTime("fStartTime", this.startTime);
		row.setFloatObject("fLostDays", this.lostDays);
		row.setFloatObject("fRemainingDays", this.remainingDays);
		row.setInteger("fSuspendDays", this.suspendDays);
	}

	/**
	 * 新建任务
	 * 
	 * @param tasks
	 */
	public void newTasks(List<Task> tasks) {
		for (Task t : tasks) {
			if (this.getActivityGroupInstanceTask(t.getId()) == null) {
				GroupInstanceTask it = new GroupInstanceTask();
				it.guid = CommonUtils.createGUID();
				it.groupInstance = this.guid;
				it.taskId = t.getId();
				this.tasks.add(it);
			}
		}
	}

	/**
	 * 新建任务
	 * 
	 * @param tasks
	 * @throws JSONException
	 */
	public void newTasks(JSONArray tasks) {
		for (int i = 0; i < tasks.size(); i++) {
			Task t = (Task) tasks.get(i);
			if (this.getActivityGroupInstanceTask(t.getId()) == null) {
				GroupInstanceTask it = new GroupInstanceTask();
				it.guid = CommonUtils.createGUID();
				it.groupInstance = this.guid;
				it.taskId = t.getId();
				this.tasks.add(it);
			}
		}
	}

	public GroupInstanceTask getActivityGroupInstanceTask(String taskId) {
		for (GroupInstanceTask task : tasks) {
			if (task.getTaskId().equals(taskId))
				return task;
		}
		return null;
	}

	private static ActivityGroupInstance loadByRow(Row row) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		ActivityGroupInstance instance = new ActivityGroupInstance();
		instance.assign(row);
		varMap.put("groupInstance", instance.getGuid());
		Table table = KSQL.select("select t.* from " + FlowBizConsts.CONCEPT_ActivityGroupInstanceTask + " t where t.fGroupInstance=:groupInstance", varMap,
				ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
		Iterator<Row> i = table.iterator();
		while (i.hasNext()) {
			row = i.next();
			GroupInstanceTask task = new GroupInstanceTask();
			task.assign(row);
			instance.tasks.add(task);
		}
		return instance;
	}

	public static List<ActivityGroupInstance> loadByFlow(String flowId, boolean activation) {
		List<ActivityGroupInstance> ret = new ArrayList<ActivityGroupInstance>();
		String query = "select g.* from " + FlowBizConsts.CONCEPT_ActivityGroupInstance + " g where g.fFlowId=:flowId";
		if (activation) {
			query += " and g.fEndTime is null";
		}
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("flowId", flowId);
		Table table = KSQL.select(query, varMap, ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
		Iterator<Row> i = table.iterator();
		while (i.hasNext()) {
			Row row = i.next();
			ActivityGroupInstance instance = ActivityGroupInstance.loadByRow(row);
			ret.add(instance);
		}
		return ret;
	}

	/**
	 * 加载流程制定环节分组
	 * 
	 * @param flowId
	 * @param groupId
	 * @return
	 */
	public static ActivityGroupInstance findFlowActivityGroup(String flowId, String groupId) {
		String query = "select g.* from " + FlowBizConsts.CONCEPT_ActivityGroupInstance + " g where g.fFlowId=:flowId and g.fGroupId=:groupId";
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("flowId", flowId);
		varMap.put("groupId", groupId);
		Table table = KSQL.select(query, varMap, ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
		if (table.size() == 0)
			return null;
		Row row = table.iterator().next();
		return ActivityGroupInstance.loadByRow(row);
	};

	public static ActivityGroupInstance loadByTask(String taskId) {
		String query = "select g.* from " + FlowBizConsts.CONCEPT_ActivityGroupInstance + " g join " + FlowBizConsts.CONCEPT_ActivityGroupInstanceTask
				+ " t on g.FGUID=t.fGroupInstance where t.fTaskId=:taskId";
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("taskId", taskId);
		Table table = KSQL.select(query, varMap, ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
		if (table.size() > 0) {
			Row row = table.iterator().next();
			if (row != null) {
				return ActivityGroupInstance.loadByRow(row);
			}
		}
		return null;
	}

	public void save() {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("guid", this.getGuid());
		Table instanceTable = KSQL.select("select t.* from " + FlowBizConsts.CONCEPT_ActivityGroupInstance + " t where t=:guid", varMap, ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
		instanceTable.getMetaData().setStoreByConcept(FlowBizConsts.CONCEPT_ActivityGroupInstance, true);
		Row row;
		if (instanceTable.size() == 0) {
			row = instanceTable.appendRow();
			row.setValue("version", 0);
		} else
			row = instanceTable.iterator().next();
		this.assignTo(row);

		varMap.clear();
		varMap.put("groupId", this.getGuid());
		Table taskTable = KSQL.select("select t.* from " + FlowBizConsts.CONCEPT_ActivityGroupInstanceTask + " t where t.fGroupInstance=:groupId", varMap,
				ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW), null);
		taskTable.getMetaData().setStoreByConcept(FlowBizConsts.CONCEPT_ActivityGroupInstanceTask, true);
		taskTable.getMetaData().setKeyColumn("FGUID");
		for (GroupInstanceTask task : this.tasks) {
			row = taskTable.getRow(task.getGuid());
			if (row == null) {
				row = taskTable.appendRow();
				row.setValue("version", 0);
			}
			task.assignTo(row);
		}
		instanceTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		taskTable.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
	}

	public static class GroupInstanceTask {
		private String guid;
		private String taskId;
		private String groupInstance;

		public void assign(Row row) {
			this.guid = row.getString("FGUID");
			this.taskId = row.getString("fTaskId");
			this.groupInstance = row.getString("fGroupInstance");
		}

		public void assignTo(Row row) {
			row.setString("FGUID", this.guid);
			row.setString("fTaskId", this.taskId);
			row.setString("fGroupInstance", this.groupInstance);
		}

		public String getGuid() {
			return guid;
		}

		public void setGuid(String guid) {
			this.guid = guid;
		}

		public String getTaskId() {
			return taskId;
		}

		public void setTaskId(String taskId) {
			this.taskId = taskId;
		}

		public String getGroupInstance() {
			return groupInstance;
		}

		public void setGroupInstance(String groupInstance) {
			this.groupInstance = groupInstance;
		}
	}
}
