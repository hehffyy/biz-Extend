package com.butone.x5Impl;

import java.lang.reflect.Method;

import com.justep.model.ModelObject;
import com.justep.security.decrypt.Decrypt;

public class ModelObjectImpl {
	private static Class<?> Class_ModelObjectImpl;
	private static Method Method_SetExtAttributeValue;
	static {
		Class_ModelObjectImpl = Decrypt.instance().getClass("com.justep.model.impl.ModelObjectImpl");
		try {
			if (Class_ModelObjectImpl != null) {
				Method_SetExtAttributeValue = Class_ModelObjectImpl.getMethod("setExtAttributeValue", new Class[] { String.class, String.class,
						Object.class });
				Method_SetExtAttributeValue.setAccessible(true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private ModelObject target;

	public ModelObjectImpl(ModelObject target) {
		this.target = target;
	}

	public void setExtAttributeValue(String uri, String name, Object value) {
		try {
			Method_SetExtAttributeValue.invoke(target, uri, name, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
