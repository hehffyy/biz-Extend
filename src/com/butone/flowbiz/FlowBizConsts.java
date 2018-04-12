package com.butone.flowbiz;

public class FlowBizConsts {

	/**
	 * 默认解除挂起操作只有一个Task参数，在操作的Lisenter中无法接收到解挂信息。通过butoneResumeAction，将解挂信息压入到RequestContext中，提供给ProcessEventHandler使用。
	 */
	public static final String RequestContext_BizRecSuspendInfo = FlowBizConsts.class.getName() + ".SuspendInfo";

	public static final String DATA_MODEL_SYSTEM = "/system/data";
	/**
	 * 流程业务数据模块
	 */
	public static final String DATA_MODEL_CORE_FLOW = "/base/core/flow/data";
	/**
	 * 流程业务操作数据模块
	 */
	public static final String DATA_MODEL_CORE_FLOWOPERATION = "/base/core/flowOperation/data";

	/**
	 * base core fn
	 */
	public static final String FN_MODEL_CORE = "/base/core/logic/fn";

	/**
	 * 案卷表
	 */
	public static final String CONCEPT_BizRec = "B_BizRec";
	/**
	 * 业务协同实例
	 */
	public static final String CONCEPT_BizCooperation = "B_BizCooperation";
	/**
	 * 业务审批实例
	 */
	public static final String CONCEPT_BizApprove = "B_BizApprove";
	/**
	 * 流程协同记录表
	 */
	public static final String CONCEPT_FlowCoopRecord = "B_FlowCoopRecord";
	/**
	 * 流程操作日志记录表
	 */
	public static final String CONCEPT_FlowOperationLog = "B_FlowOperationLog";

	/**
	 * 监管消息
	 */
	public static final String CONCEPT_SuperviseMsg = "B_SuperviseMsg";

	public static final String CONCEPT_ActivityGroupInstance = "B_ActivityGroupInstance";
	public static final String CONCEPT_ActivityGroupInstanceTask = "B_ActivityGroupInstanceTask";

	public static final String PROCESS_ACTION = "com.butone.flowBiz.process.action";

	public static final String BizApprove_Status_Processing = "asProcessing";
	public static final String BizApprove_Status_Finish = "asFinish";
	public static final String BizApprove_Status_Abort = "asAbort";
	public static final String BizApprove_Status_Returned = "asReturned";

	public static final String FlowCoopRecord_Status_Waiting = "fcsWaiting";
	public static final String FlowCoopRecord_Status_None = "fcsNone";
	public static final String FlowCoopRecord_Status_Received = "fcsReceived";

	public static final String BizCoop_Status_Processing = "csProcessing";
	public static final String BizCoop_Status_Finish = "csFinish";
	public static final String BizCoop_Status_Abort = "csAbort";

}
