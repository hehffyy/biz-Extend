package com.butone.spi;

import java.util.List;

import com.justep.model.Activity;
import com.justep.model.Process;
import com.justep.system.opm.OrgUnit;
import com.justep.system.process.ProcessControl;
import com.justep.system.process.Task;

public class FlowControlUtils {
	private static FlowControl spi = ServiceProvider.getFlowControl();

	/**
	 * 
	 * @param process			任务协同流程
	 * @param entryActivity		任务协同入口环节
	 * @return 返回启动的任务Id
	 */
	public static String startBizCooperationFlow(String process, String entryActivity) {
		return spi.startBizCooperationFlow(process, entryActivity);
	}

	/**
	 * 业务协作向下批转，当子业务办结后执行。
	 * 正确做法应该是 执行业务协作的批转，有条件的返回启动后继业务事项的ProcessControl，这样可干预业务协同的流向
	 * @param suspendTaskId 业务协同挂起任务ID，记录在机构流程Task.sData3
	 * @return 返回业务协同批转的后继任务列表
	 */
	public static List<String> advanceBizCooperationProcess(String suspendedTaskId) {
		return spi.advanceBizCooperationProcess(suspendedTaskId);
	}

	/**
	 * 启动业务事项流程,业务协作的业务事项环节批转后执行<br>
	 * --> 机构流程办结后(subProcess) --> FlowControl.advanceBizCooperationProcess <br>
	 * --> 业务流程批转后(bizProcess)根据flowTos --> FlowControl.startBizCoopActivityProcess<br> 
	 * 
	 * @param process		业务事项机构流程
	 * @param entryActivity 业务事项机构入口环节
	 * @param flowToTask		业务协作流程当前任务
	 * @return 启动的任务id
	 */
	public static void startBizCoopActivityProcess(String process, String entryActivity, Task flowToTask) {
		spi.startBizCoopActivityProcess(process, entryActivity, flowToTask);
	}

	/**
	 * 启动流程协同，自动环节完成。环节应具备扩展属性sendToCoopProcesses<br>
	 * 格式为JSONArray，元素属性如下<br>
	 * {'process':'...','activity':'...','receiver':'...','startProcess':true}<br>
	 * @param fromTask			发起方任务
	 * @param receiveActivity	发起方接收环节
	 * @param toProcess			目标方流程
	 * @param entryActivity		目标方入口环节
	 * @param bizCoopActivity	当前业务审批对应的业务协同环节
	 * @param bizCoopId			业务协同实例
	 * @param approveId			业务审批实例
	 */
	public static void startFlowCoopProcess(Task fromTask, String receiveActivity, String toProcess, String entryActivity, String bizCoopActivity,
			String bizCoopId, String approveId) {
		spi.startFlowCoopProcess(fromTask, receiveActivity, toProcess, entryActivity, bizCoopActivity, bizCoopId, approveId);
	}

	/**
	 * 发送数据到协同流程
	 * @param fApproveId
	 * @param fromProcess
	 * @param fromAcivity
	 * @param toProcess
	 * @param toAcivity
	 * @param receiveActivity
	 */
	public static void sendDataToCoopProcess(String fApproveId, String fromProcess, String fromAcivity, String toProcess, String toAcivity,
			String receiveActivity) {
		spi.sendDataToCoopProcess(fApproveId, fromProcess, fromAcivity, toProcess, toAcivity, receiveActivity);
	}

	/**
	 * 业务流程流转到接收环节时,检查此环节的流程协同状态<br>
	 * 1.如果本次审批过程
	 */
	public static void checkFlowCoopReceiveRecord(Process p, Activity act, Task task) {
		spi.checkFlowCoopReceiveRecord(p, act, task);
	}

	/**
	 * 回退
	 * @param task 业务流程的任务
	 * @param control
	 */
	public static void backBizCoopProcess(String task, ProcessControl control) {
		spi.backBizCoopProcess(task, control);
	}

	/**
	 * 回退业务协同的查询
	 * @param task 业务流程的任务
	 * @return
	 */
	public static ProcessControl backBizCoopProcessQuery(String task) {
		return spi.backBizCoopProcessQuery(task);
	}

	/**
	 * 启动流程
	 * @param bizRecId
	 * @param process
	 * @param activity
	 * @param executors
	 * @return
	 */
	public static String startFlow(String bizRecId, String process, String activity, List<OrgUnit> executors) {
		return spi.startFlow(bizRecId, process, activity, executors);
	}

}
