package com.butone.flowbiz;

import java.util.HashMap;
import java.util.Map;

import com.butone.extend.BizInfo;
import com.butone.extend.BizRuleActionExt;
import com.butone.extend.BizRuleExt;
import com.butone.extend.CacheManager;
import com.butone.logic.config.CalcLogicConfig;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.butone.model.TableDef;
import com.justep.model.Model;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.Expression;
import com.justep.util.Utils;

public class BizRuleRuntime {
	private ProcessLogicPluginContext pluginContext;
	private BizRuleExt ruleExt;

	public BizRuleRuntime(ProcessLogicPluginContext pluginContext) {
		this.pluginContext = pluginContext;
	}

	public void setBizRuleEx(BizRuleExt ruleExt) {
		this.ruleExt = ruleExt;
	}

	/**
	 * 执行验证逻辑，验证逻辑是为了计算BizRule的DataModel构造的内存table数据，从而支持更加复杂判定表达式。
	 */
	private Map<String, Object> executeVerifyLogic(BizInfo bizInfo) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.putAll(pluginContext.getParameters());
		if (ruleExt.getVerifyLogicConfig() != null) {
			// 执行规则验证通用计算
			Map<String, Object> outParams = new HashMap<String, Object>();
			pluginContext.execute(ruleExt.getVerifyLogicConfig(), null, null, outParams);
			if (outParams != null)
				params.putAll(outParams);
		}
		return params;
	}

	/**
	 * 检查业务规则
	 * 
	 * @param event
	 * @return
	 */
	public boolean checkBizRule(String event) {
		ruleTipInfo = "";
		if (event != null && ruleExt.getBizRule().getTriggerEvents().indexOf(event) < 0)
			return false;

		// 加载业务数据
		BizInfo bizInfo = CacheManager.getBizInfo(pluginContext.getProcess());
		for (String tableName : ruleExt.getDependBizTables()) {
			pluginContext.loadBizTable(tableName);
		}
		// 加载临时表
		pluginContext.clearTempTableControlObject();
		for (TableDef table : ruleExt.getTempTableDefs()) {
			pluginContext.addTempTableControlObject(table);
		}
		Model fnModel = ContextHelper.getActionContext().getProcess().getModel();
		Map<String, Object> params = executeVerifyLogic(bizInfo);
		// 计算生效条件
		boolean isValid = true;
		String verifyExpr = ruleExt.getBizRule().getVerifyExpr();
		if (Utils.isNotEmptyString(verifyExpr)) {
			// 执行验证逻辑
			isValid = (Boolean) Expression.evaluate(verifyExpr, params, fnModel);
		}

		if (isValid) {
			if (Utils.isNotEmptyString(ruleExt.getBizRule().getTipExpr()))
				ruleTipInfo = (String) Expression.evaluate(ruleExt.getBizRule().getTipExpr(), params, fnModel);
		}
		return isValid;

	}

	public boolean checkBizRule() {
		return checkBizRule(null);
	}

	private String ruleTipInfo;

	/**
	 * 业务规则提示信息，由checkBizRule计算
	 * 
	 * @return
	 */
	public String getBizRuleTipInfo() {
		return ruleTipInfo;
	}

	/**
	 * 执行业务动作
	 */
	public void executeActions() {
		// 判断是否是业务规则生效的动作
		for (BizRuleActionExt ruleAction : ruleExt.getActionExts()) {
			if (ruleAction.getPluginConfigureObject() == null)
				continue;
			String condition = ruleAction.getAction().getCondition();
			boolean isValid = Utils.isEmptyString(condition)
					|| (Boolean) Expression.evaluate(condition, null, ContextHelper.getActionContext().getProcess().getModel());
			if (isValid)
				pluginContext.execute((CalcLogicConfig) ruleAction.getPluginConfigureObject(), null, null);
		}
	}
}
