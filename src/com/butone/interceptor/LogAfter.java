package com.butone.interceptor;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.justep.model.Action;
import com.justep.system.action.Interceptor;
import com.justep.system.context.ActionContext;
import com.justep.system.context.ContextHelper;
import com.justep.system.interceptor.LogBefore;
import com.justep.system.log.LogUtils;
import com.justep.system.opm.OrgNode;
import com.justep.system.opm.PersonMember;
import com.justep.util.JustepLogUtils;
import com.justep.util.Utils;
import com.sun.management.OperatingSystemMXBean;

public class LogAfter implements Interceptor {
	public void execute() {
		doActionLog();
		doJvmLog();
		doActionTimeLog();
	}

	private void doActionTimeLog() {
		ActionContext localActionContext = ContextHelper.getActionContext();
		long l1 = ((Long) localActionContext.get("com.justep.log.action.time.start")).longValue();
		long l2 = System.currentTimeMillis() - l1;
		if (JustepLogUtils.isActionTimeDebugEnabled(l2)) {
			String str = "[Action Time]action执行时间：" + l2 + "ms," + localActionContext.getProcess().getFullName() + ","
					+ localActionContext.getActivity().getName() + "," + localActionContext.getAction().getName();
			JustepLogUtils.getActionTimeLogger().debug(str);
		}
	}

	private void doJvmLog() {
		if (JustepLogUtils.isJVMDebuggerEnabled()) {
			ActionContext localActionContext = ContextHelper.getActionContext();
			long l1 = ((Long) localActionContext.get("com.justep.log.jvm.free")).longValue();
			long l2 = ((Long) localActionContext.get("com.justep.log.jvm.use")).longValue();
			OperatingSystemMXBean localOperatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
			long l3 = localOperatingSystemMXBean.getTotalPhysicalMemorySize() / 1024L / 1024L;
			long l4 = localOperatingSystemMXBean.getFreePhysicalMemorySize() / 1024L / 1024L;
			long l5 = (localOperatingSystemMXBean.getTotalPhysicalMemorySize() - localOperatingSystemMXBean.getFreePhysicalMemorySize()) / 1024L / 1024L;
			String str1 = (String) localActionContext.get("com.justep.log.jvm.guid");
			StringBuffer localStringBuffer = new StringBuffer();
			localStringBuffer.append("[JVM]end....." + str1 + ", ");
			String str2 = localActionContext.getProcess().getFullName();
			String str3 = localActionContext.getActivity().getName();
			String str4 = localActionContext.getAction().getName();
			String str5 = ContextHelper.getOperator().getName();
			String str6 = ContextHelper.getOperator().getID();
			localStringBuffer.append("process: " + str2 + ", activity: " + str3 + ", action: " + str4 + ", operator: " + str6 + ", " + str5 + ", ");
			localStringBuffer.append("total: " + l3 + "MB, free: " + l4 + "MB, use: " + l5 + "MB" + ", ");
			localStringBuffer.append("leak free: " + (l1 - l4) + "MB, leak use: " + (l5 - l2) + "MB");
			JustepLogUtils.getJvmLogger().debug(localStringBuffer.toString());
		}
	}

	private void doActionLog() {
		ActionContext localActionContext = ContextHelper.getActionContext();
		Action localAction = localActionContext.getAction();
		if (localAction.isLogEnabled()) {
			String str = (String) localActionContext.get(LogBefore.ACTION_LOG_SID);
			if (Utils.isNotEmptyString(str)) {
				HashMap<String, Object> localHashMap = new HashMap<String, Object>();
				if ((localAction.getName().equals("loginAction")) || (localAction.getName().equals("ntLoginAction"))) {
					PersonMember localPersonMember = ContextHelper.getPersonMember();
					localHashMap.put("sCreatorFID", localPersonMember.getFID());
					localHashMap.put("sCreatorFName", ContextHelper.getPersonMemberFNameWithAgent());
					localHashMap.put("sCreatorPersonID", ContextHelper.getPerson().getID());
					localHashMap.put("sCreatorPersonName", ContextHelper.getPersonMemberNameWithAgent());
					OrgNode localOrgNode1 = localPersonMember.getPos();
					localHashMap.put("sCreatorPosID", localOrgNode1 != null ? localOrgNode1.getID() : "");
					localHashMap.put("sCreatorPosName", localOrgNode1 != null ? localOrgNode1.getName() : "");
					OrgNode localOrgNode2 = localPersonMember.getDept();
					localHashMap.put("sCreatorDeptID", localOrgNode2 != null ? localOrgNode2.getID() : "");
					localHashMap.put("sCreatorDeptName", localOrgNode2 != null ? localOrgNode2.getName() : "");
					OrgNode localOrgNode3 = localPersonMember.getOgn();
					localHashMap.put("sCreatorOgnID", localOrgNode3 != null ? localOrgNode3.getID() : "");
					localHashMap.put("sCreatorOgnName", localOrgNode3 != null ? localOrgNode3.getName() : "");
				}
				localHashMap.put("sResult", LogUtils.getActionResultAsString());
				localHashMap.put("sStatusName", "成功");
				actionLog(str, localHashMap);
			}
		}
	}

	private void actionLog(String sid, Map<String, Object> paramMap) {
		if (paramMap.isEmpty())
			return;
		String str1 = "";
		ArrayList<Object> localArrayList = new ArrayList<Object>();
		Iterator<String> i = paramMap.keySet().iterator();
		while (i.hasNext()) {
			String str2 = i.next();
			if (Utils.isNotEmptyString(str2)) {
				if (!Utils.isEmptyString(str1))
					str1 = str1 + ",";
				str1 = str1 + str2 + "=?";
				localArrayList.add(paramMap.get(str2));
			}
		}
		localArrayList.add(sid);
		String sql = "update SA_Log set " + str1 + " where SID=? and $clientFilter(NULL) ";
		LogBefore.execSqlUpdate(sql, localArrayList);
	}
}