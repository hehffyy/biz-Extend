package com.butone.gis.utils;

import com.butone.gis.utils.GisXmlInit;

public class GisConfig {

	public static String getParameterValue(String groupId,String paramId) throws Exception {
		return GisXmlInit.getInstance().getParameterValue(groupId,paramId);
	}
}