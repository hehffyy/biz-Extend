package com.butone.interceptor;

import com.butone.logic.impl.ProcessLogicPluginContext;
import com.justep.system.action.ActionUtils;
import com.justep.system.action.Interceptor;
import com.justep.system.context.ActionContext;
import com.justep.system.context.RequestContext;

public class ProcessActionAfter implements Interceptor {

	public void execute() {
		RequestContext requestContext = ActionUtils.getRequestContext();
		ActionContext actionContext = requestContext.getActionContext();
		String actionName = actionContext.getAction().getName();
		if (ProcessActionBefore.interceptorActionNames.contains(actionName) || "startProcessAction".equals(actionName) || "startProcessQueryAction".equals(actionName)) {
			// 移除代码插件上下文
			ProcessLogicPluginContext pluginContext = (ProcessLogicPluginContext) actionContext.get(ProcessActionBefore.InterceptorCreatedProcessLogicPluginContext);
			if (pluginContext != null)
				ProcessLogicPluginContext.removeLogicPluginContext(pluginContext, true);
		}
	}

}
