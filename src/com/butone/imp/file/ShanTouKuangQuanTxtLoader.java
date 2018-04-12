package com.butone.imp.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.sort.CompareCallBack;
import com.butone.sort.SortUtils;
import com.justep.exception.BusinessException;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.process.ProcessUtils;
import com.justep.system.transform.SimpleTransform;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

/**
 * 汕头矿权Txt数据加载
 * 
 * @author Administrator
 * 
 */
public class ShanTouKuangQuanTxtLoader implements TableDataImport, TableDataExporter {

	/**
	 * 闭环
	 * 
	 * @param ring
	 */
	private void closeEsriRing(JSONArray ring) {
		JSONArray pt1 = ring.getJSONArray(0);
		JSONArray pt2 = ring.getJSONArray(ring.size() - 1);
		if ((pt1.getBigDecimal(0).compareTo(pt2.getBigDecimal(0)) != 0) || (pt1.getBigDecimal(1).compareTo(pt2.getBigDecimal(1)) != 0)) {
			ring.add(pt1.clone());
		}
	}

	/**
	 * 获得空间参考对象
	 * 
	 * @param jdfd
	 * @param dh
	 * @return
	 */
	private JSONObject getSpatialReference(String zbxt) {
		JSONObject ret = new JSONObject();
		if ("2".equals(zbxt))
			ret.put("wkid", 2363);
		else
			return null;
		return ret;
	}

	private JSONArray getLastRing(JSONObject dk) {
		JSONArray rings = dk.getJSONArray("@rings");
		return rings.getJSONArray(rings.size() - 1);
	}

	private JSONObject createDK() {
		// curDk.put("点数", pointCount);
		JSONObject ret = new JSONObject();
		JSONArray rings = new JSONArray();
		JSONArray ring = new JSONArray();
		rings.add(ring);
		ret.put("@rings", rings);
		return ret;
	}

	/**
	 * 解析数据
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private JSONObject parse(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, "gbk"));

		JSONObject xmxx = new JSONObject();
		JSONArray dkList = new JSONArray();
		xmxx.put("@geometries", dkList);
		JSONObject curDk = null;
		String text = null;
		boolean createDk = false, parseCoord = false;

		while ((text = reader.readLine()) != null) {
			text = text.trim();
			if ("".equals(text))
				continue;
			if (text.indexOf("=") > 0) {
				String[] pArry = text.split("=");
				if (pArry.length == 0)
					continue;
				String key = pArry[0];
				String value = null;
				if (pArry.length == 2)
					value = pArry[1];
				if ("矿区范围拐点坐标与标高的总行数".equals(key)) {
					createDk = true;
					parseCoord = true;
					// curDk.put("点数", pointCount);
				} else if (parseCoord && value != null && value.startsWith("标高,")) {
					createDk = true;
				} else if (parseCoord) {
					if (createDk) {
						curDk = createDK();
						dkList.add(curDk);
						createDk = false;
					}
					String[] pointArry = value.split(",");
					BigDecimal x = new BigDecimal(pointArry[2]);
					BigDecimal y = new BigDecimal(pointArry[1]);
					JSONObject pPoint = new JSONObject();
					pPoint.put("点号", pointArry[0]);
					pPoint.put("x", x);
					pPoint.put("y", y);
					JSONArray ring = getLastRing(curDk);
					ring.add(pPoint);
				} else {
					xmxx.put(key, value == null ? null : value.trim());
				}
			}
		}
		return xmxx;
	}

	/**
	 * 加载数据
	 */
	@Override
	public void loadToTable(InputStream in, Map<String, Table> tables, Map<String, Object> variants) {
		JSONObject xmxx;
		try {
			xmxx = parse(in);
		} catch (Exception e) {
			throw new BusinessException("数据文件解析失败:" + e.getMessage(), e);
		}
		// 项目信息
		Table t_xmxx = tables.get("XMXX");
		Row r_xmxx = tables.get("XMXX").appendRow(CommonUtils.createGUID());
		Iterator<String> itor = xmxx.keySet().iterator();
		while (itor.hasNext()) {
			String key = itor.next();
			if (t_xmxx.getMetaData().containsColumn(key)) {
				String dataType = t_xmxx.getMetaData().getColumnMetaData(key).getType();
				Object value = xmxx.get(key);
				if (value != null) {
					if ((value instanceof String) && !dataType.equals("String")) {
						value = SimpleTransform.transToObj(dataType, (String) value);
					}
					r_xmxx.setValue(key, value);
				}
			}
		}
		// 地块

		// 获得空间参考
		String zbxt = xmxx.getString("坐标系统");
		JSONObject spatialReference = null;
		if (zbxt != null) {
			spatialReference = getSpatialReference(zbxt);
		}

		Table t_dkxx = tables.get("DKXX");
		Table t_jzdzb = tables.get("JZDZB");

		JSONArray dkList = xmxx.getJSONArray("@geometries");
		for (int i = 0; i < dkList.size(); i++) {
			JSONObject geometry = new JSONObject();
			JSONArray geoRings = new JSONArray();
			geometry.put("rings", geoRings);
			geometry.put("spatialReference", spatialReference);

			JSONObject dk = dkList.getJSONObject(i);
			String dkGuid = CommonUtils.createGUID();
			// 添加地块
			Row r_dk = t_dkxx.appendRow(dkGuid);
			int pointCnt = 0;

			JSONArray rings = dk.getJSONArray("@rings");
			for (int j = 0; j < rings.size(); j++) {
				JSONArray dkRing = rings.getJSONArray(j);
				JSONArray geoRing = new JSONArray();
				geoRings.add(geoRing);
				for (int n = 0; n < dkRing.size(); n++) {
					JSONObject pt = dkRing.getJSONObject(n);
					String zbGuid = CommonUtils.createGUID();
					// 地块添加界址点
					Row r_jzb = t_jzdzb.appendRow(zbGuid);

					r_jzb.setString("fDKGUID", dkGuid);
					r_jzb.setString("fDH", pt.getString("点号"));
					r_jzb.setInteger("fQH", j + 1);
					r_jzb.setDecimal("fXZB", pt.getBigDecimal("x"));
					r_jzb.setDecimal("fYZB", pt.getBigDecimal("y"));
					r_jzb.setInteger("fQNXH", n + 1);
					pointCnt++;

					JSONArray zb = new JSONArray();
					zb.add(pt.getBigDecimal("x"));
					zb.add(pt.getBigDecimal("y"));
					geoRing.add(zb);

				}
				closeEsriRing(geoRing);
			}
			r_dk.setInteger("fJZDS", pointCnt);
			r_dk.setString("fTXLX", "polygon");
			r_dk.setString("fTXXX", geometry.toJSONString());
		}
	}

	// /**
	// * 创建表
	// * @param names
	// * @param types
	// * @return
	// */
	// private Table createTable(String names, String types) {
	// Table ret = TableUtils.createTable(null, Arrays.asList(names.split(",")),
	// Arrays.asList(types.split(",")));
	// ret.getMetaData().setKeyColumn(BizDataUtils.MemoryTableKeyColumnName);
	// ret.getProperties().put(Table.PROP_NAME_ROWID,
	// BizDataUtils.MemoryTableKeyColumnName);
	// ret.getMetaData().getColumnMetaData(BizDataUtils.MemoryTableKeyColumnName).setDefine("guid()");
	// return ret;
	// }

	// @Override
	// public Map<String, Table> createDefaultDataModels() {
	// Map<String, Table> ret = new HashMap<String, Table>();
	// ret.put("XMXX", createTable(BizDataUtils.MemoryTableKeyColumnName,
	// BizDataUtils.MemoryTableKeyColumnType));
	// // 界址点数
	// ret.put("DKXX",
	// createTable(BizDataUtils.MemoryTableKeyColumnName + ",fTXXX,fTXLX,fJZDS",
	// BizDataUtils.MemoryTableKeyColumnType
	// + ",String,String,Integer"));
	// // 地块GUID、点号、圈号、X坐标、Y坐标、圈内序号
	// ret.put("JZDZB",
	// createTable(BizDataUtils.MemoryTableKeyColumnName +
	// ",fDKGUID,fDH,fQH,fXZB,fYZB,fQNXH", BizDataUtils.MemoryTableKeyColumnType
	// + ",String,String,Integer,Decimal,Decimal,Integer"));
	// return ret;
	// }

	private String padding(String num) {
		int n = 3 - num.length();
		while (n > 0) {
			num = "0" + num;
			n--;
		}
		return num;
	}

	@Override
	public InputStream tableToStream(Map<String, Table> tables, Map<String, Object> variants) {
		try {
			// 坐标比较器
			CompareCallBack<Row> zbCompare = new CompareCallBack<Row>() {
				@Override
				public int compare(Row a, Row b) {
					int qh1 = a.getInt("fQH"), qnxh1 = a.getInt("fQNXH");
					int qh2 = a.getInt("fQH"), qnxh2 = a.getInt("fQNXH");
					if (qh1 == qh2) {
						return qnxh1 - qnxh2;
					} else {
						return qh1 - qh2;
					}
				}
			};

			BufferedWriter bufWriter = new BufferedWriter(new StringWriter());
			bufWriter.append("[登记项目]\n");
			bufWriter.append("***" + ProcessUtils.getCurrentProcessLabel() + "项目内容***\n");
			bufWriter.newLine();
			Table xmTable = tables.get("XMXX");
			if (xmTable != null && xmTable.size() > 0) {
				Row r = xmTable.iterator().next();
				Iterator<String> i = xmTable.getMetaData().getColumnNames().iterator();
				while (i.hasNext()) {
					String name = i.next();
					Object value = r.getValue(name);
					if (value == null)
						value = "";
					bufWriter.append(name).append("=").append(value.toString()).append("\n");
				}
			}
			Table zbTable = tables.get("JZDZB"), dkTable = tables.get("DKXX");

			if (zbTable != null && dkTable != null) {
				bufWriter.append("矿区范围拐点坐标总数应小于1000个\n");
				bufWriter.append("矿区范围拐点坐标与标高的总行数=  ").append("" + (zbTable.size() + dkTable.size())).append("\n");
				Map<String, List<Row>> dkMap = new HashMap<String, List<Row>>();
				Iterator<Row> i = zbTable.iterator();
				while (i.hasNext()) {
					Row zb = i.next();
					String dkGuid = zb.getString("fDKGUID");
					List<Row> zbList = dkMap.get(dkGuid);
					if (zbList == null) {
						zbList = new ArrayList<Row>();
						dkMap.put(dkGuid, zbList);
					}
					zbList.add(zb);
				}
				Integer ptCnt = 0;
				for (String dkGuid : dkMap.keySet()) {
					List<Row> zbList = dkMap.get(dkGuid);
					Row[] rows = zbList.toArray(new Row[] {});
					SortUtils.sort(rows, zbCompare);
					for (int idx = 0; idx < rows.length; idx++) {
						Row r = rows[idx];
						// XY001=1,2588801.00,39438457.00
						ptCnt++;
						bufWriter.append("XY").append(padding(ptCnt.toString())).append("=").append("" + (idx + 1)).append(",").append(r.getString("fYZB")).append(",").append(r.getString("fXZB"))
								.append("\n");
					}
					Row dk = dkTable.getRow(dkGuid);
					// XY005=标高,220,100,,1
					ptCnt++;
					bufWriter.append("XY").append(padding(ptCnt.toString())).append("=标高,");
					String bg = ",,,";//
					if (dk.getTable().getMetaData().containsColumn("标高")) {
						if (Utils.isNotEmptyString(dk.getString("标高"))) {
							bg = dk.getString("标高");
						}
					}
					bufWriter.append(bg).append("\n");
				}
			}
			bufWriter.close();
			return new ByteArrayInputStream(bufWriter.toString().getBytes());
		} catch (Exception e) {
			throw new BusinessException("数据写入文件失败", e);
		}

	}
}
