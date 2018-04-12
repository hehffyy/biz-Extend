package com.butone.imp.file;

import java.io.InputStream;
import java.util.Map;

import com.justep.system.data.Table;

public interface TableDataExporter {

	InputStream tableToStream(Map<String, Table> tables, Map<String, Object> variants);
}
