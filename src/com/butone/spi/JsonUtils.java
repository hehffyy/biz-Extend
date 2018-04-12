package com.butone.spi;

import java.util.Map;

import com.alibaba.fastjson.JSON;

public interface JsonUtils {
	Object map2Json(Map<String, Object> map);

	Map<String, Object> json2Map(Object json);

	JSON toFastJSON(Object obj);
}
