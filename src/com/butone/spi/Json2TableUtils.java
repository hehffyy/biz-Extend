package com.butone.spi;

import com.justep.system.data.Table;

public class Json2TableUtils {
	private static com.butone.spi.Json2Table spi = ServiceProvider.getJson2Table();

	public static Table transform(Object json) {
		return spi.transform(json);
	}
}
