package com.butone.flowbiz;

import com.alibaba.fastjson.JSONObject;
import com.justep.system.data.ModifyState;
import com.justep.system.data.Row;
import com.justep.util.Utils;

public class BaseRuntime {
	protected String bizRecId;

	public String getBizRecId() {
		return bizRecId;
	}

	public void setBizRecId(String bizRecId) {
		this.bizRecId = bizRecId;
	}

	public BaseRuntime() {

	}

	public BaseRuntime(String bizRecId) {
		this.bizRecId = bizRecId;
	}

	protected void setFaultResult(JSONObject json, String message) {
		json.put("result", false);
		json.put("message", message);
	}

	protected JSONObject createResult() {
		JSONObject ret = new JSONObject();
		ret.put("result", true);
		return ret;
	}

	protected void chechNewRowModifyState(Row r) {
		ModifyState state = r.getState();
		Utils.check(ModifyState.NEW.equals(state) || ModifyState.NONE.equals(state), "新增数据的状态异常:" + state);
		if (ModifyState.NONE.equals(state)) {
			r.setState(ModifyState.NEW);
		}
	}
}
