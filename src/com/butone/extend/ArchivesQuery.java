package com.butone.extend;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.data.SQLUtils;
import com.butone.flowbiz.FlowBizConsts;
import com.butone.flowbiz.TaskExtendRelation;
import com.butone.utils.ModelExtUtils;
import com.justep.message.SystemMessages;
import com.justep.model.Config;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.context.ActionContext;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.DatabaseProduct;
import com.justep.system.data.Row;
import com.justep.system.data.SQL;
import com.justep.system.data.Table;
import com.justep.system.data.TableUtils;
import com.justep.system.opm.OrgKinds;
import com.justep.system.opm.OrgUnit;
import com.justep.system.opm.OrgUtils;
import com.justep.system.opm.PersonMember;
import com.justep.system.process.ExpressEngine;
import com.justep.system.process.Task;
import com.justep.system.process.TaskEngine;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

/**
 * 案卷查询
 * 
 * @author Administrator
 * 
 */
public class ArchivesQuery {
	private static final Logger logger = Logger.getLogger(ArchivesQuery.class);
	private static final String SQL_PARAM_PERSONID = "_sys_personid";
	public static final String ARCHIVE_PermissionFilter = "permissionFilter";
	private static List<String> SYS_TABLES = Arrays.asList(new String[] { "SYS", "B_BIZREC", "B_BJJLB", "B_AJGQJLB", "B_BIZRECATTR", "B_GROUPFUNC" });
	private static Map<String, Map<String, Object>> FIELDS_BIZREC_COLUMNS = new LinkedHashMap<String, Map<String, Object>>();
	private static final String DATAMODEL_archives = "/common/archives/data";
	private static Map<String, Map<String, Object>> bizGroupDefines = Collections.synchronizedMap(new LinkedHashMap<String, Map<String, Object>>());
	// 时间格式化
	private static Map<String, Object> JQGRUD_DATEFORMAT = new HashMap<String, Object>();
	private static Map<String, Object> JQGRUD_DATETIMEFORMAT = new HashMap<String, Object>();

	static {
		JQGRUD_DATEFORMAT.put("srcformat", "Y-m-d");
		JQGRUD_DATEFORMAT.put("newformat", "Y-m-d");

		JQGRUD_DATETIMEFORMAT.put("srcformat", "Y-m-d H:i:s");
		JQGRUD_DATETIMEFORMAT.put("newformat", "Y-m-d H:i:s");
	}

	public static Map<String, Map<String, Object>> getSysFields() {
		return Collections.unmodifiableMap(FIELDS_BIZREC_COLUMNS);
	}

	private static void appendColumnInfo(Map<String, Map<String, Object>> map, String field, String alias, String label, String dataType, boolean calc) {
		Map<String, Object> column = new HashMap<String, Object>();
		column.put("field", field);
		column.put("label", label);
		column.put("alias", alias);
		column.put("dataType", dataType);
		column.put("calc", calc);
		map.put(alias, column);
	}

	public static void initBizGroupDefines() {
		bizGroupDefines.clear();
		Map<String, String> map = new HashMap<String, String>();
		String sql_info = " select a.* From B_Businessgroup  a  Where exists(Select 1 From B_GroupFunc b Where a.fid = b.fBusinessGroupId)"
				+ "  and fGroupType='案卷查询' and fValid=1 order by fGroupOrder ";
		map.put(DatabaseProduct.DEFAULT.name(), sql_info);
		Table groupTable = SQLUtils.select(map, null, FlowBizConsts.DATA_MODEL_CORE_FLOW);
		loadBizGroupDefine(groupTable);
	}

	public static void initBizGroupDefine(String groupId) {
		Map<String, String> map = new HashMap<String, String>();
		String sql_info = " select a.* From B_Businessgroup  a  Where exists(Select 1 From B_GroupFunc b Where a.fid = b.fBusinessGroupId)"
				+ " and fGroupType='案卷查询' and fValid=1 and a.fID='" + groupId + "'";
		map.put(DatabaseProduct.DEFAULT.name(), sql_info);
		Table groupTable = SQLUtils.select(map, null, FlowBizConsts.DATA_MODEL_CORE_FLOW);
		loadBizGroupDefine(groupTable);
	}

	private static void loadBizGroupDefine(Table groupTable) {
		Iterator<Row> groupRows = groupTable.iterator();
		while (groupRows.hasNext()) {
			Row grouprow = groupRows.next();
			String fBusinessGroupId = grouprow.getString("FID");

			Set<String> tableNames = new HashSet<String>();
			// 连接表
			StringBuffer joinTables = new StringBuffer();

			// 工作表字段
			StringBuffer bizTableFields = new StringBuffer();
			//
			List<Map<String, Object>> columnMetaDatas = new ArrayList<Map<String, Object>>();
			List<Object> bindList = new ArrayList<Object>();
			bindList.add(fBusinessGroupId);
			Table fieldTable = SQLUtils.select("select b.* From B_GroupField b where b.FBUSINESSGROUPID=? order by FFIELDORDER,FGROUPINDEX",
					bindList, FlowBizConsts.DATA_MODEL_CORE_FLOW);
			Iterator<Row> itor = fieldTable.iterator();
			while (itor.hasNext()) {
				Row row = itor.next();
				String tableName = row.getString("FTABLENAME");
				// as Alias
				String fFieldAlias = row.getString("FFIELDALIAS");

				Map<String, Object> column = new HashMap<String, Object>();
				Map<String, Object> defaultDef = FIELDS_BIZREC_COLUMNS.get(fFieldAlias);
				if (defaultDef != null) {
					column.putAll(defaultDef);
				} else {
					if ("sys".equals(tableName)) {
						logger.error("案卷查询分组【" + fBusinessGroupId + "】字段(fFieldAlias)【" + fFieldAlias + "】不是系统字段");
						continue;
					}
					column.put("field", tableName + "." + row.getString("FFIELD"));
					column.put("alias", fFieldAlias);
					column.put("dataType", row.getString("FDATATYPE"));
				}

				column.put("label", row.getString("FFIELDNAME"));
				column.put("groupIndex", row.getDecimal("FGROUPINDEX"));
				column.put("width", row.getValue("FSHOWLENGTH"));
				column.put("searchType", row.getValue("FSEARCHTYPE"));

				columnMetaDatas.add(column);

				// 非系统表，并且未连接
				if (!SYS_TABLES.contains(tableName.toUpperCase()) && !tableNames.contains(tableName)) {
					tableNames.add(tableName);
					joinTables.append("left join ").append(tableName).append(" ").append(tableName).append(" on ").append(tableName)
							.append(".fBizRecId=B_BizRec.fBizRecId\n");

				}
				// 非系统系统保留字段
				if (!FIELDS_BIZREC_COLUMNS.containsKey(fFieldAlias)) {
					bizTableFields.append(",").append(column.get("field")).append(" as ").append(fFieldAlias);
				}
			}
			// 分组关联的process
			Table funcTable = SQLUtils.select("select b.* From B_GroupFunc b where b.FBUSINESSGROUPID=?", bindList,
					FlowBizConsts.DATA_MODEL_CORE_FLOW);
			String processes = "";
			Iterator<Row> funcrows = funcTable.iterator();
			while (funcrows.hasNext()) {
				Row row = funcrows.next();
				if (!row.getValue("FBUSINESSGROUPID").toString().equals(fBusinessGroupId))
					continue;
				processes += ",'" + row.getValue("FPROCESS").toString() + "'";
			}
			if (processes.length() > 0)
				processes = processes.substring(1);

			Map<String, Object> groupDefine = new HashMap<String, Object>();
			groupDefine.put("id", grouprow.getString("FID"));
			groupDefine.put("name", grouprow.getString("FGROUPNAME"));
			if (bizTableFields.length() > 0)
				groupDefine.put("bizTableFields", bizTableFields.toString().substring(1));
			else
				groupDefine.put("bizTableFields", "");
			groupDefine.put("joinTables", joinTables.toString());
			// columnMetaDatas 只包含定义列
			groupDefine.put("columnMetaDatas", columnMetaDatas);
			groupDefine.put("processes", processes);

			Table permissionTable = SQLUtils.select("select b.* From B_GroupDataPermission b where b.FBUSINESSGROUPID=?", bindList,
					FlowBizConsts.DATA_MODEL_CORE_FLOW);
			itor = permissionTable.iterator();
			JSONArray dataPermission = new JSONArray();
			while (itor.hasNext()) {
				Row row = itor.next();
				JSONObject permission = new JSONObject();
				permission.put("fValidExpr", row.getValue("FVALIDEXPR"));
				permission.put("fVisibleExpr", row.getValue("FVISIBLEEXPR"));
				permission.put("fDataPermission", row.getValue("FDATAPERMISSION"));
				dataPermission.add(permission);
			}
			groupDefine.put("dataPermission", dataPermission);
			bizGroupDefines.put(fBusinessGroupId, groupDefine);
		}
	}

	private static boolean bizRecQueryWithAuthorize() {
		boolean result = false;
		Model m = ModelUtils.getModel("/system/config");
		if (Utils.isNotNull(m)) {
			Config cfg = m.getUseableConfig("bizRecQueryWithAuthorize");
			if (Utils.isNotNull(cfg))
				result = cfg.getValue().trim().equalsIgnoreCase("true");
		}
		return result;
	}

	private static String getProcessConditionWithPermission(Map<String, Object> vars) {
		if (!bizRecQueryWithAuthorize()) {
			return null;
		}
		List<String> list = ContextHelper.getProcessList();
		List<String> items = new ArrayList<String>();
		Set<String> pp = new HashSet<String>();
		int i = 0;
		for (String proc : list) {
			Utils.check(Utils.isNotEmptyString(proc), SystemMessages.class, SystemMessages.PROCESS_LIST_NULL);
			String process = CommonUtils.getPathOfFile(proc);
			if (!pp.contains(process)) {
				String var = "_brp_p_url" + (i++);
				items.add("B_BizRec.fProcess = :" + var);
				vars.put(var, process);
			}
		}
		if (items.isEmpty()) {
			return "1=1";
		} else {
			String result = "";
			for (String item : items) {
				if (result.equals("")) {
					result = item;
				} else {
					result += " or " + item;
				}
			}
			result = "(" + result + ")";
			return result;
		}
	}

	private static String getGroupDataPermissionFilter(JSONArray permissions, Map<String, Object> variables) {
		if (permissions == null || permissions.size() == 0)
			return null;
		String ret = null;
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		for (int i = 0; i < permissions.size(); i++) {
			JSONObject p = permissions.getJSONObject(i);
			String expr = p.getString("fValidExpr");
			if ((Utils.isEmptyString(expr) || ExpressEngine.calculateBoolean(expr, variables, false, fnModel)) && !StringUtils.isEmpty(p.getString("fDataPermission"))) {
				if (ret == null ) {			 
						ret = "(" + p.getString("fDataPermission") + ")";	
				} else {
					ret += " and (" + p.getString("fDataPermission") + ")";
				}
			}
		}
		return ret;
	}
	
	private static boolean computeGroupVisible(JSONArray permissions, Map<String, Object> variables){
		if (permissions == null || permissions.size() == 0)
			return true;
		boolean ret = true;
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		for (int i = 0; i < permissions.size(); i++) {
			JSONObject p = permissions.getJSONObject(i);
			String expr = p.getString("fVisibleExpr");
			ret  = ret && (Utils.isEmptyString(expr) || ExpressEngine.calculateBoolean(expr, variables, false, fnModel));  
		}
		return ret;
	}

	/**
	 * get 查询任务 的sql
	 * 
	 * @param processUrl
	 * @param org
	 * @param taskGroupName
	 *            /status
	 * @param taskFilter
	 *            /智能过滤条件?
	 * @param bizFilter
	 *            /动态业务字段条件?
	 * @param orderBy
	 * @param offset
	 * @param limit
	 *            Map<String, Object> variables ?
	 * @return String
	 */

	public static String getQueryArchivesSql(String orderBy, Integer offset, Integer limit, Map<String, Object> variables,
			Map<String, String> filterMap) {
		return getQueryArchivesSql(orderBy, offset, limit, variables, filterMap, null, null);
	}

	private static String getQueryArchivesSql(String orderBy, Integer offset, Integer limit, Map<String, Object> variables,
			Map<String, String> filterMap, List<String> columnNames, List<String> columnTypes) {
		if (variables == null)
			variables = new HashMap<String, Object>();
		variables.put(ArchivesQuery.SQL_PARAM_PERSONID, ContextHelper.getPerson().getID());
		String groupId = filterMap.get("groupId");
		variables.put("groupId", groupId);

		Map<String, Object> groupDefine = bizGroupDefines.get(groupId);
		// 1.案卷默认条件
		String status = filterMap.get("status");
		// 压入变量，可以用于数据权限表达式计算
		variables.put("bizRecStatus", status);

		String sqlFilter = getDefaultBizFilter(status, variables);

		// 2.数据权限
		sqlFilter = SQLUtils
				.appendCondition(sqlFilter, "and", getGroupDataPermissionFilter((JSONArray) groupDefine.get("dataPermission"), variables));

		// 3.智能过滤条件
		String smartFilter = smartValueFilters(filterMap.get("smartValue"), groupDefine);
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", smartFilter);
		//
		String customFilter = filterMap.get("customFilter");
		if (customFilter != null) {
			// TODD 处理日期过滤函数 暂时先这么处理,后面优化 to_data('','')
			int startIdx = -1;
			while ((startIdx = customFilter.indexOf("stringToDate(")) >= 0) {
				String str = customFilter.substring(startIdx, customFilter.indexOf(")", startIdx) + 1);
				String newStr = str.replace("stringToDate", "to_date").replace(")", ",'yyyy-mm-dd')");
				customFilter = customFilter.replace(str, newStr);
			}
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", customFilter);
		}

		// 任务智能过滤
		// 分组的流程
		if ("全部案卷".equals(groupDefine.get("name"))) {
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", "B_BizRec.fProcess is not null");
		} else {
			String processUrl = (String) groupDefine.get("processes");
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", "B_BizRec.fProcess in (" + processUrl + ")");
		}
		if (columnNames == null)
			columnNames = new ArrayList<String>();
		if (columnTypes == null)
			columnTypes = new ArrayList<String>();
		// 系统字段
		String selectFields = "";
		for (Map<String, Object> recField : FIELDS_BIZREC_COLUMNS.values()) {
			selectFields += "," + recField.get("field") + " as " + recField.get("alias");
			columnNames.add((String) recField.get("alias"));
			columnTypes.add((String) recField.get("dataType"));
		}

		// 工作表字段
		String bizDataFields = (String) groupDefine.get("bizTableFields");
		if (Utils.isNotEmptyString(bizDataFields))
			selectFields += "," + bizDataFields;
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bizFields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		for (Map<String, Object> field : bizFields) {
			String alias = (String) field.get("alias");
			if (columnNames.contains(alias))
				continue;
			columnNames.add(alias);
			columnTypes.add((String) field.get("dataType"));
		}
		// 增加数据权限
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", filterMap.get(ARCHIVE_PermissionFilter));

		selectFields = selectFields.substring(1);
		// select
		String query = "select " + selectFields + ",B_GroupFunc.fProcessOrder from  B_BizRec \n"
				+ "left join B_BJJLB on B_BJJLB.fBizRecId=B_BizRec.fBizRecId\n"
				+ "left join B_AJGQJLB on B_AJGQJLB.fBizRecId=B_BizRec.fBizRecId and fGQZT='挂起中'\n"
				+ "left join B_BizRecAttr on B_BizRecAttr.fBizRecId=B_BizRec.fBizRecId\n"
				+ "inner join B_GroupFunc on B_GroupFunc.fBusinessGroupid='" + groupId + "' and B_GroupFunc.fProcess=B_BizRec.fProcess\n";

		String joinTabls = groupDefine.get("joinTables").toString();
		if (joinTabls.length() > 0) {
			query += joinTabls.toString();
		}
		// where
		query += " where\n" + sqlFilter;
		// orderBy
		query = "select * from (" + query + ") TMP"
				+ (orderBy != null ? "\norder by fProcessOrder," + orderBy + ",fReceiveTime  desc" : " order by fProcessOrder,fReceiveTime  desc");
		return query;
	}

	/**
	 * 查询任务
	 * 
	 * @param processUrl
	 * @param org
	 * @param taskGroupName
	 *            /status
	 * @param taskFilter
	 *            /智能过滤条件?
	 * @param bizFilter
	 *            /动态业务字段条件?
	 * @param orderBy
	 * @param offset
	 * @param limit
	 *            Map<String, Object> variables ?
	 * @return
	 */
	public static Table queryArchives(String orderBy, Integer offset, Integer limit, Map<String, Object> variables, Map<String, String> filterMap) {
		Model dataModel = ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		List<String> columnNames = new ArrayList<String>();
		List<String> columnTypes = new ArrayList<String>();
		String query = ArchivesQuery.getQueryArchivesSql(orderBy, offset, limit, variables, filterMap, columnNames, columnTypes);
		Table table = TableUtils.createTable(dataModel, columnNames, columnTypes);
		List<Object> bindings = SQLUtils.parseSqlParameters(query, variables);
		query = SQLUtils.fixSQL(query, variables, true);
		table = SQLUtils.select(query, bindings, dataModel, offset, limit, table);
		table.getProperties().put(Table.PROP_NAME_ROWID, "fBizRecId");
		return table;
	}

	private final static String SMARTSEARCH = "smart";

	private static String smartValueFilters(String smartValue, Map<String, Object> groupDefine) {
		if (Utils.isEmptyString(smartValue))
			return null;
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> columns = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		String smartFilter = "";
		for (Map<String, Object> column : columns) {
			if (!SMARTSEARCH.equals(column.get("searchType")) && Boolean.TRUE.equals(column.get("calc")))
				continue;
			String fDataType = (String) column.get("dataType");
			String fField = (String) column.get("field");
			if (fDataType.matches("String|Text"))
				if (smartValue.contains(" ")) {
					String splitFilter = null;
					String[] args = smartValue.split(" ");
					for (String s : args) {
						if (Utils.isNotEmptyString(s)) {
							splitFilter = SQLUtils.appendCondition(splitFilter, "and", "instr(" + fField + ",'" + s + "')>0");
						}
					}
					smartFilter = SQLUtils.appendCondition(smartFilter, "or", splitFilter);
				} else {
					smartFilter = SQLUtils.appendCondition(smartFilter, "or", fField + " like '%" + smartValue + "%'");
				}
			else if (fDataType.matches("Float|Integer|Decimal")) {
				smartFilter = SQLUtils.appendCondition(smartFilter, "or", String.format(sqlNumberToText(), fField) + " like '%" + smartValue + "%'");
			} else if (fDataType.matches("Time|Date|DateTime")) {
				smartFilter = SQLUtils
						.appendCondition(smartFilter, "or", String.format(sqlDateTimeToText(), fField) + " like '%" + smartValue + "%'");
			}
		}
		return smartFilter;
	}

	public static Map<String, String> getBizRecOpenParam(String fBizRecId) {
		Map<String, String> ret = new HashMap<String, String>();
		Map<String, String> sqls = new HashMap<String, String>();
		List<Object> params = new ArrayList<Object>();
		params.add(fBizRecId);

		String sqlFilter = "B_BizRec.fBizRecId=?";
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and",
				TaskUtils.getExecutorCondition("SA_Task", ContextHelper.getPerson().getPersonMembers(), false));
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and",
				"(SA_Task.sStatusID='tesReady' or SA_Task.sStatusID='tesExecuting' or SA_Task.sStatusID='tesPaused')");
		sqls.put(DatabaseProduct.DEFAULT.name(), "select sID,sTypeName," + TaskExtendRelation.FlowTask_PreemptTaskId
				+ " from SA_Task,B_BizRec where SA_Task.sFlowId=B_BizRec.fFlowId and " + sqlFilter);
		Table t = SQL.select(sqls, params, DATAMODEL_archives);
		boolean signMode = Boolean.TRUE.equals(ContextHelper.getPerson().getAttribute("signMode"));
		Row row = t.size() > 0 ? t.iterator().next() : null;
		if (row != null && (signMode && row.getString(TaskExtendRelation.FlowTask_PreemptTaskId.toUpperCase()) != null || !signMode)) {
			// 非签收 或者 签收模式且已签收
			String fid = null;
			Task current = TaskUtils.loadTask(row.getString("SID"));
			if (current.executorIsPerson()) {
				fid = current.getExecutorFID();
			} else {
				if (current.activation()) {
					// 如果是部门、岗位，且岗位下的执行者只有一个且是自己，直接抢占
					List<OrgUnit> list = (List<OrgUnit>) ExpressEngine.calculate("findOrgChildren2('" + current.getExecutorFID()
							+ "',null,null,false,true,true)", null, ModelUtils.getModel("/base/core/logic/fn"));
					if (list.size() == 1) {
						OrgUnit org = list.get(0);
						String orgKind = CommonUtils.getExtOfFile(org.getFID());
						if (OrgKinds.isPersonMember(orgKind) && OrgUtils.getPersonIDByFID(org.getFID()).equals(ContextHelper.getPerson().getID())) {
							fid = org.getFID();
							try {
								ActionContext c = ContextHelper.getActionContext();
								Field f = c.getClass().getDeclaredField("executor");
								f.setAccessible(true);
								f.set(c, org.getFID());
							} catch (Exception e) {
							}
							if (!signMode) {
								// 非签收模式
								TaskEngine engine = new TaskEngine(current.getId());
								engine.getTask().preempt();
								engine.commit();
							}
						}
					}
				}
			}

			if (fid == null) {
				fid = ContextHelper.getPerson().getID();
				List<PersonMember> items = ContextHelper.getOperator().getAuthorizedPersonMembers(ModelUtils.getProcess(current.getProcess()),
						current.getActivity(), ContextHelper.getPerson().getID());
				if (!items.isEmpty()) {
					for (PersonMember item : items) {
						if (item.getFID().startsWith(current.getExecutorFID())) {
							fid = item.getFID();
							break;
						}
					}
				}
			}

			ret.put("taskId", current.getId());
			ret.put("url", current.getEURL());
			ret.put("process", current.getProcess());
			ret.put("activity", current.getActivity());
			ret.put("executor", fid);

			boolean canDo = current.activation()
					&& (!signMode || signMode && row.getString(TaskExtendRelation.FlowTask_PreemptTaskId.toUpperCase()) != null);
			ret.put("pattern", canDo ? "do" : "detail");
			ret.put("processName", row.getString("STYPENAME"));

		} else {
			sqls.clear();
			sqls.put(
					DatabaseProduct.DEFAULT.name(),
					"select sID,sProcess,sActivity,sEURL,sTypeName from SA_Task,B_BizRec where SA_Task.sFlowId=B_BizRec.fFlowId and B_BizRec.fBizRecId=? and sKindId='tkTask' and not exists(select 1 from sa_Taskrelation r where r.sTaskId1=SA_Task.sId)");
			t = SQL.select(sqls, params, FlowBizConsts.DATA_MODEL_CORE_FLOW);
			if (t.size() > 0) {
				Row r = t.iterator().next();
				ret.put("taskId", r.getString("SID"));
				ret.put("process", r.getString("SPROCESS"));
				ret.put("processName", r.getString("STYPENAME"));

				String viewActivity = ModelExtUtils.getProcessFlowViewActivity(ModelUtils.getProcess(r.getString("SPROCESS")));
				if (Utils.isNotEmptyString(viewActivity)) {
					// 如果指定了浏览环节
					ret.put("url", CommonUtils.getPathOfFile(r.getString("SPROCESS")) + "/" + viewActivity + ".w");
					ret.put("activity", viewActivity);
				} else {
					ret.put("url", r.getString("SEURL"));
					ret.put("activity", r.getString("SACTIVITY"));

				}
				ret.put("pattern", "detail");
			}
		}
		return ret;
	}

	public static List<Map<String, Object>> queryBizGroup(Map<String, String> filterMap, Map<String, Object> variables) {
		if (variables == null) {
			variables = new HashMap<String, Object>();
		}

		String status = filterMap.get("status");
		// 压入变量，可以用于数据权限表达式计算
		variables.put("bizRecStatus", status);
		
		HashMap<String, Map<String, Object>> cntMap = new HashMap<String, Map<String, Object>>();

		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		Iterator<String> i = bizGroupDefines.keySet().iterator();
		int n = 0;
		while (i.hasNext()) {
			String groupId = i.next();
			Map<String, Object> groupDefine = bizGroupDefines.get(groupId);
			
			//计算分组是否可见,不可见不显示分组
			if(!computeGroupVisible((JSONArray) groupDefine.get("dataPermission"), variables))
				continue;
			
			Map<String, Object> bizgroup = new HashMap<String, Object>();
			ret.add(bizgroup);

			bizgroup.put("groupId", groupId);
			bizgroup.put("groupName", groupDefine.get("name"));
			Map<String, Object> setting = new HashMap<String, Object>();
			// colModel
			setting.put("colModel", getJQGridColModel(groupDefine));
			// groupingView
			setting.put("groupingView", getJQGridGroupingView(groupDefine));
			// JQGrid 的setting
			bizgroup.put("setting", setting);
			cntMap.put(groupId, bizgroup);
			String varName = "_group" + (n++);
			variables.put(varName, groupId);
			if (variables.containsKey("statistics")) {
				variables.put(ArchivesQuery.SQL_PARAM_PERSONID, ContextHelper.getPerson().getID());

				// 1.默认权限
				String sqlFilter = getDefaultBizFilter(status, variables);
				// 2.数据权限
				String groupFilter = SQLUtils.appendCondition("g.FID=:" + varName, "and",
						getGroupDataPermissionFilter((JSONArray) groupDefine.get("dataPermission"), variables));
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", groupFilter);
				// 3.任务权限
				String smartFilter = smartValueFilters(filterMap.get("smartValue"), groupDefine);
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", smartFilter);

				// 4. 自定义
				String customFilter = filterMap.get("customFilter");
				if (customFilter != null) {
					int startIdx = -1;
					while ((startIdx = customFilter.indexOf("stringToDate(")) >= 0) {
						String str = customFilter.substring(startIdx, customFilter.indexOf(")", startIdx) + 1);
						String newStr = str.replace("stringToDate", "to_date").replace(")", ",'yyyy-mm-dd')");
						customFilter = customFilter.replace(str, newStr);
					}
					sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", customFilter);
				}

				String query = "select count(B_BizRec.fBizRecId) FCNT from B_Businessgroup g"
				// B_GroupFunc
						+ "\ninner join B_GroupFunc f on f.fBusinessGroupId=g.FID "
						// B_BizRec
						+ "\ninner join B_BizRec B_BizRec on f.fProcess = B_BizRec.fProcess"
						// B_BizRecAttr
						+ "\nleft join B_BizRecAttr on B_BizRecAttr.fBizRecId=B_BizRec.fBizRecId";

				String joinTabls = groupDefine.get("joinTables").toString();
				if (joinTabls.length() > 0) {
					query += "\n" + joinTabls.toString();
				}
				// where
				query += "\nwhere (%s)";
				query = String.format(query, sqlFilter);

				List<Object> paramList = SQLUtils.parseSqlParameters(query, variables);
				Table t = SQLUtils.select(SQLUtils.fixSQL(query, variables, true), paramList, FlowBizConsts.DATA_MODEL_CORE_FLOW);
				bizgroup.put("count", t.iterator().next().getValue("FCNT").toString());
			}
		}
		return ret;
	}

	private static String getDefaultBizFilter(String status, Map<String, Object> vars) {
		// 任务类型[默认是已办结]
		String result = getProcessConditionWithPermission(vars);
		if (Utils.isEmptyString(status))
			return result;
		if (status.contains("'"))
			return SQLUtils.appendCondition(result, "and", "B_BizRec.fStatus in (" + status + ")");
		else {
			// 兼容老的
			return SQLUtils.appendCondition(result, "and", "B_BizRec.fStatus = '" + status + "'");
		}

	}

	private static Map<String, Object> getJQGridGroupingView(Map<String, Object> groupDefine) {
		Map<String, Object> groupViewer = new HashMap<String, Object>();
		List<String> groupField = new ArrayList<String>();
		List<String> groupText = new ArrayList<String>();
		List<Boolean> groupColumnShow = new ArrayList<Boolean>();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bizFields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		for (Map<String, Object> bizField : bizFields) {
			Object groupIndex = bizField.get("groupIndex");
			if (groupIndex != null) {
				groupField.add((String) bizField.get("alias"));
				groupText.add("<b>{0} : ({1})</b>");
				groupColumnShow.add(true);
			}
		}
		if (groupField.size() > 0) {
			groupViewer.put("groupField", groupField);
			groupViewer.put("groupText", groupText);
			groupViewer.put("groupColumnShow", groupColumnShow);
		}
		return groupViewer;
	}

	private static List<Map<String, Object>> getJQGridColModel(Map<String, Object> groupDefine) {
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> fields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		for (Map<String, Object> field : fields) {
			Map<String, Object> col = transformColumnMeta2JGColModel(field);
			if (col != null)
				ret.add(col);
		}
		return ret;
	}

	/**
	 * 列的元数据转jqGrid的ColModel
	 * 
	 * @param meta
	 * @return
	 */
	private static Map<String, Object> transformColumnMeta2JGColModel(Map<String, Object> meta) {
		Map<String, Object> col = new HashMap<String, Object>();
		if (meta.get("width") == null || Integer.parseInt(meta.get("width").toString()) == 0) {
			meta.put("hidden", true);
		} else {
			col.put("width", meta.get("width"));
		}
		col.put("field", meta.get("field"));
		col.put("name", meta.get("alias"));
		// col.put("index", meta.get("alias"));
		col.put("label", meta.get("label"));
		col.put("align", "left");
		if (!Boolean.TRUE.equals(meta.get("calc")))
			col.put("searchType", meta.get("searchType"));
		col.put("dataType", meta.get("dataType"));
		String sorttype = (String) meta.get("dataType");

		if (sorttype == null || sorttype.matches("String|Text")) {
			sorttype = "text";
		} else if (sorttype.matches("Date|DateTime|Time")) {
			sorttype = "date";
			col.put("formatter", "date");
			col.put("formatoptions", JQGRUD_DATEFORMAT);
			col.put("align", "center");
		} else if (sorttype.matches("DateTime|Time")) {
			sorttype = "date";
			col.put("formatter", "date");
			col.put("formatoptions", JQGRUD_DATETIMEFORMAT);
			col.put("align", "center");
		} else if (sorttype.matches("Float|Decimal")) {
			sorttype = "float";
			col.put("align", "right");
		} else if (sorttype.matches("Integer")) {
			sorttype = "int";
			col.put("align", "right");
		} else {
			sorttype = "text";
		}
		col.put("sorttype", sorttype);
		return col;
	}

	/**
	 * sql数字转字符函数
	 * 
	 * @return
	 */
	private static String sqlNumberToText() {
		return "cast(%s as char(100))";
	}

	/**
	 * sql日期转字符函数
	 * 
	 * @return
	 */
	private static String sqlDateTimeToText() {
		// DATE_FORMAT(%s,'%Y-%m-%d %H:%i%s');
		return "to_char(%s,'yyyy-mm-dd hh24-mi-ss')";
	}

	static {
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fBizRecId", "fBizRecId", "案卷编号", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fRecTitle", "fRecTitle", "案卷标题", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fReceiverName", "fReceiverName", "收件人", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fBizName", "fBizName", "业务名称", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fReceiveTime", "fReceiveTime", "收件日期", "Date", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fStatusName", "fStatusName", "状态", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fFlowId", "fFlowId", "流程编号", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fProcess", "fProcess", "流程Process", "String", false);
		appendColumnInfo(
				FIELDS_BIZREC_COLUMNS,
				"decode(B_BizRec.fFinishKind,'fkCertification','发证办结','fkUntread','退回办结','fkAbort','作废办结','fkDelete','删除办结','fkPaper','纸质办结','fkSubmit','转报办结','fkApprizeAbort','补正不来办结','fkNormal','办结','')",
				"fFinishKind", "办结类型", "String", false);

		appendColumnInfo(FIELDS_BIZREC_COLUMNS,
				"decode(B_BizRec.fSuspendKind,'skApprize','补正告知','skSpecialProcedure','特别程序','skSubmit','转报办结','普通挂起')", "fSuspendKind", "挂起类型",
				"String", false);

		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fLimitDate", "fLimitDate", "限办日期", "Date", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRec.fRemainingDays", "FlowAlter", "流程预警", "Integer", false);

		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BJJLB.fBJJGMS", "fBJJGMS", "办结结果", "Text", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BJJLB.fBJSJ", "fBJSJ", "办结日期", "Date", false);

		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.FInComeDocName", "FInComeDocName", "来文名称", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.FInComeDocOrg", "FInComeDocOrg", "来文单位", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.fSerialNo", "fSerialNo", "来文号", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.FArchivesCode", "FArchivesCode", "办文号", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.FMainDept", "FMainDept", "主办部门", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.FMainPerson", "FMainPerson", "主办人", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.FRecPriority", "FRecPriority", "紧急度", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_BizRecAttr.fKind", "fKind", "业务类型", "String", false);

		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "decode(B_AJGQJLB.fGQLX,'skApprize','补正告知','skSpecialProcedure','特别程序','skSubmit','转报办结','挂起')",
				"fGQLX", "挂起类型", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_AJGQJLB.fGQYY", "fGQYY", "挂起原因", "String", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_AJGQJLB.fFQSJ", "fFQSJ", "挂起日期", "DateTime", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_AJGQJLB.fSQGQTS", "fSQGQTS", "申请挂起天数", "Integer", false);
		appendColumnInfo(FIELDS_BIZREC_COLUMNS, "B_AJGQJLB.fRemainingDays", "fSuspRemainingDays", "挂起剩余天数", "Decimal", false);

		initBizGroupDefines();
	}
}
