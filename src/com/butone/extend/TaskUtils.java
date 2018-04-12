package com.butone.extend;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.butone.flowbiz.FlowBizConsts;
import com.justep.common.SystemUtils;
import com.justep.exception.BusinessException;
import com.justep.message.SystemMessages;
import com.justep.model.Config;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.model.Relation;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.DatabaseProduct;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.SQL;
import com.justep.system.data.Table;
import com.justep.system.opm.OrgNode;
import com.justep.system.opm.OrgUtils;
import com.justep.system.opm.PersonMember;
import com.justep.system.process.Task;
import com.justep.system.util.BizSystemException;
import com.justep.util.Utils;

public class TaskUtils {
	/**
	 * 获得执行者条件，拼人员成员SQL，可能导致SQL超长
	 * 
	 * @param alias
	 * @param pms
	 * @param useAgentProcess
	 * @return
	 */
	@Deprecated
	public static String getExecutorCondition(String alias, Collection<PersonMember> pms, boolean useAgentProcess) {
		if (isOracle()) {
			Set<String> fids = new HashSet<String>();
			for (PersonMember pm : pms) {
				fids.add(pm.getFID());
			}
			insertIntoExecutorFID(fids);
			return alias + ".sExecutorFID in (select hfid.FID from Helper_ExecutorFID hfid)";
		} else {
			String result = "";
			List<String> items = new ArrayList<String>();
			Set<String> pfids = new HashSet<String>();
			for (PersonMember pm : pms) {
				items.add(alias + ".sExecutorFID='" + pm.getFID() + "'");
				OrgNode p = pm.getParent();
				while (p != null && !pfids.contains(p.getFID())) {
					pfids.add(p.getFID());
					items.add(alias + ".sExecutorFID='" + p.getFID() + "'");
					p = p.getParent();
				}
			}

			if (items.isEmpty()) {
				result = "1<>1";
			} else {
				for (String item : items) {
					if (result.equals("")) {
						result = item;
					} else {
						result = result + " or " + item;
					}
				}
				result = "(" + result + ")";
			}
			return result;
		}

	}

	private static boolean isOracle() {
		try {
			return DatabaseProduct.ORACLE.equals(DatabaseProduct.getProduct(ContextHelper.getTransaction().getConnection(
					FlowBizConsts.DATA_MODEL_CORE_FLOW)));
		} catch (Exception e) {
			throw new BusinessException("", e.getCause());
		}
	}

	/**
	 * 获得执行者条件，宿主参数模式
	 * 
	 * @param alias
	 * @param pms
	 * @param useAgentProcess
	 * @param vars
	 * @return
	 */
	public static String getExecutorCondition(String alias, Collection<PersonMember> pms, boolean useAgentProcess, Map<String, Object> vars) {
		if (taskExecutorOnlyPerson()) {
			return getExecutorConditionWithPerson(alias, pms, useAgentProcess, vars);
		}

		boolean isOracle = isOracle();
		if (isOracle) {
			Set<String> fids = new HashSet<String>();
			for (PersonMember pm : pms) {
				fids.add(pm.getFID());
			}
			insertIntoExecutorFID(fids);
			return alias + ".sExecutorFID in (select hfid.FID from Helper_ExecutorFID hfid)";
		} else {
			String result = "";
			List<String> items = new ArrayList<String>();
			int i = 0;
			Set<String> pfids = new HashSet<String>();
			for (PersonMember pm : pms) {
				String var = "_efid" + i++;
				vars.put(var, pm.getFID());
				OrgNode p = pm.getParent();
				while (p != null && !pfids.contains(p.getFID())) {
					pfids.add(p.getFID());
					String varP = "_epfid" + i++;
					vars.put(varP, p.getFID());
					items.add(alias + ".sExecutorFID=:" + varP);
					p = p.getParent();
				}
				items.add(alias + ".sExecutorFID=:" + var);
			}
			if (items.isEmpty()) {
				result = "1=0";
			} else {
				for (String item : items) {
					if (result.equals("")) {
						result = item;
					} else {
						result = result + " or " + item;
					}
				}
				result = "(" + result + ")";
			}
			return result;
		}

	}

	public static boolean taskExecutorOnlyPerson() {
		boolean result = false;
		Model m = ModelUtils.getModel("/system/config");
		if (Utils.isNotNull(m)) {
			Config cfg = m.getUseableConfig("taskExecutorOnlyPerson");
			if (Utils.isNotNull(cfg))
				result = cfg.getValue().trim().equalsIgnoreCase("true");
		}
		return result;
	}

	public static boolean isForceIndex() {
		boolean result = false;
		Model m = ModelUtils.getModel("/system/config");
		if (Utils.isNotNull(m)) {
			Config cfg = m.getUseableConfig("forceIndex");
			if (Utils.isNotNull(cfg))
				result = cfg.getValue().trim().equalsIgnoreCase("true");
		}
		return result;
	}

	private static void insertIntoExecutorFID(Collection<String> fids) {
		String insert = "insert all";
		List<Object> binds = new ArrayList<Object>();
		for (String fID : fids) {
			do {
				if (binds.contains(fID)) {
					break;
				} else {
					binds.add(fID);
					fID = fID.substring(0, fID.lastIndexOf("/"));
				}
			} while (fID.lastIndexOf("/") >= 0);
		}
		for (int i = 0; i < binds.size(); i++) {
			insert += "\ninto Helper_ExecutorFID values(?)";
		}
		insert += "\nselect 1 from dual";
		Map<String, String> sqls = new HashMap<String, String>();
		sqls.put(DatabaseProduct.ORACLE.name(), insert);
		SQL.executeUpdate(sqls, binds, ModelUtils.getModel(FlowBizConsts.DATA_MODEL_CORE_FLOW));
	}

	private static String getExecutorConditionWithPerson(String alias, Collection<PersonMember> pms, boolean useAgentProcess, Map<String, Object> vars) {
		// 代理时，权限放大了，代理了所有人员成员
		Set<String> ids = new HashSet<String>();
		Set<String> fids = new HashSet<String>();
		boolean isOracle = isOracle();
		for (PersonMember pm : pms) {
			if (isOracle) {
				fids.add(pm.getFID());
			} else {
				String id = OrgUtils.getPersonIDByFID(pm.getFID());
				ids.add(id);
			}
		}
		if (isOracle) {
			insertIntoExecutorFID(fids);
			return alias + ".sExecutorFID in (select hfid.FID from Helper_ExecutorFID hfid)";
		} else {
			String result = "";
			if (!ids.isEmpty()) {
				int i = 0;
				for (String id : ids) {
					String var = "_pid" + i++;
					vars.put(var, id);
					if (SystemUtils.isNotEmptyString(result))
						result += " or ";
					result += " " + alias + ".sExecutorPersonID=:" + var;
				}
				result = " (" + result + ") ";
			}
			return result;
		}
	}

	@Deprecated
	private static String getAgentProcessCondition(String alias, String agentProcess) {
		try {
			String result = "";
			List<String> items = new ArrayList<String>();
			SAXReader reader = new SAXReader();
			Document doc = reader.read(new ByteArrayInputStream(agentProcess.getBytes("UTF-8")));
			if (doc.getRootElement() != null) {
				for (Object item : doc.getRootElement().elements()) {
					String type = ((Element) item).getName();
					String value = ((Element) item).getTextTrim();
					if (type.equals("m")) {
						items.add(alias + ".sProcess like '" + value + "%'");

					} else if (type.equals("r")) {
						for (String processActivity : ContextHelper.getOperator().getPermissionByRoleID(value)) {
							String process = processActivity.substring(0, processActivity.indexOf(",")).trim();
							String activity = processActivity.substring(processActivity.indexOf(",") + 1).trim();
							items.add(alias + ".sProcess = '" + process + "' and " + alias + ".sActivity = '" + activity + "'");
						}

					} else if (type.equals("p")) {
						items.add(alias + ".sProcess = '" + value + "'");

					} else if (type.equals("a")) {
						String activity = value.substring(value.lastIndexOf("/") + 1).trim();
						String process = getProcess(value.substring(0, value.lastIndexOf("/")));
						if ("*".equals(activity)) {
							items.add(alias + ".sProcess = '" + process + "'");

						} else {
							items.add(alias + ".sProcess = '" + process + "' and " + alias + ".sActivity = '" + activity + "'");
						}

					} else {
						throw BizSystemException.create(SystemMessages.TYPE_OF_AGENT1, type);
					}
				}
			}

			if (items.isEmpty()) {
				result = "1=1";
			} else {
				for (String item : items) {
					if (result.equals("")) {
						result = item;
					} else {
						result = result + " or " + item;
					}
				}
			}

			return result;
		} catch (UnsupportedEncodingException e) {
			throw BizSystemException.create(e, SystemMessages.SET_AGENT_ERROR1, agentProcess);
		} catch (DocumentException e) {
			throw BizSystemException.create(e, SystemMessages.SET_AGENT_ERROR1, agentProcess);
		}
	}

	private static String getAgentProcessCondition(String alias, String agentProcess, Map<String, Object> vars) {
		try {
			String result = "";
			List<String> items = new ArrayList<String>();
			SAXReader reader = new SAXReader();
			Document doc = reader.read(new ByteArrayInputStream(agentProcess.getBytes("UTF-8")));
			if (doc.getRootElement() != null) {
				int i = 0;
				for (Object item : doc.getRootElement().elements()) {
					i++;

					String type = ((Element) item).getName();
					String value = ((Element) item).getTextTrim();
					if (type.equals("m")) {
						String var = "_process" + i;
						vars.put(var, value + "%");
						items.add(alias + ".sProcess like :" + var);

					} else if (type.equals("r")) {
						int j = 0;
						for (String processActivity : ContextHelper.getOperator().getPermissionByRoleID(value)) {
							String process = processActivity.substring(0, processActivity.indexOf(",")).trim();
							String activity = processActivity.substring(processActivity.indexOf(",") + 1).trim();

							j++;
							String var = "_r" + j + "process" + i;
							String var2 = "r" + j + "activity" + i;

							vars.put(var, process);
							vars.put(var2, activity);
							items.add(alias + ".sProcess = :" + var + " and " + alias + ".sActivity = :" + var2);
						}

					} else if (type.equals("p")) {
						String var = "_process" + i;
						vars.put(var, value);
						items.add(alias + ".sProcess = :" + var);

					} else if (type.equals("a")) {
						String activity = value.substring(value.lastIndexOf("/") + 1).trim();
						String process = getProcess(value.substring(0, value.lastIndexOf("/")));
						if ("*".equals(activity)) {
							String var = "_process" + i;
							vars.put(var, process);
							items.add(alias + ".sProcess = :" + var);

						} else {
							String var = "_process" + i;
							vars.put(var, process);

							String var2 = "_activity" + i;
							vars.put(var2, activity);
							items.add(alias + ".sProcess = :" + var + " and " + alias + ".sActivity = :" + var2);
						}

					} else {
						throw BizSystemException.create(SystemMessages.TYPE_OF_AGENT1, type);
					}
				}
			}

			if (items.isEmpty()) {
				result = "1=1";
			} else {
				for (String item : items) {
					if (result.equals("")) {
						result = item;
					} else {
						result = result + " or " + item;
					}
				}
				result = "(" + result + ")";
			}

			return result;
		} catch (UnsupportedEncodingException e) {
			throw BizSystemException.create(e, SystemMessages.SET_AGENT_ERROR1, agentProcess);
		} catch (DocumentException e) {
			throw BizSystemException.create(e, SystemMessages.SET_AGENT_ERROR1, agentProcess);
		}
	}

	public static String getCreatorCondition(String alias, Collection<PersonMember> pms, boolean useAgentProcess, Map<String, Object> vars) {
		String result = "";
		List<String> items = new ArrayList<String>();

		boolean isOperator = true;
		for (PersonMember pm : pms) {
			if (!pm.getPerson().getID().equals(ContextHelper.getOperator().getID())) {
				isOperator = false;
				break;
			}
		}

		if (isOperator) {
			items.add(alias + ".sCreatorPersonID=:_cpid");
			vars.put("_cpid", ContextHelper.getOperator().getID());
		} else {
			int i = 0;
			for (PersonMember pm : pms) {
				String var = "_cfid" + i++;
				String item = alias + ".sCreatorFID = :" + var;
				vars.put(var, pm.getFID());
				items.add(item);
			}
		}

		if (items.isEmpty()) {
			result = "1<>1";
		} else {
			for (String item : items) {
				if (result.equals("")) {
					result = item;
				} else {
					result = result + " or " + item;
				}
			}

			result = "(" + result + ")";
		}

		return result;
	}

	private static String getProcess(String path) {
		String name = path.substring(path.lastIndexOf("/") + 1);
		name = Character.toLowerCase(name.charAt(0)) + name.substring(1) + "Process";
		return path + "/" + name;
	}

	// private static HashSet<String> taskRelations = new HashSet<String>();
	private static String taskRelations = "";

	public static Task loadTask(String id) {
		String sql = "select " + "t" + taskRelations + " from SA_Task t where t=:task";
		HashMap<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("task", id);
		Table table = KSQL.select(sql, varMap, "/system/data", null);
		if (table.size() > 0) {
			Row row = table.iterator().next();
			Task ret = new Task();

			Iterator<ColumnMetaData> i = table.getMetaData().getColumnMetaDatas().iterator();
			while (i.hasNext()) {
				ColumnMetaData col = i.next();
				String name = col.getName();
				if ("t".equals(name))
					ret.setId(row.getString("t"));
				else if ("sSequence".equals(name)) {
					Object value = row.getValue("sSequence");
					ret.setSequence((value instanceof Integer) ? ((Integer) value).intValue() : 0);
				} else if ("sResponsible".equals(name)) {
					Object value = row.getValue("sResponsible");
					ret.setReponsible(value == null ? false : "true".equalsIgnoreCase((String) value));
				} else if ((!"version".equals(name)) && (!"sParent".equals(name))) {
					ret.setRelationValue(name, row.getValue(name));
				}
			}
			ret.reset();
			return ret;
		}
		return null;
	}

	static {
		Iterator<Relation> i = ModelUtils.getModel("/system/ontology").getUseableConcept("SA_Task").getRelations().iterator();
		while (i.hasNext()) {
			Relation relation = i.next();
			if (relation.isSingleValued())
				taskRelations += ",t." + relation.getName();
		}
	}

}
