package com.butone.extend;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.butone.logic.config.CalcLogicConfig;
import com.butone.logic.impl.BizExprHelper;
import com.butone.model.BizRuleAction;
import com.butone.xml.JaxbUtils;
import com.justep.util.Utils;

public class BizRuleActionExt {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2504210974823362867L;
	private BizRuleAction action;
	private Set<String> dependBizTables;

	public BizRuleAction getAction() {
		return action;
	}

	public BizRuleActionExt(BizRuleAction action) {
		this.action = action;
		init();
	}

	private Object pluginConfigureObject;

	public Object getPluginConfigureObject() {
		return pluginConfigureObject;
	}

	public void init() {
		pluginConfigureObject = null;
		if (action.getPluginConfigure() != null) {
			try {
				pluginConfigureObject = JaxbUtils.unMarshal(new ByteArrayInputStream(action.getPluginConfigure().getBytes("utf-8")), "utf-8",
						CalcLogicConfig.class);
				action.setPluginConfigure(null);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			if (pluginConfigureObject instanceof CalcLogicConfig) {
				((CalcLogicConfig) pluginConfigureObject).prepare();
			}
		}
		parseDependBizTables();
	}

	private void parseDependBizTables() {
		dependBizTables = new HashSet<String>();
		if (!Utils.isEmptyString(action.getCondition())) {
			dependBizTables.addAll(BizExprHelper.parseObjectIdOfTableFunction(action.getCondition()));
		}
		if (Utils.isNotEmptyString(action.getRelBizDatas())) {
			dependBizTables.addAll(Arrays.asList(action.getRelBizDatas().split(",")));
		}
	}

	public Set<String> getDependBizTables() {
		return dependBizTables;
	}
}
