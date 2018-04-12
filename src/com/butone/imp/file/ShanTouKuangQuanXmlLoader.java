package com.butone.imp.file;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.sort.CompareCallBack;
import com.butone.sort.SortUtils;
import com.justep.exception.BusinessException;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.TableMetaData;
import com.justep.system.transform.SimpleTransform;
import com.justep.system.util.CommonUtils;
import com.justep.util.Utils;

/**
 * 汕头矿权xml数据加载
 * @author Administrator
 *
 */
public class ShanTouKuangQuanXmlLoader implements TableDataImport, TableDataExporter {

	/**
	* 闭环
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

	private void pickupElementValue(Element ele, JSONObject data) {
		@SuppressWarnings("unchecked")
		List<Element> children = ele.elements();
		if (children.size() == 0) {
			data.put(ele.getName(), ele.getTextTrim());
		} else {
			for (Element sub : children)
				pickupElementValue(sub, data);
		}
	}

	/**
	 * 解析数据
	 * @param in
	 * @return
	 * @throws Exception
	 */
	private JSONObject parse(InputStream in) {
		JSONObject xmxx = new JSONObject();
		JSONArray dkList = new JSONArray();
		xmxx.put("@geometries", dkList);

		SAXReader reader = new SAXReader();
		Element app = null;
		try {
			app = reader.read(in).getRootElement();
		} catch (Exception e) {
			throw new BusinessException("文件格式错误");
		}

		pickupElementValue(app, xmxx);
		String dkInfo = xmxx.getString("NA_AREA_COORDINATE");
		if (Utils.isNotEmptyString(dkInfo)) {
			String[] args = dkInfo.split(",");
			int dkCnt = Integer.parseInt(args[0].trim());
			int pos = 1;
			while (dkCnt > 0) {
				int pointCount = Integer.parseInt(args[pos].trim());
				JSONObject curDk = new JSONObject();
				dkList.add(curDk);
				curDk.put("点数", pointCount);
				JSONArray rings = new JSONArray();
				JSONArray ring = new JSONArray();
				rings.add(ring);
				curDk.put("@rings", rings);
				for (int idx = 1; idx <= pointCount; idx++) {
					BigDecimal x = new BigDecimal(args[pos + idx * 3]);
					BigDecimal y = new BigDecimal(args[pos + idx * 3 - 1]);
					JSONObject pPoint = new JSONObject();
					pPoint.put("点号", args[idx * 3 - 1]);
					pPoint.put("x", x);
					pPoint.put("y", y);
					ring.add(pPoint);
				}
				pos += pointCount * 3 + 1 + 4;
				dkCnt--;
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
		xmxx = parse(in);
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
		String zbxt = xmxx.getString("IN_COORDINATE_SYSTEM");
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

	//	/**
	//	 * 创建表
	//	 * @param names
	//	 * @param types
	//	 * @return
	//	 */
	//	private Table createTable(String names, String types) {
	//		Table ret = TableUtils.createTable(null, Arrays.asList(names.split(",")), Arrays.asList(types.split(",")));
	//		ret.getMetaData().setKeyColumn(BizDataUtils.MemoryTableKeyColumnName);
	//		ret.getProperties().put(Table.PROP_NAME_ROWID, BizDataUtils.MemoryTableKeyColumnName);
	//		ret.getMetaData().getColumnMetaData(BizDataUtils.MemoryTableKeyColumnName).setDefine("guid()");
	//		return ret;
	//	}

	//	@Override
	//	public Map<String, Table> createDefaultDataModels() {
	//		Map<String, Table> ret = new HashMap<String, Table>();
	//		ret.put("XMXX", createTable(BizDataUtils.MemoryTableKeyColumnName, BizDataUtils.MemoryTableKeyColumnType));
	//		// 界址点数
	//		ret.put("DKXX",
	//				createTable(BizDataUtils.MemoryTableKeyColumnName + ",fTXXX,fTXLX,fJZDS", BizDataUtils.MemoryTableKeyColumnType
	//						+ ",String,String,Integer"));
	//		// 地块GUID、点号、圈号、X坐标、Y坐标、圈内序号
	//		ret.put("JZDZB",
	//				createTable(BizDataUtils.MemoryTableKeyColumnName + ",fDKGUID,fDH,fQH,fXZB,fYZB,fQNXH", BizDataUtils.MemoryTableKeyColumnType
	//						+ ",String,String,Integer,Decimal,Decimal,Integer"));
	//		return ret;
	//	}

	private void setElementText(Element e, Row xmxx, TableMetaData meta) {
		if (e.elements().size() == 0) {
			e.setText("");
			String name = e.getName();
			if (meta.containsColumn(name)) {
				Object value = xmxx.getValue(name);
				if (value != null) {
					e.setText(value.toString());
				}
			}
		} else {
			@SuppressWarnings("unchecked")
			Iterator<Element> i = e.elementIterator();
			while (i.hasNext()) {
				setElementText(i.next(), xmxx, meta);
			}
		}
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

			SAXReader reader = new SAXReader();
			Document doc = reader.read(this.getClass().getResourceAsStream("ShanTouKuangQuanXmlLoader.xml"));
			Table xmTable = tables.get("XMXX");
			if (xmTable != null && xmTable.size() > 0) {
				setElementText(doc.getRootElement(), xmTable.iterator().next(), xmTable.getMetaData());
			}
			Table zbTable = tables.get("JZDZB"), dkTable = tables.get("DKXX");
			if (zbTable != null && dkTable != null) {
				// 地块数量
				String NA_AREA_COORDINATE = dkTable.size() + ",";
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
				for (String dkGuid : dkMap.keySet()) {
					List<Row> zbList = dkMap.get(dkGuid);
					Row[] rows = zbList.toArray(new Row[] {});
					SortUtils.sort(rows, zbCompare);
					// 点数，不含标高
					NA_AREA_COORDINATE += rows.length + ",";
					for (int idx = 0; idx < rows.length; idx++) {
						Row r = rows[idx];
						//1,2588801.00,39438457.00,
						NA_AREA_COORDINATE += (idx + 1) + "," + r.getString("fYZB") + "," + r.getString("fXZB") + ",";
					}
					Row dk = dkTable.getRow(dkGuid);
					//标高=220,100,,1
					String bg = ",,,";//
					if (dk.getTable().getMetaData().containsColumn("标高")) {
						if (Utils.isNotEmptyString(dk.getString("标高"))) {
							bg = dk.getString("标高");
						}
					}
					NA_AREA_COORDINATE += bg + ",";
				}
				doc.selectSingleNode("APP/DATA/NA_AREA_COORDINATE").setText(NA_AREA_COORDINATE);
			}
			return new ByteArrayInputStream(doc.asXML().getBytes());
		} catch (Exception e) {
			throw new BusinessException("数据写入文件失败", e);
		}
	}
}
