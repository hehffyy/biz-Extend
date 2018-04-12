package com.butone.excel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.alibaba.fastjson.JSONObject;
import com.butone.utils.SysUtils;
import com.justep.system.data.Row;
import com.justep.system.data.Table;


public class ExcelDataSource {
	private String name;
	private String sql;
	private String keyFld;
	private String parentFld;
	private int pageSize=1;
	private List<ExcelDataSource> children ;
	//非JSON数据
	private List<Row> dataRows;
	private ExcelDataSource parent;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public String getSql() {
		return sql;
	}
	public void setSql(String sql) {
		this.sql = sql;
	}
	
	
	public int getPageSize() {
		return pageSize;
	}
	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}
	public String getKeyFld() {
		return keyFld;
	}
	public void setKeyFld(String keyFld) {
		this.keyFld = keyFld;
	}
	public String getParentFld() {
		return parentFld;
	}
	public void setParentFld(String parentFld) {
		this.parentFld = parentFld;
	}
	public List<ExcelDataSource> getChildren() {
		return children;
	}
	public void setChildren(List<ExcelDataSource> children) {
		this.children = children;
	}
	
	public  Row getRowByIndex(int index){
		return dataRows.get(index);
	}
	public List<Row> getDataRows(Row parentCurRow) {
		if(dataRows==null)
			dataRows = this.getTableRows();
		if(keyFld!=null){
			String parentValue =parentCurRow.getString(this.getParentFld());
			List<Row> curRows = new ArrayList<Row>();
			for(Row row:dataRows){
				String value = row.getString(keyFld);
				if(value!=null && value.equals(parentValue))
					curRows.add(row);
			}
			return curRows;
		}
		else
			return dataRows;
	}
	public void setDataRows(List<Row> dataRows) {
		this.dataRows = dataRows;
	}

	public ExcelDataSource getParent() {
		return parent;
	}
	public void setParent(ExcelDataSource parent) {
		this.parent = parent;
	}
	private List<Row> getTableRows(){
		//设置子的父 方便关联查询
		if(this.getChildren()!=null){
			for(ExcelDataSource cld:this.getChildren()){
				cld.setParent(this);
			}
		}
        String  sql=this.getSql();
        Table table=  SysUtils.query(sql);
        List<Row> list=new ArrayList<Row>();
        for(Iterator<Row> it = table.iterator(); it.hasNext();){
            Row r = (Row)it.next();
            list.add(r);
        }
        return list;
	}
	
	public void parseSql(JSONObject sqlParams){
		Iterator<String> keys= sqlParams.keySet().iterator();
		while(keys.hasNext()) {
			String key = keys.next();
			Object value = sqlParams.get(key);
			if(key.startsWith("FILTER_"))
				sql = sql.replace(":" + key, value.toString());
			else if(key instanceof String){
				sql = sql.replace(":"+key, "'" + value +"'");
			}else{
				sql = sql.replace(":" + key, value.toString());
			}
		}
	}
	
}
