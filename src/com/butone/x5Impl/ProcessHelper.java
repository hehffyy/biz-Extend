package com.butone.x5Impl;

import java.util.ArrayList;
import java.util.List;

import com.justep.model.Activity;
import com.justep.model.BusinessActivity;
import com.justep.model.Process;
import com.justep.model.Unit;

public class ProcessHelper {

	public static boolean isEndActivity(Activity activity) {
		return activity.getOutputs().size() == 1 && activity.getOutputs().get(0).equals(activity.getOwner().getEnd());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static List getNextUnits(Unit unit, Class cls) {
		List ret = new ArrayList();
		List<Unit> nexts = unit.getOutputs();
		for (Unit n : nexts) {
			if (n instanceof BusinessActivity)
				ret.add(n);
			else
				ret.addAll(getNextUnits(n, cls));
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	public static List<BusinessActivity> getStartBusinessActivity(Process process) {
		return (List<BusinessActivity>) getNextUnits(process.getTemplate("").getStart(), BusinessActivity.class);
	}
}
