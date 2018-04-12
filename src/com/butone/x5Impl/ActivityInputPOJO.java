package com.butone.x5Impl;

import java.lang.reflect.Field;

import com.justep.security.decrypt.Decrypt;

public class ActivityInputPOJO {
	private static Class<?> Class_ActivityInputPOJO;
	private static Field Field_From;
	private static Field Field_Var;

	static {
		Class_ActivityInputPOJO = Decrypt.instance().getClass("com.justep.model.impl.ActivityInputPOJO");
		try {
			if(Class_ActivityInputPOJO!=null){
				Field_From = Class_ActivityInputPOJO.getField("from");
				Field_Var = Class_ActivityInputPOJO.getField("var");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private Object target;

	public ActivityInputPOJO(Object target) {
		if (Class_ActivityInputPOJO != null && target != null && Class_ActivityInputPOJO.isAssignableFrom(target.getClass()))
			this.target = target;
	}

	public String getFrom() {
		if (this.target != null) {
			try {
				return (String) Field_From.get(this.target);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}

	public String getVar() {
		if (this.target != null) {
			try {
				return (String) Field_Var.get(this.target);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}
}
