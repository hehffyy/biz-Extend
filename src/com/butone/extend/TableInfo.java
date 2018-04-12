package com.butone.extend;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程工作表扩展信息
 * @author Administrator
 *
 */
public class TableInfo {
	/**
	 * 概念名，即物理表名
	 */
	private String concept;
	/**
	 * 工作表名，即别名
	 */
	private String name;
	private String masterTable;
	private String foreignKeys;
	private List<FieldInfo> fields = new ArrayList<FieldInfo>();
	private String queryAction;
	private String createAction;
	private String saveAction;
	private String cascade;

	public String getConcept() {
		return concept;
	}

	public void setConcept(String concept) {
		this.concept = concept;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMasterTable() {
		return masterTable;
	}

	public void setMasterTable(String masterTable) {
		this.masterTable = masterTable;
	}

	public String getForeignKeys() {
		return foreignKeys;
	}

	public void setForeignKeys(String foreignKeys) {
		this.foreignKeys = foreignKeys;
	}

	public String getQueryAction() {
		return queryAction;
	}

	public void setQueryAction(String queryAction) {
		this.queryAction = queryAction;
	}

	public String getCreateAction() {
		return createAction;
	}

	public void setCreateAction(String createAction) {
		this.createAction = createAction;
	}

	public String getSaveAction() {
		return saveAction;
	}

	public void setSaveAction(String saveAction) {
		this.saveAction = saveAction;
	}

	public FieldInfo getField(String fieldName) {
		for (FieldInfo field : fields) {
			if (field.getName().equals(fieldName))
				return field;
		}
		return null;
	}

	public List<FieldInfo> getFields() {
		return fields;
	}

	private boolean isUseForTask;

	private boolean isUseForRecord;

	public boolean isUseForTask() {
		return isUseForTask;
	}

	public void setUseForTask(boolean isUseForTask) {
		this.isUseForTask = isUseForTask;
	}

	public boolean isUseForRecord() {
		return isUseForRecord;
	}

	public String getCascade() {
		return cascade;
	}

	public void setCascade(String cascade) {
		this.cascade = cascade;
	}

}
