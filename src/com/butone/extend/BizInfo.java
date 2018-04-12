package com.butone.extend;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import com.butone.model.BizLogicPlugin;
import com.butone.model.BizRule;
import com.butone.model.TableLogicPlugin;
import com.butone.model.resource.MaterialGroup;
import com.butone.xml.JaxbUtils;
import com.justep.filesystem.FileSystemWrapper;
import com.justep.model.Action;
import com.justep.model.Model;
import com.justep.model.ModelObject;
import com.justep.model.ModelUtils;
import com.justep.system.util.BizSystemException;
import com.justep.util.Utils;

/**
 * 流程业务扩展信息
 * 
 * @author Administrator
 * 
 */
public class BizInfo implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3250091102119170869L;
	/**
	 * 表信息
	 */
	private Map<String, TableInfo> tableInfos = new LinkedHashMap<String, TableInfo>();

	/**
	 * 业务逻辑插件
	 */
	private Map<String, BizLogicPluginEx> bizLogicPlugins = new HashMap<String, BizLogicPluginEx>();
	/**
	 * 工作表逻辑插件
	 */
	private Map<String, TableLogicPluginEx> tableLogicPlugins = new HashMap<String, TableLogicPluginEx>();
	/**
	 * 业务规则
	 */
	private Map<String, BizRuleExt> bizRules = new HashMap<String, BizRuleExt>();

	private MaterialGroup materialGroup;

	private Map<String, ProcessInfo> processInfos = new HashMap<String, ProcessInfo>();

	private String bizPath;

	private Set<String> flowTable = new HashSet<String>();

	public BizInfo(String bizPath) {
		this.bizPath = bizPath;
		try {
			initTableInfo();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("加载业务工作表失败");
		}
		try {
			initMaterialGroup();
		} catch (Exception e) {
			throw new RuntimeException("加载业务审批材料失败");
		}

	}

	public boolean isFlowTable(String tableName) {
		return flowTable.contains(tableName);
	}

	/**
	 * 加载审批材料
	 * 
	 * @throws Exception
	 */
	public void initMaterialGroup() throws Exception {

		String url = this.bizPath + "/bizMaterial/materialGroup.xml";
		File file = new File(FileSystemWrapper.instance().getRealPath(url));
		if (file.exists()) {
			FileInputStream in = new FileInputStream(file);
			materialGroup = (MaterialGroup) JaxbUtils.unMarshal(in, "utf-8", MaterialGroup.class);
			in.close();
		}

	}

	private String upperCaseFirstLetter(String name) {
		return name.substring(0, 1).toUpperCase() + name.substring(1);
	}

	private String getDefaultCreateActionName(String coneptName) {
		return "create" + upperCaseFirstLetter(coneptName) + "Action";
	}

	private String getDefaultQueryActionName(String coneptName) {
		return "query" + upperCaseFirstLetter(coneptName) + "Action";
	}

	private String getDefaultSaveActionName(String coneptName) {
		return "save" + upperCaseFirstLetter(coneptName) + "Action";
	}

	/**
	 * 获得table对应的Action
	 */
	private Map<String, String> getTableActions(String tableName) {
		Map<String, String> actions = new HashMap<String, String>();
		String url = this.bizPath + "/logic/action";
		Model dataModel = ModelUtils.getModel(url);
		// query
		String actionName = getDefaultQueryActionName(tableName);
		ModelObject obj = dataModel.getLocalObject(actionName, Action.TYPE);
		if (obj != null)
			actions.put("query", obj.getName());
		// save
		actionName = getDefaultSaveActionName(tableName);
		obj = dataModel.getLocalObject(actionName, Action.TYPE);
		if (obj != null)
			actions.put("save", obj.getName());
		// create
		actionName = getDefaultCreateActionName(tableName);
		obj = dataModel.getLocalObject(actionName, Action.TYPE);
		if (obj != null)
			actions.put("create", obj.getName());

		return actions;
	}

	/**
	 * 初始化工作表信息
	 * 
	 * @param bizInfo
	 * @param process
	 */
	public void initTableInfo() throws Exception {
		tableInfos.clear();
		// 默认动作【获取process所属模块类型是Concept的元素】
		SAXReader reader = new SAXReader();
		Element root = reader.read(new File(FileSystemWrapper.instance().getRealPath(bizPath + "/ontology/.tables.xml"))).getRootElement();
		@SuppressWarnings("unchecked")
		List<Node> rootTables = root.selectNodes("table");
		for (Node n : rootTables) {
			Element table = (Element) n;
			TableInfo tableInfo = parseTableInfo(table, null);
			if (Utils.isNotEmptyString(tableInfo.getForeignKeys())) {
				flowTable.add(tableInfo.getConcept());
			}
		}
	}

	private TableInfo parseTableInfo(Element element, String masterTable) {
		String conceptName = element.attributeValue("concept");
		if (Utils.isEmptyString(conceptName)) {
			conceptName = element.attributeValue("name");
		}
		String foreignKeys = element.attributeValue("foreignKeys");
		String cascade = element.attributeValue("cascade");
		// 工作表信息
		TableInfo tableInfo = new TableInfo();
		tableInfo.setName(element.attributeValue("name")); // 设置概念对应的表名
		tableInfo.setConcept(conceptName); // 设置概念名
		tableInfo.setMasterTable(masterTable);
		tableInfo.setForeignKeys(foreignKeys);
		tableInfo.setCascade(cascade);

		// 获取概念(扩展属性)的Actions

		Map<String, String> actions = getTableActions(tableInfo.getName());
		if (actions != null) {
			tableInfo.setQueryAction(actions.get("query"));// 设置Actions
			tableInfo.setSaveAction(actions.get("save"));
			tableInfo.setCreateAction(actions.get("create"));
		}
		// [添加工作表信息]
		tableInfos.put(tableInfo.getName(), tableInfo);
		@SuppressWarnings("unchecked")
		List<Node> subTables = element.selectNodes("table");
		for (Node n : subTables) {
			Element subTable = (Element) n;
			parseTableInfo(subTable, tableInfo.getName());
		}
		return tableInfo;
	}

	public String getBizPath() {
		return bizPath;
	}

	public boolean containsTable(String name) {
		return tableInfos.containsKey(name);
	}

	public TableInfo getTableInfo(String tableName) {
		return tableInfos.get(tableName);
	}

	public Collection<TableInfo> getTableInfos() {
		return Collections.unmodifiableCollection(tableInfos.values());
	}

	public MaterialGroup getMaterialGroup() {
		return materialGroup;
	}

	public void setMaterialGroup(MaterialGroup materialGroup) {
		this.materialGroup = materialGroup;
	}

	public ProcessInfo loadProcessInfo(com.justep.model.Process process) {
		ProcessInfo processInfo = this.processInfos.get(process.getFullName());
		if (processInfo == null) {
			processInfo = new ProcessInfo(this, process);
			this.processInfos.put(process.getFullName(), processInfo);
		}
		return processInfo;
	}

	public void removeProcessInfo(String process) {
		this.processInfos.remove(process);
	}

	/**
	 * 获得业务规则
	 * 
	 * @param uri
	 * @return
	 */
	public BizRuleExt getBizRule(String uri) {
		String bizPath = uri.substring(0, uri.indexOf("/bizRule/"));
		if (bizPath.equals(this.getBizPath())) {
			BizRuleExt ruleExt = this.bizRules.get(uri);
			if (ruleExt == null) {
				try {
					FileInputStream in = new FileInputStream(new File(FileSystemWrapper.instance().getRealPath(uri)));
					ruleExt = new BizRuleExt((BizRule) JaxbUtils.unMarshal(in, "utf-8", BizRule.class));
					in.close();
					this.bizRules.put(uri, ruleExt);

				} catch (FileNotFoundException e) {
					throw new BizSystemException("业务规则" + uri + "未发布");
				} catch (Exception e) {
					throw new BizSystemException("规则文件" + uri + "加载异常", e);
				}
			}
			return ruleExt;
		} else {
			return CacheManager.getBizInfo(bizPath).getBizRule(uri);
		}
	}

	/**
	 * 获得业务逻辑插件
	 * 
	 * @param uri
	 * @return
	 */
	public BizLogicPluginEx getBizLogicPlugin(String uri) {
		String bizPath = uri.substring(0, uri.indexOf("/logicPlugin/"));
		if (bizPath.equals(this.getBizPath())) {
			BizLogicPluginEx logicPluginEx = bizLogicPlugins.get(uri);
			if (logicPluginEx == null) {
				try {
					FileInputStream in = new FileInputStream(FileSystemWrapper.instance().getRealPath(uri));
					BizLogicPlugin plugin = (BizLogicPlugin) JaxbUtils.unMarshal(in, "utf-8", BizLogicPlugin.class);
					in.close();
					if (Utils.isEmptyString(plugin.getParameter()))
						return null;
					logicPluginEx = new BizLogicPluginEx(plugin);
					bizLogicPlugins.put(uri, logicPluginEx);
				} catch (Exception e) {
					throw new BizSystemException("业务组件未发布:" + uri);
				}
			}
			return logicPluginEx;
		} else {
			BizInfo bizInfo = CacheManager.getBizInfo(bizPath);
			return bizInfo.getBizLogicPlugin(uri);
		}

	}

	/**
	 * 获得工作表逻辑插件
	 * 
	 * @param uri
	 * @return
	 */
	public TableLogicPluginEx getTableLogicPlugin(String uri) {
		String bizPath = uri.substring(0, uri.indexOf("/tablePlugin/"));
		if (bizPath.equals(this.getBizPath())) {
			TableLogicPluginEx logicPluginEx = tableLogicPlugins.get(uri);
			if (logicPluginEx == null) {
				try {
					FileInputStream in = new FileInputStream(FileSystemWrapper.instance().getRealPath(uri));
					TableLogicPlugin plugin = (TableLogicPlugin) JaxbUtils.unMarshal(in, "utf-8", TableLogicPlugin.class);
					in.close();
					logicPluginEx = new TableLogicPluginEx(plugin);
					tableLogicPlugins.put(uri, logicPluginEx);
				} catch (Exception e) {
					throw new BizSystemException("工作表组件未发布:" + uri);
				}
			}
			return logicPluginEx;
		} else {
			BizInfo bizInfo = CacheManager.getBizInfo(bizPath);
			return bizInfo.getTableLogicPlugin(uri);
		}

	}

}
