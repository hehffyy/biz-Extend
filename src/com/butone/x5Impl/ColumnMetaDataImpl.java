package com.butone.x5Impl;

import java.lang.reflect.Field;

import com.justep.security.decrypt.Decrypt;

public class ColumnMetaDataImpl {
	private static Class<?> Class_ColumnMetaDataImpl;
	private static Field Field_type;
	
	static {
		Class_ColumnMetaDataImpl = Decrypt.instance().getClass("com.justep.system.data.impl.ColumnMetaDataImpl");
		
		try {
			if(Class_ColumnMetaDataImpl!=null){
				Field_type = Class_ColumnMetaDataImpl.getDeclaredField("type");
				Field_type.setAccessible(true);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void setType(String type){
		try {
			Field_type.set(target, type);
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	private Object target;

	public ColumnMetaDataImpl(Object target) {
		if (Class_ColumnMetaDataImpl != null && target != null && Class_ColumnMetaDataImpl.isAssignableFrom(target.getClass()))
			this.target = target;
	}

}
