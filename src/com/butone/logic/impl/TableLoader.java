package com.butone.logic.impl;

import com.justep.model.Model;
import com.justep.system.data.Row;
import com.justep.system.data.Table;

public interface TableLoader {
	Table loadDetailTable(Row masterRow, Model model);

	String getForignKeys();

	String getMasterKeysValue(Row masterRow);

	void setDetailForignKeyValues(Row masterRow, Row rec);

	String getCascade();
}
