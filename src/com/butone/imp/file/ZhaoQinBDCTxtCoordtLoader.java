package com.butone.imp.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.justep.exception.BusinessException;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.transform.SimpleTransform;
import com.justep.system.util.CommonUtils;

/**
 * 肇庆不动产Txt坐标
 * 
 * @author Administrator
 * 
 */
public class ZhaoQinBDCTxtCoordtLoader implements TableDataImport {
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
	private JSONObject getSpatialReference(String jdfd, int dh, Map<String, Object> variants) {
		JSONObject ret = new JSONObject();
		if (variants != null && variants.containsKey("wkid")) {
			ret.put("wkid", Integer.parseInt(variants.get("wkid").toString()));
		} else if (variants != null && variants.containsKey("wkt")) {
			ret.put("wkt", variants.get("wkt"));
		} else {
			String wkid;
			if (jdfd.equals("6")) {
				wkid = "23" + (14 + dh);
			} else {
				wkid = "23" + (24 + dh);
			}
			ret.put("wkid", Integer.parseInt(wkid));
		}
		return ret;
	}

	/**
	 * 解析数据
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private JSONObject parse(InputStream in) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, "gbk"));
		JSONObject xmxx = new JSONObject();
		JSONArray dkList = new JSONArray();
		xmxx.put("@geometries", dkList);
		JSONObject curDk = null;
		JSONArray curRing = null;
		String curRingIndex = null;
		String text = null;
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
				xmxx.put(key, value == null ? null : value.trim());
			} else {
				String[] pointArry = text.split(",");
				if (pointArry.length < 4)
					continue;
				boolean bLandStart = pointArry.length > 5;
				if (bLandStart) {
					// 创建新的地块，并闭合之前的环
					curRingIndex = "-1";
					curDk = new JSONObject();
					curDk.put("地块面积", pointArry[1]);
					curDk.put("地块编号", pointArry[2]);
					curDk.put("地块名称", pointArry[3]);
					curDk.put("图形特征", pointArry[4]);
					curDk.put("图幅号", pointArry[5]);
					curDk.put("土地用途", pointArry[6]);
					curDk.put("@rings", new JSONArray());
					dkList.add(curDk);
				} else {
					String ringIndex = pointArry[1];
					BigDecimal x = new BigDecimal(pointArry[3]);
					BigDecimal y = new BigDecimal(pointArry[2]);
					JSONObject pPoint = new JSONObject();
					pPoint.put("点号", pointArry[0]);
					if ("肇庆独立坐标系".equals(xmxx.getString("坐标系"))) {
						BigDecimal pyl = new BigDecimal(400000);
						pPoint.put("x", y.add(pyl));
						pPoint.put("y", x);
					} else {
						pPoint.put("x", x);
						pPoint.put("y", y);
					}
					if (!ringIndex.equals(curRingIndex)) {
						curRing = new JSONArray();
						JSONArray rings = curDk.getJSONArray("@rings");
						rings.add(curRing);
						curRingIndex = ringIndex;
					}
					curRing.add(pPoint);
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
		String jdfd = xmxx.getString("几度分带");
		String dh = xmxx.getString("带号");
		JSONObject spatialReference = null;
		if (jdfd != null && dh != null) {
			spatialReference = getSpatialReference(jdfd, Integer.parseInt(dh), variants);
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
			r_dk.setDecimal("fDKMJ", dk.getBigDecimal("地块面积"));
			r_dk.setString("fDKMC", dk.getString("地块名称"));
			r_dk.setString("fDKBH", dk.getString("地块编号"));
			r_dk.setString("fTFH", dk.getString("图幅号"));
			r_dk.setString("fDKYT", dk.getString("土地用途"));
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

	// /**
	// * 创建默认数据模型
	// */
	// @Override
	// public Map<String, Table> createDefaultDataModels() {
	// Map<String, Table> ret = new HashMap<String, Table>();
	// ret.put("XMXX", createTable(BizDataUtils.MemoryTableKeyColumnName,
	// BizDataUtils.MemoryTableKeyColumnType));
	// // 界址点数、地块面积、地块名称、地块编号、图幅号、地块用途
	// ret.put("DKXX",
	// createTable(BizDataUtils.MemoryTableKeyColumnName +
	// ",fTXXX,fTXLX,fJZDS,fDKMJ,fDKMC,fDKBH,fTFH,fDKYT",
	// BizDataUtils.MemoryTableKeyColumnType +
	// ",String,String,Integer,Decimal,String,String,String,String"));
	// // 地块GUID、点号、圈号、X坐标、Y坐标、圈内序号
	// ret.put("JZDZB",
	// createTable(BizDataUtils.MemoryTableKeyColumnName +
	// ",fDKGUID,fDH,fQH,fXZB,fYZB,fQNXH", BizDataUtils.MemoryTableKeyColumnType
	// + ",String,String,Integer,Decimal,Decimal,Integer"));
	// return ret;
	// }
}
