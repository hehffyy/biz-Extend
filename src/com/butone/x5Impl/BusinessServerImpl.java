package com.butone.x5Impl;

import java.lang.reflect.Method;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.justep.security.decrypt.Decrypt;
import com.justep.system.context.SessionContext;

public class BusinessServerImpl {
	private static Method m_createRequestContext;
	private static Method m_removeRequestContext;
	private static Method m_destroyModel;
	private static Object instance_BusinessServer;
	static {
		try {
			Class<?> cls = Decrypt.instance().getClass("com.justep.business.server.BusinessServer");
			instance_BusinessServer = cls.getDeclaredMethod("instance").invoke(null);
			m_createRequestContext = cls.getDeclaredMethod("createRequestContext2", HttpServletRequest.class, HttpServletResponse.class, SessionContext.class, String.class, String.class);
			m_createRequestContext.setAccessible(true);
			m_removeRequestContext = cls.getDeclaredMethod("removeRequestContext");
			m_destroyModel = cls.getDeclaredMethod("destroyModel");
			m_destroyModel.setAccessible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void createRequestContext2(HttpServletRequest request, HttpServletRequest response, SessionContext sessionContext, String request_data_type, String response_data_type) {
		try {
			m_createRequestContext.invoke(instance_BusinessServer, request, response, sessionContext, request_data_type, response_data_type);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void removeRequestContext() {
		try {
			m_removeRequestContext.invoke(instance_BusinessServer);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void destroyModel() {
		try {
			m_destroyModel.invoke(instance_BusinessServer);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
