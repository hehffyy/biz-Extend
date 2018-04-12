package com.butone.logic.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.butone.data.BizDataUtils;
import com.butone.data.SQLUtils;
import com.butone.logic.config.ListControlObjectDesc;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.util.Utils;

/**
 * 查询数据加载器
 * 
 * @author Administrator
 */
public class QueryDataLoader implements TableLoader {

	private ListControlObjectDesc desc;

	public ListControlObjectDesc getDesc() {
		return desc;
	}

	@Override
	public String getCascade() {
		return desc.getCascade();
	}

	private Map<String, Object> varMap;

	public QueryDataLoader(ListControlObjectDesc desc, Map<String, Object> paramValues) {
		this.desc = desc;
		this.varMap = new HashMap<String, Object>();
		if (paramValues != null)
			this.varMap.putAll(paramValues);
	}

	public void setDetailForignKeyValues(Row masterRow, Row rec) {
		String[] forignKeys = desc.getForignKeys().split(":");
		String[] masterKeys = desc.getMasterKeys().split(":");
		for (int i = 0; i < forignKeys.length; i++) {
			Utils.check(!desc.isQuery(), QueryDataLoader.class, "查询数据集不支持DDL操作");
			String forignKey = forignKeys[i].split("\\.")[1];
			String masterKey = masterKeys[i];
			if (masterKey.contains("."))
				masterKey = masterKey.split("\\.")[1];
			rec.setValue(forignKey, masterRow.getValue(masterKey));
		}
	}

	@Override
	public String getMasterKeysValue(Row masterRow) {
		String[] masterKeys = desc.getMasterKeys().split(":");
		String values = "";
		for (int i = 0; i < masterKeys.length; i++) {
			String masterKey = masterKeys[i];
			if (masterKey.contains("."))
				masterKey = masterKey.split("\\.")[1];
			values += (values.length() > 0 ? ";" : "") + masterRow.getValue(masterKey);
		}
		return values;
	}

	@Override
	public Table loadDetailTable(Row masterRow, Model model) {
		String[] forignKeys = desc.getForignKeys().split(":");
		String[] masterKeys = desc.getMasterKeys().split(":");
		for (int i = 0; i < forignKeys.length; i++) {
			String paramName = forignKeys[i];
			if (paramName.indexOf(".") >= 0)
				paramName = "param" + paramName.split("\\.")[1];
			else
				paramName = "param" + paramName;
			if (masterRow == null)
				varMap.put(paramName, null);
			else {
				String masterKey = masterKeys[i];
				if (masterKey.contains("."))
					masterKey = masterKey.split("\\.")[1];
				varMap.put(paramName, masterRow.getValue(masterKey));
			}
		}
		String sql = getSelect(desc, varMap);
		Table table = null;
		String concept = getMainTable(desc);
		if (desc.isQuery()) {
			List<Object> paramList = SQLUtils.parseSqlParameters(sql, varMap);
			sql = SQLUtils.fixSQL(sql, varMap, true);
			table = SQLUtils.select(sql, paramList, desc.getDataModel());
		} else {
			table = KSQL.select(sql, varMap, desc.getDataModel(), ModelUtils.getModel("/base/core/logic/fn"));
			String keyColumns = BizDataUtils.getTableKeyColumns(concept, table);
			if (Utils.isNotEmptyString(keyColumns)) {
				table.getMetaData().setKeyColumn(keyColumns);
				table.getMetaData().setStoreByConcepts(new String[] { concept });
			} else {
				// 设置不存在的表为store，用于取消所有表的保存
				table.getMetaData().setStoreByConcepts(new String[] { "!" });
			}
		}
		return table;
	}

	@Override
	public String getForignKeys() {
		return desc.getForignKeys();
	}

	//	private static String fixSQL(String sql, Map<String, Object> variables) {
	//		String str = sql;
	//		while (str.contains(":inClause(")) {
	//			int i = str.indexOf(":inClause(");
	//			String expr = str.substring(i + 1, str.indexOf(")", i) + 1);
	//			String in = (String) ExpressEngine.calculate(expr, variables, ModelUtils.getModel("/base/core/logic/fn"));
	//			str = str.substring(0, i) + in + str.substring(str.indexOf(")", i) + 1);
	//		}
	//		return str;
	//	}

	private static String getMainTable(ListControlObjectDesc desc) {
		// TODO 这个判断机制不是很健壮 getExtendData中记录了Form的主表
		return desc.getExtendData();
	}

	private static String getSelect(ListControlObjectDesc desc, Map<String, Object> paramValues) {
		if (desc.isQuery()) {
			String sql = desc.getSelect();
			if (Utils.isNotEmptyString(desc.getMasterObjectId())) {
				Utils.check(Utils.isNotEmptyString(desc.getForignKeys()), "明细表" + desc.getObjectId() + "未设置外键");
				String[] forignKeys = desc.getForignKeys().split(":");
				Utils.check(forignKeys.length > 0, "明细表" + desc.getObjectId() + "未设置外键");
				String where = "";
				for (int i = 0; i < forignKeys.length; i++) {
					// forignKey format = table.field
					String fieldName = forignKeys[i];
					fieldName = fieldName.contains(".") ? fieldName.split("\\.")[1] : fieldName;
					String paramName = "param" + fieldName;
					// 注意SQL的where 必须是 fieldAlas = :xxxx
					where = SQLUtils.appendCondition(where, "and", fieldName + "=:" + paramName);
				}
				sql = "select * from (" + sql + ") TMP where " + where;
			}
			return SQLUtils.fixSQL(sql, paramValues, false);
		} else {
			StringBuffer sql = new StringBuffer();
			sql.append("select ");
			if (desc.isDistinct())
				sql.append(" distinct ");
			if (desc.getSelect().contains("*")) {
				sql.append(desc.getSelect());
			} else {
				String mainTable = getMainTable(desc);
				if (!desc.getSelect().contains(mainTable + ","))
					sql.append(mainTable).append(",");
				sql.append(desc.getSelect());
			}
			sql.append(" FROM " + desc.getFrom() + " ");
			String where = desc.getCondition();

			if (Utils.isNotEmptyString(desc.getMasterObjectId())) {
				Utils.check(Utils.isNotEmptyString(desc.getForignKeys()), "明细表" + desc.getObjectId() + "未设置外键");
				String[] forignKeys = desc.getForignKeys().split(":");
				Utils.check(forignKeys.length > 0, "明细表" + desc.getObjectId() + "未设置外键");
				for (int i = 0; i < forignKeys.length; i++) {
					// forignKey format = table.field
					String forignKey = forignKeys[i];
					String fieldName = forignKey.contains(".") ? forignKey.split("\\.")[1] : forignKey;
					String paramName = "param" + fieldName;
					// 注意KSQL的where 必须是 tableAlias.fieldAlas = :xxxx
					where = SQLUtils.appendCondition(where, "and", forignKey + "=:" + paramName);
				}
			}
			if (Utils.isNotEmptyString(where))
				sql.append(" where ").append(where);

			if (Utils.isNotEmptyString(desc.getOrderBy()))
				sql.append(" order by ").append(desc.getOrderBy());
			return SQLUtils.fixSQL(sql.toString(), paramValues, false);
		}
	}

	public static Table getMainTable(ListControlObjectDesc desc, Map<String, Object> paramValues) {
		String sql = getSelect(desc, paramValues);
		Table table = null;

		String concept = getMainTable(desc);

		if (desc.isQuery()) {
			List<Object> paramList = SQLUtils.parseSqlParameters(sql, paramValues);
			sql = SQLUtils.fixSQL(sql, paramValues, true);
			table = SQLUtils.select(sql, paramList, desc.getDataModel());
		} else {
			table = KSQL.select(sql, paramValues, desc.getDataModel(), ModelUtils.getModel("/base/core/logic/fn"));
			String keyColumns = BizDataUtils.getTableKeyColumns(concept, table);
			if (Utils.isNotEmptyString(keyColumns)) {
				table.getMetaData().setKeyColumn(keyColumns);
				table.getMetaData().setStoreByConcepts(new String[] { concept });
			} else {
				// 设置不存在的表为store，用于取消所有表的保存
				table.getMetaData().setStoreByConcepts(new String[] { "!" });
			}
		}
		return table;
	}

	public static TableControlObject createListControlObject(ListControlObjectDesc desc, Map<String, Object> paramValues) {
		String sql = getSelect(desc, paramValues);
		Table table = null;

		String concept = getMainTable(desc);

		if (desc.isQuery()) {
			List<Object> paramList = SQLUtils.parseSqlParameters(sql, paramValues);
			sql = SQLUtils.fixSQL(sql, paramValues, true);
			table = SQLUtils.select(sql, paramList, desc.getDataModel());
		} else {
			table = KSQL.select(sql, paramValues, desc.getDataModel(), ModelUtils.getModel("/base/core/logic/fn"));
			String keyColumns = BizDataUtils.getTableKeyColumns(concept, table);
			if (Utils.isNotEmptyString(keyColumns)) {
				table.getMetaData().setKeyColumn(keyColumns);
				table.getMetaData().setStoreByConcepts(new String[] { concept });
			} else {
				// 设置不存在的表为store，用于取消所有表的保存
				table.getMetaData().setStoreByConcepts(new String[] { "!" });
			}
		}
		TableControlObject controlObject = new TableControlObject(concept, table, ModelUtils.getModel(desc.getDataModel()));
		controlObject.setObjectId(desc.getObjectId());
		return controlObject;
	}
}
