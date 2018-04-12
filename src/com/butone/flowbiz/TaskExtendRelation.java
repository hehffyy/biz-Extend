package com.butone.flowbiz;

public class TaskExtendRelation {
	public static enum TaskExtendKind {
		bizCooperation, preempt, clone
	}

	/**
	 * Process为业务协同，记录业务事项实例ID
	 */
	public static final String BizCoop_ApproveId = "sData2";
	/**
	 * Process为业务流程，记录业务事项实例ID
	 */
	public static final String FlowTask_ApproveId = "sData2";
	/**
	 * Process为业务流程，记录被挂起的业务协同任务Id
	 */
	public static final String FlowTask_BizCoopSuspendTaskId = "sData3";
	/**
	 * Process为业务流程,记录业务协同实例ID
	 */
	public static final String FlowTask_BizCoopId = "sData4";
	/**
	 * 环节分组时限类型
	 */
	public static final String FlowTask_ActLimitKind = "sESField04";
	/**
	 * 阶段分组时限类型
	 */
	public static final String FlowTask_GroupLimitKind = "sESField05";
	/**
	 * Process为业务流程,记录抢占任务ID，描述我是被哪个任务抢占放弃的。
	 */
	public static final String FlowTask_PreemptTaskId = "sESField07";
	/**
	 * 任务类型 <br>
	 * BizCooperation:协同任务 <br>
	 * preempt：抢占任务(用于判断是否具有撤销抢占的操作 <br>
	 * clone : 如果是部门或者岗位抢占，会直接修改部门、岗位任务的数据，为了撤销抢占时回复数据，克隆部门或岗位任务
	 */
	public static final String Task_TaskKind = "sESField08";

	/**
	 * Process为业务流程,记录任务耗时
	 */
	public static final String FlowTask_LostDays = "sENField11";

	/**
	 * 任务剩余天数
	 */
	public static final String FlowTask_RemaindingDays = "sENField12";
	/**
	 * 阶段剩余天数
	 */
	public static final String FlowTask_GroupRemaindingDays = "sENField13";

	/**
	 * 阶段分组时限
	 */
	public static final String FlowTask_GroupLimit = "sEDField21";

	/**
	 * 辅助控制单选(临时，不存储) 与TaskGanttEngine.java 冲突
	 */
	public static final String FlowTask_Single = "sEIField41";
	
	/**
	 * 是否启用移动端（0，1） 与TaskGanttEngine.java 冲突
	 */
	public static final String FlowTask_MobileEnable = "sEIField42";
}
