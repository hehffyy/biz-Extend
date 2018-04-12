package com.butone.extend;

import java.io.ByteArrayInputStream;

import com.butone.logic.config.CalcLogicConfig;
import com.butone.model.BizLogicPlugin;
import com.butone.xml.JaxbUtils;

public class BizLogicPluginEx {
	private BizLogicPlugin plugin;
	private CalcLogicConfig calcLogicConfig;

	public BizLogicPluginEx(BizLogicPlugin plugin) {
		this.plugin = plugin;
		init();
	}

	private void init() {
		calcLogicConfig = null;
		if (plugin.getParameter() != null) {
			try {
				calcLogicConfig = (CalcLogicConfig) JaxbUtils.unMarshal(new ByteArrayInputStream(plugin.getParameter().getBytes("utf-8")), "utf-8",
						CalcLogicConfig.class);
				calcLogicConfig.prepare();
				plugin.setParameter(null);

			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public CalcLogicConfig getCalcLogicConfig() {
		return calcLogicConfig;
	}

	public BizLogicPlugin getBizLogicPlugin() {
		return plugin;
	}
}
