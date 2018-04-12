package com.butone.spi;

import java.util.Map;

public class JsonUtilsUtils {
	private static com.butone.spi.JsonUtils spi = ServiceProvider.getJsonUtils();

	public static Object map2Json(Map<String, Object> map) {
		return spi.map2Json(map);
	}

	public static Map<String, Object> json2Map(Object json) {
		return spi.json2Map(json);
	}

	public static com.alibaba.fastjson.JSON toFastJSON(Object obj) {
		return spi.toFastJSON(obj);
	}

}
