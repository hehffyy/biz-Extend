package com.butone.utils;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.logic.impl.BizExprHelper;
import com.butone.x5Impl.ModelObjectImpl;
import com.justep.model.Action;
import com.justep.model.Activity;
import com.justep.model.Concept;
import com.justep.model.Listener;
import com.justep.model.Mapping;
import com.justep.model.Model;
import com.justep.model.ModelObject;
import com.justep.model.ModelUtils;
import com.justep.model.Process;
import com.justep.model.Relation;
import com.justep.util.Utils;

public class ModelExtUtils {
	public static final String MODEL_EXT_URI = "http://www.butone.com";
	public static final String EXT_LimitDays = "limitDays";
	public static final String EXT_LimitKind = "limitKind";
	public static final String EXT_LimitDateExpr = "limitDateExpr";

	public static final String Process_isBizCoop = "isBizCoop";
	public static final String Process_finishKind = "finishKind";

	public static final String Process_includeReceivers = "includeReceivers";
	public static final String Process_coopProcess = "coopProcess";
	public static final String Process_bizId = "bizId";
	public static final String Process_bizName = "bizName";
	public static final String Process_bizOrgLevel = "bizOrgLeve";
	public static final String Process_codeDefines = "codeDefines";
	public static final String Process_viewActivity = "viewActivity";
	public static final String Process_activityGroup = "activityGroup";
	public static final String Process_limitEffectActivity = "limitEffectActivity";
	public static final String Process_limitStartDateExpr = "limitStartDateExpr";
	public static final String Process_silenceFinish = "silenceFinish";
	public static final String Process_flowViewActivity = "flowViewActivity";
	public static final String Process_oldExecutorDialog = "oldExecutorDialog";

	//

	public static final String Activity_approveEntryProcess = "approveEntryProcess";
	public static final String Activity_sendToCoopProcesses = "sendToCoopProcesses";
	public static final String Activity_batchOperationOption = "batchOperationOption";
	public static final String Activity_bizOperationForms = "bizOperationForms";
	public static final String Activity_asyncAdvance = "asyncAdvance";

	public static final String Activity_BizOperations = "bizOperations";
	public static final String Activity_BizRules = "bizRules";
	public static final String Activity_LogicPlugins = "logicPlugins";
	public static final String Activity_UiLogicPlugins = "uiLogicPlugins";
	public static final String Activity_isCoopReceiver = "isCoopReceiver";
	public static final String Activity_codeFields = "codeFields";
	private static final String Activity_group = "group";
	private static final String Activity_controlTable = "controlTable";
	private static final String Activity_forms = "forms";
	private static final String Activity_multiTransferRule = "multiTransferRule";
	private static final String Activity_ForbidWithdraw = "forbidWithdraw";
	// 启用移动端
	private static final String Activity_MobileEnable = "mobileEnable";

	//
	public static final String HasRelation_taskField = "taskField";
	public static final String HasRelation_queryField = "queryField";

	//
	public static final String HasRelation_unitType = "unitType";
	public static final String HasRelation_codeDef = "codeDef";

	public static final String Listener_pluginUrls = "pluginUrls";

	public static final String Action_signDataFields = "signDataFields";

	public static final String ProcessRule_label = "label";

	/**
	 * 判断是否业务协同过程
	 * 
	 * @param p
	 * @return
	 */
	public static boolean isBizCoopProcess(Process p) {

		Boolean isBizCoop = (Boolean) p.getExtAttributeValue(MODEL_EXT_URI, Process_isBizCoop);
		return isBizCoop != null && isBizCoop.booleanValue();
	}

	public static String getProcessFinishKind(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_finishKind);
	}

	public static boolean isIncludeReceiveActivity(Process p) {
		Boolean includeReceivers = (Boolean) p.getExtAttributeValue(MODEL_EXT_URI, Process_includeReceivers);
		return includeReceivers != null && includeReceivers.booleanValue();
	}

	public static boolean isSilenceFinish(Process p) {
		Boolean silenceFinish = (Boolean) p.getExtAttributeValue(MODEL_EXT_URI, Process_silenceFinish);
		return silenceFinish != null && silenceFinish.booleanValue();
	}

	/**
	 * 流程限办生效环节，如果为空流程启动后立即计算，否则在指定环节流转后计算
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessLimitEffectActivity(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_limitEffectActivity);
	}

	/**
	 * 流程限办开始时间表达式
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessLimitStartDateExpr(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_limitStartDateExpr);
	}

	/**
	 * 获得过程业务ID
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessBizId(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_bizId);
	}

	/**
	 * 获得过程业务名称
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessBizName(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_bizName);
	}

	/**
	 * 获得业务的机构级别
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessBizOrgLevel(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_bizOrgLevel);
	}

	/**
	 * 流程实例浏览环节
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessFlowViewActivity(Process p) {
		return (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_flowViewActivity);
	}

	/**
	 * 获得业务的机构级别
	 * 
	 * @param p
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> getProcessCodeDefines(Process p) {
		return (Map<String, String>) p.getExtAttributeValue(MODEL_EXT_URI, Process_codeDefines);
	}

	/**
	 * 获取浏览环节
	 * 
	 * @param p
	 * @return
	 */
	public static String getProcessViewActivity(Process p) {
		return p.getExtAttributeValue(MODEL_EXT_URI, Process_viewActivity).toString();
	}

	/**
	 * 获得业务协同过程
	 * 
	 * @param p
	 * @return
	 */
	public static JSONObject getProcessCoopProcess(Process p) {
		String coopProcess = (String) p.getExtAttributeValue(MODEL_EXT_URI, Process_coopProcess);
		if (Utils.isNotEmptyString(coopProcess)) {
			return JSON.parseObject(coopProcess);
		}
		return null;
	}

	/**
	 * 获得业务环节分定义
	 * 
	 * @param p
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> getProcessActivityGroup(Process p) {
		return (Map<String, String>) p.getExtAttributeValue(MODEL_EXT_URI, Process_activityGroup);
	}

	/**
	 * 获得流程的限办天数属性
	 * 
	 * @param p
	 * @return
	 */
	public static BigDecimal getModelObjectLimitDays(ModelObject model) {
		Object obj = model.getExtAttributeValue(MODEL_EXT_URI, EXT_LimitDays);
		return obj == null ? null : new BigDecimal(obj.toString());
	}

	/**
	 * 获得流程的限办天数类型
	 * 
	 * @param p
	 * @return
	 */
	public static String getModelObjectLimitKind(ModelObject model) {
		return (String) model.getExtAttributeValue(MODEL_EXT_URI, EXT_LimitKind);
	}

	/**
	 * 获得流程的限办日期表达式
	 * 
	 * @param p
	 * @return
	 */
	public static String getModelObjectLimitDateExpr(ModelObject model) {
		return (String) model.getExtAttributeValue(MODEL_EXT_URI, EXT_LimitDateExpr);
	}

	/**
	 * 获得业务逻辑插件(URI列表)
	 * 
	 * @param process
	 * @return
	 */
	public static List<String> getActivityLogicPluginURIs(Activity act) {
		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) act.getExtAttributeValue(MODEL_EXT_URI, Activity_LogicPlugins);
		return result;
	}

	/**
	 * 获得业务UI插件(URI列表)
	 * 
	 * @param process
	 * @return
	 */
	public static JSONObject getActivityUiLogicPluginURIs(Activity act) {
		Object obj = act.getExtAttributeValue(MODEL_EXT_URI, Activity_UiLogicPlugins);
		if (obj != null) {
			if (obj instanceof Map)
				return (JSONObject) obj;
			else if (obj instanceof List) {
				JSONObject ret = new JSONObject();
				JSONArray uiLogicPlugins = new JSONArray();
				List<?> uiLogicText = (List<?>) obj;
				Set<String> tableNames = new HashSet<String>();
				for (Object item : uiLogicText) {
					JSONObject json = JSON.parseObject(item.toString());
					if (json.containsKey("visibleExpr")) {
						String visibleExpr = json.getString("visibleExpr");
						tableNames.addAll(BizExprHelper.parseObjectIdOfTableFunction(visibleExpr));
					}
					uiLogicPlugins.add(json);
				}
				ret.put("dependBizTables", new JSONArray((List) new ArrayList<String>(tableNames)));
				ret.put("uiLogicPlugins", uiLogicPlugins);
				new ModelObjectImpl(act).setExtAttributeValue(MODEL_EXT_URI, Activity_UiLogicPlugins, ret);
				return ret;
			}
		}
		return null;
	}

	public static List<String> getActivityBizOperations(Activity act) {
		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) act.getExtAttributeValue(MODEL_EXT_URI, Activity_BizOperations);
		return result;
	}

	/**
	 * 获得业务规则(URI列表)
	 * 
	 * @param process
	 * @return
	 */
	public static List<String> getActivityBizRuleURIs(Activity act) {
		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) act.getExtAttributeValue(MODEL_EXT_URI, Activity_BizRules);
		return result;
	}

	/**
	 * 获得业务协作的环节对应的业务审批的入口流程
	 * 
	 * @param act
	 * @return
	 */
	public static JSONObject getActivityApproveEntryProcess(Activity act) {
		String approveEntryProcess = (String) act.getExtAttributeValue(MODEL_EXT_URI, Activity_approveEntryProcess);
		if (approveEntryProcess != null) {
			return JSON.parseObject(approveEntryProcess);
		}
		return null;
	}

	/**
	 * 流程协同，发送到目标process的目标环节
	 * 
	 * @param act
	 * @return
	 */
	public static JSONArray getActivitySendToCoopProcesses(Activity act) {
		String sendToCoopProcesses = (String) act.getExtAttributeValue(MODEL_EXT_URI, Activity_sendToCoopProcesses);
		if (sendToCoopProcesses != null) {
			return JSON.parseArray(sendToCoopProcesses);
		}
		return null;
	}

	public static Map<String, Object> getActivityBatchOperationOption(Activity act) {
		return (Map<String, Object>) act.getExtAttributeValue(MODEL_EXT_URI, Activity_batchOperationOption);
	}

	/**
	 * 业务操作表单
	 * 
	 * @param act
	 * @return
	 */
	public static Map<String, Object> getActivityBizOperationForms(Activity act) {
		return (Map<String, Object>) act.getExtAttributeValue(MODEL_EXT_URI, Activity_bizOperationForms);
	}

	/**
	 * 判断是是否流程协同的接收环节
	 * 
	 * @param p
	 * @return
	 */
	public static boolean isFlowCoopReceiveActivity(Activity act) {
		Boolean isReceiver = (Boolean) act.getExtAttributeValue(MODEL_EXT_URI, Activity_isCoopReceiver);
		return isReceiver != null && isReceiver.booleanValue();
	}

	public static String getActivityCodeFields(Activity act) {
		String codeFields = (String) act.getExtAttributeValue(MODEL_EXT_URI, Activity_codeFields);
		return codeFields;
	}

	public static JSONObject getActivityGroup(Activity act) {
		String groupId = (String) act.getExtAttributeValue(MODEL_EXT_URI, Activity_group);
		if (Utils.isNotEmptyString(groupId)) {
			Map<String, String> define = getProcessActivityGroup(act.getOwner().getProcess());
			if (define.containsKey(groupId)) {
				JSONObject ret = JSON.parseObject(define.get(groupId));
				return ret;
			}

		}
		return null;
	}

	public static String getActivityControlTable(Activity act) {
		return (String) act.getExtAttributeValue(MODEL_EXT_URI, Activity_controlTable);
	}

	public static boolean isActivityMultiTransferRule(Activity act) {
		return Boolean.TRUE.equals(act.getExtAttributeValue(MODEL_EXT_URI, Activity_multiTransferRule));
	}

	public static boolean isActivityForbidWithdraw(Activity act) {
		return Boolean.TRUE.equals(act.getExtAttributeValue(MODEL_EXT_URI, Activity_ForbidWithdraw));
	}

	public static JSONObject getActivityForms(Activity act) {
		Object obj = act.getExtAttributeValue(MODEL_EXT_URI, Activity_forms);
		if (obj != null) {
			if (obj instanceof JSONObject)
				return (JSONObject) obj;
			else if (obj instanceof List) {
				JSONObject ret = new JSONObject();
				JSONArray forms = new JSONArray();
				List<?> formsText = (List<?>) obj;
				Set<String> tableNames = new HashSet<String>();
				for (Object item : formsText) {
					JSONObject json = JSON.parseObject(item.toString());
					if (json.containsKey("visibleExpr")) {
						String visibleExpr = json.getString("visibleExpr");
						tableNames.addAll(BizExprHelper.parseObjectIdOfTableFunction(visibleExpr));
					}
					forms.add(json);
				}
				ret.put("dependBizTables", new JSONArray((List) new ArrayList<String>(tableNames)));
				ret.put("forms", forms);
				new ModelObjectImpl(act).setExtAttributeValue(MODEL_EXT_URI, Activity_forms, ret);
				return ret;
			}
		}
		return null;
	}

	/**
	 * 是否任务中心字段
	 * 
	 * @param relation
	 * @return
	 */
	public static boolean isTaskCenterField(Relation relation) {
		return Boolean.TRUE.equals(relation.getExtAttributeValue(MODEL_EXT_URI, HasRelation_taskField));
	}

	/**
	 * 是否案卷查询字段
	 * 
	 * @param relation
	 * @return
	 */
	public static boolean isRecordField(Relation relation) {
		return Boolean.TRUE.equals(relation.getExtAttributeValue(MODEL_EXT_URI, HasRelation_queryField));
	}

	/**
	 * 是否旧版执行者选择框
	 * 
	 * @param p
	 * @return
	 */
	public static boolean isOldExecutorDialog(Process p) {
		return Boolean.TRUE.equals(p.getExtAttributeValue(MODEL_EXT_URI, Process_oldExecutorDialog));
	}

	/**
	 * 获得编码定义GUID
	 * 
	 * @param relation
	 * @return
	 */
	public static String getCodeDef(Relation relation) {
		return (String) relation.getExtAttributeValue(MODEL_EXT_URI, HasRelation_codeDef);
	}

	/**
	 * 获得监听的插件url
	 * 
	 * @param concept
	 * @return
	 */
	public static String getTableName(Concept concept) {
		File file = new File(concept.getFullName());
		String url = file.getParentFile().getParent() + "/data";
		url = url.replace('\\', '/');
		Model dataModel = ModelUtils.getModel(url);
		List<ModelObject> modelObjects = dataModel.getLocalObjectsByType(Mapping.TYPE);
		for (ModelObject obj : modelObjects) {
			Mapping mapping = (Mapping) obj;
			if (mapping.getConcept().equals(concept)) {
				return mapping.getPrimaryTable().getName();
			}
		}
		return concept.getName().toString();
	}

	public static String getListenerPluginUrls(Listener listener) {
		return (String) listener.getExtAttributeValue(MODEL_EXT_URI, Listener_pluginUrls);
	}

	public static String getActionSignDataFields(Action action) {
		return (String) action.getExtAttributeValue(MODEL_EXT_URI, Action_signDataFields);
	}

	public static boolean isAsyncAdvanceActivity(Activity activity) {
		return Boolean.TRUE.equals(activity.getExtAttributeValue(MODEL_EXT_URI, Activity_asyncAdvance));
	}
	
	public static boolean isMobileEnableActivity(Activity activity) {
		return Boolean.TRUE.equals(activity.getExtAttributeValue(MODEL_EXT_URI, Activity_MobileEnable));
	}

	public static String getProcessRuleLabel(ModelObject rule) {
		return (String) rule.getExtAttributeValue(MODEL_EXT_URI, ProcessRule_label);
	}

}
