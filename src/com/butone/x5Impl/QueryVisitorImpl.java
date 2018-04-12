package com.butone.x5Impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import com.justep.model.Model;
import com.justep.security.decrypt.Decrypt;

public class QueryVisitorImpl {

	private Object target;

	public Object getTarget() {
		return target;
	}

	public QueryVisitorImpl(Map<String, Object> paramMap, Model paramModel, Map<String, Object> paramMap1) {
		try {
			Class cls = Decrypt.instance().getClass("com.justep.system.ksql.visitor.QueryVisitor");
			Constructor constructor = cls.getConstructor(Map.class, Model.class, Map.class);
			target = constructor.newInstance(paramMap, paramModel, paramMap1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getSql() {
		try {
			Class cls = Decrypt.instance().getClass("com.justep.system.ksql.visitor.QueryVisitor");
			Method m = cls.getDeclaredMethod("getSql");
			Object sql = m.invoke(target);
			m = sql.getClass().getDeclaredMethod("getSql");
			return (String) m.invoke(sql);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
