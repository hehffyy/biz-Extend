package com.butone.logic.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.butone.logic.impl.ExpressionValidator.Function;
import com.butone.logic.impl.ExpressionValidator.Parameter;
import com.butone.logic.impl.ExpressionValidator.Word;
import com.justep.util.Utils;

/**
 * 业务表达式辅助工具
 * @author Administrator
 *
 */
public class BizExprHelper {

	/**
	 * 提取函数参数
	 * @param expr
	 * @param fnName
	 * @param paramIndex
	 * @return
	 */
	public static Set<String> pickupFuncitonParameter(String expr, String fnName, int paramIndex) {
		Set<String> ret = new HashSet<String>();
		if (Utils.isEmptyString(expr)) {
			return ret;
		}
		ExpressionValidator validator = new ExpressionValidator(expr);
		List<Word> words = validator.parse();
		List<Function> fns = validator.parseFn(words);
		for (Function fn : fns) {
			ret.addAll(pickupFuncitonParameter(fn, fnName, paramIndex));
		}
		return ret;
	}

	public static Set<String> pickupFuncitonParameter(Function fn, String fnName, int paramIndex) {
		Set<String> ret = new HashSet<String>();
		if (fn.getName().equals(fnName)) {
			ret.add(fn.getParameters().get(paramIndex).getValue());
		} else {
			for (Parameter p : fn.getParameters()) {
				if (p.getFunction() != null) {
					ret.addAll(pickupFuncitonParameter(p.getFunction(), fnName, paramIndex));
				}
			}
		}
		return ret;
	}

	/**
	 * 提取tableData函数中表名参数值
	 * @param expr
	 * @return
	 */
	public static Set<String> parseObjectIdOfTableFunction(String expr) {
		Set<String> ret = new HashSet<String>();
		if (Utils.isEmptyString(expr)) {
			return ret;
		}
		Set<String> params = pickupFuncitonParameter(expr, "tableData", 0);
		params.addAll(pickupFuncitonParameter(expr, "tableRecordCount", 0));
		params.addAll(pickupFuncitonParameter(expr, "tableRow", 0));
		params.addAll(pickupFuncitonParameter(expr, "setTableRowValue", 0));
		for (String name : params) {
			ret.add(name.replace('\'', ' ').trim());
		}
		return ret;
	}
}
