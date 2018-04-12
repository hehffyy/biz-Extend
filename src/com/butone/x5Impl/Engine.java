package com.butone.x5Impl;

import java.lang.reflect.Method;
import java.util.Map;


import com.justep.model.Action;
import com.justep.model.Activity;
import com.justep.model.Procedure;
import com.justep.model.Process;
import com.justep.security.decrypt.Decrypt;

public class Engine {
	private static Class<?> Class_Engine;
	private static Method Method_invokeProcedure;
	private static Method Method_invokeAction;
	static {
		Class_Engine = Decrypt.instance().getClass("com.justep.system.action.Engine");
		try {
			if (Class_Engine != null) {
				Method_invokeProcedure = Class_Engine.getMethod("invokeProcedure", new Class[] { Procedure.class, Object[].class });
				Method_invokeAction = Class_Engine.getMethod("invokeAction", new Class[] { Process.class, Activity.class, Action.class, String.class,
						String.class, Map.class });
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Object invokeProcedure(Procedure paramProcedure, Object[] paramArray) {
		if (Method_invokeProcedure != null) {
			try {
				return Method_invokeProcedure.invoke(null, new Object[] { paramProcedure, paramArray });
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}
	public static Object invokeAction(Process process, Activity activity, Action action, String executor, String executeContext,
			Map<String, Object> paramMap) {
		if (Method_invokeAction != null) {
			try {
				return Method_invokeAction.invoke(Class_Engine, new Object[] { process, activity, action, executor, executeContext, paramMap });
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

 

}
