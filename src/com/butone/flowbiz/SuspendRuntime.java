package com.butone.flowbiz;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.butone.logic.impl.TableControlObject;
import com.butone.spi.Json2TableUtils;
import com.butone.spi.JsonUtilsUtils;
import com.butone.workdate.WorkDayUtils;
import com.justep.exception.BusinessException;
import com.justep.model.Concept;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.BizData;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.TableMetaData;
import com.justep.system.data.TableUtils;
import com.justep.system.process.ProcessUtils;
import com.justep.system.process.TaskStatus;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class SuspendRuntime extends BaseRuntime {
	/**
	 * 案卷挂起记录表
	 */
	public static final String CONCEPT_B_AJGQJLB = "B_AJGQJLB";
	public static final String CONCEPT_B_BZGZ = "B_BZGZ";
	public static final String CONCEPT_B_BZCLQD = "B_BZCLQD";
	public static final String CONCEPT_B_BZGZYY = "B_BZGZYY";
	public static final String CONCEPT_B_TBCX = "B_TBCX";
	public static final String CONCEPT_B_Material = "B_Material";
	public static final String CONCET_B_ActivityGroupInstance = "B_ActivityGroupInstance";

	public SuspendRuntime(String bizRecId) {
		super(bizRecId);
	}

	private JSONObject checkCanSuspend(SuspendKind kind, boolean apprizeAgain) {
		JSONObject ret = createResult();

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
		if (apprizeAgain) {
			if (!currStatus.equals(BizRecStatus.bsSuspended)) {
				setFaultResult(ret, "当前案卷状态为" + r.getString("fStatusName") + "不允许" + kind.getDisplayName());
			}
		} else if (!currStatus.equals(BizRecStatus.bsProcessing)) {
			setFaultResult(ret, "当前案卷状态为" + r.getString("fStatusName") + "不允许" + kind.getDisplayName());
			return ret;
		}

		if (!apprizeAgain) {
			if (kind.equals(SuspendKind.skApprize)) {
				// 补正告知时限检查
				Long days = WorkDayUtils.calcWorkDaysBetween(r.getDateTime("fReceiveTime"), CommonUtils.getCurrentDateTime());
				days -= 1;
				if (days > 5) {
					setFaultResult(ret, "补正告知必须自受理起5个工作日内办理");
					return ret;
				}
			}

			// 保险起见，再检查一次挂起记录表
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", bizRecId);
			Table gqjl = KSQL.select("select b.fFQRXM,b.fFQSJ,b.fGQLX from " + CONCEPT_B_AJGQJLB
					+ " b where b.fGQZT = '挂起中' AND b.fBizRecId = :bizRecId ", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			if (gqjl.size() > 0) {
				Row row = gqjl.iterator().next();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String message = row.getString("fFQRXM") + "已于" + sdf.format(row.getDateTime("fFQSJ")) + "进行了"
						+ SuspendKind.valueOf(row.getString("fGQLX")).getDisplayName();
				setFaultResult(ret, message);
				return ret;
			}
		} else {
			// 保险起见，再检查一次挂起记录表
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.put("bizRecId", bizRecId);
			Table gqjl = KSQL.select("select b.fFQRXM,b.fFQSJ,b.fGQLX from " + CONCEPT_B_AJGQJLB
					+ " b where b.fGQZT = '挂起中' AND b.fBizRecId = :bizRecId ", varMap, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			if (gqjl.size() == 0) {
				setFaultResult(ret, "不存在当前案卷的挂起记录信息");
				return ret;
			}
			Row row = gqjl.iterator().next();
			SuspendKind currSuspKind = SuspendKind.valueOf(row.getString("fGQLX"));
			if (!currSuspKind.equals(SuspendKind.skApprize)) {
				setFaultResult(ret, "当前案卷为" + currSuspKind.getDisplayName() + "中,不能进行" + kind.getDisplayName());
				return ret;
			}
		}
		return ret;
	}

	public void applySuspend(JSONObject suspendInfo) throws Exception {
		SuspendKind kind = SuspendKind.valueOf((String) suspendInfo.get("suspendKind"));
		JSONObject data = suspendInfo.getJSONObject("tables");
		boolean apprizeAgain = suspendInfo.containsKey("apprizeAgain") && suspendInfo.getBooleanValue("apprizeAgain");
		Map<String, Table> tables = new HashMap<String, Table>();
		Iterator<String> i = data.keySet().iterator();
		while (i.hasNext()) {
			String key = (String) i.next();
			tables.put(key, Json2TableUtils.transform(data.getJSONObject(key)));
		}
		applySuspend(kind, tables, apprizeAgain);
	}

	public void applySuspend(Map<String, Object> suspendInfo) {
		SuspendKind kind = SuspendKind.valueOf((String) suspendInfo.get("suspendKind"));
		@SuppressWarnings("unchecked")
		Map<String, Table> tables = (Map<String, Table>) suspendInfo.get("tables");
		applySuspend(kind, tables, false);
	}

	public void applySuspend(SuspendKind kind, Map<String, Table> tables, boolean apprizeAgain) {
		JSONObject ret = checkCanSuspend(kind, apprizeAgain);
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
		if (SuspendKind.skApprize.equals(kind) && apprizeAgain) {
			// 欺骗SuspendProcessEngin
			ProcessUtils.getPI().getTask().setStatus(TaskStatus.EXECUTING);
			// 再次补正
			Table t_gqjl = tables.remove(CONCEPT_B_AJGQJLB);
			Table t_bzgzyy = TableUtils.createTable(CONCEPT_B_BZGZYY, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
			t_bzgzyy.getMetaData().setKeyColumn("FGUID");
			tables.put(CONCEPT_B_BZGZYY, t_bzgzyy);
			Row gqjl = t_gqjl.iterator().next();
			Table t_bzgz = tables.get(CONCEPT_B_BZGZ);
			this.apprizeAgain(t_bzgz, t_bzgzyy, gqjl.getString("fGQYY"), now);

			// 再次补正先提交已确认的材料信息，二次补正只显示未确认或者待确认的材料信息
			Map<String, Object> varMap = new HashMap<String, Object>();
			varMap.clear();
			varMap.put("bzgz", t_bzgz.iterator().next().getValue("FGUID"));
			Table t_bzclqd = KSQL.select("select b.* from " + CONCEPT_B_BZCLQD + " b where b.fBZGZ=:bzgz", varMap,
					FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			// 查询材料表
			Table t_material = KSQL.select("select B_Material.* from B_Material B_Material where B_Material.fBizRecId='" + this.bizRecId + "'", null,
					"/base/core/material/data", null);
			t_material.getMetaData().setKeyColumn("B_Material");
			tables.put(CONCEPT_B_Material, t_material);
			resumeBZCLData(t_bzclqd, t_material, tables.get(CONCEPT_B_BZCLQD));
			tables.put(CONCEPT_B_BZCLQD, t_bzclqd);
			// TODO 增加网上办事aop
		} else {
			// 首次补正
			// 更新案卷表
			r.setString("fStatus", BizRecStatus.bsSuspended.name());
			r.setString("fStatusName", BizRecStatus.bsSuspended.getDisplayName());
			r.setString("fSuspendKind", kind.name());
			r.setDateTime("fStatusTime", now);
			// 更新挂起记录表
			Row gqjl = suspendAJGQJLData(tables.get(CONCEPT_B_AJGQJLB), kind, now);
			if (SuspendKind.skApprize.equals(kind)) {
				// 补交告知默认60天
				gqjl.setInt("fSQGQTS", 60);
				// 补正告知
				Row bzgz = suspendBZGZData(tables.get(CONCEPT_B_BZGZ), gqjl.getString("FGUID"), gqjl.getString("fGQYY"), now);
				Table t_bzgzyy = TableUtils.createTable(CONCEPT_B_BZGZYY, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
				t_bzgzyy.getMetaData().setKeyColumn("FGUID");
				tables.put(CONCEPT_B_BZGZYY, t_bzgzyy);
				// 补正原因
				appendBZGZYY(t_bzgzyy, bzgz.getString("FGUID"), gqjl.getString("fGQYY"), now);
				// 材料清单
				String clqd = suspendBZCLLBData(tables.get(CONCEPT_B_BZCLQD), bzgz.getString("FGUID"));
				bzgz.setString("fBZCLQD", clqd);
				gqjl.setInt("fRemainingDays", 60);
			} else if (SuspendKind.skSpecialProcedure.equals(kind)) {
				Row tbcx = checkTBCXData(tables.get(CONCEPT_B_TBCX), gqjl.getString("FGUID"));
				gqjl.setInt("fSQGQTS", tbcx.getInt("fTBCXSX"));
				gqjl.setInt("fRemainingDays", tbcx.getInt("fTBCXSX"));

			} else if (SuspendKind.skSubmit.equals(kind)) {
				// 转报办结转调办结
				Map<String, Table> newMap = new HashMap<String, Table>();
				newMap.putAll(tables);
				newMap.put(FinishRuntime.CONCEPT_B_BJJLB, createBJJLB(tables.get(CONCEPT_B_AJGQJLB)));
				new FinishRuntime(this.bizRecId).applyFinish(BizRecStatus.bsSuspended, FinishKind.fkSubmit, newMap);
				return;
			}

			if (saveBizRec) {
				bizRec.getMetaData().setStoreByConcept(FlowBizConsts.CONCEPT_FlowCoopRecord, true);
				bizRec.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
			}
			// TODO 增加网上办事aop
		}

		Iterator<Entry<String, Table>> i = tables.entrySet().iterator();
		while (i.hasNext()) {
			Entry<String, Table> e = i.next();
			Table t = e.getValue();
			t.getMetaData().setStoreByConcept(e.getKey(), true);
			t.save(FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
		}
	}

	/**
	 * 检查挂起记录数据
	 * 
	 * @param r
	 */
	private Row suspendAJGQJLData(Table t_gqjl, SuspendKind kind, Timestamp now) {
		Utils.check(t_gqjl.size() > 0, kind.getDisplayName() + "请求参数异常");
		Row gqjl = t_gqjl.iterator().next();
		chechNewRowModifyState(gqjl);
		String recId = gqjl.getString("fBizRecId");
		Utils.check(Utils.isEmptyString(recId) || recId.equals(this.bizRecId), "挂起记录的案卷编号不一致");
		gqjl.setValue("fBizRecId", this.bizRecId);
		gqjl.setDateTime("fFQSJ", now);
		gqjl.setValue("fGQLX", kind.toString());
		gqjl.setValue("fGQZT", "挂起中");
		gqjl.setValue("fTaskId", ProcessUtils.getCurrentAI().getId());

		if (Utils.isEmptyString(gqjl.getString("FGUID"))) {
			gqjl.setString("FGUID", CommonUtils.createGUID());
		}
		if (Utils.isEmptyString(gqjl.getString("fFQRID")) || Utils.isEmptyString(gqjl.getString("fFQRXM"))) {
			gqjl.setString("fFQRID", ContextHelper.getPerson().getID());
			gqjl.setString("fFQRXM", ContextHelper.getPerson().getName());
		}
		return gqjl;
	}

	/**
	 * 获得下一个特别程序序号
	 * 
	 * @return
	 */
	private int getNextTBCXXH() {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("bizRecId", this.bizRecId);
		Table t = KSQL.select("select (countAll()) as FCNT from B_TBCX b where b.fBizRecId=:bizRecId", varMap,
				FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
		int n = new Integer(t.iterator().next().getValue("FCNT").toString());
		return n + 1;
	}

	/**
	 * 检查补正告知数据
	 * 
	 * @param r
	 */
	private Row checkTBCXData(Table t_tbcx, String fAJGQJL) {
		Utils.check(t_tbcx.size() > 0, "补正告知请求参数异常");
		Row tbcx = t_tbcx.iterator().next();
		chechNewRowModifyState(tbcx);
		String recId = tbcx.getString("fBizRecId");
		Utils.check(Utils.isEmptyString(recId) || recId.equals(this.bizRecId), "特别程序的案卷编号不一致");

		recId = tbcx.getString("fAJGQJL");
		Utils.check(Utils.isEmptyString(recId) || recId.equals(fAJGQJL), "特别程序的挂起记录编号不一致");

		tbcx.setValue("fBizRecId", this.bizRecId);
		tbcx.setValue("fAJGQJL", fAJGQJL);
		tbcx.setInt("fTBCXXH", getNextTBCXXH());

		if (Utils.isEmptyString(tbcx.getString("FGUID"))) {
			tbcx.setString("FGUID", CommonUtils.createGUID());
		}
		Utils.check(
				Utils.isNotEmptyString(tbcx.getString("fTBCXZL")) && Utils.isNotEmptyString(tbcx.getString("fTBCXPZR"))
						&& Utils.isNotEmptyString(tbcx.getString("fSQNR")) && Utils.isNotNull(tbcx.getInteger("fTBCXSX"))
						&& Utils.isNotNull(tbcx.getString("fTBCXSXDW")), "特别程序种类、批准热、申请内容、时限、时限单位不允许为空");
		return tbcx;
	}

	/**
	 * 检查补正告知数据
	 * 
	 * @param r
	 */
	private Row suspendBZGZData(Table t_bzgz, String fAJGQJL, String gqyy, Timestamp now) {
		Utils.check(t_bzgz.size() == 1, "补正告知请求参数异常");
		Row bzgz = t_bzgz.iterator().next();
		chechNewRowModifyState(bzgz);
		String recId = bzgz.getString("fBizRecId");
		Utils.check(Utils.isEmptyString(recId) || recId.equals(this.bizRecId), "补正告知的案卷编号不一致");

		recId = bzgz.getString("fAJGQJL");
		Utils.check(Utils.isEmptyString(recId) || recId.equals(fAJGQJL), "补正告知的挂起记录编号不一致");

		bzgz.setValue("fBizRecId", this.bizRecId);
		bzgz.setValue("fAJGQJL", fAJGQJL);
		if (Utils.isEmptyString(bzgz.getString("fBZGZYY")))
			bzgz.setString("fBZGZYY", gqyy);
		bzgz.setDateTime("fBZSJ", now);
		if (Utils.isEmptyString(bzgz.getString("FGUID"))) {
			bzgz.setString("FGUID", CommonUtils.createGUID());
		}
		return bzgz;
	}

	/**
	 * 检查补正告知材料列表
	 * 
	 * @param r
	 */
	private String suspendBZCLLBData(Table t_cllb, String fBZGZ) {
		Utils.check(t_cllb.size() > 0, "补正告知的材料清单数量为0");
		String bzclqd = "";
		Iterator<Row> i = t_cllb.iterator();
		while (i.hasNext()) {
			Row cl = i.next();
			chechNewRowModifyState(cl);
			String recId = cl.getString("fBZGZ");
			Utils.check(Utils.isEmptyString(recId) || recId.equals(fBZGZ), "补正告知的挂起记录编号不一致");
			Utils.check(Utils.isNotEmptyString(cl.getString("fCLBH")), "补正材料的材料编号不允许为空");
			if (Utils.isEmptyString(cl.getString("FGUID"))) {
				cl.setString("FGUID", CommonUtils.createGUID());
			}
			cl.setString("fBZGZ", fBZGZ);
			bzclqd += cl.getString("fCLMC") + ";";
		}
		return bzclqd;
	}

	/**
	 * 挂起记录生成办结表
	 * 
	 * @param t_gqjl
	 * @return
	 */
	private Table createBJJLB(Table t_gqjl) {
		Row gqjl = t_gqjl.iterator().next();
		Table table = TableUtils.createTable(FinishRuntime.CONCEPT_B_BJJLB, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
		table.getMetaData().setKeyColumn("fBizRecId");
		table = BizData.create(table, FinishRuntime.CONCEPT_B_BJJLB, null, FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
		Row r = table.iterator().next();
		r.setString("fBJJGMS", gqjl.getString("fGQYY"));
		return table;
	}

	/**
	 * 再次补正
	 * 
	 * @param t_bzgz
	 * @param t_clqd
	 * @param fBZGZYY
	 * @param now
	 */
	private void apprizeAgain(Table t_bzgz, Table t_bzgzyy, String fBZGZYY, Timestamp now) {
		Utils.check(t_bzgz.size() == 1, "补正告知请求参数异常");
		Row bzgz = t_bzgz.iterator().next();
		appendBZGZYY(t_bzgzyy, bzgz.getString("FGUID"), fBZGZYY, now);
	}

	private void appendBZGZYY(Table t_bzgzyy, String bzgz, String fBZGZYY, Timestamp now) {
		Row bzgzyy = t_bzgzyy.appendRow();
		bzgzyy.setString("FGUID", CommonUtils.createGUID());
		bzgzyy.setDateTime("fFQSJ", now);
		bzgzyy.setString("fBZGZYY", fBZGZYY);
		bzgzyy.setString("fBZGZ", bzgz);
	}

	/**
	 * 解除挂起，返回挂起天数。转报办结返回null，不计算挂起天数、不累计案卷挂起天数、不 推迟限办日期
	 * 
	 * @return
	 */
	public Integer resume(SuspendKind requestKind, Map<String, Table> suspendInfo) {
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
		Row rec = bizRec.iterator().next();
		// 检查并更新案卷表
		BizRecStatus currStatus = BizRecStatus.valueOf(rec.getString("fStatus"));
		Utils.check(currStatus.equals(BizRecStatus.bsSuspended), "当前案卷状态为" + currStatus.getDisplayName() + ",不能解挂");
		String bizRecSuspendKind = rec.getString("fSuspendKind");
		Timestamp now = CommonUtils.getCurrentDateTime();
		rec.setString("fStatus", BizRecStatus.bsProcessing.name());
		rec.setString("fStatusName", BizRecStatus.bsProcessing.getDisplayName());
		rec.setString("fSuspendKind", null);
		rec.setDateTime("fStatusTime", now);

		// 检查并更新挂起记录
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("bizRecId", this.bizRecId);
		Map<String, Table> tables = new HashMap<String, Table>();

		Table t_gqjl = tables.get(CONCEPT_B_AJGQJLB);
		if (t_gqjl == null) {
			t_gqjl = KSQL.select("select b.* from " + CONCEPT_B_AJGQJLB + " b where b.fGQZT = '挂起中' and b.fBizRecId=:bizRecId", varMap,
					FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			tables.put(CONCEPT_B_AJGQJLB, t_gqjl);
		} else {
			Row gqjl = t_gqjl.iterator().next();
			String recId = gqjl.getString("fBizRecId");
			Utils.check(Utils.isEmptyString(recId) || recId.equals(this.bizRecId), "挂起记录的案卷编号不一致");
		}
		Utils.check(t_gqjl.size() == 1, "案卷挂起记录表数据异常，记录数量应该为1，实际数量为" + t_gqjl.size());
		Row gqjl = t_gqjl.iterator().next();
		Utils.check(gqjl.getString("fGQLX").equals(bizRecSuspendKind), "案卷表挂起类型与挂起记录表类型不一致");
		resumeAJGQJLData(gqjl, now);
		SuspendKind kind = SuspendKind.valueOf(gqjl.getString("fGQLX"));
		// 转报办结挂起兼容处理
		if (requestKind.equals(SuspendKind.skSuspend) && kind.equals(SuspendKind.skSubmit))
			requestKind = SuspendKind.skSubmit;
		Utils.check(requestKind.equals(kind), "请求类型与案卷当前挂起状态不一致");
		if (kind.equals(SuspendKind.skApprize)) {
			varMap.clear();
			varMap.put("ajgqjl", gqjl.getValue("FGUID"));
			Table t_bzgz = KSQL.select("select b.* from " + CONCEPT_B_BZGZ + " b where b.fAJGQJL=:ajgqjl", varMap,
					FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			Utils.check(t_bzgz.size() == 1, "缺少补正告知受理信息");
			tables.put(CONCEPT_B_BZGZ, t_bzgz);

			varMap.clear();
			varMap.put("bzgz", t_bzgz.iterator().next().getValue("FGUID"));
			Table t_bzclqd = KSQL.select("select b.* from " + CONCEPT_B_BZCLQD + " b where b.fBZGZ=:bzgz", varMap,
					FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			tables.put(CONCEPT_B_BZCLQD, t_bzclqd);
			// 查询材料表
			Table t_material = KSQL.select("select B_Material.* from B_Material B_Material where B_Material.fBizRecId='" + this.bizRecId + "'", null,
					"/base/core/material/data", null);
			t_material.getMetaData().setKeyColumn("B_Material");
			tables.put(CONCEPT_B_Material, t_material);
			resumeBZGZData(t_bzgz.iterator().next(), now, (Table) suspendInfo.get(CONCEPT_B_BZGZ));
			resumeBZCLData(t_bzclqd, t_material, (Table) suspendInfo.get(CONCEPT_B_BZCLQD));
		} else if (kind.equals(SuspendKind.skSpecialProcedure)) {
			varMap.clear();
			varMap.put("ajgqjl", gqjl.getValue("FGUID"));
			Table t_tbcx = KSQL.select("select b.* from " + CONCEPT_B_TBCX + " b where b.fAJGQJL=:ajgqjl", varMap,
					FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION, null);
			Utils.check(t_tbcx.size() == 1, "缺少特别程序结果信息");
			tables.put(CONCEPT_B_BZCLQD, t_tbcx);

			resumeTBCXData(t_tbcx.iterator().next(), now, (Table) suspendInfo.get(CONCEPT_B_TBCX));
		}

		Double suspendDays = null;
		if (!kind.equals(SuspendKind.skSubmit)) {
			// 转报办结 无需累计挂起天数 及 推迟限办日期
			String recLimitKind = rec.getString("fLimitKind");
			Date startTime = gqjl.getDateTime("fFQSJ");

			suspendDays = WorkDayUtils.calcLostDaysBetween(startTime, now, recLimitKind, false, false);
			if (suspendDays.intValue() != suspendDays.doubleValue()) {
				suspendDays = new Double(suspendDays.intValue() + 1);
			}
			gqjl.setInt("fSJGQTS", suspendDays.intValue());
			// ProcessLimitManager.resume处理剩余天数和挂起天数
		}

		if (saveBizRec) {
			bizRec.getMetaData().setStoreByConcept(FlowBizConsts.CONCEPT_FlowCoopRecord, true);
			bizRec.save(FlowBizConsts.DATA_MODEL_CORE_FLOW);
		}

		// 保存解挂系统表
		Iterator<Entry<String, Table>> i = tables.entrySet().iterator();
		while (i.hasNext()) {
			Entry<String, Table> e = i.next();
			Table t = e.getValue();
			t.getMetaData().setStoreByConcept(e.getKey(), true);
			t.save(FlowBizConsts.DATA_MODEL_CORE_FLOWOPERATION);
		}
		return suspendDays != null ? suspendDays.intValue() : null;
	}

	public Integer resume(Map<String, Object> suspendInfo) throws Exception {
		SuspendKind kind = SuspendKind.valueOf((String) suspendInfo.get("suspendKind"));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Object>> data = (Map<String, Map<String, Object>>) suspendInfo.get("tables");
		Map<String, Table> tables = new HashMap<String, Table>();
		if (data != null) {
			Iterator<String> i = data.keySet().iterator();
			while (i.hasNext()) {
				String key = i.next();

				JSONObject tableJSON = (JSONObject) JsonUtilsUtils.map2Json(data.get(key));
				tables.put(key, Json2TableUtils.transform(tableJSON));
			}
		}
		return resume(kind, tables);
	}

	/**
	 * 解挂案卷挂起记录
	 * 
	 * @param gqjl
	 * @param now
	 * @param bizRec
	 * @return
	 */
	private void resumeAJGQJLData(Row gqjl, Timestamp now) {
		gqjl.setString("fGQZT", "已完成");
		gqjl.setDateTime("fJSSJ", now);
		if (Utils.isEmptyString(gqjl.getString("fJSRXM")) || Utils.isEmptyString(gqjl.getString("fJSRID"))) {
			gqjl.setString("fJSRID", ContextHelper.getPerson().getID());
			gqjl.setString("fJSRXM", ContextHelper.getPerson().getName());
		}
	}

	/**
	 * 解挂补正告知
	 * 
	 * @param currBZGZ
	 * @param now
	 * @param t_bzgz
	 * @return
	 */
	private void resumeBZGZData(Row currBZGZ, Timestamp now, Table t_bzgz) {
		Utils.check(t_bzgz != null && t_bzgz.size() == 1, "补正受理请求中缺少补正受理数据");
		Row r_bzgz = t_bzgz.iterator().next();
		currBZGZ.setDateTime("fBZSJ", now);
		currBZGZ.setString("fBZSLDD", r_bzgz.getString("fBZSLDD"));
		currBZGZ.setString("fBZSLRID", ContextHelper.getPerson().getID());
		currBZGZ.setString("fBZSLRXM", ContextHelper.getPerson().getName());
	}

	/**
	 * 解挂补正材料
	 * 
	 * @param t_currBZCLQD
	 * @param t_currMaterial
	 * @param t_bzcl
	 * @return
	 */
	private void resumeBZCLData(Table t_currBZCLQD, Table t_currMaterial, Table t_bzcl) {
		Utils.check(t_bzcl != null, "补正受理请求中缺少补正材料清单数据");
		Iterator<Row> i = t_bzcl.iterator();
		TableMetaData meta = t_currBZCLQD.getMetaData();
		meta.setKeyColumn("FGUID");
		while (i.hasNext()) {
			Row bzcl = i.next();
			Row currBZCL = t_currBZCLQD.getRow(bzcl.getString("FGUID"));
			String fCLQR = bzcl.getString("fCLQR");
			String fBLYCL = bzcl.getString("fBLYCL");
			// 确保材料已确认的进行补正
			if (Utils.isNotEmptyString(fCLQR) && fCLQR.equals("已确认")) {
				if (currBZCL == null) {
					currBZCL = t_currBZCLQD.appendRow();
					for (ColumnMetaData column : meta.getColumnMetaDatas()) {
						if (column.getDefineModelObject() instanceof Concept)
							continue;
						currBZCL.setValue(column.getName(), bzcl.getValue(column.getName()));
					}
				} else {
					currBZCL.setValue("fCLQR", fCLQR);
					// 当前附件清单为空，传入有附件清单
					if (Utils.isEmptyString(currBZCL.getString("fFJQD")) && Utils.isNotEmptyString(bzcl.getString("fFJQD")))
						currBZCL.setValue("fFJQD", bzcl.getValue("fFJQD"));
				}
				if (Utils.isEmptyString(currBZCL.getString("fFJQD")))
					continue;
				Row currMaterial = t_currMaterial.getRow("fMaterialId", currBZCL.getString("fCLBH"));
				if (currMaterial == null) {
					currMaterial = t_currMaterial.appendRow();
					currMaterial.setString("B_Material", CommonUtils.createGUID());
					currMaterial.setString("fMaterialId", currBZCL.getString("fCLBH"));
					currMaterial.setString("fMaterialName", "补正-" + currBZCL.getString("fCLMC"));
					currMaterial.setString("fBizRecId", this.bizRecId);
					currMaterial.setString("fMaterialType", "必要材料");
					currMaterial.setString("fDocIds", currBZCL.getString("fFJQD"));
					currMaterial.setInt("fDispOrder", t_currMaterial.size());
					currMaterial.setString("fIsDefSelect", "是");
					currMaterial.setInt("fMtNums", JSONArray.parseArray(currBZCL.getString("fFJQD")).size());
				} else {
					String curDocIds = currMaterial.getString("fDocIds");
					JSONArray oldDocJson = Utils.isNotEmptyString(curDocIds) ? JSONArray.parseArray(curDocIds) : new JSONArray();
					JSONArray bzDocJson = JSONArray.parseArray(currBZCL.getString("fFJQD"));
					// 不保留原材料，则修改原材的文件名称为无效材料
					if (Utils.isNotEmptyString(fBLYCL) && fBLYCL.equals("不保留")) {
						for (int k = 0; k < oldDocJson.size(); k++) {
							JSONObject doc = oldDocJson.getJSONObject(k);
							doc.put("docName", "无效-" + doc.getString("docName"));
						}
					}
					for (int j = 0; j < bzDocJson.size(); j++) {
						JSONObject doc = bzDocJson.getJSONObject(j);
						doc.put("docName", "补正-" + doc.getString("docName"));
						oldDocJson.add(bzDocJson.getJSONObject(j));
					}
					currMaterial.setString("fDocIds", oldDocJson.toString());
					currMaterial.setInt("fMtNums", bzDocJson.size());
				}
			}
		}
	}

	/**
	 * 解挂特别程序
	 */
	private void resumeTBCXData(Row currTBCX, Timestamp now, Table t_tbcx) {
		Utils.check(t_tbcx != null && t_tbcx.size() == 1, "特别程序结果受理请求中缺少结果数据");
		Row tbcx = t_tbcx.iterator().next();
		currTBCX.setValue("fJGCSRQ", tbcx.getValue("fJGCSRQ"));
		if (currTBCX.getValue("fJGCSRQ") == null)
			currTBCX.setValue("fJGCSRQ", now);
		currTBCX.setValue("fTBCXSFJE", tbcx.getValue("fTBCXSFJE"));
		if (currTBCX.getValue("fTBCXSFJE") == null)
			currTBCX.setDecimal("fTBCXSFJE", new BigDecimal("0.00"));
		currTBCX.setValue("fTBCXJG", tbcx.getValue("fTBCXJG"));
		if (Utils.isEmptyString(currTBCX.getString("fTBCXJG")))
			currTBCX.setValue("fTBCXJG", "无");
	}
}
