package com.butone.interceptor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.butone.extend.TaskUtils;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.justep.system.action.ActionUtils;
import com.justep.system.action.Interceptor;
import com.justep.system.context.ActionContext;
import com.justep.system.context.RequestContext;

public class ProcessActionBefore implements Interceptor {
	public final static String InterceptorCreatedProcessLogicPluginContext = "InterceptorCreatedProcessLogicPluginContext";

	public static Set<String> interceptorActionNames;

	static {
		interceptorActionNames = new HashSet<String>();

		// 流转
		interceptorActionNames.add("startProcessAction");
		interceptorActionNames.add("startProcessQueryAction");

		// 流转
		interceptorActionNames.add("advanceProcessAction");
		interceptorActionNames.add("advanceProcessQueryAction");

		// 回退
		interceptorActionNames.add("backProcessQueryAction");
		interceptorActionNames.add("backProcessAction");
		// 作废
		interceptorActionNames.add("abortProcessQueryAction");
		interceptorActionNames.add("abortProcessAction");
		// 挂起
		interceptorActionNames.add("suspendProcessQueryAction");
		interceptorActionNames.add("suspendProcessAction");
		// 解挂
		interceptorActionNames.add("resumeProcessQueryAction");
		interceptorActionNames.add("resumeProcessAction");
		// 跳转
		interceptorActionNames.add("specialProcessQueryAction");
		interceptorActionNames.add("specialProcessAction");
		// 移交
		interceptorActionNames.add("multiTransferQueryAction");
		interceptorActionNames.add("transferTaskQueryAction");
		interceptorActionNames.add("transferTaskAction");

		// 办结
		interceptorActionNames.add("finishProcessAction");
	}

	public void execute() {
		RequestContext requestContext = ActionUtils.getRequestContext();
		ActionContext actionContext = requestContext.getActionContext();
		String actionName = actionContext.getAction().getName();
		if (interceptorActionNames.contains(actionName)) {
			String bizRecId = null;
			if (actionName.startsWith("start")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> attributes = (Map<String, Object>) actionContext.getParameter("attributes");
				bizRecId = (String) attributes.get("sData1");
			} else {
				bizRecId = TaskUtils.loadTask((String) actionContext.getParameter("task")).getData1();
			}
			if (ProcessLogicPluginContext.findLogicPluginContext(actionContext.getActivity(), bizRecId) == null) {
				ProcessLogicPluginContext pluginContext = ProcessLogicPluginContext.createLogicPluginContext(actionContext.getProcess(), actionContext.getActivity(), bizRecId);
				actionContext.put(InterceptorCreatedProcessLogicPluginContext, pluginContext);
				pluginContext.loadFlowSysTables();
				// TODO 2017-04-18 注释保留3个月，不再加载主表，findTableControlObject中自动加载
				//pluginContext.loadBizMasterTable(null);
			}
		}
	}
}
