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
 * 广东矿产资源Txt文件
 * 
 * @author tangkj
 * 
 */
public class GuangDongMineralTxtLoader implements TableDataImport {
	public static void main(String[] args) {
		try {
			new GuangDongMineralTxtLoader().parse(GuangDongMineralTxtLoader.class.getClassLoader().getResourceAsStream("com/butone/imp/file/GuangDongMineralTxtLoader.txt"));
		} catch (IOException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}
	}

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

		Table t_dkxx = tables.get("DKXX");
		Table t_jzdzb = tables.get("JZDZB");
		JSONObject spatialReference = null;
		if (variants != null && variants.containsKey("wkid")) {
			spatialReference = new JSONObject();
			spatialReference.put("wkid", variants.get("wkid"));
		} else if (variants != null && variants.containsKey("wkt")) {
			spatialReference = new JSONObject();
			spatialReference.put("wkt", variants.get("wkt"));
		}

		JSONArray dkList = xmxx.getJSONArray("@geometries");
		for (int i = 0; i < dkList.size(); i++) {
			JSONObject geometry = new JSONObject();
			JSONArray geoRings = new JSONArray();
			geometry.put("rings", geoRings);
			if (spatialReference != null)
				geometry.put("spatialReference", spatialReference);

			JSONObject dk = dkList.getJSONObject(i);
			String dkGuid = CommonUtils.createGUID();
			// 添加地块
			Row r_dk = t_dkxx.appendRow(dkGuid);
			int pointCnt = 0;
			JSONArray rings = dk.getJSONArray("@rings");
			for (int j = 0; j < rings.size(); j++) {
				JSONArray dkRing = rings.getJSONArray(j);
				if (dkRing.size() == 0)
					continue;
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
		String text = null;
		String lastKey = null;
		while ((text = reader.readLine()) != null) {
			text = text.trim();
			if ("".equals(text))
				continue;
			if (text.indexOf("=") > 0) {
				lastKey = text.substring(0, text.indexOf("="));
				if (!"区域范围".endsWith(lastKey)) {
					String value = text.substring(text.indexOf("=") + 1);
					xmxx.put(lastKey, value == null ? null : value.trim());
				}
			} else if (lastKey != null) {
				if (text.startsWith("***")) {
					xmxx.put(lastKey, xmxx.getString(lastKey) + "\n" + text.substring(3));
				} else if ("区域范围".endsWith(lastKey)) {
					boolean bLandStart = text.startsWith("序号");
					if (bLandStart) {
						curDk = new JSONObject();
						dkList.add(curDk);
						curRing = new JSONArray();
						JSONArray rings = new JSONArray();
						rings.add(curRing);
						curDk.put("@rings", rings);
						continue;
					}
					String[] pointArry = text.split(",");
					if (pointArry.length == 3) {
						JSONObject pPoint = new JSONObject();
						if ("0".equals(pointArry[1]) && "0".equals(pointArry[1])) {
							curRing = new JSONArray();
							curDk.getJSONArray("@rings").add(curRing);
						} else {
							BigDecimal x = new BigDecimal(pointArry[1]);
							BigDecimal y = new BigDecimal(pointArry[2]);
							pPoint.put("点号", pointArry[0]);
							pPoint.put("x", x);
							pPoint.put("y", y);
							curRing.add(pPoint);
						}
					}
				}
			}
		}
		return xmxx;
	}
}
