package com.butone.logic.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionValidator {
	private String exp;

	public static final String FUN_START = "[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(";
	public static final String LP = "\\(";
	public static final String RP = "\\)";
	public static final String COMMA = ",";
	public static final String OPT_NUM = "\\+|\\-|\\*|\\/";
	public static final String OPT_COMP = ">=|<=|<>|>|<|=";
	public static final String OPT_BOOL = "\\b(and|or|not)\\b";
	public static final String VAR = "\\:[a-zA-Z_][a-zA-Z0-9_]*";
	public static final String CST_STRING = "'[^']*'";
	public static final String CST_NULL = "null";
	public static final String CST_BOOLEAN = "\\b(true|false)\\b";
	public static final String CST_NUM = "(-?\\d+)(\\.\\d+)?";
	public static final String SPACE = "\\s";
	public static final String OPT_ADD = "\\+";
	public static final String OPT_SUB = "\\-";
	public static final String OPT_MUL = "\\*";
	public static final String OPT_DIV = "\\/";
	public static final String OPT_NOT = "not";
	public static final String OPT_AND = "and";
	public static final String OPT_OR = "or";
	public static final String OPT_EQU = "=";
	public static final String OPT = "\\+|\\-|\\*|\\/|>=|<=|<>|>|<|=|\\b(and|or|not)\\b";
	public static final String CST = "'[^']*'|\\b(true|false)\\b|(-?\\d+)(\\.\\d+)?";
	public static final String VALUE = "\\:[a-zA-Z_][a-zA-Z0-9_]*|'[^']*'|\\b(true|false)\\b|(-?\\d+)(\\.\\d+)?";

	public ExpressionValidator(String expr) {
		this.exp = expr;
		expr = expr.replace("''''", "''");
		expr = expr.replace("'''", "'");
		expr = expr.replace("''", "0");
	}

	public List<Word> parse() {
		ArrayList<Word> wordList = new ArrayList<Word>();
		StringBuffer localStringBuffer = new StringBuffer();
		localStringBuffer.append("(").append(CST_STRING).append(")");
		localStringBuffer.append("|(").append(VAR).append(")");
		localStringBuffer.append("|(").append(OPT_BOOL).append(")");
		localStringBuffer.append("|(").append(CST_BOOLEAN).append(")");
		localStringBuffer.append("|(").append(FUN_START).append(")");
		localStringBuffer.append("|(").append(LP).append(")");
		localStringBuffer.append("|(").append(RP).append(")");
		localStringBuffer.append("|(").append(COMMA).append(")");
		localStringBuffer.append("|(").append(OPT_NUM).append(")");
		localStringBuffer.append("|(").append(OPT_COMP).append(")");
		localStringBuffer.append("|(").append(CST_NULL).append(")");
		localStringBuffer.append("|(").append(CST_NUM).append(")");
		localStringBuffer.append("|(").append(SPACE).append(")");
		localStringBuffer.append("|(").append("\\w+").append(")");
		String str1 = localStringBuffer.toString();
		Pattern localPattern = Pattern.compile(str1);
		Matcher localMatcher = localPattern.matcher(this.exp);
		String expr = "";
		int i = 0;

		while (localMatcher.find()) {
			String group = localMatcher.group();
			String type = getType(group);
			if (type == null) {
				throwError("表达式第" + localMatcher.start() + "个列\"" + group + "\"是非法字符");
			}
			expr = expr + group;
			if (SPACE.equals(type))
				continue;
			if ((LP.equals(type)) || (FUN_START.equals(type)))
				i++;
			else if (RP.equals(type))
				i--;
			wordList.add(new Word(group, type, localMatcher.start(), localMatcher.end()));
		}
		if (i != 0) {
			throwError("括号不匹配");
		}
		return wordList;
	}

	public List<Function> parseFn(List<Word> wordList) {
		ArrayList<Function> fnList = new ArrayList<Function>();
		Function function = null;
		String fnExpr = "";
		for (int j = 0; j < wordList.size(); j++) {
			Word word = wordList.get(j);
			String wordText = word.getValue();
			String wordType = word.getType();
			if (wordType.equals(FUN_START)) {
				if (function == null) {
					function = new Function(wordText, null);
					fnList.add(function);
				} else {
					Parameter param = new Parameter();
					function.addParam(param);
					function = new Function(wordText, function);
					param.setFunction(function);
					fnExpr = "";
				}
				function.brCount += 1;
			} else if (function != null) {
				if (wordType.equals(LP)) {
					function.brCount += 1;
					fnExpr = fnExpr + wordText;
				} else if (wordType.equals(RP)) {
					function.brCount -= 1;
					if (function.brCount <= 0) {
						if (!fnExpr.equals("")) {
							function.brCount = 0;
							Parameter param = new Parameter();
							param.setValue(fnExpr);
							function.addParam(param);
							fnExpr = "";
						}
						function = function.getParent();
					} else {
						fnExpr = fnExpr + wordText;
					}
				} else if (wordType.equals(COMMA)) {
					if (!fnExpr.equals("")) {
						Parameter param = new Parameter();
						param.setValue(fnExpr);
						function.addParam(param);
						fnExpr = "";
					}
				} else {
					fnExpr = fnExpr + wordText;
				}
			}
		}
		return fnList;
	}

	private String getType(String paramString) {
		if (paramString.matches("^'[^']*'$"))
			return CST_STRING;
		if (paramString.matches("^\\:[a-zA-Z_][a-zA-Z0-9_]*$"))
			return VAR;
		if (paramString.matches("^[a-zA-Z_][a-zA-Z0-9_]*\\s*\\($"))
			return FUN_START;
		if (paramString.matches("^\\($"))
			return LP;
		if (paramString.matches("^\\)$"))
			return RP;
		if (paramString.matches("^,$"))
			return COMMA;
		if (paramString.matches("^\\+$"))
			return OPT_ADD;
		if (paramString.matches("^\\*$"))
			return OPT_MUL;
		if (paramString.matches("^\\/$"))
			return OPT_DIV;
		if (paramString.matches("^\\-$"))
			return OPT_SUB;
		if (paramString.matches("^=$"))
			return OPT_EQU;
		if (paramString.matches("^>=|<=|<>|>|<|=$"))
			return OPT_COMP;
		if (paramString.matches("^not$"))
			return OPT_NOT;
		if (paramString.matches("^and$"))
			return OPT_AND;
		if (paramString.matches("^or$"))
			return OPT_OR;
		if (paramString.matches("^null$"))
			return CST_NULL;
		if (paramString.matches("^\\b(true|false)\\b$"))
			return CST_BOOLEAN;
		if (paramString.matches("^(-?\\d+)(\\.\\d+)?$"))
			return CST_NUM;
		if (paramString.matches("^\\s$"))
			return SPACE;
		return null;
	}

	private void throwError(String error) {
		throw new RuntimeException(error);
	}

	public static class Word {
		private String value;
		private String type;
		private int start;
		private int end;

		public Word(String value, String type, int start, int end) {
			this.value = value;
			this.type = type;
			this.start = start;
			this.end = end;
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String paramString) {
			this.value = paramString;
		}

		public String getType() {
			return this.type;
		}

		public void setType(String paramString) {
			this.type = paramString;
		}

		public int getStart() {
			return this.start;
		}

		public void setStart(int paramInt) {
			this.start = paramInt;
		}

		public int getEnd() {
			return this.end;
		}

		public void setEnd(int paramInt) {
			this.end = paramInt;
		}

		@Override
		public String toString() {
			return this.value;
		}
	}

	public static class Parameter {
		private String type;
		private ExpressionValidator.Function function;
		private String value;

		Parameter() {
		}

		public String getValue() {
			return this.value;
		}

		public void setValue(String paramString) {
			this.value = paramString;
			if (paramString.matches(CST_STRING))
				this.type = "String";
			else if (paramString.matches("-?\\d+"))
				this.type = "Integer";
			else if (paramString.matches(CST_NUM))
				this.type = "Decimal";
			else if (paramString.matches(CST_BOOLEAN))
				this.type = "Boolean";
			else
				this.type = "Object";
		}

		public String getType() {
			return this.type;
		}

		public void setType(String paramString) {
			this.type = paramString;
		}

		public ExpressionValidator.Function getFunction() {
			return this.function;
		}

		public void setFunction(ExpressionValidator.Function paramFunction) {
			this.function = paramFunction;
		}

		@Override
		public String toString() {
			return this.type + " " + value;
		}
	}

	public static class Function {
		private String name;
		private List<ExpressionValidator.Parameter> parameters = new ArrayList<ExpressionValidator.Parameter>();
		private String returnType;
		private Function parent;
		public int brCount = 0;

		public Function getParent() {
			return this.parent;
		}

		public void setParent(Function paramFunction) {
			this.parent = paramFunction;
		}

		public Function(String paramFunction, Function parent) {
			this.name = paramFunction.replace("(", "").trim();
			this.parent = parent;
		}

		public void addParam(ExpressionValidator.Parameter paramParameter) {
			this.parameters.add(paramParameter);
		}

		public String getName() {
			return this.name;
		}

		public List<ExpressionValidator.Parameter> getParameters() {
			return this.parameters;
		}

		public String getReturnType() {
			return this.returnType;
		}

		public void setReturnType(String paramString) {
			this.returnType = paramString;
		}

		@Override
		public String toString() {
			return super.toString();
		}
	}

}
