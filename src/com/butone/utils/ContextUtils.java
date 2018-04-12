package com.butone.utils;

import java.sql.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;

import com.justep.license.LicenseChecker;
import com.justep.model.Config;
import com.justep.model.ModelUtils;
import com.justep.system.action.ActionUtils;
import com.justep.system.context.ActionContext;
import com.justep.system.context.ContextHelper;
import com.justep.system.context.RequestContext;
import com.justep.system.context.SessionContext;
import com.justep.system.context.User;
import com.justep.system.context.UserManager;
import com.justep.system.opm.Operator;
import com.justep.system.opm.Person;
import com.justep.system.opm.api.Org;
import com.justep.system.opm.api.OrgHelper;
import com.justep.system.opm.api.PersonHelper;
import com.justep.system.process.ExpressEngine;
import com.justep.system.process.ProcessContext;
import com.justep.system.process.ProcessUtils;
import com.justep.system.process.Task;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class ContextUtils {

	/**
	 * 获取语言
	 * @param request
	 * @return
	 */
	private static String getLanguage(HttpServletRequest request) {
		if (request == null)
			return "zh_CN";
		String str = request.getParameter("language");
		if (str == null)
			str = "zh_CN";
		else
			str = str.replaceAll("-", "_");
		return str;
	}

	/**
	 * 获取请求中的语言设置
	 * @return
	 */
	public static String getRequestLanguage() {
		return getLanguage(ContextHelper.getRequestContext().getRequest());
	}

	public static Task findTaskInProcessContext() {
		ProcessContext localProcessContext = findProcessContext();
		if (localProcessContext != null)
			return localProcessContext.getTask();
		return null;
	}

	public static ProcessContext findProcessContext() {
		return (ProcessContext) findObjectInActionContextStack(ProcessUtils.PROCESS_CONTEXT);
	}

	public static Object findObjectInActionContextStack(String paramString) {
		if (Utils.isEmptyString(paramString))
			return null;
		Stack<ActionContext> localStack = ActionUtils.getRequestContext().getActionContextStack();
		for (int i = localStack.size() - 1; i >= 0; i--) {
			ActionContext localActionContext = localStack.get(i);
			Object localObject = localActionContext.get(paramString);
			if (localObject != null)
				return localObject;
		}
		return null;
	}

	/**
	 * 替换操作者
	 * @param personID
	 * @return
	 */

	public static Operator replaceOperator(String personID) {
		Operator old = ContextHelper.getOperator();
		if (old.getID().equals(personID))
			return old;

		Set<String> attrs = new HashSet<String>();
		attrs.add("sMainOrgID");
		attrs.add("sTitle");
		attrs.add("sGlobalSequence");
		com.justep.system.opm.api.Person pp = PersonHelper.loadPerson(personID, attrs);

		Org mainOrg = OrgHelper.loadOrg(pp.getMainOrgID(), null);
		Person person = new Person(personID, pp.getName(), pp.getCode(), pp.getMainOrgID(), mainOrg.getFullID(), mainOrg.getFullName(),
				mainOrg.getFullCode());

		Map<String, Object> d = pp.getExtValues();
		for (String k : d.keySet()) {
			if (!k.equals("sMainOrgID"))
				person.setAttribute(k, d.get(k));
		}
		person.setAttribute("sysParams", true);
		// 设置任务中心签收模式
		Config c = (Config) ModelUtils.getModel("/system/config").getLocalObject("signMode", Config.TYPE);
		String expr = c == null ? "" : c.getValue();
		try {
			person.setAttribute("signMode", ExpressEngine.calculateBoolean(expr, null, true, ModelUtils.getModel("/base/core/logic/fn")));
		} catch (Exception e) {
		}

		Operator operator = new Operator(person, new Date(System.currentTimeMillis()), ContextUtils.getRequestLanguage());

		SessionContext sessionContext = ContextHelper.getSessionContext();
		sessionContext.put("sys.orgCache.old", old);
		sessionContext.put("sys.orgCache", operator);
		sessionContext.reset();

		RequestContext requestContext = ContextHelper.getRequestContext();
		requestContext.put("sys.orgCache", operator);
		requestContext.reset();
		// 防止在线检查
		String sessionID = ContextHelper.getSessionContext().getSessionID();
		User user = UserManager.instance().getAllUsers().get(sessionID);
		LicenseChecker.instance().addOnlineUser(operator.getID(), null,
				user.getLoginIP() + "/" + CommonUtils.dateFormat(user.getLoginDate(), "yyyy-MM-dd HH:mm:ss.SSS") + "/" + person.getCode(), sessionID);

		return old;
	}

	/**
	 * 还原操作者
	 * @return
	 */
	public static Operator restoreOperator() {
		Operator old = (Operator) ContextHelper.getSessionContext().get("sys.orgCache.old");
		if (old != null) {
			String sessionID = ContextHelper.getSessionContext().getSessionID();
			User user = UserManager.instance().getAllUsers().get(sessionID);
			LicenseChecker.instance()
					.addOnlineUser(old.getID(), null,
							user.getLoginIP() + "/" + CommonUtils.dateFormat(user.getLoginDate(), "yyyy-MM-dd HH:mm:ss.SSS") + "/" + old.getCode(),
							sessionID);
			SessionContext sessionContext = ContextHelper.getSessionContext();
			sessionContext.put("sys.orgCache", old);
			sessionContext.remove("sys.orgCache.old");
			sessionContext.reset();

			RequestContext requestContext = ContextHelper.getRequestContext();
			requestContext.remove("sys.orgCache");
			requestContext.reset();
		}
		return old;
	}
}
