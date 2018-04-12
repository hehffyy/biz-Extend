package com.butone.gis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.butone.system.SystemConst;
import com.butone.utils.SysUtils;
import com.justep.exception.BusinessException;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.ColumnTypes;
import com.justep.system.data.Row;
import com.justep.system.data.Table;

public class GisUtils {
	public static final String cJsonSucceed = "succeed";
	public static final String cJsonContent = "content";

	/**
	 * GIS分析
	 * 
	 * @param paramJson
	 * @param trgTables
	 * @return
	 * @throws Exception
	 */
	public static JSONObject gisAnal(JSONObject paramJson, List<Table> trgTables, String soeKey) throws Exception {
		JSONObject resultJson = null;
		try {
			//获取图层和临时表的映射
			Map<Integer, Integer> layerTableMap = new HashMap<Integer, Integer>();
			List<String> targetLayerList = new ArrayList<String>();
			getTargetTable(paramJson.getJSONArray("analParams"), targetLayerList);
			if (targetLayerList.size() != trgTables.size())
				throw new BusinessException("目标图层个数和临时表个数必须一致");
			for (int i = 0; i < trgTables.size(); i++) {
				String[] names = targetLayerList.get(i).split(",");
				for (int j = 0; j < names.length; j++) {
					layerTableMap.put(layerTableMap.size(), i);
				}
			}
			// 修改数据源
			doModifyParam(paramJson);
			System.out.println("GIS分析最终请求参数：" + paramJson.toJSONString());
			// 调用服务
			resultJson = SysUtils.callRest(SystemConst.getParamValue(soeKey) + "//overlapAnal", paramJson);

			// 解析结果
			boolean analState = resultJson.getBoolean("succeed");
			if (analState) {
				JSONArray analArray = resultJson.getJSONArray("content");
				for (int i = 0; i < analArray.size(); i++) {
					JSONObject analOne = analArray.getJSONObject(i);
					if (analOne == null || !analOne.containsKey("name"))
						continue;
					JSONArray rows = analOne.getJSONArray("rows");
					Table table = trgTables.get(layerTableMap.get(i));
					for (int j = 0; j < rows.size(); j++) {
						JSONObject attributes = rows.getJSONObject(j).getJSONObject("attributes");
						jsonToTable(attributes, table);
					}
				}
			}
		} catch (Exception e) {
			resultJson = new JSONObject();
			resultJson.put("succeed", false);
			resultJson.put("content", e.getMessage());
		}
		return resultJson;
	}

	public static void main(String[] args) {
		JSONObject param = JSONObject
				.parseObject("{source:{name:'1|src'},analParams:[{subParams:[{target:{name:'2|21,22,23',version:''}}],target:{name:'11,12,13',version:''},returnFields:'',xzdwLayer:{name:'3|xzdw1,3|xzdw2,3|xzdw3'},returnIntersectedShape:false}]}");
		doModifyParam(param);
		System.out.println(param);
	}

	// 处理图层数据源
	private static void doModifyParam(JSONObject param) {
		// 修改source
		if (param.get("source") instanceof JSONObject)
			_doModifyLayer(param.getJSONObject("source"));
		// 修改analParams
		JSONArray analParams = param.getJSONArray("analParams");
		_doModifySubParam(analParams);
	}

	private static void _doModifySubParam(JSONArray subParams) {
		for (int i = 0; i < subParams.size(); i++) {
			JSONObject analParam = subParams.getJSONObject(i);
			JSONObject target = analParam.getJSONObject("target");
			String layerName = target.getString("name");
			//修复多图层同结构的JSON
			if (layerName.contains(",")) {
				String[] names = layerName.split(",");
				JSONObject xzdwLayer = null;
				String[] xzdwNames = null;
				if (analParam.containsKey("xzdwLayer")) {
					xzdwLayer = analParam.getJSONObject("xzdwLayer");
					xzdwNames = xzdwLayer.getString("name").split(",");
				}
				for (int j = 0; j < names.length; j++) {
					if (j == 0) {
						target.put("name", names[0]);
						if (xzdwNames != null)
							xzdwLayer.put("name", xzdwNames[0]);
					} else {
						JSONObject newAnalParam = JSONObject.parseObject(analParam.toJSONString());
						JSONObject newTarget = newAnalParam.getJSONObject("target");
						newTarget.put("name", names[j]);
						if (xzdwNames != null){
							newAnalParam.getJSONObject("xzdwLayer").put("name", xzdwNames[j]);
						}
						subParams.add(i + j, newAnalParam);
					}
				}
			}
			_doModifyLayer(target);
			if (analParam.containsKey("xzdwLayer"))
				_doModifyLayer(analParam.getJSONObject("xzdwLayer"));
			if (analParam.containsKey("subParams"))
				_doModifySubParam(analParam.getJSONArray("subParams"));
		}
	}

	private static void _doModifyLayer(JSONObject layerJson) {
		String layerName = "";
		String wsid = "";
		String[] tempAry = null;
		if (layerJson.containsKey("name")) {
			layerName = layerJson.getString("name");
			if (layerName.contains("|")) {
				tempAry = layerName.split("\\|");
				wsid = tempAry[0];
				layerName = tempAry[1];
				layerJson.put("wsid", wsid);
				layerJson.put("name", layerName);
			}
		}
	}

	//获得目标图层
	private static void getTargetTable(JSONArray subParams, List<String> list) {
		for (int i = 0; i < subParams.size(); i++) {
			JSONObject analParam = subParams.getJSONObject(i);
			JSONObject target = analParam.getJSONObject("target");
			String layerName = target.getString("name");
			list.add(layerName);
			if (analParam.containsKey("subParams"))
				getTargetTable(analParam.getJSONArray("subParams"), list);
		}
	}

	public static void jsonToTable(JSONObject json, Table table) throws Exception {
		Row row = table.appendRow();
		Object[] keys = json.keySet().toArray();
		for (Object key : keys) {
			String column = key.toString().toUpperCase();
			Object value = null;
			if (table.getColumnNames().contains(column)) {
				try {
					ColumnMetaData meata = table.getMetaData().getColumnMetaData(column);
					value = json.get(key);
					if (value == null)
						continue;
					String dataType = meata.getType();
					if (dataType.equalsIgnoreCase(ColumnTypes.DECIMAL)) {
						value = BigDecimal.valueOf(Double.parseDouble(value.toString()));
					} else if (dataType.equalsIgnoreCase(ColumnTypes.FLOAT)) {
						value = Float.valueOf(value.toString());
					}
					if (value != null)
						row.setValue(column, value);
				} catch (Exception e) {
					throw new Exception(column + "字段 赋值异常 ：" + value + "," + e.getMessage());
				}
			}
		}
	}

	/**
	 * 坐标准换
	 * 
	 * @param paramJson
	 * @return
	 * @throws Exception
	 */
	public static String geometrysTrans(int kind, String geometrys, String convertName) throws Exception {
		try {
			JSONObject paramJson = new JSONObject();
			paramJson.put("kind", kind);
			paramJson.put("geometrys", JSONArray.parseObject(geometrys));
			paramJson.put("convertName", convertName);
			paramJson.put("f", "pjson");
			// 调用服务
			JSONObject resultJson = SysUtils.callRest(SystemConst.getParamValue("bizSoeUrl") + "//geometrysTrans", paramJson);
			// 解析
			boolean analState = resultJson.getBoolean("succeed");
			if (analState) {
				resultJson = resultJson.getJSONObject(cJsonContent);
				return resultJson.toString();
			} else
				throw new Exception(resultJson.getString(cJsonContent));
		} catch (Exception e) {
			throw new Exception("坐标转换异常:" + e.getMessage());
		}
	}

	/**
	 * 面积计算
	 * 
	 * @param paramJson
	 * @return
	 * @throws Exception
	 */
	public static double calPolygonArea(String geometry, String convertName) throws Exception {
		try {
			JSONObject paramJson = new JSONObject();
			paramJson.put("geometry", JSONObject.parseObject(geometry));
			paramJson.put("convertName", convertName);
			// 调用服务
			JSONObject resultJson = SysUtils.callRest(SystemConst.getParamValue("bizSoeUrl") + "//calPolygonArea", paramJson);
			// 解析
			boolean analState = resultJson.getBoolean(cJsonSucceed);
			if (analState) {
				return resultJson.getDouble(cJsonContent);
			} else
				throw new Exception(resultJson.getString(cJsonContent));
		} catch (Exception e) {
			throw new Exception("计算面积异常:" + e.getMessage());
		}
	}

	/**
	 * Geometry Json
	 */
}
