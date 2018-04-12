package com.butone.logic.impl;

import com.butone.logic.ObjectAccess;
import com.justep.system.data.Row;

public class RowAccess implements ObjectAccess {

	public Object getObjectValue(Object target, String property) {
		try {
			Object objValue = ((Row) target).getValue(property);
			return objValue;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setObjectValue(Object target, String property, Object value) {
		try {
			((Row) target).setValue(property, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

}
