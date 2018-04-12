package com.butone.x5Impl;

import java.lang.reflect.Method;

import com.justep.security.decrypt.Decrypt;

public class QuerySentenceImpl {

	private Object target;

	public QuerySentenceImpl(Object target) {
		this.target = target;
	}

	public void accept(Object visitor) {
		try {
			Class cls = Decrypt.instance().getClass("com.justep.system.ksql.node.QuerySentence");
			Class pCls = Decrypt.instance().getClass("com.justep.system.ksql.visitor.Visitor");
			Method m = cls.getDeclaredMethod("accept", pCls);
			m.invoke(target, visitor);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
}
