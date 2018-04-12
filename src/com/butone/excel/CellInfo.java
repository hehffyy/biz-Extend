package com.butone.excel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFCell;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.justep.exception.BusinessException;

public class CellInfo {

	private String dsId;
	private String fieldName;
	private CellKind kind; // 0普通 1 纵向 2横向
	private Map<String, String> pzInfo = new HashMap<String, String>();

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public CellKind getKind() {
		return kind;
	}

	public void setKind(CellKind kind) {
		this.kind = kind;
	}

	public String getDsId() {
		return dsId;
	}

	public void setDsId(String dsId) {
		this.dsId = dsId;
	}

	public Map<String, String> getPzInfo() {
		return pzInfo;
	}

	public void setPzInfo(Map<String, String> pzInfo) {
		this.pzInfo = pzInfo;
	}

	// 解析Excel单元格类型
	public static CellInfo parseCell(String value) {
		String[] temps;
		CellInfo info = new CellInfo();
		if (value != null && value.contains("selectH")) {
			value = value.replace(")", "");
			value = value.replace("selectH(", "");
			temps = value.split("\\.");
			info.setDsId(temps[0]);
			info.setFieldName(temps[1].toUpperCase());
			info.setKind(CellKind.ckSelectH);
		} else if (value != null && value.contains("select")) {
			value = value.replace(")", "");
			value = value.replace("select(", "");
			if (!value.contains("|"))
				temps = value.split("\\.");
			else {
				temps = value.split("\\|");
				String pz = temps[1];
				info.setPzInfo(parsePzInfo(pz));
				temps = temps[0].split("\\.");
			}
			info.setDsId(temps[0]);
			info.setFieldName(temps[1].toUpperCase());
			info.setKind(CellKind.ckSelect);

		} else if (value != null && value.contains("sum")) {
			value = value.replace(")", "");
			value = value.replace("sum(", "");
			if (!value.contains("|"))
				temps = value.split("\\.");
			else {
				temps = value.split("\\|");
				String pz = temps[1];
				info.setPzInfo(parsePzInfo(pz));
				temps = temps[0].split("\\.");
			}
			info.setDsId(temps[0]);
			info.setFieldName(temps[1].toUpperCase());
			info.setKind(CellKind.ckSum);

		} else if (value.contains(".")) {
			if (!value.contains("|"))
				temps = value.split("\\.");
			else {
				temps = value.split("\\|");
				String pz = temps[1];
				info.setPzInfo(parsePzInfo(pz));
				temps = temps[0].split("\\.");
			}
			info.setDsId(temps[0]);
			info.setFieldName(temps[1].toUpperCase());
			info.setKind(CellKind.ckCommon);
		} else
			info = null;
		return info;
	}

	private static Map<String, String> parsePzInfo(String pz) {
		Map<String, String> pzMap = new HashMap<String, String>();
		String[] pzAry = pz.split(",");
		for (String item : pzAry) {
			String[] itemAry = item.split(":");
			pzMap.put(itemAry[0], itemAry[1]);
		}
		return pzMap;
	}

	public static void setCellValue(XSSFCell cell, CellInfo info, String cellVal,JSONObject extendParam) {
		if(extendParam!=null && extendParam.containsKey("hideFields")){
			JSONArray fields= extendParam.getJSONArray("hideFields");
			
			if(fields.contains(info.getFieldName())){
				cell.getSheet().setColumnHidden(cell.getColumnIndex(), true);
			}
				
		}
		if (info.getPzInfo().containsKey("scale")) {

			if (cellVal != null && cellVal.trim().equals("")) {
				cell.setCellValue(cellVal);
				return;
			}
			try {
				BigDecimal dVal = BigDecimal.valueOf(Double
						.parseDouble(cellVal));
				
				if (dVal.compareTo(BigDecimal.ZERO)==0){
					cell.setCellValue("");
					return;
				}
				dVal = dVal.setScale(
						Integer.parseInt(info.getPzInfo().get("scale")),
						RoundingMode.HALF_UP);
				
				
					cell.setCellValue(dVal.toString());
			} catch (Exception e) {
				throw new BusinessException(e.getMessage());
			}

		} else if (info.getPzInfo().containsKey("hideCol")) {
			if (cellVal == null || cellVal.equals("") || cellVal.equals("0")) {
				cell.getSheet().setColumnHidden(cell.getColumnIndex(), true);
			} else {
				cell.setCellValue(cellVal);
			}

		} else {
			cell.setCellValue(cellVal);
		}
	}
}
