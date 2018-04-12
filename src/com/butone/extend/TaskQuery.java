package com.butone.extend;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.butone.data.SQLUtils;
import com.butone.flowbiz.FinishKind;
import com.butone.flowbiz.FlowBizConsts;
import com.butone.flowbiz.TaskExtendRelation;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.DatabaseProduct;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.TableUtils;
import com.justep.system.process.TaskKind;
import com.justep.system.process.TaskStatus;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class TaskQuery {
	private static final Log logger = LogFactory.getLog(TaskQuery.class);
	private static final String SQL_PARAM_PERSONID = "_sys_personid";
	private static final String SQL_PARAM_PROCESS = "_sys_process";
	private static final String SQL_PARAM_ACTIVITY = "_sys_activity";
	private static List<String> SYS_TABLES = Arrays.asList(new String[] { "SYS", "SA_TASK", "B_BIZREC", "B_BJJLB", "B_AJGQJLB", "B_BIZRECATTR",
			"B_GROUPFUNC" });

	private static Map<String, Map<String, Object>> FIELDS_SA_TASK_COLUMNS = new LinkedHashMap<String, Map<String, Object>>();
	// 时间格式化
	private static Map<String, Object> JQGRID_DATEFORMAT = new HashMap<String, Object>();
	// 时间格式化
	private static Map<String, Object> JQGRID_DATETIMEFORMAT = new HashMap<String, Object>();
	static {
		JQGRID_DATEFORMAT.put("srcformat", "Y-m-d");
		JQGRID_DATEFORMAT.put("newformat", "Y-m-d");

		JQGRID_DATETIMEFORMAT.put("srcformat", "Y-m-d H:i:s");
		JQGRID_DATETIMEFORMAT.put("newformat", "Y-m-d H:i:s");
	}

	public static Map<String, Map<String, Object>> getSysFields() {
		return Collections.unmodifiableMap(FIELDS_SA_TASK_COLUMNS);
	}

	private static void appendColumnInfo(Map<String, Map<String, Object>> list, String field, String alias, String label, String dataType,
			boolean calc, boolean required, String taskGroups) {
		Map<String, Object> column = new HashMap<String, Object>();
		column.put("field", field);
		column.put("label", label);
		column.put("alias", alias);
		column.put("dataType", dataType);
		column.put("calc", calc);
		column.put("required", required);
		column.put("taskGroups", taskGroups);
		list.put(alias, column);
	}

	private static HashMap<String, Map<String, Object>> bizGroupDefines = new LinkedHashMap<String, Map<String, Object>>();

	public static void initBizGroupDefines() {
		bizGroupDefines.clear();
		Map<String, String> map = new HashMap<String, String>();
		String sql_info = " select a.* From B_Businessgroup  a  Where exists(Select 1 From B_GroupFunc b Where a.fid = b.fBusinessGroupId) and (fGroupType='案卷中心' or fGroupType='移动案卷中心') and fValid=1 order by fGroupOrder ";
		map.put(DatabaseProduct.DEFAULT.name(), sql_info);
		Table groupTable = SQLUtils.select(map, null, FlowBizConsts.DATA_MODEL_CORE_FLOW);
		loadBizGroupDefine(groupTable);
	}

	public static void initBizGroupDefine(String groupId) {
		Map<String, String> map = new HashMap<String, String>();
		String sql_info = " select a.* From B_Businessgroup  a  Where exists(Select 1 From B_GroupFunc b Where a.fid = b.fBusinessGroupId) and (fGroupType='案卷中心' or fGroupType='移动案卷中心') and a.fID=? and fValid=1";// And
		map.put(DatabaseProduct.DEFAULT.name(), sql_info);
		List<Object> binds = new ArrayList<Object>();
		binds.add(groupId);
		Table groupTable = SQLUtils.select(map, binds, FlowBizConsts.DATA_MODEL_CORE_FLOW);
		loadBizGroupDefine(groupTable);
	}

	/**
	 * 批量操作要使用
	 * 
	 * @param process
	 * @return
	 */
	public static Map<String, Object> getBizGroupByProcess(String process) {
		Map<String, Object> bizgroup = new HashMap<String, Object>();
		Iterator<String> i = bizGroupDefines.keySet().iterator();
		while (i.hasNext()) {
			String groupId = i.next();
			String processes = (String) (bizGroupDefines.get(groupId).get("processes"));
			if (processes.contains("'" + process + "'")) {
				bizgroup.put("groupId", groupId);
				// JQGrid 的setting
				Map<String, Object> setting = new HashMap<String, Object>();
				Map<String, Object> groupDefine = bizGroupDefines.get(groupId);
				setting.put("colModel", getJQGridColModel(groupDefine, null));
				// groupingView
				setting.put("groupingView", getJQGridGroupingView(groupDefine));
				bizgroup.put("setting", setting);
				return bizgroup;
			}
		}
		return null;
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
			Iterator<Row> fieldrows = fieldTable.iterator();
			while (fieldrows.hasNext()) {
				Row row = fieldrows.next();
				String tableName = row.getString("FTABLENAME");
				// as Alias
				String fFieldAlias = row.getString("FFIELDALIAS");
				Map<String, Object> column = new HashMap<String, Object>();
				Map<String, Object> defaultDef = FIELDS_SA_TASK_COLUMNS.get(fFieldAlias);
				if (defaultDef != null) {
					column.putAll(defaultDef);
				} else {
					if ("sys".equals(tableName)) {
						logger.error("任务中心分组【" + fBusinessGroupId + "】字段(fFieldAlias)【" + fFieldAlias + "】不是系统字段");
						continue;
					}
					column.put("field", tableName + "." + row.getString("FFIELD"));
					column.put("alias", fFieldAlias);
					column.put("dataType", row.getString("FDATATYPE"));
				}
				column.put("label", row.getString("FFIELDNAME"));
				column.put("groupIndex", row.getDecimal("FGROUPINDEX"));
				column.put("width", row.getValue("FSHOWLENGTH"));
				column.put("hidden", row.getValue("FSHOWLENGTH") == null || row.getDecimal("FSHOWLENGTH").intValue() == 0);
				column.put("searchType", row.getValue("FSEARCHTYPE"));
				column.put("taskGroups", row.getValue("FTASKGROUPS"));

				columnMetaDatas.add(column);
				// 非系统表，并且未连接
				if (!SYS_TABLES.contains(tableName.toUpperCase()) && !tableNames.contains(tableName)) {
					tableNames.add(tableName);
					joinTables.append("left join ").append(tableName).append(" ").append(tableName).append(" on ").append(tableName)
							.append(".fBizRecId=B_BizRec.fBizRecId\n");

				}
				// 非系统系统保留字段
				if (!FIELDS_SA_TASK_COLUMNS.containsKey(fFieldAlias)) {
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
				processes += ",'" + row.getString("FPROCESS") + "'";
			}
			if (processes.length() > 0)
				processes = processes.substring(1);

			Map<String, Object> groupDefine = new HashMap<String, Object>();
			groupDefine.put("id", grouprow.getString("FID"));
			groupDefine.put("name", grouprow.getString("FGROUPNAME"));
			if (bizTableFields.length() > 0)
				groupDefine.put("bizTableFields", bizTableFields.toString().substring(1));
			else
				groupDefine.put("bizTableFields", null);
			groupDefine.put("joinTables", joinTables.toString());
			groupDefine.put("columnMetaDatas", columnMetaDatas);
			groupDefine.put("processes", processes);
			groupDefine.put("orderBy", grouprow.getString("FORDERBY"));
			bizGroupDefines.put(fBusinessGroupId, groupDefine);
		}
	}

	static enum TaskGroup {
		SUBMITED, WAITTING, SUSPEND, NOTHANDLE, HANDLED, SIGNIN, PERSONALATTENTION, PUSHATTENTION, HAVEDONE;
		String getName() {
			switch (this) {
			case SIGNIN:
				return "签收件";
			case SUBMITED:
				return "移交件";
			case WAITTING:
				return "待办件";
			case SUSPEND:
				return "挂起件";
			case NOTHANDLE:
				return "经办件(未办结)";
			case HANDLED:
				return "经办件(已办结)";
			case PERSONALATTENTION:
				return "个人关注件";
			case PUSHATTENTION:
				return "推送关注件";
			case HAVEDONE:
				return "已办件";
			default:
				throw new RuntimeException("不支持的任务类型");
			}
		}

		/**
		 * 任务类型对应的任务状态
		 * 
		 * @param kind
		 * @return
		 */
		String[] getTaskStatuses() {
			switch (this) {
			case SUBMITED:
				return new String[] { TaskStatus.READY, TaskStatus.EXECUTING };
			case WAITTING:
				return new String[] { TaskStatus.READY, TaskStatus.EXECUTING };
			case SIGNIN:
				return new String[] { TaskStatus.READY, TaskStatus.EXECUTING };
			case SUSPEND:
				return new String[] { TaskStatus.SUSPEND };
			case NOTHANDLE:
				return new String[] {};
			case HANDLED:
				return new String[] {};
			case PERSONALATTENTION:
				return new String[] {};
			case PUSHATTENTION:
				return new String[] {};
			case HAVEDONE:
				return new String[] {};
			default:
				throw new RuntimeException("不支持的任务分组");
			}
		}

		String[] getBizRecStatuses() {
			switch (this) {
			case NOTHANDLE:
				return new String[] {};
			case HANDLED:
				return new String[] {};
			default:
				return new String[] {};
			}
		}
	}

	// /**
	// * 获得业务分组的字段列表，字段包括name、label、dataType、width
	// *
	// * @param fBusinessGroupId
	// * @return
	// */
	// public static List<Map<String, Object>> getBizGroupFields(String
	// fBusinessGroupId) {
	// List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
	// ret.addAll(FIELDS_SA_TASK_COLUMNS);
	// Map<String, Object> groupDefine = bizGroupDefines.get(fBusinessGroupId);
	// @SuppressWarnings("unchecked")
	// List<Map<String, Object>> bizFields = (List<Map<String, Object>>)
	// groupDefine.get("columnMetaDatas");
	// ret.addAll(bizFields);
	// return ret;
	// }

	// 返回分组导航
	public static List<Map<String, Object>> queryBizGroup(String org, String taskGroup, String taskFilter, Map<String, Object> variables) {
		if (Utils.isEmptyString(taskGroup))
			taskGroup = TaskGroup.WAITTING.name();
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		if (variables == null)
			variables = new HashMap<String, Object>();

		// 增加 区分移动端和PC端的过滤条件
		String mobileFilter = " and g.fgrouptype='案卷中心'";
		if ("移动案卷中心".equals(String.valueOf(variables.get("groupType"))))
			mobileFilter = " and g.fgrouptype='移动案卷中心' and SA_Task.sEIField42=1";

		variables.put(TaskQuery.SQL_PARAM_PERSONID, ContextHelper.getPerson().getID());
		// 过滤条件是否包含B_BizRecAttr，如果包含关联否则不关联
		boolean flag = Utils.isNotEmptyString(taskFilter) && taskFilter.contains("B_BizRecAttr");
		String query = "select %s g.FID,g.FGROUPNAME,g.FGROUPORDER,count(distinct SA_Task.sID) FCNT from B_Businessgroup g,B_GroupFunc f,"
				+ "SA_Task SA_Task,B_BizRec B_BizRec" + (flag ? ",B_BizRecAttr B_BizRecAttr" : "")
				+ "\nwhere g.FValid=1 and f.fBusinessGroupId=g.FID " + mobileFilter + " and f.fProcess = SA_Task.Sprocess"
				+ "\nand SA_Task.sFlowId=B_BizRec.fFlowId" + (flag ? " and B_BizRecAttr.fBizRecId = B_BizRec.fBizRecId" : "")
				+ " and (%s) group by g.FID,g.FGROUPNAME,g.FGROUPORDER order by g.FGROUPORDER";

		TaskGroup group = TaskGroup.valueOf(taskGroup);

		if (group.equals(TaskGroup.NOTHANDLE) || group.equals(TaskGroup.HANDLED) || group.equals(TaskGroup.HAVEDONE)
				|| group.equals(TaskGroup.PERSONALATTENTION)) {
			query = "select %s g.FID,g.FGROUPNAME,g.FGROUPORDER,count(distinct B_BizRec.fBizRecId) FCNT from B_Businessgroup g,B_GroupFunc f,"
					+ "SA_Task SA_Task,B_BizRec B_BizRec" + (flag ? ",B_BizRecAttr B_BizRecAttr" : "")
					+ "\nwhere g.FValid=1 and f.fBusinessGroupId=g.FID " + mobileFilter + " and f.fProcess = SA_Task.Sprocess"
					+ "\nand SA_Task.sFlowId=B_BizRec.fFlowId" + (flag ? " and B_BizRecAttr.fBizRecId = B_BizRec.fBizRecId" : "")
					+ " and (%s) group by g.FID,g.FGROUPNAME,g.FGROUPORDER order by g.FGROUPORDER";
		}
		String sqlFilter = getDefaultTaskFilter("SA_Task", taskGroup, variables);
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", taskFilter);

		String forceIndex = "";
		if (TaskUtils.isForceIndex()) {
			if (TaskGroup.WAITTING.name().equals(taskGroup) || TaskGroup.SIGNIN.name().equals(taskGroup)) {
				// 待办件强制索引
				forceIndex = "/*+INDEX(SA_Task IDX_TASK_WAIT)*/";
			} else if (taskGroup.equals(TaskGroup.SUBMITED.name())) {
				// 移交件强制索引
				forceIndex = "/*+INDEX(SA_Task IDX_TASK_SUB2)*/";
			} else {

			}
		}
		query = String.format(query, forceIndex, sqlFilter);

		List<Object> paramList = SQLUtils.parseSqlParameters(query, variables);
		Table t = SQLUtils.select(SQLUtils.fixSQL(query, variables, true), paramList, FlowBizConsts.DATA_MODEL_CORE_FLOW);
		t.getMetaData().setKeyColumn("FID");

		// 移动端传入固定分组，显示所有分组
		boolean fixedGroup = variables.containsKey("fixedGroup");

		Iterator<String> i = bizGroupDefines.keySet().iterator();
		while (i.hasNext()) {
			String groupId = i.next();
			Row r = t.getRow(groupId);
			if (r == null && !fixedGroup)
				continue;

			Map<String, Object> groupDefine = bizGroupDefines.get(groupId);

			Map<String, Object> bizgroup = new HashMap<String, Object>();
			ret.add(bizgroup);

			bizgroup.put("groupId", groupId);
			bizgroup.put("groupName", groupDefine.get("name"));
			Map<String, Object> setting = new HashMap<String, Object>();
			// colModel
			setting.put("colModel", getJQGridColModel(groupDefine, taskGroup));
			// groupingView
			setting.put("groupingView", getJQGridGroupingView(groupDefine));
			// JQGrid 的setting
			bizgroup.put("setting", setting);
			if (r != null) {
				bizgroup.put("count", r.getValue("FCNT").toString());
			} else {
				bizgroup.put("count", 0);
			}
		}
		return ret;
	}

	// 返回分组导航数量
	public static Map<String, Object> queryGroupTaskCount(String sql, List<String> taskGroupList, String taskFilter, Map<String, Object> variables) {
		HashMap<String, Object> ret = new HashMap<String, Object>();
		if (variables == null)
			variables = new HashMap<String, Object>();
		variables.put(TaskQuery.SQL_PARAM_PERSONID, ContextHelper.getPerson().getID());
		// 过滤条件是否包含B_BizRecAttr，如果包含关联否则不关联
		boolean flag = Utils.isNotEmptyString(taskFilter) && taskFilter.contains("B_BizRecAttr");
		for (String taskGroup : taskGroupList) {
			String query = "select '" + taskGroup + "', count(distinct SA_Task.sID) FCNT from B_Businessgroup g,B_GroupFunc f,"
					+ "SA_Task SA_Task,B_BizRec B_BizRec" + (flag ? ",B_BizRecAttr B_BizRecAttr" : "")
					+ "\nwhere g.FValid=1 and f.fBusinessGroupId=g.FID and g.fgrouptype='案卷中心' and f.fProcess = SA_Task.Sprocess"
					+ "\nand SA_Task.sFlowId=B_BizRec.fFlowId" + (flag ? " and B_BizRecAttr.fBizRecId = B_BizRec.fBizRecId" : "") + " and (%s)";
			if (Utils.isNotEmptyString(taskGroup)
					&& ((TaskGroup.valueOf(taskGroup)).equals(TaskGroup.NOTHANDLE) || (TaskGroup.valueOf(taskGroup)).equals(TaskGroup.HANDLED)
							|| (TaskGroup.valueOf(taskGroup)).equals(TaskGroup.HAVEDONE) || (TaskGroup.valueOf(taskGroup))
							.equals(TaskGroup.PERSONALATTENTION))) {
				query = "select '" + taskGroup + "', count(distinct B_BizRec.fBizRecId) FCNT from B_Businessgroup g,B_GroupFunc f,"
						+ "SA_Task SA_Task,B_BizRec B_BizRec" + (flag ? ",B_BizRecAttr B_BizRecAttr" : "")
						+ "\nwhere g.FValid=1 and f.fBusinessGroupId=g.FID and g.fgrouptype='案卷中心' and f.fProcess = SA_Task.Sprocess"
						+ "\nand SA_Task.sFlowId=B_BizRec.fFlowId" + (flag ? " and B_BizRecAttr.fBizRecId = B_BizRec.fBizRecId" : "") + " and (%s)";
			}
			String sqlFilter = getDefaultTaskFilter("SA_Task", taskGroup, variables);
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", taskFilter);
			query = String.format(query, sqlFilter);
			if (Utils.isNotEmptyString(sql))
				sql += "union all " + query;
			else
				sql = query;
		}
		List<Object> paramList = SQLUtils.parseSqlParameters(sql, variables);
		Table t = SQLUtils.select(SQLUtils.fixSQL(sql, variables, true), paramList, FlowBizConsts.DATA_MODEL_CORE_FLOW);
		Iterator<Row> it = t.iterator();
		while (it.hasNext()) {
			Row r = it.next();
			ret.put(r.getString(0), r.getValue(1).toString());
		}
		return ret;
	}

	// private static Map<String, Object> getJQGridSetting(String groupId) {
	// Map<String, Object> groupDefine = bizGroupDefines.get(groupId);
	// Map<String, Object> ret = new HashMap<String, Object>();
	// if (groupDefine == null)
	// throw new BusinessException("业务分组定义不存在[" + groupId + "]");
	// // colModel
	// ret.put("colModel", getJQGridColModel(groupDefine));
	// // groupingView
	// ret.put("groupingView", getJQGridGroupingView(groupDefine));
	//
	// return ret;
	// }

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
				groupColumnShow.add(false);
			}
		}
		if (groupField.size() > 0) {
			groupViewer.put("groupField", groupField);
			groupViewer.put("groupText", groupText);
			groupViewer.put("groupColumnShow", groupColumnShow);
		}
		return groupViewer;
	}

	private static String getOrderByFormGroupDefine(String orderBy1, Map<String, Object> groupDefine, Set<String> orderFields,
			Map<String, String> orderMap2) {
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bizFields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		String order2 = "";
		for (Map<String, Object> bizField : bizFields) {
			Object groupIndex = bizField.get("groupIndex");
			String field, alias = (String) bizField.get("alias");
			if (!orderFields.contains((String) bizField.get("alias"))) {
				if (FIELDS_SA_TASK_COLUMNS.containsKey(bizField.get("alias"))) {
					field = (String) FIELDS_SA_TASK_COLUMNS.get(alias).get("field");
				} else {
					field = (String) bizField.get("field");
				}
				if (groupIndex != null) {
					orderFields.add(alias);
					orderBy1 += (orderBy1.length() > 0 ? "," : "") + field;
				} else if (orderMap2.containsKey(alias.toLowerCase())) {
					orderFields.add(alias);
					order2 += order2 + (order2.length() > 0 ? "," : "") + field;
				}
			}
		}

		if (order2.length() == 0) {
			return orderBy1;
		} else if (orderBy1.length() == 0) {
			return order2;
		} else {
			return orderBy1 + "," + order2;
		}
	}

	/**
	 * 列的元数据转jqGrid的ColModel
	 * 
	 * @param meta
	 * @return
	 */
	private static Map<String, Object> transformColumnMeta2JGColModel(Map<String, Object> meta) {
		Map<String, Object> col = new HashMap<String, Object>();
		if (Boolean.TRUE.equals(meta.get("hidden"))) {
			// 列不可见也回传客户端，用于创建过滤条件
			col.put("hidden", true);
		} else {
			col.put("width", meta.get("width"));
		}
		col.put("field", meta.get("field"));
		col.put("name", meta.get("alias"));// 列名
		// col.put("index", meta.get("alias"));// 传到服务器的名称
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
			col.put("formatoptions", JQGRID_DATEFORMAT);
			col.put("align", "center");
		} else if (sorttype.matches("DateTime|Time")) {
			sorttype = "date";
			col.put("formatter", "date");
			col.put("formatoptions", JQGRID_DATETIMEFORMAT);
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
	 * 根据任务分组英文名称找中文名称
	 * @param key  英文名称
	 * @return
	 */
	private static String getTaskGroupValue(String key) {
		//
		if (key.equals(TaskGroup.SUBMITED.name())) {
			return TaskGroup.SUBMITED.getName();
		} else if (key.equals(TaskGroup.WAITTING.name())) {
			return TaskGroup.WAITTING.getName();
		} else if (key.equals(TaskGroup.SUSPEND.name())) {
			return TaskGroup.SUSPEND.getName();
		} else if (key.equals(TaskGroup.NOTHANDLE.name())) {
			return TaskGroup.NOTHANDLE.getName();
		} else if (key.equals(TaskGroup.HANDLED.name())) {
			return TaskGroup.HANDLED.getName();
		} else if (key.equals(TaskGroup.SIGNIN.name())) {
			return TaskGroup.SIGNIN.getName();
		} else if (key.equals(TaskGroup.PERSONALATTENTION.name())) {
			return TaskGroup.PERSONALATTENTION.getName();
		} else if (key.equals(TaskGroup.PUSHATTENTION.name())) {
			return TaskGroup.PUSHATTENTION.getName();
		} else if (key.equals(TaskGroup.HAVEDONE.name())) {
			return TaskGroup.HAVEDONE.getName();
		} else {
			throw new RuntimeException("不支持的任务分组");
		}
	}

	/**
	 * 转换为JQGrid的列模型
	 * 
	 * @param groupId
	 * @return
	 */
	private static List<Map<String, Object>> getJQGridColModel(Map<String, Object> groupDefine, String taskGroup) {
		List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bizFields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		Set<String> allFields = new HashSet<String>();
		for (Map<String, Object> bizField : bizFields) {
			String taskGroups = (String) bizField.get("taskGroups");
			//taskGroups字段存储的是任务分组中文名称，而不是英文名称
			if (Utils.isNotEmptyString(taskGroups) && Utils.isNotEmptyString(taskGroup) && !taskGroups.contains(getTaskGroupValue(taskGroup)))
				continue;
			Map<String, Object> col = transformColumnMeta2JGColModel(bizField);
			if (col != null) {
				ret.add(col);
				allFields.add((String) col.get("name"));
			}
		}
		return ret;
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
	public static Table queryTask(String orderBy, Integer limit, Integer offset, Map<String, Object> variables, Map<String, String> filterMap) {
		if (variables == null)
			variables = new HashMap<String, Object>();
		variables.put(TaskQuery.SQL_PARAM_PERSONID, ContextHelper.getPerson().getID());
		String groupId = filterMap.get("groupId");
		variables.put("groupId", groupId);

		Map<String, Object> groupDefine = bizGroupDefines.get(groupId);
		Set<String> orderFields = new HashSet<String>();
		String defineOrderBy = "B_GroupFunc.fProcessOrder,decode(B_BizRecAttr.FRecPriority,'特提',1,'特急',2,'紧急',3,'急件',4,'平急',5,'平件',6,10),B_BizRec.fReceiveTime desc";
		orderFields.add("fProcessOrder");
		orderFields.add("fReceiveTime");
		orderFields.add("FRecPriority");
		Map<String, String> customOrder = new LinkedHashMap<String, String>();
		if (orderBy == null)
			orderBy = (String) groupDefine.get("orderBy");
		if (orderBy != null) {
			String[] args = orderBy.toLowerCase().split(",");
			for (String str : args) {
				String flag = "", alias = str;
				if (str.contains(" ")) {
					flag = str.substring(str.indexOf(" ") + 1);
					str = str.substring(0, str.indexOf(" "));
				}
				customOrder.put(alias, flag);
			}
		}
		defineOrderBy = getOrderByFormGroupDefine(defineOrderBy, groupDefine, orderFields, customOrder);

		// task默认条件
		String taskGroup = filterMap.get("taskGroup");
		if (taskGroup == null)
			taskGroup = TaskGroup.WAITTING.name();
		String sqlFilter = getDefaultTaskFilter("SA_Task", taskGroup, variables);

		// 智能过滤条件
		String smartFilter = smartValueFilters(filterMap.get("smartValue"), groupDefine);
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", smartFilter);

		// 自定义条件
		String customFilter = filterMap.get("customFilter");
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", customFilter);

		String joinTabls = (String) groupDefine.get("joinTables");

		List<String> columnNames = new ArrayList<String>();
		List<String> columnTypes = new ArrayList<String>();

		String selectFields = "";
		// 系统必要字段字段
		for (Map<String, Object> field : FIELDS_SA_TASK_COLUMNS.values()) {
			if (Boolean.TRUE.equals(field.get("required"))) {
				selectFields += "," + field.get("field") + " as " + field.get("alias");
				columnNames.add((String) field.get("alias"));
				columnTypes.add((String) field.get("dataType"));
			}
		}

		// 分组定义要求返回的字段
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bizFields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		for (Map<String, Object> field : bizFields) {
			String alias = (String) field.get("alias");
			if (columnNames.contains(alias) || Boolean.TRUE.equals(field.get("hidden")))
				continue;
			// 排除非当前任务分组的字段
			Map<String, Object> sysField = FIELDS_SA_TASK_COLUMNS.get(field.get("alias"));
			if (sysField != null) {
				String taskGroups = (String) sysField.get("taskGroups");
				if (Utils.isNotEmptyString(taskGroups) && !taskGroups.contains(taskGroup))
					continue;
			}
			if (FIELDS_SA_TASK_COLUMNS.containsKey(alias))
				selectFields += "," + field.get("field") + " as " + field.get("alias");
			columnNames.add(alias);
			columnTypes.add((String) field.get("dataType"));
		}

		// 工作表字段
		String bizDataFields = (String) groupDefine.get("bizTableFields");
		if (Utils.isNotEmptyString(bizDataFields))
			selectFields += "," + bizDataFields;

		columnNames.add("SA_Task");
		columnTypes.add("String");

		Model dataModel = ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		Table table = TableUtils.createTable(dataModel, columnNames, columnTypes);
		table.getMetaData().setKeyColumn("sID");
		table.getProperties().put(Table.PROP_NAME_ROWID, "SA_Task");

		selectFields = selectFields.substring(1)
				+ ",decode(B_BizRecAttr.FRecPriority,'特提',1,'特急',2,'紧急',3,'急件',4,'平急',5,'平件',6,10) fRecPriorityLevel,fProcessOrder";
		// select
		String query = selectFields + " from SA_Task SA_Task\ninner join B_BizRec B_BizRec on B_BizRec.fBizRecId=SA_Task.sData1\n";
		TaskGroup group = TaskGroup.valueOf(taskGroup);
		if (group.equals(TaskGroup.NOTHANDLE) || group.equals(TaskGroup.HANDLED) || group.equals(TaskGroup.HAVEDONE)
				|| group.equals(TaskGroup.PERSONALATTENTION)) {
			query = selectFields
					+ " from  B_BizRec B_BizRec \ninner join ( select  *  From SA_task  a  Where  skindid ='tkProcessInstance' ) SA_Task on B_BizRec.fBizRecId=SA_Task.sData1\n";
		}

		if (TaskUtils.isForceIndex()) {
			if (group.equals(TaskGroup.WAITTING)) {
				// 待办件强制索引
				query = "/*+INDEX(SA_Task IDX_TASK_WAIT) INDEX(B_GroupFunc B_GROUPFUNC_GROUPPROC)*/ " + query;
			} else if (group.equals(TaskGroup.SUBMITED)) {
				// 移交件强制索引
				query = "/*+INDEX(SA_Task IDX_TASK_SUB2) INDEX(B_GroupFunc B_GROUPFUNC_GROUPPROC)*/ " + query;
			} else {
				// 其他强制索引
				query = "/*+INDEX(B_GroupFunc B_GROUPFUNC_GROUPPROC)*/ " + query;
			}
		}

		query = "select " + query;

		// TODO 先不使用宿主变量，有性能问题
		query += "inner join B_GroupFunc on B_GroupFunc.fBusinessGroupid='" + groupId + "' and B_GroupFunc.fProcess=SA_Task.sProcess\n";
		query += "left join B_BizRecAttr on B_BizRecAttr.fBizRecId=B_BizRec.fBizRecId\n";
		query += "left join B_AJGQJLB on B_AJGQJLB.fBizRecId=B_BizRec.fBizRecId and fGQZT='挂起中'\n";
		if (joinTabls.length() > 0) {
			query += joinTabls;
		}
		// where
		query += "where\n" + sqlFilter;
		// orderBy
		query = "select * from (" + query + "\norder by " + defineOrderBy + ") TMP";
		if (orderBy != null) {
			query += "\norder by fProcessOrder," + orderBy + ",fRecPriorityLevel,fReceiveTime desc";
		}

		List<Object> bindings = SQLUtils.parseSqlParameters(query, variables);
		long t = 0;
		if (logger.isDebugEnabled()) {
			t = System.currentTimeMillis();
		}
		table = SQLUtils.select(SQLUtils.fixSQL(query, variables, true), bindings, dataModel, offset, limit, table);
		if (logger.isDebugEnabled()) {
			logger.info(query + "\n耗时:" + (System.currentTimeMillis() - t));
		}
		return table;
	}

	/**
	 * 查询指定process和环节的批量
	 * 
	 * @return
	 */
	public static Table queryBatchTask(String process, String activity, String orderBy, Integer limit, Integer offset, Map<String, Object> variables,
			Map<String, String> filterMap) {
		if (variables == null)
			variables = new HashMap<String, Object>();
		variables.put(TaskQuery.SQL_PARAM_PERSONID, ContextHelper.getPerson().getID());
		variables.put(TaskQuery.SQL_PARAM_PROCESS, process);
		variables.put(TaskQuery.SQL_PARAM_ACTIVITY, activity);

		String groupId = filterMap.get("groupId");
		Map<String, Object> groupDefine = bizGroupDefines.get(groupId);

		Set<String> orderFields = new HashSet<String>();
		String defineOrderBy = "decode(B_BizRecAttr.FRecPriority,'特提',1,'特急',2,'紧急',3,'急件',4,'平急',5,'平件',6,10),B_BizRec.fReceiveTime desc";
		orderFields.add("fReceiveTime");
		Map<String, String> customOrder = new LinkedHashMap<String, String>();
		if (orderBy == null)
			orderBy = (String) groupDefine.get("orderBy");
		if (orderBy != null) {
			String[] args = orderBy.toLowerCase().split(",");
			for (String str : args) {
				String flag = "", alias = str;
				if (str.contains(" ")) {
					flag = str.substring(str.indexOf(" ") + 1);
					str = str.substring(0, str.indexOf(" "));
				}
				customOrder.put(alias, flag);
			}
		}
		defineOrderBy = getOrderByFormGroupDefine(defineOrderBy, groupDefine, orderFields, customOrder);

		// task默认条件
		String taskGroup = filterMap.get("taskGroup");
		if (taskGroup == null)
			taskGroup = TaskGroup.WAITTING.name();
		String sqlFilter = getDefaultTaskFilter("SA_Task", taskGroup, variables);

		// **************批量分组条件******************
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", "SA_Task.sProcess=:" + TaskQuery.SQL_PARAM_PROCESS + " and SA_Task.sActivity=:"
				+ TaskQuery.SQL_PARAM_ACTIVITY);

		// 智能过滤条件
		String smartFilter = smartValueFilters(filterMap.get("smartValue"), groupDefine);
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", smartFilter);

		// 自定义条件
		String customFilter = filterMap.get("customFilter");
		sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", customFilter);

		String joinTabls = (String) groupDefine.get("joinTables");

		List<String> columnNames = new ArrayList<String>();
		List<String> columnTypes = new ArrayList<String>();

		String selectFields = "";
		// 系统必要字段字段
		for (Map<String, Object> field : FIELDS_SA_TASK_COLUMNS.values()) {
			if (Boolean.TRUE.equals(field.get("required"))) {
				selectFields += "," + field.get("field") + " as " + field.get("alias");
				columnNames.add((String) field.get("alias"));
				columnTypes.add((String) field.get("dataType"));
			}
		}

		// 分组定义要求返回的字段
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bizFields = (List<Map<String, Object>>) groupDefine.get("columnMetaDatas");
		for (Map<String, Object> field : bizFields) {
			String alias = (String) field.get("alias");
			if (columnNames.contains(alias) || Boolean.TRUE.equals(field.get("hidden")))
				continue;
			if (FIELDS_SA_TASK_COLUMNS.containsKey(alias))
				selectFields += "," + field.get("field") + " as " + field.get("alias");
			columnNames.add(alias);
			columnTypes.add((String) field.get("dataType"));
		}

		// 工作表字段
		String bizDataFields = (String) groupDefine.get("bizTableFields");
		if (Utils.isNotEmptyString(bizDataFields))
			selectFields += "," + bizDataFields;

		columnNames.add("SA_Task");
		columnTypes.add("String");

		Model dataModel = ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		Table table = TableUtils.createTable(dataModel, columnNames, columnTypes);
		table.getMetaData().setKeyColumn("sID");
		table.getProperties().put(Table.PROP_NAME_ROWID, "SA_Task");

		selectFields = selectFields.substring(1)
				+ ",decode(B_BizRecAttr.FRecPriority,'特提',1,'特急',2,'紧急',3,'急件',4,'平急',5,'平件',6,10) fRecPriorityLevel";
		// select
		String query = "select " + selectFields + " from SA_Task SA_Task\ninner join B_BizRec B_BizRec on B_BizRec.fBizRecId=SA_Task.sData1\n";
		TaskGroup group = TaskGroup.valueOf(taskGroup);
		if (group.equals(TaskGroup.NOTHANDLE) || group.equals(TaskGroup.HANDLED)) {
			query = "select "
					+ selectFields
					+ " from  B_BizRec B_BizRec \ninner join ( select  *  From SA_task  a  Where  skindid ='tkProcessInstance' ) SA_Task on B_BizRec.fBizRecId=SA_Task.sData1\n";
		}
		query += "left join B_BizRecAttr on B_BizRecAttr.fBizRecId=B_BizRec.fBizRecId\n";
		query += "left join B_AJGQJLB on B_AJGQJLB.fBizRecId=B_BizRec.fBizRecId and fGQZT='挂起中'\n";
		if (joinTabls.length() > 0) {
			query += joinTabls;
		}
		// where
		query += "where\n" + sqlFilter;
		// orderBy
		query = "select * from (" + query + "\norder by " + defineOrderBy + ") TMP";
		if (orderBy != null) {
			query += "\norder by " + orderBy + ",fRecPriorityLevel,fReceiveTime desc";
		}

		List<Object> bindings = SQLUtils.parseSqlParameters(query, variables);
		long t = 0;
		if (logger.isDebugEnabled()) {
			t = System.currentTimeMillis();
		}
		table = SQLUtils.select(SQLUtils.fixSQL(query, variables, true), bindings, dataModel, offset, limit, table);
		if (logger.isDebugEnabled()) {
			logger.info(query + "\n耗时:" + (System.currentTimeMillis() - t));
		}
		return table;
	}

	public static void main(String[] args) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -100);
		System.out.println(CommonUtils.dateDiff("day", new Date(), cal.getTime()));
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

	private static boolean getPersonSignMode() {
		return Boolean.TRUE.equals(ContextHelper.getPerson().getAttribute("signMode"));
	}

	/**
	 * 任务默认条件(FlowOperation中调用了此方法)
	 * 
	 * @param org
	 * @param taskGroupName
	 * @return
	 */
	private static String getDefaultTaskFilter(String alias, String taskGroup, Map<String, Object> vars) {
		// 任务类型[默认是代办件]
		TaskGroup group = Utils.isEmptyString(taskGroup) ? TaskGroup.WAITTING : TaskGroup.valueOf(taskGroup);

		String sqlFilter = "";
		// 查询tkProcessInstance
		if (TaskGroup.NOTHANDLE.name().equals(taskGroup)) {
			// 经办件(未办结),但不包含待办(tesReady、tesExecuting),挂起(tesSuspend),取消(tesCanceled)
			// tesFinished,tesTransmited,tesAborted,tesRemain,tesReturned,tesSleeping,tesWaited
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", alias + ".sKindID='tkProcessInstance' and B_BizRec.fFinishKind is null");
			return SQLUtils.appendCondition(sqlFilter, "and", "exists(select 1 from SA_Task e where e.sFlowID=" + alias
					+ ".sID and e.sExecutorPersonID=:" + TaskQuery.SQL_PARAM_PERSONID + " and e.sStatusID <>'" + TaskStatus.READY
					+ "' and e.sStatusID <>'" + TaskStatus.EXECUTING + "' and e.sStatusID <>'" + TaskStatus.SUSPEND + "' and e.sStatusID <>'"
					+ TaskStatus.CANCELED + "')");
		} else if (TaskGroup.HANDLED.name().equals(taskGroup)) {
			// 经办件(已办结)，排除放弃 tesCanceled
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", alias + ".sKindID='tkProcessInstance' and B_BizRec.fFinishKind is not null");
			return SQLUtils.appendCondition(sqlFilter, "and", "exists(select 1 from SA_Task e where e.sFlowID=" + alias
					+ ".sID and e.sExecutorPersonID=:" + TaskQuery.SQL_PARAM_PERSONID + " and e.sStatusID<>'" + TaskStatus.CANCELED + "')");
		} else if (TaskGroup.HAVEDONE.name().equals(taskGroup)) {
			// 已办件,包含已转发(tesTransmited),已回退(tesReturned),等待中(tesWaited),已完成(tesFinished),已终止(tesAborted)
			sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", alias + ".sKindID='tkProcessInstance'");
			return SQLUtils.appendCondition(sqlFilter, "and", "exists(select 1 from SA_Task e where e.sFlowID=" + alias
					+ ".sID and e.sExecutorPersonID=:" + TaskQuery.SQL_PARAM_PERSONID + " and e.sStatusID in('" + TaskStatus.TRANSMITED + "'," + "'"
					+ TaskStatus.RETURNED + "','" + TaskStatus.FINISHED + "','" + TaskStatus.ABORTED + "'))");
		} else if (TaskGroup.PERSONALATTENTION.name().equals(taskGroup)) {
			// 个人关注件,存在关注记录的案卷
			return SQLUtils.appendCondition(sqlFilter, "and",
					"exists(select 1 from B_BizRecAttention a where a.fBizRecId = B_BizRec.fBizRecId and a.fPersonID=:"
							+ TaskQuery.SQL_PARAM_PERSONID + ")");
		} else {
			// 1. 执行者条件
			if (group.equals(TaskGroup.SUBMITED)) {
				// 移交
				String submitedCondition = getSubmitedCondition(alias);
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", submitedCondition);
			} else {
				// 待办、挂起、经办、签收
				String executorCondition = getExecutorCondition(alias, vars);
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", executorCondition);
			}

			// 2.任务状态条件
			String[] taskStatuses = group.getTaskStatuses();
			if (taskStatuses != null && taskStatuses.length > 0) {
				StringBuffer sb = new StringBuffer();
				sb.append("(");
				for (String taskStatus : taskStatuses) {
					if (sb.length() > 1)
						sb.append(" or ");
					sb.append(alias + ".sStatusID=").append("'").append(taskStatus).append("'");
				}
				sb.append(")");
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", sb.toString());
			}

			// 3.特殊处理
			if (group.equals(TaskGroup.SUSPEND)) {
				// 排除转报办结和补交不来办结
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", "B_BizRec.fFinishKind is null or (B_BizRec.fFinishKind<>'"
						+ FinishKind.fkApprizeAbort.name() + "' and B_BizRec.fFinishKind<>'" + FinishKind.fkSubmit.name() + "')");
			} else if (group.equals(TaskGroup.SIGNIN)) {
				sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", alias + "." + TaskExtendRelation.FlowTask_PreemptTaskId + " is null");
			} else if (group.equals(TaskGroup.WAITTING)) {
				if (getPersonSignMode()) {
					sqlFilter = SQLUtils.appendCondition(sqlFilter, "and", alias + "." + TaskExtendRelation.FlowTask_PreemptTaskId + " is not null");
				}
			}

			return sqlFilter;
		}
	}

	/**
	 * 移交件，即任务创建者为指定机构
	 * 
	 * @param alias
	 * @param org
	 * @param useAgentProcess
	 * @return
	 */
	private static String getSubmitedCondition(String ailas) {
		// 创建人等于自己，执行人不等于自己,且状态为tesReady 排除and环节
		return "(SA_Task.sKindID='" + TaskKind.TASK + "' and SA_Task.sCreatorPersonID =:" + TaskQuery.SQL_PARAM_PERSONID
				+ " and (SA_Task.sExecutorPersonID is null or SA_Task.sExecutorPersonID<>:" + TaskQuery.SQL_PARAM_PERSONID + ")"
				// 有前驱(非首环节)
				+ " and exists(select 1 from sa_taskrelation r,sa_task p where p.sID=r.sTaskId1 and r.sTaskId2=SA_Task.sID and p.sStatusID<>'"
				+ TaskStatus.RETURNED + "'))";
	}

	/**
	 * 任务执行者条件
	 * 
	 * @param alias
	 * @param org
	 * @param useAgentProcess
	 * @return
	 * @throws NamingException
	 * @throws SQLException
	 */
	private static String getExecutorCondition(String alias, Map<String, Object> vars) {
		// TODO 可以优化减少Hard Parse
		return TaskUtils.getExecutorCondition(alias, ContextHelper.getPerson().getPersonMembers(), false, vars);
	}

	static {
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sID", "sID", "ID", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sEURL", "sEURL", "执行页面", "Text", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sCURL", "sCURL", "创建页面", "Text", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sFlowID", "sFlowID", "流程ID", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sStatusID", "sStatusID", "状态", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sName", "sName", "标题", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sTypeName", "sTypeName", "类型", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sESField07", "sESField07", "签收人", "String", false, false, null);

		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sExecutorFID", "sExecutorFID", "接收人FID", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sExecutorNames", "sExecutorNames", "接收人", "Text", false, false, null);
		// 移交件 必须
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sCreatorFID", "sCreatorFID", "提交人ID", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sCreatorPersonName", "sCreatorPersonName", "提交人", "String", true, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sProcess", "sProcess", "过程URL", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sActivity", "sActivity", "环节ID", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sActivityName", "sActivityName", "环节", "String", false, true, TaskGroup.WAITTING.toString()
				+ TaskGroup.SUBMITED.toString() + TaskGroup.SUSPEND.toString());
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sCreateTime", "sCreateTime", "创建时间", "DateTime", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.sLimitTime", "ActLimit", "环节时限", "DateTime", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task." + TaskExtendRelation.FlowTask_GroupLimit, "GroupLimit", "阶段时限", "DateTime", false, true,
				null);
		// TODO TaskExtendRelation.FlowTask_MobileEnable 仅作为后段过滤，不作为前端(显示、输入查询条件、其他控制性用途)无需添加列信息
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task." + TaskExtendRelation.FlowTask_MobileEnable, "mobileEnable", "启用移动端", "Integer", false, true, null);

		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fBizRecID", "fBizRecID", "案卷编号", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fBizName", "fBizName", "业务名称", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fRecTitle", "fRecTitle", "案卷标题", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fReceiveTime", "fReceiveTime", "收件时间", "DateTime", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fLimitDate", "FlowLimit", "流程时限", "DateTime", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fStatusTime", "fStatusTime", "当前状态时间", "DateTime", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fStatusName", "fStatusName", "案卷状态", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRec.fStatus", "fStatus", "案卷状态ID", "String", false, false, null);
		appendColumnInfo(
				FIELDS_SA_TASK_COLUMNS,
				"decode(B_BizRec.fFinishKind,'fkCertification','发证办结','fkUntread','退回办结','fkAbort','作废办结','fkDelete','删除办结','fkPaper','纸质办结','fkSubmit','转报办结','fkApprizeAbort','补正不来办结','fkNormal','办结','')",
				"fFinishKind", "办结类型", "String", false, true, "HANDLED");
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS,
				"decode(B_BizRec.fSuspendKind,'skApprize','补正告知','skSpecialProcedure','特别程序','skSubmit','转报办结','普通挂起')", "fSuspendKind", "挂起类型",
				"String", false, true, null);

		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.FInComeDocName", "FInComeDocName", "来文名称", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.FInComeDocOrg", "FInComeDocOrg", "来文单位", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.fSerialNo", "fSerialNo", "来文号", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.FArchivesCode", "FArchivesCode", "办文号", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.FMainDept", "FMainDept", "主办部门", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.FMainPerson", "FMainPerson", "主办人", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.FRecPriority", "FRecPriority", "紧急度", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.fKind", "fKind", "业务类型", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_BizRecAttr.fSerialNo", "fSerialNo", "网上受理号", "String", false, false, null);
		// 会办状态(待办、移交 、挂起、经办在办)
		appendColumnInfo(
				FIELDS_SA_TASK_COLUMNS,
				"(select nvl(sum(decode(b1.fstatus,'bsFinished',1,0)),0)||'/'|| count(*) from b_Bizrecrelation r1 join b_bizrec b1 on r1.fbizrecid=b1.fbizrecid where r1.fparentid=B_BizRec.Fbizrecid and b1.fstatus<>'bsAborted')",
				"fHbState", "会办状态", "String", false, false, "WAITTING,SUBMITED,SUSPEND,NOTHANDLE");
		// 当前环节（经办在办）
		appendColumnInfo(
				FIELDS_SA_TASK_COLUMNS,
				"(select wm_concat(concat(concat(t1.sActivityName,':'),nvl(t1.sExecutorPersonName,'无'))) as FDQHJ  from sa_task t1 where t1.sData1=B_BizRec.fbizrecid and t1.sActivityName is not null and t1.sStatusID in ('tesExecuting', 'tesPaused', 'tesReady'))",
				"fCurActs", "当前环节", "String", false, false, "NOTHANDLE");
		// 状态描述(挂起件)
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "to_char(B_BizRec.FSTATUSDESC)", "FSTATUSDESC", "状态描述", "String", false, false, "SUSPEND");

		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "decode(B_AJGQJLB.fGQLX,'skApprize','补正告知','skSpecialProcedure','特别程序','skSubmit','转报办结','挂起')",
				"fGQLX", "挂起类型", "String", false, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_AJGQJLB.fGQYY", "fGQYY", "挂起原因", "String", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_AJGQJLB.fFQSJ", "fFQSJ", "挂起时间", "DateTime", false, false, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "B_AJGQJLB.fSQGQTS", "fSQGQTS", "申请挂起天数", "Integer", false, false, null);

		appendColumnInfo(FIELDS_SA_TASK_COLUMNS,
				"case when B_AJGQJLB.fBizRecId is null then B_BizRec.fRemainingDays else B_AJGQJLB.fRemainingDays end", "FlowAlter", "流程预警",
				"Integer", true, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.SENFIELD12", "ActAlter", "环节预警", "Decimal", true, true, null);
		appendColumnInfo(FIELDS_SA_TASK_COLUMNS, "SA_Task.SENFIELD13", "GroupAlter", "分组预警", "Decimal", true, true, null);
		try {
			initBizGroupDefines();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
