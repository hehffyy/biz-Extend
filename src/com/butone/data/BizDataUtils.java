package com.butone.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.butone.x5Impl.TableImpl;
import com.justep.message.CommonMessages;
import com.justep.message.SystemMessages;
import com.justep.model.Concept;
import com.justep.model.Expr;
import com.justep.model.Model;
import com.justep.model.ModelObject;
import com.justep.model.ModelUtils;
import com.justep.model.Relation;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.Expression;
import com.justep.system.data.ModifyState;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.UpdateMode;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class BizDataUtils {
	public static final String MemoryTableKeyColumnName = "_innerId";
	public static final String MemoryTableKeyColumnType = "String";

	/**
	 * 创建内存表的行
	 * @param table
	 * @return
	 */
	public static Row createMemoryRow(Table table) {
		Row newRow = table.appendRow(CommonUtils.createGUID());
		Iterator<ColumnMetaData> i = table.getMetaData().getColumnMetaDatas().iterator();
		while (i.hasNext()) {
			ColumnMetaData colMeta = (ColumnMetaData) i.next();
			String define = colMeta.getDefine();
			if (Utils.isNotEmptyString(define)) {
				//Object value = SimpleTransform.transToObj(colMeta.getType(), define);
				Object value = Expression.evaluate(define, new HashMap<String, Object>(), ModelUtils.getModel("/base/core/logic/fn"));
				newRow.setValue(colMeta.getName(), value);
			}
		}
		return newRow;
	}

	/**
	 * 创建概念表的行
	 * @param table
	 * @param conceptName
	 * @param defaultValues
	 * @param fnModel
	 * @return
	 */
	public static Row createRow(Table table, String conceptName, Map<String, String> defaultValues, String fnModel) {

		Utils.check(Utils.isNotNull(table), CommonMessages.class, "JUSTEP050006", "table");
		Utils.check(Utils.isNotEmptyString(conceptName), CommonMessages.class, "JUSTEP050006", "cocnept");
		if (defaultValues == null)
			defaultValues = Collections.emptyMap();
		if (Utils.isEmptyString(fnModel))
			fnModel = "/system/logic/fn";
		Model model_fn = ModelUtils.getModel(fnModel);
		Row newRow = table.appendRow();

		String str = conceptName + ".";

		HashMap<String, Object> keyValues = new HashMap<String, Object>();
		Iterator<ColumnMetaData> i = table.getMetaData().getColumnMetaDatas().iterator();

		while (i.hasNext()) {
			ColumnMetaData colMeta = (ColumnMetaData) i.next();
			if (newRow.getValue(colMeta.getName()) == null) {
				Object defaultValue = null;
				if (defaultValues.containsKey(colMeta.getName())) {
					// 指定默认值表达式
					Utils.check(Utils.isNotEmptyString(defaultValues.get(colMeta.getName())), SystemMessages.class, "JUSTEP180011", colMeta.getName());
					defaultValue = Expression.evaluate(defaultValues.get(colMeta.getName()), new HashMap<String, Object>(), model_fn);
					newRow.setValue(colMeta.getName(), defaultValue);
				} else if (Utils.isNotEmptyString(colMeta.getDefine())
						&& (colMeta.getDefine().equals(conceptName) || colMeta.getDefine().startsWith(str))) {
					// 计算所有Relation默认值(除Conept外)
					ModelObject defModelObject = colMeta.getDefineModelObject();
					Utils.check(defModelObject != null, SystemMessages.class, "JUSTEP180343", colMeta.getName(), colMeta.getDefine());
					if ((defModelObject instanceof Concept))
						continue;
					Expr defaultExpr = ((Relation) defModelObject).getDefaultValueExpr();
					if (defaultExpr != null && Utils.isNotEmptyString(defaultExpr.getExpr()))
						newRow.setValue(
								colMeta.getName(),
								defaultValue = Expression.evaluate(defaultExpr.getExpr(), new HashMap<String, Object>(), defaultExpr.getDefineModel()));
				}
			}
		}
		// 计算Concept列值 由Concept的主键值组合而成
		i = table.getMetaData().getColumnMetaDatas().iterator();
		while (i.hasNext()) {
			ColumnMetaData colMeta = i.next();
			if (newRow.getValue(colMeta.getName()) == null && Utils.isNotEmptyString(colMeta.getDefine())
					&& (colMeta.getDefine().equals(conceptName) || colMeta.getDefine().startsWith(str))) {
				ModelObject defModelObject = colMeta.getDefineModelObject();
				Utils.check(defModelObject != null, SystemMessages.class, "JUSTEP180343", colMeta.getName(), colMeta.getDefine());
				if (!(defModelObject instanceof Concept))
					continue;
				Concept concept = (Concept) defModelObject;
				if (concept.getKeyRelations().size() > 0) {
					List<Object> datas = new ArrayList<Object>();
					Iterator<Relation> iRelation = concept.getKeyRelations().iterator();
					while (iRelation.hasNext()) {
						Relation r = iRelation.next();
						if (keyValues.containsKey(r.getName())) {
							datas.add(keyValues.get(r.getName()));
						} else {
							Expr defaultExpr = r.getDefaultValueExpr();
							Utils.check(defaultExpr != null && Utils.isNotEmptyString(defaultExpr.getExpr()), SystemMessages.class, "JUSTEP180012",
									concept, r.getName());
							datas.add(Expression.evaluate(defaultExpr.getExpr(), new HashMap<String, Object>(), defaultExpr.getDefineModel()));
						}
					}
					Object coneptValue = ModelUtils.encode(datas);
					newRow.setValue(colMeta.getName(), datas.size() == 1 ? datas.get(0) : coneptValue);
				} else {
					Expr defaultExpr = concept.getDefaultValueExpr();
					if (defaultExpr != null && Utils.isNotEmptyString(defaultExpr.getExpr()))
						newRow.setValue(colMeta.getName(),
								Expression.evaluate(defaultExpr.getExpr(), new HashMap<String, Object>(), defaultExpr.getDefineModel()));
				}

			}
		}
		return newRow;
	}

	public static boolean isKeyRelation(ColumnMetaData meta) {
		ModelObject defModelObject = meta.getDefineModelObject();
		return defModelObject != null && (defModelObject instanceof Relation) && (((Relation) defModelObject).isKeyRelation());
	}

	public static String getTableKeyColumns(String concept, Table table) {
		if (table.getMetaData().getKeyColumnName() != null)
			return table.getMetaData().getKeyColumnName();
		StringBuffer keys = new StringBuffer();
		Iterator<ColumnMetaData> i = table.getMetaData().getColumnMetaDatas().iterator();
		while (i.hasNext()) {
			ColumnMetaData colMeta = (ColumnMetaData) i.next();
			if (colMeta.getDefine() != null && (colMeta.getDefine().endsWith(concept) || colMeta.getDefine().startsWith(concept + "."))
					&& BizDataUtils.isKeyRelation(colMeta))
				keys.append(colMeta.getName()).append(":");
		}
		if (keys.length() > 0)
			return keys.substring(0, keys.length() - 1);
		else {
			if (table.getMetaData().containsColumn(concept)) {
				return concept;
			}
		}
		return null;

	}

	/**
	 * 合并保存后的状态，用于多组件连续编辑保存
	 * @param table
	 */
	public static void mergeStateAfterSave(Table table) {
		String keyColumn = table.getMetaData().getKeyColumnName();
		if (Utils.isEmptyString(keyColumn)) {
			keyColumn = (String) table.getProperties().get(Table.PROP_NAME_ROWID);
			if (Utils.isNotEmptyString(keyColumn))
				table.getMetaData().setKeyColumn(keyColumn);
			else {
				// 没有keyColumn，不支持保存，无需合并
				return;
			}
		}

		TableImpl impl = new TableImpl(table);
		table.setRecordState(false);
		Iterator<Row> i = impl.iteratorAllRows();
		while (i.hasNext()) {
			Row r = i.next();
			ModifyState state = r.getState();
			if (ModifyState.DELETE.equals(state)) {
				r.setState(ModifyState.NONE);
				table.deleteRows(r.getValue(keyColumn));
			} else if (ModifyState.EDIT.equals(state) || ModifyState.NEW.equals(state)) {
				if (UpdateMode.WHERE_VERSION.equals(table.getUpdateMode()) && ModifyState.EDIT.equals(state)) {
					r.setValue("version", r.getInt("version") + 1);
				}
				r.resetState();
			}
		}
		table.setRecordState(true);
	}
}
