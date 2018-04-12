package com.butone.logic.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpSession;

import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.Expression;

/**
 * 
 * @author tangkejie 不是很好的方案，废弃
 */

public class SystemParams {
	private static final String cBaseFnUrl = "/base/core/logic/fn";
	private static final String cSystemFnUrl = "/system/logic/fn";
	private static final String[] cBaseFnAry = {};
	private static final String[] cSystemFnAry = { "currentPersonID", "currentPersonName", "currentOrgID" };

	// 初始化个人全局变量
	private static void init() {
		HttpSession session = ContextHelper.getSessionContext().getSession();
		Map<String, Object> _sysParams = new HashMap<String, Object>();
		// 初始化系统变量
		for (int i = 0; i < cSystemFnAry.length; i++) {
			String name = cSystemFnAry[i];
			Object value = getFnValue(name + "()", cSystemFnUrl);
			_sysParams.put(cSystemFnAry[i], value);
		}
		// 初始化Base变量
		for (int i = 0; i < cBaseFnAry.length; i++) {
			String name = cBaseFnAry[i];
			Object value = getFnValue(name + "()", cBaseFnUrl);
			_sysParams.put(cBaseFnAry[i], value);
		}
		// 初始化需要传参的变量
		_sysParams.put("currentAreaCode", getFnValue("currentAreaIdOrName(true)", cBaseFnUrl));
		_sysParams.put("currentAreaId", _sysParams.get("currentAreaCode"));// 兼容支持组扩展的这个类,类名和内容重复了
		_sysParams.put("currentAreaName", getFnValue("currentAreaIdOrName(false)", cBaseFnUrl));
		session.setAttribute("_sysParams", _sysParams);
	}

	// 计算Fn
	private static Object getFnValue(String name, String modelUrl) {
		Model fnModel = ModelUtils.getModel(modelUrl);
		return Expression.evaluate(name, null, fnModel);
	}

	@Deprecated
	public static Map<String, Object> getSysParams() {

		HttpSession session = ContextHelper.getSessionContext().getSession();
		Map<String, Object> _sysParams = (Map<String, Object>) session.getAttribute("_sysParams");

		if (_sysParams == null) {
			init();
			_sysParams = (Map<String, Object>) session.getAttribute("_sysParams");
		}
		return _sysParams;
	}

	// 获得系统全局参数值
	@Deprecated
	public static Object getParamValue(String name) {
		HttpSession session = ContextHelper.getSessionContext().getSession();
		Map<String, Object> _sysParams = (Map<String, Object>) session.getAttribute("_sysParams");
		if (_sysParams == null) {
			init();
			_sysParams = (Map<String, Object>) session.getAttribute("_sysParams");
		}
		if (_sysParams != null && _sysParams.containsKey(name))
			return _sysParams.get(name);
		else {
			Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
			return Expression.evaluate(name, null, fnModel);
		}
	}

	/**
	 * 解析Sql
	 * 
	 * @param sql
	 * @return
	 * @throws Exception
	 */
	public static String parseSql(String sql) throws Exception {
		// 提取变量[]
		List<String> paramList = parseSqlParams(sql);
		Map<String, String> valMap = new HashMap<String, String>();
		for (String param : paramList) {
			if (valMap.containsKey(param))
				continue;
			Object val = getParamValue(param);
			if (val == null)
				throw new Exception(param + "变量无法解析！");
			else {
				if (val instanceof String)
					valMap.put(param, "'" + val.toString() + "'");
				else
					valMap.put(param, val.toString());
			}
		}
		Iterator<String> iter = valMap.keySet().iterator();
		while (iter.hasNext()) {
			String name = iter.next();
			String val = valMap.get(name);
			// 支持在字段区域设置变量 必须带引号
			sql = sql.replace("'[" + name + "]'", val);
			sql = sql.replace("[" + name + "]", val);
		}
		return sql;
	}

	private static List<String> parseSqlParams(String str) {
		List<String> list = new ArrayList<String>();
		// Pattern p = Pattern.compile("(?<=\\[)(.+?)(?=\\])");
		Pattern p = Pattern.compile(":([\\w\u4e00-\u9fa5]+)\\b");
		Matcher m = p.matcher(str);
		while (m.find()) {
			String param = m.group(0);
			if (list.indexOf(param) == -1)
				list.add(param);
		}
		return list;
	}

}
