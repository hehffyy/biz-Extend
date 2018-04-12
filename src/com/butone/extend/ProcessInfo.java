package com.butone.extend;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.butone.model.BizLogicPlugin;
import com.butone.utils.ModelExtUtils;
import com.justep.exception.BusinessException;
import com.justep.model.Activity;
import com.justep.model.BusinessActivity;
import com.justep.model.Process;
import com.justep.util.Utils;

public class ProcessInfo implements Serializable {
	private static final Log logger = LogFactory.getLog(ProcessInfo.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 5097329954703167151L;

	/**
	 * 流程事件触发的代码插件 act.getName()/before|after/event
	 */
	private Map<String, List<BizLogicPluginEx>> actEventTriggerLogicPlugins = new HashMap<String, List<BizLogicPluginEx>>();

	/**
	 * 流程事件触发的业务规则 act.getName()/event
	 */
	private Map<String, List<BizRuleExt>> actEventBizRules = new HashMap<String, List<BizRuleExt>>();

	/**
	 * 环节材料权限
	 */
	private Map<String, Map<String, String>> activityMaterialPermissions = new HashMap<String, Map<String, String>>();

	public Map<String, List<BizLogicPluginEx>> getEventTriggerLogicPlugins() {
		return actEventTriggerLogicPlugins;
	}

	private BizInfo bizInfo;

	public ProcessInfo(BizInfo bizInfo, Process process) {
		this.bizInfo = bizInfo;
		initProcessLogicPlugins(process);
		initProcessBizRules(process);
	}

	/**
	 * 获得环节指定事件的业务逻辑列表
	 * 
	 * @param activity
	 * @param when
	 * @param event
	 * @return
	 */
	public List<BizLogicPluginEx> getBizLogicPlugins(String activity, String when, String event) {
		return actEventTriggerLogicPlugins.get(activity + "/" + when + "/" + event);
	}

	public List<BizRuleExt> getBizRules(String activity, String event) {
		return actEventBizRules.get(activity + "/" + event);
	}

	public Map<String, String> getActivityMaterialPermissions(String activity) {
		return activityMaterialPermissions.get(activity);
	}

	/**
	 * 加载process业务逻辑插件
	 */
	private void initProcessLogicPlugins(Process process) {
		for (Activity act : process.getActivities()) {
			if (!BusinessActivity.class.isInstance(act))
				continue;
			List<String> logicPluginURIs = ModelExtUtils.getActivityLogicPluginURIs(act);
			if (logicPluginURIs == null) {
				continue;
			}
			for (String uri : logicPluginURIs) {
				BizLogicPluginEx logicPluginEx = this.bizInfo.getBizLogicPlugin(uri);
				if (logicPluginEx == null)
					continue;
				BizLogicPlugin logicPlugin = logicPluginEx.getBizLogicPlugin();
				if ("EventTrigger".equals(logicPlugin.getTriggerKind())) {
					String[] events = logicPlugin.getTriggerEvents().split(",");
					for (String event : events) {
						if ((event == null) || (event.equals("")))
							continue;
						String eventName = act.getName() + "/" + event;
						List<BizLogicPluginEx> plugins = (List<BizLogicPluginEx>) this.actEventTriggerLogicPlugins.get(eventName);
						if (plugins == null) {
							plugins = new ArrayList<BizLogicPluginEx>();
							this.actEventTriggerLogicPlugins.put(eventName, plugins);
						}
						plugins.add(logicPluginEx);
					}
				}
			}
		}
	}

	/**
	 * 加载process业务规则
	 */
	private void initProcessBizRules(Process process) {
		for (Activity act : process.getActivities()) {
			if (!BusinessActivity.class.isInstance(act))
				continue;

			List<String> bizRuleURIs = ModelExtUtils.getActivityBizRuleURIs(act);
			if (bizRuleURIs == null)
				continue;
			for (String uri : bizRuleURIs) {
				BizRuleExt ext = bizInfo.getBizRule(uri);
				if (Utils.isEmptyString(ext.getBizRule().getTriggerEvents())) {
					throw new BusinessException(ext.getBizRule().getName() + "未设置触发时机");
				}
				String[] events = ext.getBizRule().getTriggerEvents().split(",");
				logger.debug("加载[" + process.getLabel("zh_CN") + "." + act.getLabel("act") + "]业务规则:" + ext.getBizRule().getName() + "[" + events + "]");
				for (String event : events) {
					// activity/event
					String actEvent = act.getName() + "/" + event;
					List<BizRuleExt> rules = actEventBizRules.get(actEvent);
					if (rules == null) {
						rules = new ArrayList<BizRuleExt>();
						actEventBizRules.put(actEvent, rules);
					}
					rules.add(ext);
				}
			}
		}
	}

}
