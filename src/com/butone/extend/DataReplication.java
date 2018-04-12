package com.butone.extend;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.butone.utils.UUIDMaker;
import com.justep.model.Concept;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.model.Relation;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.UpdateMode;
import com.justep.system.util.CommonUtils;

public class DataReplication {

	public static void copyTable(Table fromTable, Table toTable) {

		Iterator<ColumnMetaData> fromColumns = fromTable.getMetaData().getColumnMetaDatas().iterator();
		Iterator<ColumnMetaData> toColumns = toTable.getMetaData().getColumnMetaDatas().iterator();
		HashMap<String, ColumnMetaData> toMap = new HashMap<String, ColumnMetaData>();

		while (toColumns.hasNext()) {
			ColumnMetaData toColumn = toColumns.next();
			toMap.put(toColumn.getName().toUpperCase() + "_" + toColumn.getType().toUpperCase(), toColumn);

		}

		if (fromTable.size() > 0) {
			Iterator<Row> rows = fromTable.iterator();
			while (rows.hasNext()) {
				Row fromRow = rows.next();
				Row toRow = toTable.appendRow(CommonUtils.createGUID());
				toRow.setValue("version", 0);
				if (StringUtils.isNotEmpty(fromRow.getTable().getMetaData().getKeyColumnName()))
					toRow.setValue(fromRow.getTable().getMetaData().getKeyColumnName(), CommonUtils.createGUID());

				copyRowOfTable(fromColumns, toMap, fromRow, toRow);
			}
		}
	}

	private static void copyRowOfTable(Iterator<ColumnMetaData> fromColumns, HashMap<String, ColumnMetaData> toMap, Row fromRow, Row toRow) {

		String nameType = "";
		while (fromColumns.hasNext()) {
			ColumnMetaData fromColumn = fromColumns.next();
			nameType = fromColumn.getName() + "_" + fromColumn.getType();
			nameType = nameType.toUpperCase();
			if ("version".equals(fromColumn.getName().toLowerCase()) || fromColumn.getName().equals(fromRow.getTable().getMetaData().getKeyColumnName()))
				continue;
			if (toMap.get(nameType) != null) {
				toRow.setValue(toMap.get(nameType).getName(), fromRow.getValue(fromColumn.getName()));
			}
		}
	}

	/**
	 * 代码兼容
	 * 
	 * @param fromRow
	 * @param toRow
	 */
	public static void copyRow(Row fromRow, Row toRow) {
		copyRow(fromRow, toRow, null, null, null);
	}

	/**
	 * 行数据复制
	 * 
	 * @param fromRow
	 * @param toRow
	 */
	public static void copyRow(Row fromRow, Row toRow, Table toTable, String column, String value) {

		if (fromRow == null)
			return;
		Table fromTable = fromRow.getTable();

		if (toRow == null) {
			toRow = toTable.appendRow(CommonUtils.createGUID());
			toRow.setValue("version", 0);
		} else if (toTable == null) {
			toTable = toRow.getTable();
		}

		Iterator<ColumnMetaData> fromColumns = fromTable.getMetaData().getColumnMetaDatas().iterator();
		Iterator<ColumnMetaData> toColumns = toTable.getMetaData().getColumnMetaDatas().iterator();
		HashMap<String, ColumnMetaData> toMap = new HashMap<String, ColumnMetaData>();

		while (toColumns.hasNext()) {
			ColumnMetaData toColumn = toColumns.next();
			toMap.put(toColumn.getName().toUpperCase() + "_" + toColumn.getType().toUpperCase(), toColumn);

		}

		String nameType = "";
		while (fromColumns.hasNext()) {
			ColumnMetaData fromColumn = fromColumns.next();
			nameType = fromColumn.getName() + "_" + fromColumn.getType();
			nameType = nameType.toUpperCase();
			if (toMap.get(nameType) != null) {
				toRow.setValue(toMap.get(nameType).getName(), fromRow.getValue(fromColumn.getName()));
			}

		}
		if (!StringUtils.isEmpty(column))
			toRow.setValue(column, value);

	}

	public static void copyRowEx(Row fromRow, Row toRow, Table toTable, Object fields, Map<String, Object> mapValue) {

		if (fromRow == null)
			return;
		Table fromTable = fromRow.getTable();

		if (toRow == null) {
			toTable.setUpdateMode(UpdateMode.WHERE_ALL);
			toRow = toTable.appendRow(CommonUtils.createGUID());
			toRow.setValue("version", 0);
		} else if (toTable == null) {
			toTable = toRow.getTable();
		}

		Iterator<ColumnMetaData> fromColumns = fromTable.getMetaData().getColumnMetaDatas().iterator();
		Iterator<ColumnMetaData> toColumns = toTable.getMetaData().getColumnMetaDatas().iterator();
		HashMap<String, ColumnMetaData> toMap = new HashMap<String, ColumnMetaData>();

		while (toColumns.hasNext()) {
			ColumnMetaData toColumn = toColumns.next();
			toMap.put(toColumn.getName().toUpperCase() + "_" + toColumn.getType().toUpperCase(), toColumn);

		}

		String nameType = "";
		while (fromColumns.hasNext()) {
			ColumnMetaData fromColumn = fromColumns.next();
			nameType = fromColumn.getName() + "_" + fromColumn.getType();
			nameType = nameType.toUpperCase();
			if (toMap.get(nameType) != null && (fields == null || !fields.toString().toUpperCase().contains(fromColumn.getName()))) {
				toRow.setValue(toMap.get(nameType).getName(), fromRow.getValue(fromColumn.getName()));
			}

		}

		Iterator<String> keys = mapValue.keySet().iterator();
		String key = "";
		while (keys.hasNext()) {
			key = keys.next().toString();
			toRow.setValue((String) key, mapValue.get(key));
		}

	}

	/*
	 * 材料复制
	 */
	public static void copyMaterial(String fromId, String toId) {

		Model dataModel = ModelUtils.getModel("/base/core/material/data");
		Table toTab = KSQL.select("Select p.* from B_Material p where 1=2 ", null, dataModel, null);
		toTab.getMetaData().setStoreByConcept("B_Material", true);
		toTab.getMetaData().setKeyColumn("p");

		StringBuffer sql_info = new StringBuffer();
		sql_info.append("Select p.* from B_Material p where p.fBizRecId='" + fromId + "'");
		Table fromTble = KSQL.select(sql_info.toString(), null, dataModel, null);
		Concept conceptTable = dataModel.getUseableConcept("B_Material");

		HashMap<String, Object> ingores = new HashMap<String, Object>();
		ingores.put("fID", "fID");

		Iterator<Row> rows = fromTble.iterator();

		while (rows.hasNext()) {
			Row fromRow = rows.next();
			Row toRow = toTab.appendRow(CommonUtils.createGUID());
			rowSelfCopy(conceptTable, fromRow, toRow, ingores);
			toRow.setValue("fBizRecId", toId);
		}
		toTab.save(dataModel);

	}

	/**
	 * 本概念内的行记录复制
	 * 
	 * @param conceptTable
	 *            概念名
	 * @param From
	 *            源Row
	 * @param to
	 *            目标Row
	 * @param ingores
	 *            不复制的字段
	 */
	private static void rowSelfCopy(Concept conceptTable, Row From, Row to, HashMap<String, Object> ingores) {
		for (Relation r : conceptTable.getRelations()) {
			if (ingores.get(r.getName()) != null)
				continue;
			to.setValue(r.getName(), From.getValue(r.getName()));
		}

	}

	/**
	 * 复制A概念的数据到B概念(任意2个业务的数据复制,复制的原则是字段名和类型都要相同复制) 暂不放开
	 * 
	 * @param sourceProcess
	 *            源业务的process
	 * @param toProcess
	 *            目标业务的process
	 * @param foreignKeyName
	 *            源概念的外键
	 * @param foreignKeyValue
	 *            源概念的外键值
	 * @param sourceConcept
	 *            源概念
	 * @param toConcept
	 *            目标概念
	 * @return
	 */
	private static String copyTable(String fFlowBizRecId, String sourceProcess, String toProcess, String sourceConcept, String toConcept, String foreignKeyName, String foreignKeyValue) {

		String sourceBizPath = ModelPathHelper.getProcessBizPath(sourceProcess);
		Model sourceModel = ModelUtils.getModel(sourceBizPath + "/ontology");
		String toBizPath = ModelPathHelper.getProcessBizPath(toProcess);
		Model toModel = ModelUtils.getModel(toBizPath + "/ontology");
		Concept conceptSourceTable = sourceModel.getUseableConcept(sourceConcept);
		// String sourceTableName =
		// ModelExtUtils.getTableName(conceptSourceTable);
		Concept concepteToTable = toModel.getUseableConcept(toConcept);
		// String toConcepteTableName =
		// ModelExtUtils.getTableName(concepteToTable);

		HashMap<String, Relation> toMap = new HashMap<String, Relation>();
		for (Relation r : concepteToTable.getRelations()) {
			toMap.put(r.getName() + "_" + r.getDataType(), r);
		}
		String sourceDataModel = ModelPathHelper.getProcessDataModel(ModelUtils.getProcess(sourceProcess));
		String toDataModel = ModelPathHelper.getProcessDataModel(ModelUtils.getProcess(toProcess));

		Table sourceTab = KSQL.select("SELECT p.* FROM " + sourceConcept + " p where p." + foreignKeyName + "='" + foreignKeyValue + "'", null, sourceDataModel, null);
		Table toTab = KSQL.select("SELECT p.* FROM " + toConcept + " p where 1=2", null, toDataModel, null);
		toTab.getMetaData().setStoreByConcept(toConcept, true);
		toTab.getMetaData().setKeyColumn("p");

		if (sourceTab.size() > 0) {
			Iterator<Row> rows = sourceTab.iterator();
			while (rows.hasNext()) {
				Row sourceRow = rows.next();
				Row toRow = toTab.appendRow(UUIDMaker.generate());
				copyRowOfConcept(conceptSourceTable, toMap, sourceRow, toRow);
				if (toMap.get("fBizRecId_String") != null) {
					toRow.setValue("fBizRecId", fFlowBizRecId);
				}
			}
		}

		return String.valueOf(toTab.save(toDataModel));
	}

	/**
	 * 行数据复制
	 * 
	 * @param conceptSourceTable
	 * @param toMap
	 * @param sourceRow
	 * @param toRow
	 */
	private static void copyRowOfConcept(Concept conceptSourceTable, HashMap<String, Relation> toMap, Row sourceRow, Row toRow) {
		String nameType = "";
		for (Relation r : conceptSourceTable.getRelations()) {
			if ("version".equals(r.getName()) || "fBizRecId".equals(r.getName()))
				continue;

			nameType = r.getName() + "_" + r.getDataType();
			if (toMap.get(nameType) != null) {
				toRow.setValue(r.getName(), sourceRow.getValue(r.getName()));
			}
		}
	}

}
