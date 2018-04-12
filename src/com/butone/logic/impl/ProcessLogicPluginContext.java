package com.butone.logic.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.lang.builder.EqualsBuilder;

import com.butone.data.BizDataUtils;
import com.butone.data.SQLUtils;
import com.butone.extend.BizInfo;
import com.butone.extend.BizLogicPluginEx;
import com.butone.extend.CacheManager;
import com.butone.extend.ModelPathHelper;
import com.butone.extend.TableInfo;
import com.butone.flowbiz.FlowBizConsts;
import com.butone.logic.ExpressionCalc;
import com.butone.logic.LogicProcessContext;
import com.butone.logic.config.CalcLogicConfig;
import com.butone.logic.config.ListControlObjectDesc;
import com.butone.model.FieldDef;
import com.butone.model.TableDef;
import com.butone.utils.ModelExtUtils;
import com.butone.x5Impl.Engine;
import com.butone.x5Impl.RequestContextUtils;
import com.justep.exception.BusinessException;
import com.justep.model.Action;
import com.justep.model.Activity;
import com.justep.model.BusinessActivity;
import com.justep.model.Concept;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.model.Process;
import com.justep.model.Relation;
import com.justep.system.action.ActionUtils;
import com.justep.system.context.ActionContext;
import com.justep.system.context.ContextHelper;
import com.justep.system.context.RequestContext;
import com.justep.system.data.KSQL;
import com.justep.system.data.ModifyState;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.TableUtils;
import com.justep.system.data.Transaction;
import com.justep.system.util.BizSystemException;
import com.justep.util.Utils;

/**
 * 逻辑插件上下文，包含了业务环节加载业务表的数据。一次请求中RequestContext中，同一份数据(activity、bizRecId公用一个插件上下文
 * )。<br>
 * 逻辑插件上下文可以执行数据处理逻辑LogicProcessContext，LogicProcessContext中可以触发其他环节的逻辑插件，
 * 从而形成一个逻辑插件上下文堆栈,堆栈记录在RequestContext中<br>
 * 相同或者不同的逻辑插件上下文中的数据处理逻辑同样形成一个调用堆栈，此堆栈由逻辑插件上下文自动维护<br>
 * ProcessLogicPluginContext.create
 * 
 * @author Administrator
 * 
 */
public class ProcessLogicPluginContext {
	private static final String REQUESTCONTEXT_PARAMETERNAME = ProcessLogicPluginContext.class.getName();
	private static ThreadLocal<Stack<LogicProcessContext>> logicProcessContextStack = new ThreadLocal<Stack<LogicProcessContext>>();
	private Process process;
	private Activity activity;
	private String controlTable;// 主控概念
	private String bizRecId;
	private String executor;
	private String executeContext;
	private BizInfo contextBizInfo;
	// private ActionContext actionContext;

	private Map<String, String> bizDataFilters = new HashMap<String, String>();
	private HashMap<String, TableControlObject> bizTables = new HashMap<String, TableControlObject>();
	private HashMap<String, TableControlObject> tempTables = new HashMap<String, TableControlObject>();
	/**
	 * 上下文参数
	 */
	private HashMap<String, Object> parameters = new HashMap<String, Object>();

	public Process getProcess() {
		return process;
	}

	public String getBizRecId() {
		return bizRecId;
	}

	public Activity getActivity() {
		return activity;
	}

	public boolean isBusinessActivity() {
		return activity instanceof BusinessActivity;
	}

	public boolean haveControlTable() {
		return Utils.isNotEmptyString(controlTable);
	}

	public BizInfo getBizInfo() {
		if (contextBizInfo == null)
			contextBizInfo = CacheManager.getBizInfo(process);
		return contextBizInfo;
	}

	public void addBizDataFilter(String tableName, String filter) {
		bizDataFilters.put(tableName, filter);
	}

	public void addBizDataFilters(Map<String, String> filters) {
		bizDataFilters.putAll(filters);
	}

	public void setParameter(String name, Object value) {
		parameters.put(name, value);
	}

	public void addParameters(Map<String, Object> params) {
		parameters.putAll(params);
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}

	public static Object getContextParameter(String name, boolean parent) {
		RequestContext request = ActionUtils.getRequestContext();
		Stack<?> stack = (Stack<?>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		if (stack == null)
			throw new BusinessException("不存在插件执行环境");
		if (!parent) {
			ProcessLogicPluginContext context = (ProcessLogicPluginContext) stack.peek();
			return context.parameters.get(name);
		} else {
			for (int i = stack.size() - 1; i >= 0; i--) {
				ProcessLogicPluginContext context = (ProcessLogicPluginContext) stack.get(i);
				if (context.parameters.containsKey(name))
					return context.parameters.get(name);
			}
		}
		return null;
	}

	public static void setContextParameter(String name, Object value) {
		RequestContext request = ActionUtils.getRequestContext();
		Stack<?> stack = (Stack<?>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		if (stack == null)
			throw new BusinessException("不存在插件执行环境");
		ProcessLogicPluginContext context = (ProcessLogicPluginContext) stack.peek();
		context.parameters.put(name, value);
	}

	/**
	 * 清除数据
	 */
	private void clear() {
		bizTables.clear();
		tempTables.clear();
	}

	private void innerSaveTables(List<String> tableNames, boolean ignoreException, boolean emptyData) throws Exception {
		if (tableNames == null || tableNames.size() == 0)
			return;
		// 扫描当前环境中的TableControlObject
		Map<String, TableControlObject> allTables = new HashMap<String, TableControlObject>();
		for (String tableName : tableNames) {
			TableControlObject c = ProcessLogicPluginContext.findTableControlObject(tableName);
			if (c == null) {
				throw new BusinessException("逻辑计算上下文中不存在id为" + tableName + "的对象");
			}
			allTables.put(tableName, c);
		}
		// 判断事务是否已启动
		Transaction t = new Transaction();
		t.begin();
		Transaction old = ContextHelper.getTransaction();
		RequestContextUtils.setTransaction(t);
		try {
			// 执行保存
			BizInfo bizInfo = getBizInfo();
			for (String tableName : tableNames) {
				saveTableCascade(tableName, bizInfo, allTables);
			}
			// 提交事务
			t.commit();
		} catch (Exception e) {
			// 重置状态，防止ignoreException情况下的再次提交
			for (TableControlObject c : allTables.values()) {
				if (emptyData) {
					c.emptyAllData();
				} else {
					c.resetStatus();
				}
			}
			// 回滚事务
			t.rollback();
			if (!ignoreException) {
				throw e;
			}
		} finally {
			RequestContextUtils.setTransaction(old);
		}

	}

	/**
	 * 保存所有列表控制对象对应的表
	 */
	private void saveAllTable() {
		BizInfo bizInfo = getBizInfo();
		Map<String, TableControlObject> allTabls = new HashMap<String, TableControlObject>();
		allTabls.putAll(this.bizTables);
		// 为了 实现业务工作表的保存按照业务定义的顺序执行，例如 A1，A2工作表在A业务下，先保存A1；在B业务下面先保存A2
		// 保存工作表
		for (TableInfo ti : bizInfo.getTableInfos()) {
			if (allTabls.containsKey(ti.getName())) {
				saveTableCascade(ti.getName(), bizInfo, allTabls);
			}
		}
		// 保存系统表
		while (!allTabls.isEmpty()) {
			String tableName = allTabls.keySet().iterator().next();
			saveTableCascade(tableName, bizInfo, allTabls);
		}
	}

	private void saveTableCascade(String tableName, BizInfo bizInfo, Map<String, TableControlObject> allTables) {
		TableControlObject tableObject = allTables.remove(tableName);
		if (tableObject == null)
			return;
		TableInfo tableInfo = bizInfo.getTableInfo(tableObject.getObjectId());
		if (tableInfo == null) {
			tableObject.save(null);
		} else {
			if (Utils.isNotEmptyString(tableInfo.getMasterTable())) {
				saveTableCascade(tableInfo.getMasterTable(), bizInfo, allTables);
			}
			String actionName = tableInfo.getSaveAction();
			if (Utils.isNotEmptyString(actionName)) {
				final Action saveAction = this.activity.getAction(actionName);
				SaveCallback callback = new SaveCallback() {
					@Override
					public void save(Table table) {
						Map<String, Object> params = new HashMap<String, Object>();
						params.put("table", table);
						Engine.invokeAction(process, activity, saveAction, executor, executeContext, params);
					}
				};
				tableObject.save(callback);
			}
		}
	}

	private void saveInnerListControlObjectCascade(LogicProcessContext processContext, ListControlObjectDesc desc, Set<String> innerIds) {
		if (desc == null || !innerIds.contains(desc.getObjectId()) || desc.isQuery())
			return;

		innerIds.remove(desc.getObjectId());
		if (Utils.isNotEmptyString(desc.getMasterObjectId())) {
			saveInnerListControlObjectCascade(processContext, processContext.findInnerListControlObject(desc.getMasterObjectId()), innerIds);
		}
		TableControlObject table = (TableControlObject) processContext.getControlObject(desc.getObjectId());
		if (Utils.isEmptyString(table.getTarget().getMetaData().getKeyColumnName())) {
			if (table.getTarget().iterator(new ModifyState[] { ModifyState.DELETE, ModifyState.EDIT, ModifyState.NEW }).hasNext()) {
				throw new BizSystemException("内部引用数据集【" + desc.getName() + "】的KSQL中不包含主键列，不支持修改");
			}
		} else {
			table.save(null);
		}
	}

	private void saveInnerListControlObjects(LogicProcessContext processContext) {
		CalcLogicConfig logicConfig = processContext.getConfig();
		Set<String> innerIds = new HashSet<String>();
		innerIds.addAll(logicConfig.getInnerListTargetObjectIds());
		for (ListControlObjectDesc desc : logicConfig.getInnerListControlObjects()) {
			saveInnerListControlObjectCascade(processContext, desc, innerIds);
		}
	}

	public Object execute(CalcLogicConfig logicConfig, Map<String, Object> parameters, List<TableControlObject> memoryTables, Map<String, Object> out) {
		LogicProcessContext processContext = new LogicProcessContext(logicConfig);
		ProcessLogicPluginContext.registerLogicProcessContext(processContext);
		try {
			processContext.setConfig(logicConfig);
			// 内部数据集构造器
			processContext.setListControlObjectConstructor(new ListControlObjectConstructorImpl());
			// 行数据对象访问
			processContext.setObjectAccess(new RowAccess());
			//
			ExpressionCalc expressionCalc = new ExpressionCalcImpl(processContext, null);
			// 表达式计算器
			processContext.setExpressionCalc(expressionCalc);

			// TODO 兼容性处理
			BizInfo bizInfo = getBizInfo();
			for (ListControlObjectDesc desc : logicConfig.getInnerListControlObjects()) {
				if (Utils.isNotEmptyString(desc.getMasterObjectId()) && bizInfo.containsTable(desc.getMasterObjectId())
						&& !bizTables.containsKey(desc.getMasterObjectId())) {
					// 如果引用表使用业务表为主表，且业务表未加载
					this.loadBizTable(desc.getMasterObjectId(), parameters);
				}
			}

			for (TableControlObject obj : this.bizTables.values()) {
				processContext.addControlObject(obj);
			}

			// 添加临时表
			for (TableControlObject controlObject : tempTables.values()) {
				processContext.addControlObject(controlObject);
			}

			// 添加指定的
			if (memoryTables != null) {
				for (TableControlObject controlObject : memoryTables) {
					controlObject.setMemoryTable(true);
					processContext.addControlObject(controlObject);
				}
			}
			processContext.execute(parameters);
			saveInnerListControlObjects(processContext);
			if (out != null)
				out.putAll(processContext.getParameterValues());
			return processContext.getOutput();
		} catch (Exception e) {
			clear();
			throw new RuntimeException(e);
		} finally {
			ProcessLogicPluginContext.unRegisterLogicProcessContext();
		}
	}

	/**
	 * 
	 * @param dataModel
	 * @param logicConfig
	 * @param parameters
	 * @param memoryTables
	 * @return
	 */
	public Object execute(CalcLogicConfig logicConfig, Map<String, Object> parameters, List<TableControlObject> memoryTables) {
		return execute(logicConfig, parameters, memoryTables, null);
	}

	public TableControlObject loadBizSysTable(String dataModel, String conceptName, String keyColumn) {
		TableControlObject tableControlObject = bizTables.get(conceptName);
		if (tableControlObject != null)
			return tableControlObject;
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("bizRecId", this.bizRecId);
		Table table = KSQL.select("select t.* from " + conceptName + " t where fBizRecId=:bizRecId", varMap, dataModel, null);
		table.getMetaData().setStoreByConcept(conceptName, true);
		table.getMetaData().setKeyColumn(keyColumn);
		tableControlObject = new TableControlObject(conceptName, table, ModelUtils.getModel(dataModel));
		tableControlObject.setObjectId(conceptName);
		bizTables.put(conceptName, tableControlObject);
		tableControlObject.first();
		return tableControlObject;
	}

	/**
	 * 保存前后调用组件前传入table
	 * 
	 * @param tableName
	 * @param concept
	 * @param dataModel
	 * @param table
	 */
	public TableControlObject addTableControlObject(String tableName, String concept, String dataModel, Table table) {
		TableControlObject tableControlObject = new TableControlObject(concept, table, ModelUtils.getModel(dataModel));
		tableControlObject.setObjectId(tableName);
		String keyColumns = BizDataUtils.getTableKeyColumns(concept, table);
		if (Utils.isNotEmptyString(keyColumns)) {
			table.getMetaData().setStoreByConcepts(new String[] { concept });
			table.getMetaData().setKeyColumn(keyColumns);
		} else {
			table.getMetaData().setStoreByConcept(concept, false);
		}
		bizTables.put(tableName, tableControlObject);
		tableControlObject.first();
		return tableControlObject;
	}

	/**
	 * 保存前后执行插件后移除添加的表，避免重复提交
	 * 
	 * @param tableControlObject
	 */
	public void removeTableControlObject(TableControlObject tableControlObject) {
		bizTables.remove(tableControlObject.getObjectId());
	}

	/**
	 * 加载流程系统表(BusinessActivity)
	 * 
	 * @return
	 */
	public List<TableControlObject> loadFlowSysTables() {
		ArrayList<TableControlObject> ret = new ArrayList<TableControlObject>();
		ret.add(loadBizSysTable(FlowBizConsts.DATA_MODEL_CORE_FLOW, FlowBizConsts.CONCEPT_BizRec, "fBizRecId"));
		return ret;
	}

	/**
	 * 加载所有业务表
	 */
	public List<TableControlObject> loadAllBizTable() {
		return loadAllBizTable(null);
	}

	/**
	 * 加载所有业务表
	 * 
	 * @param parameters
	 * @return
	 */
	public List<TableControlObject> loadAllBizTable(Map<String, Object> parameters) {
		ArrayList<TableControlObject> ret = new ArrayList<TableControlObject>();
		// TODO tangkejie 2017-04-24 按需加载，不再加载所有表。保留3个月，移除整个方法
		// if (this.isBusinessActivity())
		// ret.addAll(loadFlowSysTables());
		// BizInfo bizInfo = getBizInfo();
		// for (TableInfo tableInfo : bizInfo.getTableInfos()) {
		// ret.add(loadBizTable(tableInfo.getName(), parameters));
		// }
		return ret;
	}

	// /**
	// * 加载业务主表，用于ProcessQueryAction前加载主表。
	// *
	// * @param parameters
	// * @return
	// */
	// public List<TableControlObject> loadBizMasterTable(Map<String, Object>
	// parameters) {
	// ArrayList<TableControlObject> ret = new ArrayList<TableControlObject>();
	// if (this.isBusinessActivity())
	// ret.addAll(loadFlowSysTables());
	// BizInfo bizInfo = getBizInfo();
	// for (TableInfo tableInfo : bizInfo.getTableInfos()) {
	// if (Utils.isEmptyString(tableInfo.getMasterTable()))
	// ret.add(loadBizTable(tableInfo.getName(), parameters));
	// }
	// return ret;
	// }

	public TableControlObject loadBizTable(String tableName) {
		return loadBizTable(tableName, this.parameters);
	}

	private String getConceptKeyFields(Concept concept) {
		String ret = "";
		for (Relation r : concept.getKeyRelations()) {
			ret += ":" + r.getName();
		}
		if (ret.length() > 1) {
			return ret.substring(1);
		}
		return ret;
	}

	public TableControlObject loadBizTable(String tableName, Map<String, Object> parameters) {
		BizInfo bizInfo = getBizInfo();
		TableInfo tableInfo = bizInfo.getTableInfo(tableName);
		if (tableInfo == null)
			throw new BusinessException("业务不包含工作表" + tableName);
		TableControlObject tableControlObject = bizTables.get(tableInfo.getConcept());
		if (tableControlObject != null)
			return tableControlObject;
		Action queryAction = activity.getAction(tableInfo.getQueryAction());
		Model ontologyModel = ModelUtils.getModel(ModelPathHelper.getProcessOntology(process));
		Concept concept = ontologyModel.getUseableConcept(tableInfo.getConcept());
		if (concept == null)
			throw new BusinessException("概念" + tableInfo.getConcept() + "不存在");
		Model dataModel = ModelUtils.getModel(ModelPathHelper.getConceptDataModel(concept));
		if (Utils.isEmptyString(tableInfo.getMasterTable())) {
			// 主表
			HashMap<String, Object> paramMap = new HashMap<String, Object>();
			paramMap.put("limit", -1);
			paramMap.put("offset", 0);
			paramMap.put("columns", null);
			String filter = null;
			Map<String, Object> variables = new HashMap<String, Object>();
			if (this.isBusinessActivity()) {
				// 流程业务的主表，使用外键过滤数据 添加了预收件的支持
				if (Utils.isNotEmptyString(tableInfo.getForeignKeys())) {
					filter = SQLUtils.appendCondition(filter, "and", tableInfo.getConcept() + "." + tableInfo.getForeignKeys() + "=:_innerBizRecId");
					variables.put("_innerBizRecId", bizRecId);
				}
			} else if (this.haveControlTable()) {
				// 非流程业务,并且有主控概念
				if (tableInfo.getName().equals(this.controlTable)) {
					// 当前表是主控表，使用ID过滤
					filter = SQLUtils.appendCondition(filter, "and", tableInfo.getConcept() + "=:_innerBizRecId");
					variables.put("_innerBizRecId", bizRecId);
				} else if (Utils.isNotEmptyString(tableInfo.getForeignKeys())) {
					// 非主控表，但是有外键，使用外键过滤
					filter = SQLUtils.appendCondition(filter, "and", tableInfo.getConcept() + "." + tableInfo.getForeignKeys() + "=:_innerBizRecId");
					variables.put("_innerBizRecId", bizRecId);
				}
			}

			// 设定了表的过滤
			String dataFilter = bizDataFilters.get(tableInfo.getName());
			filter = SQLUtils.appendCondition(filter, "and", dataFilter);

			// 没有条件的一律不加载数据
			if (Utils.isEmptyString(filter)) {
				filter = "1=0";
			}
			paramMap.put("filter", filter);

			if (parameters != null) {
				if (parameters.containsKey("_innerBizRecId"))
					throw new RuntimeException("业务逻辑插件传入的参数列表中使用了系统保留参数名_innerBizRecId");
				variables.putAll(parameters);
			}
			paramMap.put("variables", variables);

			Table table = (Table) Engine.invokeAction(process, activity, queryAction, executor, executeContext, paramMap);
			tableControlObject = new TableControlObject(tableInfo.getConcept(), table, dataModel);
			tableControlObject.setObjectId(tableInfo.getName());
			String keyColumns = BizDataUtils.getTableKeyColumns(tableInfo.getConcept(), table);
			if (Utils.isNotEmptyString(keyColumns)) {
				table.getMetaData().setStoreByConcepts(new String[] { tableInfo.getConcept() });
				table.getMetaData().setKeyColumn(keyColumns);
			} else {
				table.getMetaData().setStoreByConcept(tableInfo.getConcept(), false);
			}
			bizTables.put(tableInfo.getName(), tableControlObject);
			tableControlObject.first();
			return tableControlObject;
		} else {
			// 明细表
			TableInfo masterTableInfo = bizInfo.getTableInfo(tableInfo.getMasterTable());
			Concept masterConcept = ontologyModel.getUseableConcept(masterTableInfo.getConcept());
			String masterKeyFields = getConceptKeyFields(masterConcept);
			TableControlObject masterTable = loadBizTable(masterTableInfo.getName(), parameters);
			// 让主表滚动一次
			masterTable.first();

			BizDataLoader tableLoader = new BizDataLoader(process, activity, tableInfo.getConcept(), tableInfo.getForeignKeys(), masterKeyFields,
					queryAction, parameters);
			// 业务工作表不设置级联，由saveAction自动级联处理 tableLoader.setCascade(false);
			if (bizDataFilters.containsKey(tableInfo.getConcept())) {
				String dataFilter = bizDataFilters.get(tableInfo.getConcept());
				tableLoader.setDataFilter(dataFilter);
			}
			tableControlObject = new TableControlObject(masterTable, tableInfo.getConcept(), tableLoader, dataModel);
			tableControlObject.setObjectId(tableInfo.getName());

			bizTables.put(tableInfo.getName(), tableControlObject);
			return tableControlObject;
		}
	}

	public TableControlObject addTempTableControlObject(TableDef tableDef) {
		TableControlObject controlObject = tempTables.get(tableDef.getName());
		if (controlObject != null)
			return controlObject;
		List<String> names = new ArrayList<String>();
		List<String> types = new ArrayList<String>();
		names.add(BizDataUtils.MemoryTableKeyColumnName);
		types.add(BizDataUtils.MemoryTableKeyColumnType);
		Map<String, String> defaultValues = new HashMap<String, String>();
		for (FieldDef fld : tableDef.getFields()) {
			names.add(fld.getName());
			types.add(fld.getDataType());
			if (Utils.isNotEmptyString(fld.getAutoFillDef())) {
				defaultValues.put(fld.getName(), fld.getAutoFillDef());
			}
		}
		Table table = TableUtils.createTable(null, names, types);
		table.getMetaData().setKeyColumn(BizDataUtils.MemoryTableKeyColumnName);
		table.getProperties().put(Table.PROP_NAME_ROWID, BizDataUtils.MemoryTableKeyColumnName);
		table.getMetaData().getColumnMetaData(BizDataUtils.MemoryTableKeyColumnName).setDefine("guid()");

		controlObject = new TableControlObject(tableDef.getName(), table, null);
		controlObject.setObjectId(tableDef.getName());
		controlObject.setMemoryTable(true);
		if (!defaultValues.isEmpty()) {
			for (Iterator<Entry<String, String>> i = defaultValues.entrySet().iterator(); i.hasNext();) {
				Entry<String, String> e = i.next();
				if (e.getKey().equals(BizDataUtils.MemoryTableKeyColumnName))
					table.getMetaData().getColumnMetaData(e.getKey()).setDefine(e.getValue());
			}
		}
		// TODO Column的define能否生效？？？
		// Row row = table.appendRow();
		// row.setValue(BizDataUtils.MemoryTableKeyColumnName,
		// CommonUtils.createGUID());
		tempTables.put(controlObject.getObjectId(), controlObject);
		return controlObject;
	}

	public TableControlObject finidTempTableControlObject(String objectId) {
		return tempTables.get(objectId);
	}

	public void clearTempTableControlObject() {
		tempTables.clear();
	}

	/**
	 * 新建插件运行环境
	 * 
	 * @param process
	 * @param activity
	 * @param bizRecId
	 * @return
	 */
	private static ProcessLogicPluginContext newContext(Process process, Activity activity, String bizRecId) {
		return newContext(process, activity, bizRecId, null);
	}

	private static ProcessLogicPluginContext newContext(Process process, Activity activity, String bizRecId, ActionContext actionContext) {
		ProcessLogicPluginContext context = new ProcessLogicPluginContext();

		context.bizRecId = bizRecId;
		context.process = process;
		context.activity = activity;
		if (context.isBusinessActivity()) {
			context.controlTable = FlowBizConsts.CONCEPT_BizRec;
		} else {
			context.controlTable = ModelExtUtils.getActivityControlTable(activity);
		}
		if (actionContext == null)
			actionContext = ContextHelper.getActionContext();
		context.executor = actionContext.getExecutor();
		context.executeContext = actionContext.getExecuteContext();
		context.setParameter("@bizRecId", bizRecId);

		RequestContext request = ContextHelper.getRequestContext();
		@SuppressWarnings("unchecked")
		Stack<ProcessLogicPluginContext> stack = (Stack<ProcessLogicPluginContext>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		if (stack == null) {
			stack = new Stack<ProcessLogicPluginContext>();
			request.put(REQUESTCONTEXT_PARAMETERNAME, stack);
		}
		stack.push(context);
		return context;
	}

	/**
	 * 创建插件运行环境，创建前需要findLogicPluginContext
	 * 
	 * @param bizProcess
	 * @param bizActivity
	 * @param bizRecId
	 * @return
	 */
	public static ProcessLogicPluginContext createLogicPluginContext(Process bizProcess, Activity bizActivity, String bizRecId) {
		ProcessLogicPluginContext context = findLogicPluginContext(bizActivity, bizRecId);
		if (context != null) {
			String language = ContextHelper.getOperator().getLanguage();
			throw new BizSystemException(bizProcess.getLabel(language) + "." + bizActivity.getLabel(language) + "[bizRecId:" + bizRecId
					+ "]的插件运行上下文已创建");
		}
		return newContext(bizProcess, bizActivity, bizRecId);
	}

	public static ProcessLogicPluginContext createLogicPluginContext(Process bizProcess, Activity bizActivity, String bizRecId,
			ActionContext actionContext) {
		ProcessLogicPluginContext context = findLogicPluginContext(bizActivity, bizRecId);
		if (context != null) {
			String language = ContextHelper.getOperator().getLanguage();
			throw new BizSystemException(bizProcess.getLabel(language) + "." + bizActivity.getLabel(language) + "[bizRecId:" + bizRecId
					+ "]的插件运行上下文已创建");
		}
		return newContext(bizProcess, bizActivity, bizRecId, actionContext);
	}

	/**
	 * 查找当前ActionContext链中是否存在指定环节，指定数据集的插件运行环境
	 * 
	 * @param task
	 * @return
	 */
	public static ProcessLogicPluginContext findLogicPluginContext(Activity bizActivity, String bizRecId) {
		RequestContext request = ActionUtils.getRequestContext();
		Stack<?> stack = (Stack<?>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		if (stack == null)
			return null;
		for (int i = stack.size() - 1; i >= 0; i--) {
			ProcessLogicPluginContext context = (ProcessLogicPluginContext) stack.get(i);
			if (context.activity == bizActivity) {
				EqualsBuilder builder = new EqualsBuilder();
				builder.append(context.getBizRecId(), bizRecId);
				if (builder.isEquals())
					return context;
			}
		}
		return null;
	}

	/**
	 * 注销任务代码逻辑插件运行环境，并保存所有表
	 * 
	 * @param actionContext
	 * @param task
	 */
	public static void removeLogicPluginContext(ProcessLogicPluginContext context, boolean save) {
		RequestContext request = ContextHelper.getRequestContext();
		@SuppressWarnings("unchecked")
		Stack<ProcessLogicPluginContext> stack = (Stack<ProcessLogicPluginContext>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		// Utils.check(stack != null && stack.peek() == context,
		// "当前插件运行上下文与注销对象不一致");
		Utils.check(stack.contains(context), "当前请求线程中不包含注销逻辑插件执行环境");
		// 如果是ProcessActionAfter可能执行不到，导致ProcessActionBefore创建的上下文没有注销，出栈直到当前context
		while (stack.size() > 0) {
			ProcessLogicPluginContext ctx = stack.pop();
			if (ctx == context)
				break;
		}
		if (save)
			try {
				context.saveAllTable();
				context.clear();
			} catch (Exception e) {
				throw new RuntimeException("保存数据失败", e);
			}

	}

	// /**
	// * 注销任务代码逻辑插件运行环境，并保存所有表
	// * @param actionContext
	// * @param task
	// */
	// public static void removeLogicPluginContext(ActionContext actionContext,
	// boolean save) {
	// RequestContext request = ContextHelper.getRequestContext();
	// @SuppressWarnings("unchecked")
	// Stack<ProcessLogicPluginContext> stack =
	// (Stack<ProcessLogicPluginContext>)
	// request.get(REQUESTCONTEXT_PARAMETERNAME);
	// if (stack != null) {
	// while (!stack.isEmpty()) {
	// ProcessLogicPluginContext context = stack.pop();
	// if (save) {
	// try {
	// context.saveAllTable();
	// context.clear();
	// } catch (Exception e) {
	// throw new RuntimeException("保存数据失败", e);
	// }
	// }
	// }
	// }
	// }

	/**
	 * 是否入口的逻辑计算
	 * 
	 * @param context
	 * @return
	 */
	public static boolean isEntryLogicProcessContext(LogicProcessContext context) {
		Stack<LogicProcessContext> stack = logicProcessContextStack.get();
		if (stack == null) {
			throw new RuntimeException("不存在逻辑计算上下文环境");
		}
		return stack.size() > 0 && stack.get(stack.size() - 1).equals(context);
	}

	/**
	 * 获得当前逻辑计算上下文环境
	 * 
	 * @return
	 */
	public static LogicProcessContext getCurrentLogicProcessContext() {
		Stack<LogicProcessContext> stack = logicProcessContextStack.get();
		if (stack == null) {
			throw new RuntimeException("不存在逻辑计算上下文环境");
		}
		LogicProcessContext ret = stack.peek();
		if (ret == null) {
			throw new RuntimeException("不存在逻辑计算上下文环境");
		}
		return ret;
	}

	/**
	 * 注册逻辑计算上下文环境
	 * 
	 * @param context
	 */
	private static void registerLogicProcessContext(LogicProcessContext context) {
		Stack<LogicProcessContext> stack = logicProcessContextStack.get();
		if (stack == null) {
			stack = new Stack<LogicProcessContext>();
			logicProcessContextStack.set(stack);
		}
		stack.push(context);
	}

	/**
	 * 注销逻辑计算上下文环境
	 * 
	 * @param context
	 */
	private static void unRegisterLogicProcessContext() {
		Stack<LogicProcessContext> stack = logicProcessContextStack.get();
		if (stack != null) {
			LogicProcessContext logicContext = stack.pop();
			logicContext.destory();
			if (stack.size() == 0)
				logicProcessContextStack.set(null);
		}
	}

	public static ProcessLogicPluginContext getCurrentPluginContext() {
		RequestContext request = ContextHelper.getRequestContext();
		@SuppressWarnings("unchecked")
		Stack<ProcessLogicPluginContext> stack = (Stack<ProcessLogicPluginContext>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		return stack.peek();
	}

	public static void saveTables(List<String> tableNames, boolean ignoreException, boolean emptyData) throws Exception {
		ProcessLogicPluginContext.getCurrentPluginContext().innerSaveTables(tableNames, ignoreException, emptyData);
	}

	/**
	 * 查找指定的表控制对象。如果计算逻辑处理中，使用逻辑处理器的控制对象。否在取当前插件上下文中的控制对象。
	 * 
	 * @param objectId
	 * @return
	 */
	public static TableControlObject findTableControlObject(String objectId) {
		TableControlObject ret = null;
		// 优先取插件环境
		RequestContext request = ContextHelper.getRequestContext();
		@SuppressWarnings("unchecked")
		Stack<ProcessLogicPluginContext> pluginStack = (Stack<ProcessLogicPluginContext>) request.get(REQUESTCONTEXT_PARAMETERNAME);
		if (pluginStack != null) {
			ProcessLogicPluginContext pluginContext = pluginStack.peek();
			ret = pluginContext.bizTables.get(objectId);
			if (ret == null)
				ret = pluginContext.tempTables.get(objectId);
			if (ret == null) {
				try {
					if (pluginContext.getBizInfo().containsTable(objectId))
						ret = pluginContext.loadBizTable(objectId);
				} catch (Exception e) {
				}
			}
		}
		if (ret == null) {
			// 取计算逻辑内部
			Stack<LogicProcessContext> processContextStack = logicProcessContextStack.get();
			if (processContextStack != null) {
				for (int i = processContextStack.size() - 1; i >= 0; i--) {
					LogicProcessContext logicProcessContext = processContextStack.peek();
					ret = (TableControlObject) logicProcessContext.getControlObject(objectId);
					if (ret != null)
						return ret;
				}
			}
		}
		return ret;
	}

	private static TableControlObject getTableControlObject(String objectId) {
		TableControlObject ret = findTableControlObject(objectId);
		if (ret == null)
			throw new RuntimeException("逻辑计算上下文中不存在id为" + objectId + "的对象");
		return ret;
	}

	public static Object executeBizLogicPlugin(String url, String targetProcess, String targetActivity, String bizRecId,
			Map<String, Object> variants, Map<String, String> filters) {
		ActionContext actionContext = ContextHelper.getActionContext();
		Process bizProcess = null;
		Activity bizActivity = null;
		if (Utils.isEmptyString(targetProcess) && Utils.isEmptyString(targetActivity)) {
			bizProcess = actionContext.getProcess();
			bizActivity = actionContext.getActivity();
		} else {
			bizProcess = ModelUtils.getProcess(targetProcess);
			bizActivity = bizProcess.getActivity(targetActivity);
		}
		Utils.check(bizProcess != null && bizActivity != null, "参数错误，无法获取目标业务及环节");
		ProcessLogicPluginContext context = ProcessLogicPluginContext.findLogicPluginContext(bizActivity, bizRecId);
		boolean releseContext = context == null;
		if (releseContext)
			context = ProcessLogicPluginContext.createLogicPluginContext(bizProcess, bizActivity, bizRecId);
		try {
			if (filters != null) {
				context.addBizDataFilters(filters);
			}
			BizInfo bizInfo = context.getBizInfo();
			BizLogicPluginEx pluginEx = bizInfo.getBizLogicPlugin(url);
			if (pluginEx == null) // pluginEx 不存在表示无后台逻辑
				return null;
			String relBizDatas = pluginEx.getBizLogicPlugin().getRelBizDatas();
			if (relBizDatas == null) {
				// TODO 兼容老的资源
				context.loadAllBizTable(variants);
			} else {
				String[] tableNames = relBizDatas.split(",");
				for (String tableName : tableNames) {
					context.loadBizTable(tableName, variants);
				}
			}

			Object ret = context.execute(pluginEx.getCalcLogicConfig(), variants, null);
			return ret;
		} finally {
			if (releseContext)
				ProcessLogicPluginContext.removeLogicPluginContext(context, true);
		}
	}

	/**
	 * 获得当前通用计算组件中的工作表当前记录值。如果列表数量为0，或者当前行为空(游标位于eof、bof)，返回null。
	 * 
	 * @param objectId
	 *            对象Id。如果是表，对应于物理表名及概念名
	 * @param propName
	 *            对象属性。如果是表，对应于物理字段名及关系名
	 * @return
	 */
	public static Object getTableControlObjectCurrentValue(String objectId, String propName) {
		TableControlObject tableObject = getTableControlObject(objectId);
		if (tableObject.getCount() > 0 && tableObject.getCurrentObject() == null)
			tableObject.first();
		if (tableObject.getCurrentObject() == null)
			return null;
		Row row = (Row) tableObject.getCurrentObject();
		return row.getValue(propName);
	}

	/**
	 * 获得当前通用计算组件中的工作表记录数。
	 * 
	 * @param objectId
	 *            对象Id。如果是表，对应于物理表名及概念名
	 * @param propName
	 *            对象属性。如果是表，对应于物理字段名及关系名
	 * @return
	 */
	public static Integer getTableControlObjectRecordCount(String objectId) {
		TableControlObject tableObject = getTableControlObject(objectId);
		return new Long(tableObject.getCount()).intValue();
	}

	/**
	 * 获得Table控制对象实例
	 * 
	 * @param objectId
	 * @return
	 */
	public static Table getTableControlObjectTarget(String objectId) {
		TableControlObject tableObject = getTableControlObject(objectId);
		return tableObject.getTarget();
	}

	public static void setTableControlObjectCursor(String objectId, int index) {
		TableControlObject tableObject = getTableControlObject(objectId);
		tableObject.setCursorIndex(index);
	}

	/**
	 * 获得Table控制对象实例
	 * 
	 * @param objectId
	 * @return
	 */
	public static Table findTableControlObjectTarget(String objectId) {
		TableControlObject tableObject = findTableControlObject(objectId);
		if (tableObject != null)
			return tableObject.getTarget();
		else
			return null;
	}

	/**
	 * 打印列表控制对象的状态
	 * 
	 * @param objectId
	 * @return
	 */
	public static Boolean printTableControlObjectState(String objectId) {
		TableControlObject tableObject = getTableControlObject(objectId);
		tableObject.rowInfoOut();
		return true;
	}

	/**
	 * 获取表控制对象的当前行
	 * 
	 * @param objectId
	 * @return
	 */
	public static Row getTableControlObjectCurrentRow(String objectId) {
		TableControlObject tableObject = getTableControlObject(objectId);
		return (Row) tableObject.getCurrentObject();
	}

	/**
	 * 刷新内部引用数据集
	 * 
	 * @param objectId
	 */
	public static void refreshLogicProcessInnerControlObject(String objectId) {
		Stack<LogicProcessContext> processContextStack = logicProcessContextStack.get();
		LogicProcessContext logic = processContextStack.peek();
		logic.refreshInnerControlObject(objectId);
	}
}
