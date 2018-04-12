package com.butone.spi;

import java.util.ServiceLoader;

public class ServiceProvider {
	static FlowControl getFlowControl() {
		ServiceLoader<FlowControl> s = ServiceLoader.load(FlowControl.class);
		return s.iterator().next();

	}

	static Json2Table getJson2Table() {
		ServiceLoader<Json2Table> s = ServiceLoader.load(Json2Table.class);
		return s.iterator().next();

	}

	static JsonUtils getJsonUtils() {
		ServiceLoader<JsonUtils> s = ServiceLoader.load(JsonUtils.class);
		return s.iterator().next();
	}
}
