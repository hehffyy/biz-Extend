package com.butone.sequencecode;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.butone.codeinf.model.SequenceCode;
import com.butone.codeinf.model.node.ParameterNode;
import com.butone.codeinf.model.node.VariableNode;
import com.justep.exception.BusinessException;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.context.ContextHelper;
import com.justep.system.data.KSQL;
import com.justep.system.data.Row;
import com.justep.system.data.SQL;
import com.justep.system.data.Table;
import com.justep.system.data.Transaction;
import com.justep.system.process.ExpressEngine;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

public class SequenceCodeImpl implements SequenceCode {
	private static final String DEFAULT_DATA_MODEL = "/base/system/sequenceCode/data";

	@Override
	public String calcNodeValue(ParameterNode node) {
		return "";
	}

	@Override
	public String calcNodeValue(VariableNode node) {
		Object obj = ExpressEngine.calculate(node.getScript(), null, ModelUtils.getModel("/base/core/logic/fn"));
		return obj == null ? "" : obj.toString();
	}

	@Override
	public int initSequenceCodeLog(String codeGuid, String groupValue, String relTableName, String codeName) {
		long l = System.currentTimeMillis();
		while (true) {
			try {
				Table table = querySequenceCodeLog(codeGuid, groupValue, relTableName);
				Row r = null;
				if (table.size() == 0) {
					r = table.appendRow();
					r.setString("B", CommonUtils.createGUID());
					r.setString("fCodeGuid", codeGuid);
					r.setString("fGroupValue", groupValue);
					r.setString("fRelTableName", relTableName);
					r.setInt("fCurrentValue", 0);
					r.setValue("version", 0);
					table.save(DEFAULT_DATA_MODEL);
				} else {
					r = table.iterator().next();
				}
				return r.getInt("fCurrentValue");
			} catch (Exception e) {
				if (e instanceof IOException)
					throw new BusinessException("初始化通用编码IO异常," + e.getMessage(), e);
				if (System.currentTimeMillis() - l >= MAX_WAITED_TIME) {
					throw new BusinessException("初始化通用编码等待超时");
				}
				Thread.yield();
			}
		}

	}

	/**
	 * 编码5分钟编码超时
	 */
	private static final long MAX_WAITED_TIME = 5 * 60 * 1000;

	/**
	 * 更新通用编码序列记录
	 */
	@Override
	public int updateSequenceCodeLog(String codeGuid, String groupValue, String relTableName, int interval, boolean immediate) {
		long l = System.currentTimeMillis();
		Transaction trans = immediate ? new Transaction() : ContextHelper.getTransaction();
		if (immediate) {
			try {
				trans.begin();
			} catch (SQLException e) {
				throw new BusinessException("无法启动数据库事物", e);
			}
		}
		Model dataModel = ModelUtils.getModel(DEFAULT_DATA_MODEL);
		List<Object> params = new ArrayList<Object>();
		int nextValue = interval;
		try {
			while (true) {
				try {
					params.clear();
					Table table = querySequenceCodeLog(codeGuid, groupValue, relTableName);
					if (table.size() == 0) {
						params.add(CommonUtils.createGUID());
						params.add(codeGuid);
						params.add(groupValue);
						params.add(relTableName);
						nextValue = interval;
						SQL.executeUpdate(
								"insert into B_SequenceCodeLog (fID,fCodeGuid,fGroupValue,fRelTableName,fCurrentValue,version) values (?,?,?,?,1,0)",
								params, dataModel, trans);
					} else {
						Row r = table.iterator().next();
						nextValue = r.getInt("fCurrentValue") + interval;
						int version = r.getInteger("version");
						int nextVer = version + 1;
						if (nextVer >= Integer.MAX_VALUE - 1000) {
							nextVer = 0;
						}
						params.add(nextValue);
						params.add(nextVer);
						params.add(r.getString("B"));
						params.add(version);
						int cnt = SQL.executeUpdate("update B_SequenceCodeLog set fCurrentValue=?,version=? where fId=? and version=?", params,
								dataModel, trans);
						if (cnt == 0) {
							throw new BusinessException("通用编码记录表数据已被其他用户修改");
						}
					}
					if (immediate) {
						try {
							trans.commit();
						} catch (Exception ec) {
							try {
								trans.rollback();
							} catch (Exception er) {
								er.printStackTrace();
							}
							throw ec;
						}
					}
					return nextValue;
				} catch (Exception e) {
					if (e instanceof IOException)
						throw new BusinessException("更新通用编码IO异常," + e.getMessage(), e);
					if (System.currentTimeMillis() - l >= MAX_WAITED_TIME) {
						throw new BusinessException("更新通用编码等待超时");
					}
					Thread.yield();
				}
			}
			//			return nextValue;
		} finally {

		}

	}

	/**
	 * 更新通用编码使用记录
	 */
	@Override
	public void makeSequenceUseRecord(String codeGuid, String groupValue, String relTableName, String userTable, String userField,
			String userKeyValues, String sequenceValue) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		Model dataModel = ModelUtils.getModel(DEFAULT_DATA_MODEL);
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		// 检查是否已占用某编码,如占用则释放
		String sql = "select B.* from B_SequenceCodeUseRecord B where B.fUserTableName=:fUserTableName and B.fUserField=:fUserField and B.fUserKeyValues=:fUserKeyValues";
		varMap.put("fUserTableName", userTable);
		varMap.put("fUserField", userField);
		varMap.put("fUserKeyValues", userKeyValues);
		Table table = KSQL.select(sql, varMap, dataModel, fnModel);
		if (table.size() > 0) {
			Row r = table.iterator().next();
			r.setValue("fUserTableName", null);
			r.setValue("fUserField", null);
			r.setValue("fUserKeyValues", null);
			table.save(dataModel);
		}

		// 新增序号
		varMap.clear();
		sql = "select B.* from B_SequenceCodeUseRecord B where B.fCodeGuid=:fCodeGuid and B.fGroupValue=:fGroupValue and B.fRelTableName=:fRelTableName and B.fSequenceValue=:fSequenceValue";
		varMap.put("fCodeGuid", codeGuid);
		varMap.put("fGroupValue", groupValue);
		varMap.put("fRelTableName", relTableName);
		varMap.put("fSequenceValue", sequenceValue);
		table = KSQL.select(sql, varMap, dataModel, fnModel);
		if (table.size() == 0) {
			Row r = table.appendRow();
			r.setString("B", CommonUtils.createGUID());
			r.setValue("fCodeGuid", codeGuid);
			r.setValue("fGroupValue", groupValue);
			r.setValue("fRelTableName", relTableName);
			r.setValue("fSequenceValue", sequenceValue);
			r.setValue("fUserTableName", userTable);
			r.setValue("fUserField", userField);
			r.setValue("fUserKeyValues", userKeyValues);
			r.setValue("fUpdateTime", CommonUtils.getCurrentDateTime());
			r.setValue("fOperator", ContextHelper.getPerson().getName());
			r.setValue("version", 0);
		} else {
			Row r = table.iterator().next();
			StringBuffer sb1 = new StringBuffer();
			sb1.append(r.getString("fUserTableName"));
			sb1.append(r.getString("fUserField"));
			sb1.append(r.getString("fUserKeyValues"));
			StringBuffer sb2 = new StringBuffer();
			sb1.append(userTable);
			sb1.append(userField);
			sb1.append(userKeyValues);
			if (!sb1.equals(sb2)) {
				throw new RuntimeException("编码[" + sequenceValue + "]已使用");
			}
			r.setValue("fUserTableName", userTable);
			r.setValue("fUserField", userField);
			r.setValue("fUserKeyValues", userKeyValues);
		}
		table.save(dataModel);
	}

	private Table querySequenceCodeLog(String codeGuid, String groupValue, String relTableName) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("fCodeGuid", codeGuid);
		varMap.put("fGroupValue", groupValue);
		varMap.put("fRelTableName", relTableName);
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		String sql = "select B.* from B_SequenceCodeLog B where B.fCodeGuid=:fCodeGuid and B.fGroupValue=:fGroupValue and B.fRelTableName=:fRelTableName";
		Table table = KSQL.select(sql, varMap, DEFAULT_DATA_MODEL, fnModel);
		return table;
	}

	public void releaseCodeValue(String userTable, String userField, String userKeyValues) {
		Model dataModel = ModelUtils.getModel(DEFAULT_DATA_MODEL);
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		// 检查是否已占用某编码,如占用则释放
		String sql = "select B.* from B_SequenceCodeUseRecord B where B.fUserTableName=:fUserTableName and B.fUserField=:fUserField and B.fUserKeyValues=:fUserKeyValues";
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("fUserTableName", userTable);
		varMap.put("fUserField", userField);
		varMap.put("fUserKeyValues", userKeyValues);
		Table table = KSQL.select(sql, varMap, dataModel, fnModel);
		if (table.size() > 0) {
			Row r = table.iterator().next();
			r.setValue("fUserTableName", null);
			r.setValue("fUserField", null);
			r.setValue("fUserKeyValues", null);
			table.save(dataModel);
		}
	}

	@Override
	public List<String> queryUnusedCodeValues(String codeGuid, String groupValue, String relTableName) {
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("fCodeGuid", codeGuid);
		varMap.put("fGroupValue", groupValue);
		varMap.put("fRelTableName", relTableName);
		Model dataModel = ModelUtils.getModel(DEFAULT_DATA_MODEL);
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		String sql = "select B.fSequenceValue as fSequenceValue from B_SequenceCodeUseRecord B where B.fCodeGuid=:fCodeGuid and B.fGroupValue=:fGroupValue and B.fRelTableName=:fRelTableName"
				+ " and B.fUserTableName is null and B.fUserField is null and B.fUserKeyValues is null order by B.fSequenceValue";
		Table table = KSQL.select(sql, varMap, dataModel, fnModel);
		List<String> ret = new ArrayList<String>();
		if (table.size() > 0) {
			Iterator<Row> i = table.iterator();
			while (i.hasNext()) {
				Row r = i.next();
				ret.add(r.getString("fSequenceValue"));
			}
		}
		return ret;
	}

	@Override
	public void lockUnusedCodeValue(String codeGuid, String groupValue, String relTableName, String sequenceValue, String userTable,
			String userField, String userKeyValues) {
		Model dataModel = ModelUtils.getModel(DEFAULT_DATA_MODEL);
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		// 检查是否已占用某编码,如占用则释放
		String sql = "select B.* from B_SequenceCodeUseRecord B where B.fCodeGuid=:fCodeGuid and B.fGroupValue=:fGroupValue and B.fRelTableName=:fRelTableName and B.fSequenceValue=:fSequenceValue";
		Map<String, Object> varMap = new HashMap<String, Object>();
		varMap.put("fCodeGuid", codeGuid);
		varMap.put("fGroupValue", groupValue);
		varMap.put("fRelTableName", relTableName);
		varMap.put("fSequenceValue", sequenceValue);
		Table table = KSQL.select(sql, varMap, dataModel, fnModel);
		if (table.size() == 0) {
			throw new BusinessException("编码" + sequenceValue + "不存在");
		} else {
			Row r = table.iterator().next();
			if (Utils.isNotEmptyString(r.getString("fUserKeyValues"))) {
				throw new BusinessException("编码" + sequenceValue + "已被他人使用");
			}
			r.setValue("fUserTableName", userTable);
			r.setValue("fUserField", userField);
			r.setValue("fUserKeyValues", userKeyValues);
			table.save(dataModel);
		}
	}

}
