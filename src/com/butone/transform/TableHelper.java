package com.butone.transform;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.justep.system.data.Row;
import com.justep.system.data.Table;

public class TableHelper {
	public static final String TABLE_BLOBCOLUMNS = "row.blobColumns";

	/**
	 * 缓存Blob字段值
	 * @param table
	 * @param r
	 * @param col
	 * @param value
	 */
	public static void cacheBlobValue(Table table, Row r, String col, Object value) {
		@SuppressWarnings("unchecked")
		Map<Row, Map<String, Object>> blobColumns = (Map<Row, Map<String, Object>>) table.getProperties().get(TABLE_BLOBCOLUMNS);
		if (blobColumns == null) {
			blobColumns = new HashMap<Row, Map<String, Object>>();
			table.getProperties().put(TABLE_BLOBCOLUMNS, blobColumns);
		}
		Map<String, Object> blobColumn = (Map<String, Object>) blobColumns.get(r);
		if (blobColumn == null) {
			blobColumn = new HashMap<String, Object>();
			blobColumns.put(r, blobColumn);
		}
		blobColumn.put(col + ".value", value);
	}

//	/**
//	 * 缓存Blob字段Old值
//	 * @param table
//	 * @param r
//	 * @param col
//	 * @param value
//	 */
//	public static void cacheBlobOldValue(Table table, Row r, String col, Object value) {
//		@SuppressWarnings("unchecked")
//		Map<Row, Map<String, Object>> blobColumns = (Map<Row, Map<String, Object>>) table.getProperties().get(TABLE_BLOBCOLUMNS);
//		if (blobColumns == null) {
//			blobColumns = new HashMap<Row, Map<String, Object>>();
//			table.getProperties().put(TABLE_BLOBCOLUMNS, blobColumns);
//		}
//		Map<String, Object> blobColumn = (Map<String, Object>) blobColumns.get(r);
//		if (blobColumn == null) {
//			blobColumn = new HashMap<String, Object>();
//			blobColumns.put(r, blobColumn);
//		}
//		blobColumn.put(col + ".oldValue", value);
//	}

	/**
	 * 获得Blob字段缓存对象
	 * @param table
	 * @param r
	 * @return
	 */
	public static Map<String, Object> getBlobColumnCacheMap(Table table, Row r) {
		@SuppressWarnings("unchecked")
		Map<Row, Map<String, Object>> blobColumns = (Map<Row, Map<String, Object>>) table.getProperties().get(TABLE_BLOBCOLUMNS);
		if (blobColumns != null)
			return blobColumns.get(r);
		return null;
	}

	private static InputStream transform2InputStream(Object value) {
		if (value == null)
			return null;
		if (value instanceof String)
			return new ByteArrayInputStream(((String) value).getBytes());
		else if (value instanceof byte[])
			return new ByteArrayInputStream((byte[]) value);
		else if (value instanceof InputStream)
			return (InputStream) value;
		throw new RuntimeException("缓存数据类型无效。支持String|byte[]|InputStream，当前为" + value.getClass().getName());
	}

	/**
	 * 获得Blob字段缓存值
	 * @param cacheMap
	 * @param col
	 * @return
	 */
	public static InputStream getBlobColumnValue(Map<String, Object> cacheMap, String col) {
		return transform2InputStream(cacheMap.get(col + ".value"));
	}

	/**
	 * 获得Blob字段缓存Old值
	 * @param cacheMap
	 * @param col
	 * @return
	 */
//	public static InputStream getBlobColumnOldValue(Map<String, Object> cacheMap, String col) {
//		return transform2InputStream(cacheMap.get(col + ".oldValue"));
//	}
}
