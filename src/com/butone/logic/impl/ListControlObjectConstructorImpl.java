package com.butone.logic.impl;

import java.util.Map;

import com.butone.logic.ListControlObject;
import com.butone.logic.ListControlObjectConstructor;
import com.butone.logic.config.ListControlObjectDesc;
import com.justep.model.ModelUtils;
import com.justep.system.data.Row;
import com.justep.util.Utils;

public class ListControlObjectConstructorImpl implements ListControlObjectConstructor {

	@Override
	public ListControlObject createListControlObject(ListControlObject master, ListControlObjectDesc desc, Map<String, Object> paramValues) {

		if (master == null) {
			TableControlObject controlObject = QueryDataLoader.createListControlObject(desc, paramValues);
			return controlObject;
		} else {
			// 处理自定义sql引用数据集作为从表的情况处理
			TableControlObject controlObject = new TableControlObject((TableControlObject) master, desc.getExtendData(), new QueryDataLoader(desc, paramValues), ModelUtils.getModel(desc
					.getDataModel()));
			controlObject.setObjectId(desc.getObjectId());
			return controlObject;
		}
	}

	@Override
	public void refreshListControlObject(ListControlObject master, ListControlObject object, ListControlObjectDesc desc, Map<String, Object> paramValues) {
		if (Utils.isEmptyString(desc.getMasterObjectId())) {
			((TableControlObject) object).setTable(QueryDataLoader.getMainTable(desc, paramValues));
		} else {
			((TableControlObject) object).reloadDetailTable((Row) master.getCurrentObject());
		}

	}
}
