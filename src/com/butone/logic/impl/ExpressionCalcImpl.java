package com.butone.logic.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.butone.logic.ExpressionCalc;
import com.butone.logic.LogicProcessContext;
import com.butone.logic.config.ParameterConfig;
import com.butone.logic.impl.ExpressionValidator.Word;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.data.Expression;

public class ExpressionCalcImpl implements ExpressionCalc {

	/**
	 * 已经计算过的变量 <br>
	 * 比如 v1 = v2 + v3 + v4,v2 = v3 /5,v3=objectA.abc+100，v4=v2-20 保证v2,v3只计算1次
	 */
	private HashSet<String> parsedVaraint = new HashSet<String>();
	/**
	 * 计算中的变量，用于打断循环引用 <br>
	 * 比如v1 = (v2 + v4)/v3, v2= v3 - 100, v3 = v1>10?5:10，v4= v2 - 100
	 * 无论由谁开始都会存在循环引用 <br>
	 * 因此其中一个参数必须为无依赖参数，可由设计器来验证 <br>
	 * 对于自引用参数v1 = v1 + "ABC",首次计算(exprVariants中不包含)使用defaultExpr
	 */
	private HashSet<String> parsingVaraint = new HashSet<String>();

	/**
	 * 表达式使用的变量映射表 <br>
	 * 1.解析表达式中使用的对象属性值放入exprVariables中(key=objectId.attribute) <br>
	 * 2.解析表达式中参数对象的expr依赖对象(迭代)，计算后放入exprVariables中(key=objectId)) <br>
	 * 3.exprVariables中进行表达式计算时，为其引用的虽有对象属性值和参数对象值
	 */
	private Map<String, Object> exprVariables;

	private Model fnModel;
	/**
	 * 表达式解析器
	 */
	//private ExpressionParser ee = new ExpressionParser();

	private LogicProcessContext context;

	public ExpressionCalcImpl(LogicProcessContext context, Model fnModel) {
		this.context = context;
		this.exprVariables = new HashMap<String, Object>();
		this.fnModel = fnModel;
	}

	public void parse(String expr) {
		try {
			prepareExprVariablesFromExpr(expr);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Object evaluate(String expr) {
		///system/logic/fn
		if (expr == null) {
			return null;
		}
		Expression expression = Expression.createEvaluator(fnModel != null ? fnModel : ModelUtils.getModel("/base/core/logic/fn"));
		return expression.evaluate(expr, exprVariables);
	}

	public void setParameterValue(String name, Object value) {
		exprVariables.put(name, value);
	}

	public Object getParameterValue(String name) {
		return exprVariables.get(name);
	}

	@Override
	public Map<String, Object> getParameterValues() {
		return exprVariables;
	}

	/**
	 * 准备表达式中所依赖的所有变量
	 * @param expr
	 * @throws Exception
	 */
	private void prepareExprVariablesFromExpr(String expr) throws Exception {
		List<Word> list = new ExpressionValidator(expr).parse();
		parsedVaraint.clear();
		parsingVaraint.clear();
		try {
			for (Word token : list) {
				if (!ExpressionValidator.VAR.equals(token.getType()))
					continue;
				String varName = token.getValue().substring(1);

				// 如果是参数属性值
				ParameterConfig parameter = context.getParameterConfig(varName);
				if (parameter == null) {
					throw new RuntimeException("变量未申明：" + varName);
				} else {
					// 如果不包含此参数，或者要求动态计算
					if (!exprVariables.containsKey(parameter.getObjectId()) || parameter.getDynamicCalc().booleanValue())
						innerPrepareExprVariablesFromParameterConfig(parameter);
				}
			}
		} finally {
			parsedVaraint.clear();
			parsingVaraint.clear();
		}
	}

	/**
	 * 准备参数对象变量(迭代计算引用参数或者控制对象属性值)，内部方法，由prepareExprVariablesFromExpr调用 <br>
	 * 1. 如果是静态参数只初始化一次，然后由赋值逻辑进行赋值，exprVariables包含则不处理 <br>
	 * 2. 如果正在计算，表示循环引用 <br>
	 * 3. 分析参数配置依赖的对象 <br>
	 * &nbsp&nbsp a)如果是自身依赖，且exprVariables不包含，则计算defaultExpr到exprVariables <br>
	 * &nbsp&nbsp b)如果是对象属性，添加属性值到exprVariables，并记录parsed <br>
	 * &nbsp&nbsp c)如果是其他参数，迭代本方法 <br>
	 * 4. 计算参数值，添加到exprVariables，并记录parsed
	 * 
	 * )* @param expr
	 * 
	 * @return
	 * @throws FormatException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 */
	private void innerPrepareExprVariablesFromParameterConfig(ParameterConfig config) throws IllegalAccessException, InvocationTargetException,
			NoSuchMethodException {
		String currVarName = config.getObjectId();

		// 如果已经解析，不再处理
		if (parsedVaraint.contains(currVarName))
			return;

		//		// 如果是静态参数，且已经存在，不再计算
		//		if (!config.isDynamicCalc() && v != null) {
		//			parsedVaraint.add(currVarName);
		//			return v;
		//		}

		// 如果正在计算中
		if (parsingVaraint.contains(currVarName)) {
			throw new RuntimeException("变量循环引用:" + config.toString());
		}

		parsingVaraint.add(currVarName);
		// 分析表达式依赖的对象
		if (config.getCalcExpr() != null) {
			List<Word> list = new ExpressionValidator(config.getCalcExpr()).parse();
			for (Word token : list) {
				if (!ExpressionValidator.VAR.equals(token.getType()))
					continue;
				String dependVarName = token.getValue().substring(1);
				// 如果dependVarName已经解析过，不再解析
				if (parsedVaraint.contains(dependVarName)) {
					continue;
				}
				if (currVarName.equals(dependVarName)) {
					// 如果是当前参数(参数的表达式依赖其自身)
					if (!exprVariables.containsKey(dependVarName)) {
						// 如果运行时变量列表不包含此参数，先添加默认表达式值
						Object defaultValue = evaluate(config.getCalcExpr());
						exprVariables.put(dependVarName, defaultValue);
					}
				} else {
					// 如果是其他参数，准备依赖参数变量
					innerPrepareExprVariablesFromParameterConfig(context.getParameterConfig(dependVarName));
				}
			}
		}

		// 静态参数不会多次计算，这里必然是动态参数，或者静态参数初始化
		Object paramValue = evaluate(config.getCalcExpr());
		exprVariables.put(currVarName, paramValue);
		parsedVaraint.add(config.getObjectId());
		parsingVaraint.remove(currVarName);
		return;
	}

}
