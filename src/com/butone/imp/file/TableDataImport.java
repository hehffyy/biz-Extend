package com.butone.imp.file;

import java.io.InputStream;
import java.util.Map;

import com.justep.system.data.Table;

/**
 * Table数据加载器
 * @author Administrator
 *
 */
public interface TableDataImport {
	void loadToTable(InputStream in, Map<String, Table> tables, Map<String, Object> variants);
}
