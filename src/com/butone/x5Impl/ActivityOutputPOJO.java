package com.butone.x5Impl;

import java.lang.reflect.Field;

import com.justep.security.decrypt.Decrypt;

public class ActivityOutputPOJO {
	private static Class<?> Class_ActivityOutputPOJO;
	private static Field Field_To;
	private static Field Field_Var;
	private static Field Field_Condition;
	
	static{
		Class_ActivityOutputPOJO = Decrypt.instance().getClass("com.justep.model.impl.ActivityOutputPOJO");
		try {
			if(Class_ActivityOutputPOJO!=null){
				Field_To = Class_ActivityOutputPOJO.getField("to");
				Field_Var = Class_ActivityOutputPOJO.getField("var");
				Field_Condition = Class_ActivityOutputPOJO.getField("condition"); 
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private Object target;
	public ActivityOutputPOJO(Object target) {
		if(Class_ActivityOutputPOJO!=null && target!=null && Class_ActivityOutputPOJO.isAssignableFrom(target.getClass()))
			this.target = target;
	}

	public String getTo(){
		if(this.target!=null){
			try {
				return (String)Field_To.get(this.target);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}
	public String getVar(){
		if(this.target!=null){
			try {
				return (String)Field_Var.get(this.target);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
		
	}
	public Boolean isTrueOutput() {
		if(this.target!=null){
			try {
				return Field_Condition.getBoolean(this.target);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return null;
	}

	public Boolean isFalseOutput() {
		return !isTrueOutput();
	}
}
