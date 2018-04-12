package com.butone.flowbiz;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.alibaba.fastjson.JSONObject;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.butone.logic.impl.TableControlObject;
import com.butone.spi.Json2TableUtils;
import com.butone.workdate.WorkDayUtils;
import com.justep.exception.BusinessException;
import com.justep.model.Config;
import com.justep.model.ConfigItem;
import com.justep.model.ModelUtils;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.BizData;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.TableUtils;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class FinishRuntime extends BaseRuntime {
	public static final String CONCEPT_B_BJJLB = "B_BJJLB";
	public static final String CONCEPT_B_DICT_BJLXZD = "B_DICT_BJLXZD";

	public FinishRuntime(String bizRecId) {
		super(bizRecId);
	}

	private JSONObject checkCanFinish(BizRecStatus recStatus, FinishKind kind) {
		JSONObject ret = createResult();
		if (!kind.isMatch(recStatus)) {
			setFaultResult(ret, "完成状态[" + kind + "]与案卷状态[" + recStatus + "]不匹配");
			return ret;
		}

		TableControlObject target = ProcessLogicPluginContext.findTableControlObject(FlowBizConsts.CONCEPT_BizRec);
		Table bizRec;

		if (target == null) {
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", this.bizRecId);
			bizRec = KSQL.select("select b.* from B_BizRec b where b=:bizRecId", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOW, null);
		} else {
			bizRec = target.getTarget();
		}
		Row r = bizRec.iterator().next();

		// 案卷状态检查
		BizRecStatus currStatus = BizRecStatus.valueOf(r.getString("fStatus"));
		if (BizRecStatus.bsSuspended.equals(currStatus)) {
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", bizRecId);
			Table t_gqjl = KSQL
					.select("select b.fFQRXM,b.fFQSJ,b.fGQLX from B_AJGQJLB b where b.fGQZT = '挂起中' AND b.fBizRecId = :bizRecId ", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			if (FinishKind.fkSubmit.equals(kind) && t_gqjl.size() == 0)
				return ret;
			Utils.check(t_gqjl.size() == 1, "系统数据异常，没有找到案卷的挂起记录数据");
			Row gqjl = t_gqjl.iterator().next();
			SuspendKind suspKind = SuspendKind.valueOf(gqjl.getString("fGQLX"));
			if (SuspendKind.skApprize.equals(suspKind)) {
				if (!FinishKind.fkApprizeAbort.equals(kind)) {
					setFaultResult(ret, "当前请求为" + kind.toString() + ",请进行补交不来办结操作");
					return ret;
				}
			} else if (SuspendKind.skSpecialProcedure.equals(suspKind)) {
				setFaultResult(ret, "案卷状态当前为" + suspKind.getDisplayName() + "，请先进行特别程序结果操作");
				return ret;
			} else {
				setFaultResult(ret, "案卷状态当前为" + suspKind.getDisplayName() + "，请先进行解挂操作");
				return ret;
			}
		} else if (!BizRecStatus.bsProcessing.equals(currStatus)) {
			setFaultResult(ret, "当前案卷状态为" + r.getString("fStatusName") + "不允许" + kind.getDisplayName());
			return ret;
		}

		return ret;

	}

	public void applyFinish(BizRecStatus recStatus, JSONObject finishInfo) throws Exception {
		FinishKind kind = FinishKind.valueOf((String) finishInfo.get("finishKind"));
		JSONObject data = finishInfo.getJSONObject("tables");
		Map<String, Table> tables = new HashMap<String, Table>();
		Iterator<String> i = data.keySet().iterator();
		while (i.hasNext()) {
			String key = (String) i.next();
			tables.put(key, Json2TableUtils.transform(data.getJSONObject(key)));
		}
		applyFinish(recStatus, kind, tables);
	}

	public void applyFinish(BizRecStatus recStatus, Map<String, Object> finishInfo) {
		FinishKind kind = FinishKind.valueOf((String) finishInfo.get("finishKind"));
		@SuppressWarnings("unchecked")
		Map<String, Table> tables = (Map<String, Table>) finishInfo.get("tables");
		applyFinish(recStatus, kind, tables);
	}

	public void applyFinish(BizRecStatus recStatus, FinishKind kind, Map<String, Table> tables) {
		JSONObject ret = checkCanFinish(recStatus, kind);
		if (!ret.getBooleanValue("result"))
			throw new BusinessException(ret.getString("message"));

		TableControlObject target = ProcessLogicPluginContext.findTableControlObject(FlowBizConsts.CONCEPT_BizRec);
		Table bizRec;
		boolean saveBizRec = target == null;
		if (saveBizRec) {
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", this.bizRecId);
			bizRec = KSQL.select("select b.* from B_BizRec b where b=:bizRecId", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOW, null);
		} else {
			bizRec = target.getTarget();
		}
		Row r = bizRec.iterator().next();

		Timestamp now = CommonUtils.getCurrentDateTime();
		// 更新案卷表
		r.setString("fStatus", recStatus.name());
		r.setString("fStatusName", kind.getDisplayName());
		r.setString("fFinishKind", kind.name());
		if (kind.equals(FinishKind.fkSubmit))
			r.setString("fSuspendKind", SuspendKind.skSubmit.toString());
		r.setDateTime("fStatusTime", now);

		{
			// 如果不存在办结记录,检查是否传入办结信息
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", this.bizRecId);
			Table bjjlb = KSQL.select("select b from " + CONCEPT_B_BJJLB + " b where b=:bizRecId", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			if (bjjlb.size() == 0) {
				checkBJJLB(tables.get(CONCEPT_B_BJJLB), recStatus, kind);
			} else {
				tables.remove(CONCEPT_B_BJJLB);
			}
		}

		if (kind.equals(FinishKind.fkApprizeAbort)) {
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", this.bizRecId);
			Table t_gqjl = KSQL.select("select b.* from " + SuspendRuntime.CONCEPT_B_AJGQJLB + " b where b.fGQZT = '挂起中' and b.fBizRecId=:bizRecId", varMap,
					FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			Utils.check(t_gqjl.size() == 1, "缺少补正告知受理信息");
			tables.put(SuspendRuntime.CONCEPT_B_AJGQJLB, t_gqjl);
			abortAJGQJLData(r, t_gqjl.iterator().next(), now);
		}

		if (saveBizRec) {
			bizRec.getMetaData().setStoreByConcept(FlowBizConsts.CONCEPT_FlowCoopRecord, true);
			bizRec.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		}

		Iterator<Entry<String, Table>> i = tables.entrySet().iterator();
		while (i.hasNext()) {
			Entry<String, Table> e = i.next();
			Table t = e.getValue();
			t.getMetaData().setStoreByConcept(e.getKey(), true);
			t.save(FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
		}
	}

	private Row getBJLXDICTByFinishKind(FinishKind kind) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("finishKind", kind.name());
		Table t = queryFinishResultDict();
		if (t.size() > 0) {
			Iterator<Row> i = t.iterator();

			while (i.hasNext()) {
				Row r = i.next();
				if (kind.name().equals(r.getString("finishKind")))
					return r;

			}
		}
		throw new BusinessException("不存在" + kind.getDisplayName() + "的办理结编码，请检查/BIZ/system/config中的配置文件");
	}

	/**
	 * 检查办结结果
	 * 
	 * @param t_bjjl
	 * @param status
	 * @param kind
	 */
	private void checkBJJLB(Table t_bjjl, BizRecStatus status, FinishKind kind) {
		Utils.check(t_bjjl.size() == 1, kind.getDisplayName() + "请求参数异常");
		Row bjjl = t_bjjl.iterator().next();
		if (Utils.isEmptyString(bjjl.getString("fBJJGDM"))) {
			Row bjlx = getBJLXDICTByFinishKind(kind);
			bjjl.setString("fBJJGDM", bjlx.getString("code"));
			bjjl.setString("fBJJGMC", bjlx.getString("name"));
		}
		if (Utils.isEmptyString(bjjl.getString("fBJJGMS"))) {
			bjjl.setString("fBJJGMS", "无");
		}
		if (status.equals(BizRecStatus.bsAborted) && Utils.isEmptyString(bjjl.getString("fZFHTHYY"))) {
			bjjl.setString("fZFHTHYY", "无");
		}
		if (Utils.isNull(bjjl.getValue("fSFJE"))) {
			bjjl.setDecimal("fSFJE", CommonUtils.toDecimal(0));
		}
		bjjl.setString("fJEDWDM", "CNY");
		if (Utils.isNull(bjjl.getValue("fBJSJ"))) {
			bjjl.setDateTime("fBJSJ", CommonUtils.getCurrentDateTime());
		}
	}

	/**
	 * 解挂案卷挂起记录
	 * 
	 * @param gqjl
	 * @param now
	 * @param bizRec
	 * @return
	 */
	private void abortAJGQJLData(Row bizRec, Row gqjl, Timestamp now) {
		gqjl.setString("fGQZT", "已作废");
		gqjl.setDateTime("fJSSJ", now);
		if (Utils.isEmptyString(gqjl.getString("fJSRXM")) || Utils.isEmptyString(gqjl.getString("fJSRID"))) {
			gqjl.setString("fJSRID", ContextHelper.getPerson().getID());
			gqjl.setString("fJSRXM", ContextHelper.getPerson().getName());
		}
		// 转报办结 无需累计挂起天数 及 推迟限办日期
		String limitKind = bizRec.getString("fLimitKind");
		Date startTime = gqjl.getDateTime("fFQSJ");
		Double days = WorkDayUtils.calcLostDaysBetween(startTime, now, limitKind, false, false);
		if (days.intValue() != days.doubleValue()) {
			days = new Double(days.intValue() + 1);
		}
		gqjl.setInt("fSJGQTS", days.intValue());
		bizRec.setInt("fSuspendDays", (bizRec.getValue("fSuspendDays") == null ? 0 : bizRec.getInt("fSuspendDays")) + days.intValue());
		if (bizRec.getValue("fLimitDate") != null) {
			Date newLimitDate = WorkDayUtils.calcDateAfterDays(bizRec.getDateTime("fLimitDate"), new BigDecimal(days.intValue()), limitKind, false);
			bizRec.setDateTime("fLimitDate", new Timestamp(newLimitDate.getTime()));
		}
	}

	/**
	 * 创建默认办结记录表
	 * 
	 * @return
	 */
	public Table createDefaultBJJLBTable() {
		Table table = TableUtils.createTable(CONCEPT_B_BJJLB, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
		table = BizData.create(table, CONCEPT_B_BJJLB, null, "/base/core/logic/fn");
		Row r = table.iterator().next();
		r.setString("fBJJGDM", "6");
		r.setString("fBJJGMC", "办结");
		r.setString("fBizRecId", this.bizRecId);
		return table;
	}

	/**
	 * 查询办结结果字典
	 * 
	 * @return
	 */
	public static Table queryFinishResultDict() {
		// 在内存中构造客户的数据集
		List<String> names = new ArrayList<String>();
		names.add("name");
		names.add("code");
		names.add("finishKind");
		names.add("desc");
		names.add("isAbort");
		List<String> types = new ArrayList<String>();
		types.add("String");
		types.add("String");
		types.add("String");
		types.add("String");
		types.add("String");
		// 创建table，names代表列，types代表列的类型
		Table dict = TableUtils.createTable(null, names, types);
		dict.getMetaData().setKeyColumn("code");
		Config config = (Config) ModelUtils.getModelObjectByFullName("/system/config/finishResult", Config.TYPE);
		for (String name : config.getNames()) {
			ConfigItem item = config.getItem(name);
			ConfigItem disable = item.getChildren("disable");
			if (disable == null || !"false".equals(disable.getValue())) {
				Row r = dict.appendRow(item.getValue());
				ConfigItem desc = item.getChildren("desc");
				ConfigItem finishKind = item.getChildren("finishKind");
				ConfigItem isAbort = item.getChildren("isAbort");
				r.setString("name", item.getName());
				r.setString("finishKind", finishKind.getValue());
				r.setString("desc", desc.getValue());
				r.setString("isAbort", isAbort.getValue());
			}
		}
		return dict;
	}

}
