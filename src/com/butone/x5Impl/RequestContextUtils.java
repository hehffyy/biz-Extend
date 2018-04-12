package com.butone.x5Impl;

import java.lang.reflect.Field;

import com.justep.security.decrypt.Decrypt;
import com.justep.system.context.ContextHelper;
import com.justep.system.context.RequestContext;
import com.justep.system.data.Transaction;

public class RequestContextUtils {
	private static Field f_transaction;
	static {
		Class<?> cls = Decrypt.instance().getClass("com.justep.system.context.RequestContextImpl");
		try {
			f_transaction = cls.getDeclaredField("transaction");
			f_transaction.setAccessible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setTransaction(Transaction transaction) throws Exception {
		RequestContext rc = ContextHelper.getRequestContext();
		f_transaction.set(rc, transaction);
	}
}
