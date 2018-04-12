package com.butone.logic.impl;

import java.util.HashMap;
import java.util.Map;

import com.butone.data.BizDataUtils;
import com.butone.data.SQLUtils;
import com.butone.x5Impl.Engine;
import com.justep.model.Action;
import com.justep.model.Activity;
import com.justep.model.Model;
import com.justep.model.Process;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.util.Utils;

/**
 * 业务数据加载器
 * @author Administrator
 *
 */
public class BizDataLoader implements TableLoader {

	private String detailConcept;
	private String forignKeys;
	private String masterKeys;
	private Action queryAction;
	private String dataFilter;
	private Process process;
	private Activity activity;
	private Map<String, Object> parameters;
	private String cascade;

	public String getForignKeys() {
		return forignKeys;
	}

	public String getCascade() {
		return cascade;
	}

	public void setCascade(String cascade) {
		this.cascade = cascade;
	}

	/**
	 * 标准概念表加载构造函数
	 * @param process
	 * @param activity
	 * @param detailConcept
	 * @param forignkey
	 * @param queryAction
	 */
	public BizDataLoader(com.justep.model.Process process, Activity activity, String detailConcept, String forignKeys, String masterKeys,
			Action queryAction, Map<String, Object> parameters) {
		this.process = process;
		this.activity = activity;
		this.detailConcept = detailConcept;
		this.masterKeys = masterKeys;
		this.forignKeys = forignKeys;
		this.queryAction = queryAction;
		this.parameters = parameters;
	}

	public void setDataFilter(String dataFilter) {
		this.dataFilter = dataFilter;
	}

	@Override
	public String getMasterKeysValue(Row masterRow) {
		return (String) masterRow.getValue(masterKeys);
	}

	public Table loadDetailTable(Row masterRow, Model model) {
		Map<String, Object> params = new HashMap<String, Object>();
		Map<String, Object> variables = new HashMap<String, Object>();
		if (parameters != null)
			variables.putAll(parameters);
		params.put("limit", -1);
		params.put("offset", 0);
		params.put("columns", null);
		params.put("variables", variables);
		String paramName = "param" + forignKeys, filter = SQLUtils.appendCondition(detailConcept + "." + forignKeys + "=:" + paramName, "and",
				dataFilter);
		params.put("filter", filter);
		if (masterRow == null)
			variables.put(paramName, null);
		else
			variables.put(paramName, masterRow.getValue(masterKeys));
		Table tab = (Table) Engine.invokeAction(process, activity, queryAction, ContextHelper.getActionContext().getExecutor(), ContextHelper
				.getActionContext().getExecuteContext(), params);
		String keyColumns = BizDataUtils.getTableKeyColumns(detailConcept, tab);
		if (Utils.isNotEmptyString(keyColumns)) {
			tab.getMetaData().setStoreByConcepts(new String[] { detailConcept });
			tab.getMetaData().setKeyColumn(keyColumns);
		} else {
			tab.getMetaData().setStoreByConcept(detailConcept, false);
		}

		return tab;
	}

	@Override
	public void setDetailForignKeyValues(Row masterRow, Row rec) {
		rec.setValue(forignKeys, masterRow.getValue(masterKeys));
	}

}
