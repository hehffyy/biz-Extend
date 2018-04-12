package com.butone.codeinf.util;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.codeinf.model.CodeDef;
import com.butone.codeinf.model.SequenceCode;
import com.butone.codeinf.model.emu.DataType;
import com.butone.codeinf.model.emu.SequenceType;
import com.butone.codeinf.model.node.ConstantNode;
import com.butone.codeinf.model.node.DateNode;
import com.butone.codeinf.model.node.Node;
import com.butone.codeinf.model.node.ParameterNode;
import com.butone.codeinf.model.node.SequenceNode;
import com.butone.codeinf.model.node.VariableNode;

public class CodeGenerator {

	public static void main(String[] args) {

	}

	private SequenceCode sequenceCode;

	public void setSequenceCode(SequenceCode sequenceCode) {
		this.sequenceCode = sequenceCode;
	}

	private Map<String, Object> calcGroupValue(CodeDef def, String tableName, Map<String, String> nodeValues) {
		Map<String, Object> ret = new HashMap<String, Object>();
		StringBuffer sequenceFormat = new StringBuffer();
		StringBuffer groupValue = new StringBuffer();
		for (Node node : def.getNodes()) {
			String value = null;
			if (nodeValues != null && nodeValues.containsKey(node.getGuid())) {
				value = nodeValues.get(node.getGuid());
			} else if (node instanceof SequenceNode) {
				ret.put("sequenceNode", node);
				value = "%s";
			} else {
				value = getNodeValue(node);

			}
			sequenceFormat.append(value);
			if (node.isRelateSequenceValue() || (node instanceof SequenceNode))
				groupValue.append(value);
		}
		ret.put("groupValue", groupValue.toString());
		ret.put("sequenceFormat", sequenceFormat.toString());
		return ret;
	}

	public Map<String, String> previewCodeValue(CodeDef def, String userTable, Map<String, String> nodeValues) {
		Map<String, Object> args = calcGroupValue(def, userTable, nodeValues);
		String groupValue = (String) args.get("groupValue");
		String sequenceFormat = (String) args.get("sequenceFormat");
		SequenceNode sequNode = (SequenceNode) args.get("sequenceNode");
		int num;
		String relTableName = sequNode.isRelTableName() ? userTable : "NULL";
		num = sequenceCode.initSequenceCodeLog(def.getGuid(), groupValue.toString(), relTableName, def.getName());
		String numText = getNodeValue(sequNode, num + 1);
		Map<String, String> ret = new HashMap<String, String>();
		ret.put("groupValue", groupValue.toString());
		ret.put("sequenceValue", String.format(sequenceFormat, numText));
		return ret;
	}

	/**
	 * 只编码不记录使用
	 * @param def
	 * @param nodeValues
	 * @param userTable
	 * @return
	 */
	public String makeCodeValue(CodeDef def, Map<String, String> nodeValues, String userTable) {
		return (String) makeCodeValue(def, nodeValues, userTable, 1, false).getJSONObject(0).getString("codeValue");
	}

	public JSONArray makeCodeValue(CodeDef def, Map<String, String> nodeValues, String userTable, int interval) {
		return makeCodeValue(def, nodeValues, userTable, interval, false);
	}

	/**
	 * 只编码不记录，并使用指定的步进
	 * @param def
	 * @param nodeValues
	 * @param userTable
	 * @param interval
	 * @return
	 */
	public JSONArray makeCodeValue(CodeDef def, Map<String, String> nodeValues, String userTable, int interval, boolean immediate) {
		Map<String, Object> args = calcGroupValue(def, userTable, nodeValues);
		String groupValue = (String) args.get("groupValue");
		String sequenceFormat = (String) args.get("sequenceFormat");
		SequenceNode sequNode = (SequenceNode) args.get("sequenceNode");
		String relTableName = sequNode.isRelTableName() ? userTable : "NULL";
		// 初始化编码序列日志
		int newValue = sequenceCode.updateSequenceCodeLog(def.getGuid(), groupValue, relTableName, interval, immediate);
		JSONArray ret = new JSONArray();
		for (int n = 0; n < interval; n++) {
			JSONObject obj = new JSONObject();
			int number = newValue - n;
			obj.put("number", number);
			String numText = getNodeValue(sequNode, number);
			String codeValue = String.format(sequenceFormat, numText);
			obj.put("codeValue", codeValue);
			ret.add(obj);
		}
		return ret;
	}

	public String makeCodeValue(CodeDef def, Map<String, String> nodeValues, String userTable, String userField, String userKeyValues) {
		return makeCodeValue(def, nodeValues, userTable, userField, userKeyValues, false);
	}

	public String makeCodeValue(CodeDef def, Map<String, String> nodeValues, String userTable, String userField, String userKeyValues,
			boolean immediate) {
		Map<String, Object> args = calcGroupValue(def, userTable, nodeValues);
		String groupValue = (String) args.get("groupValue");
		String sequenceFormat = (String) args.get("sequenceFormat");
		SequenceNode sequNode = (SequenceNode) args.get("sequenceNode");
		String relTableName = sequNode.isRelTableName() ? userTable : "NULL";
		// 初始化编码序列日志
		int newValue = sequenceCode.updateSequenceCodeLog(def.getGuid(), groupValue, relTableName, 1, immediate);

		String numText = getNodeValue(sequNode, newValue);
		String codeValue = String.format(sequenceFormat, numText);
		// 产生编码使用记录
		sequenceCode.makeSequenceUseRecord(def.getGuid(), groupValue, relTableName, userTable, userField, userKeyValues, codeValue);
		return codeValue;

	}

	public void releaseCodeValue(String userTable, String userField, String userKeyValues) {
		sequenceCode.releaseCodeValue(userTable, userField, userKeyValues);
	}

	private String getNodeValue(Node node) {
		if (node instanceof DateNode)
			return getNodeValue((DateNode) node);
		else if (node instanceof ConstantNode)
			return getNodeValue((ConstantNode) node);
		else if (node instanceof ParameterNode)
			return getNodeValue((ParameterNode) node);
		else if (node instanceof VariableNode)
			return getNodeValue((VariableNode) node);
		throw new RuntimeException("不支持的通用编码节点类型:" + node.getClass().getName());
	}

	private String getNodeValue(SequenceNode node, int num) {
		if (node.getRuleType().equals(SequenceType.IS_Lower_FillZero.name())) {
			return String.format("%0" + node.getSequenceLength() + "d", num);
		} else if (node.getRuleType().equals(SequenceType.IS_Lower_NotFillZero.name())) {
			return new Integer(num).toString();
		} else if (node.getRuleType().equals(SequenceType.IS_Capital_FillZero.name())) {
			return ChinaUtil.numToUpper(num, node.getSequenceLength());
		} else if (node.getRuleType().equals(SequenceType.IS_Capital_NotFillZero.name())) {
			return ChinaUtil.numToUpper(num);
		}
		throw new RuntimeException("不支持序列编码类型:" + node.getRuleType());
	}

	private String getNodeValue(ParameterNode node) {
		return sequenceCode.calcNodeValue(node);
	}

	private String getNodeValue(VariableNode node) {
		return sequenceCode.calcNodeValue(node);
	}

	private String getNodeValue(ConstantNode node) {
		return node.getConstStr();
	}

	private String getNodeValue(DateNode node) {
		StringBuilder ret = new StringBuilder();
		// 是否显示年
		if (node.isUseYear()) {
			if (node.getYearCodeType().equals(DataType.IS_Capital_Year_yy.name())) {
				ret.append(ChinaUtil.numToUpper(FormatDate.getCurrentYear("yy")));
			} else if (node.getYearCodeType().equals(DataType.IS_Capital_Year_yyyy.name())) {
				ret.append(ChinaUtil.numToUpper(FormatDate.getCurrentYear("yyyy")));
			} else if (node.getYearCodeType().equals(DataType.IS_Lower_Year_yy.name())) {
				ret.append(FormatDate.getCurrentYear("yy"));
			} else if (node.getYearCodeType().equals(DataType.IS_Lower_Year_yyyy.name())) {
				ret.append(FormatDate.getCurrentYear("yyyy"));
			}
		}
		// 是否显示月
		if (node.isUseMonth()) {
			if (node.getMonthCodeType().equals(DataType.IS_Chinese_Month.name())) {
				ret.append(ChinaUtil.numToUpper(FormatDate.getCurrentMonth("MM")));
			} else if (node.getMonthCodeType().equals(DataType.IS_Lower_Month_FillZero.name())) {
				ret.append(FormatDate.getCurrentMonthFillZero("MM"));
			} else if (node.getMonthCodeType().equals(DataType.IS_Lower_Month_NotFillZero.name())) {
				ret.append(FormatDate.getCurrentMonth("MM"));
			}
		}
		// 是否显示日
		if (node.isUseDay()) {
			if (node.getDayCodeType().equals(DataType.IS_Chinese_Day.name())) {
				ret.append(ChinaUtil.numToUpper(FormatDate.getCurrentday("dd")));
			} else if (node.getDayCodeType().equals(DataType.IS_Lower_Day_FillZero.name())) {
				ret.append(FormatDate.getCurrentdayFillZero("dd"));
			} else if (node.getDayCodeType().equals(DataType.IS_Lower_Day_NotFillZero.name())) {
				ret.append(FormatDate.getCurrentMonth("dd"));
			}
		}
		return ret.toString();
	};

	public Map<String, Object> queryUnusedCodeValues(CodeDef codeDef, String userTable, Map<String, String> nodeValues) {
		Map<String, Object> args = calcGroupValue(codeDef, userTable, nodeValues);
		String groupValue = (String) args.get("groupValue");
		SequenceNode sequNode = (SequenceNode) args.get("sequenceNode");
		String relTableName = sequNode.isRelTableName() ? userTable : "NULL";
		Map<String, Object> ret = new HashMap<String, Object>();
		ret.put("groupValue", groupValue);
		ret.put("list", sequenceCode.queryUnusedCodeValues(codeDef.getGuid(), groupValue, relTableName));
		return ret;
	}

	public void lockUnusedCodeValue(CodeDef codeDef, String groupValue, String sequenceCodeValue, String userTable, String userField,
			String userKeyValues) {
		SequenceNode sequNode = codeDef.getSequenceNode();
		String relTableName = sequNode.isRelTableName() ? userTable : "NULL";
		sequenceCode.releaseCodeValue(userTable, userField, userKeyValues);
		sequenceCode.lockUnusedCodeValue(codeDef.getGuid(), groupValue, relTableName, sequenceCodeValue, userTable, userField, userKeyValues);
	}
}
