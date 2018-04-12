package com.butone.x5Impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.justep.security.decrypt.Decrypt;

public class ActivityImpl {
	private static Class<?> Class_ActivityImpl;
	private static Method Method_getOutputPOJOs;
	private static Method Method_getInputPOJOs;

	static {
		Class_ActivityImpl = Decrypt.instance().getClass("com.justep.model.impl.ActivityImpl");
		
		try {
			if(Class_ActivityImpl!=null){
				Method_getOutputPOJOs = Class_ActivityImpl.getMethod("getOutputPOJOs", new Class[] {});
				Method_getInputPOJOs = Class_ActivityImpl.getMethod("getInputPOJOs", new Class[] {});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Object target;

	public ActivityImpl(Object target) {
		if (Class_ActivityImpl != null && target != null && Class_ActivityImpl.isAssignableFrom(target.getClass()))
			this.target = target;
	}

	@SuppressWarnings("rawtypes")
	public List<ActivityOutputPOJO> getOutputPOJOs() {
		List<ActivityOutputPOJO> r = new ArrayList<ActivityOutputPOJO>();
		if (target != null) {
			try {
				List l = (List) Method_getOutputPOJOs.invoke(target, new Object[] {});
				for (Object o : l)
					r.add(new ActivityOutputPOJO(o));

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return r;
	}

	@SuppressWarnings("rawtypes")
	public List<ActivityInputPOJO> getInputPOJOs() {
		List<ActivityInputPOJO> r = new ArrayList<ActivityInputPOJO>();
		if (target != null) {
			try {
				List l = (List) Method_getInputPOJOs.invoke(target, new Object[] {});
				for (Object o : l)
					r.add(new ActivityInputPOJO(o));

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return r;
	}
}
