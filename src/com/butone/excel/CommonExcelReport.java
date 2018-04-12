package com.butone.excel;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.alibaba.fastjson.JSONObject;
import com.butone.doc.DocExUtils;
import com.justep.system.data.Row;
import com.justep.util.Utils;

public class CommonExcelReport {
	private List<Map<String, List<Row>>> pageDataList = null;// 分页数据
	private ExcelPageInfo pageInfo = null;// 模板页面配置
	private JSONObject extendParamJson = null;
	private XSSFWorkbook curWorkBook;
	private XSSFSheet curSheet;

	// 初始化配置数据
	public void initConfig(String config, String template, JSONObject sqlParam, String extendParam,String customSql) throws Exception {
		BufferedInputStream in = null;
		try {
			JSONObject configJson = JSONObject.parseObject(config);
			in = new BufferedInputStream(new FileInputStream(new File(template)));
			curWorkBook = new XSSFWorkbook(in);
			curSheet =  curWorkBook.getSheetAt(0);
			// 先获取每页数据
			pageDataList = pageDataList(configJson.getJSONObject("dsConfig"), sqlParam, customSql);
			pageInfo = parsePageInfo(configJson.getJSONObject("pageConfig"));
			if (extendParam != null)
				this.extendParamJson = JSONObject.parseObject(extendParam);
		} finally {
			if (in != null)
				in.close();
		}
	}

	// 产生Excel报表文件
	public String genExcelReport() throws Exception {
		drawExcel();
		String name = java.util.UUID.randomUUID().toString() + ".xlsx";
		String fullFileName = DocExUtils.getDocTempDir() + File.separator + name;
		File file = new File(fullFileName);
		if (file.exists())
			file.delete();
		FileOutputStream output;
		try {
			output = new FileOutputStream(file);
			try {
				curWorkBook.write(output);
				output.flush();
			} finally {
				output.close();
			}
		} catch (Exception e) {
			throw new RuntimeException("导出excel失败!", e);
		}
		return fullFileName;
	}

	//产生pdf报表文件
	public String genReport(String fileType) throws Exception {
		String exceFile = genExcelReport();
		if (fileType.equalsIgnoreCase("excel")) {
			return exceFile;
		}
		String pdfName = java.util.UUID.randomUUID().toString() + ".pdf";
		String pdfFullFile = DocExUtils.getDocTempDir() + File.separator + pdfName;
		ExcelReportUtils.excel2pdf(exceFile, pdfFullFile);
		return pdfFullFile;
	}

	// 解析批注信息
	private void doPzInfo(XSSFCell cell) throws Exception {
		Map<String, String> resultMap = new HashMap<String, String>();

		XSSFComment comment = cell.getCellComment();

		if (comment != null) {
			String cellVal = cell.getStringCellValue();
			String commentValue = comment.getString().getString();
			if (commentValue != null && !commentValue.equals("")) {
				String[] items = commentValue.split("\r\n");
				for (String item : items) {
					String[] strAry = item.split(":");
					if (strAry.length != 2)
						continue;
					String cmKind = strAry[0];
					String cmVal = strAry[1];
					if (cmKind.equalsIgnoreCase("scale")) {

						if (cellVal != null && cellVal.trim().equals(""))
							continue;
						try {
							BigDecimal dVal = BigDecimal.valueOf(Double.parseDouble(cellVal));
							dVal = dVal.setScale(Integer.parseInt(cmVal), RoundingMode.HALF_UP);
							cell.setCellValue(dVal.toString());

						} catch (Exception e) {
							throw new Exception(e.getMessage());
						}

					} else if (cmKind.equalsIgnoreCase("hideCol")) {
						if (cellVal == null || cellVal.equals("") || cellVal.equals("0")) {
							curSheet.setColumnHidden(cell.getColumnIndex(), true);
						}

					}
				}
			}
		}

		if (comment != null) {
			cell.removeCellComment();
		}
	}

	// 绘制后特殊处理
	private void afterDrawExcel() throws Exception {
		int totalRow = (pageInfo.getEndRow() - pageInfo.getStartRow()) + 1;
		for (int iPage = 1; iPage <= pageDataList.size(); iPage++) {
			for (int i = iPage * totalRow; i < (iPage + 1) * totalRow; i++) {
				boolean bNoData = true;
				XSSFRow row = curSheet.getRow(i);
				if (row == null || row.getHeight() == 1)
					continue;
				for (int j = pageInfo.getStartCol(); j < pageInfo.getEndCol(); j++) {
					XSSFCell tempCell = curSheet.getRow(i).getCell(j);
					if (tempCell == null)
						continue;
					String tempCellValue="";
					if(tempCell.getCellType()==XSSFCell.CELL_TYPE_NUMERIC)
						tempCellValue = tempCell.getNumericCellValue()+"";
					else
					 tempCellValue = tempCell.getStringCellValue();
					// 判断是否空数据
					if (tempCellValue != null && !tempCellValue.equals("")) {
						bNoData = false;
					}
					// 处理批注信息
					// doPzInfo(tempCell);
				}
				// 删除空行
				if (bNoData) {
					PoiUtils.delteRows(curSheet, i, i + 1);
				}
			}
		}
	}

	// 画Excel
	private void drawExcel() throws Exception {
		// 合并单元号数量
		int regions = curSheet.getNumMergedRegions();
		// 总行数
		int totalRow = (pageInfo.getEndRow() - pageInfo.getStartRow()) + 1;
		// 每页数据集游标
		Map<String, Integer> dsCursor = new HashMap<String, Integer>();
		//预先绘制
		for (int iPage = 1; iPage < pageDataList.size(); iPage++) {
			dsCursor.clear();
			PoiUtils.copyRows(curWorkBook, pageInfo.getStartRow(), pageInfo.getEndRow()+1, pageInfo.getStartRow() + totalRow * iPage - 1, regions);
		}
		// 循环页数
		for (int iPage = 0; iPage < pageDataList.size(); iPage++) {
			boolean isStop = false;
			dsCursor.clear();
			// 循环行数
			for (int i = pageInfo.getStartRow() + iPage * totalRow; i < pageInfo.getStartRow() + (iPage + 1) * totalRow; i++) {
				// 本页数据
				Map<String, List<Row>> curPageData = pageDataList.get(iPage);
				XSSFRow row = curSheet.getRow(i);
				if (row == null)
					continue;
				// 循环列数
				for (int j = pageInfo.getStartCol(); j < pageInfo.getEndCol(); j++) {
					XSSFCell cell = row.getCell(j);
					if (cell == null)
						continue;
					String cellValue = cell.getStringCellValue().trim();
					CellInfo cellInfo = CellInfo.parseCell(cellValue);
					if (cellInfo == null)
						continue;
					List<Row> dataRows = curPageData.get(cellInfo.getDsId());
					if (dataRows == null || dataRows.size() == 0) {
						cell.setCellValue("");
						continue;
					}

					if (cellInfo.getKind() == CellKind.ckCommon) {
						if (dataRows == null || dsCursor.get(cellInfo.getDsId()) == null || dataRows.size() < dsCursor.get(cellInfo.getDsId()) + 1) {
							cell.setCellValue("");
							continue;
						} else {
							String cellVal = PoiUtils.getCellValue(dataRows.get(dsCursor.get(cellInfo.getDsId())), cellInfo.getFieldName());
							CellInfo.setCellValue(cell, cellInfo, cellVal, this.extendParamJson);

						}

					} else if (cellInfo.getKind() == CellKind.ckSelect) {
						if (!dsCursor.containsKey(cellInfo.getDsId()))
							dsCursor.put(cellInfo.getDsId(), -1);
						dsCursor.put(cellInfo.getDsId(), dsCursor.get(cellInfo.getDsId()) + 1);
						if (dataRows.size() < dsCursor.get(cellInfo.getDsId()) + 1) {
							cell.setCellValue("");
							continue;
						} else {
							String cellVal = PoiUtils.getCellValue(dataRows.get(dsCursor.get(cellInfo.getDsId())), cellInfo.getFieldName());
							CellInfo.setCellValue(cell, cellInfo, cellVal, this.extendParamJson);
						}

					} else if (cellInfo.getKind() == CellKind.ckSum) {
						Double total = 0D;
						for (Row row1 : dataRows) {
							Object row1Val = row1.getValue(cellInfo.getFieldName());
							if (row1Val == null)
								continue;
							else
								total = total + Double.parseDouble(row1Val.toString());
						}
						String cellVal = "" + total;
						CellInfo.setCellValue(cell, cellInfo, cellVal, this.extendParamJson);

					} else if (cellInfo.getKind() == CellKind.ckSelectH) {
						List<Row> dataRowsH = curPageData.get(cellInfo.getDsId());
						XSSFCell cellH = curSheet.getRow(i).getCell(j);
						CellInfo cellInfoH = CellInfo.parseCell(cellH.getStringCellValue());
						if (cellInfoH == null)
							continue;
						String filedName = cellInfoH.getFieldName();
						// 遍历数据 纵向展现
						int iDataIndex = 0;
						for (int iHCol = j; iHCol < pageInfo.getEndCol(); iHCol++) {
							XSSFCell tempCell = curSheet.getRow(i).getCell(iHCol);
							if (tempCell == null)
								continue;
							if (dataRowsH.size() <= iDataIndex) {
								tempCell.setCellValue("");
								continue;
							}
							CellInfo tempCellInfo = CellInfo.parseCell(tempCell.getStringCellValue());
							if (tempCellInfo != null && tempCellInfo.getDsId() != null) {
								Object value = dataRowsH.get(iDataIndex).getValue(filedName);
								String tempValue = "";
								if (value != null)
									tempValue = value.toString();
								tempCell.setCellValue(tempValue);
								iDataIndex++;
							}
						}
						break;
					}
					;
					if (isStop)
						break;
				}
				if (isStop)
					break;
			}
		}
		//PoiUtils.delteRows(curSheet, pageInfo.getStartRow(), pageInfo.getEndRow());
		afterDrawExcel();
	}

	// 解析分页数据
	private List<Map<String, List<Row>>> pageDataList(JSONObject dsConfigJson, JSONObject slqParams,String customSql) {
		// 解析数据源
		ExcelDataSource dataSource = (ExcelDataSource) JSONObject.toJavaObject(dsConfigJson, ExcelDataSource.class);
	   // 用户自定义sql
		if(Utils.isNotEmptyString(customSql)){
			dataSource.setSql(customSql);
		}
		
		dataSource.parseSql(slqParams);
		List<Map<String, List<Row>>> list = new ArrayList<Map<String, List<Row>>>();
		List<Row> rowList = dataSource.getDataRows(null);
		int pageSize = (int) Math.ceil((double) rowList.size() / dataSource.getPageSize());
		for (int i = 1; i <= pageSize; i++) {
			Map<String, List<Row>> pageData = new HashMap<String, List<Row>>();
			int fromIndex = (i - 1) * dataSource.getPageSize();
			int toIndex = i * dataSource.getPageSize();
			if (toIndex > rowList.size())
				toIndex = rowList.size();
			List<Row> pageRowList = rowList.subList(fromIndex, toIndex);
			pageData.put(dataSource.getName(), pageRowList);
			list.add(pageData);
			if (dataSource.getChildren() == null)
				continue;
			for (int j = 1; j <= pageRowList.size(); j++) {
				for (ExcelDataSource cld : dataSource.getChildren()) {
					cld.parseSql(slqParams);
					List<Row> cldRowList = cld.getDataRows(pageRowList.get(j - 1));
					int cldPageSize = (int) Math.ceil((double) cldRowList.size() / cld.getPageSize());
					if (cldPageSize == 0) {
						pageData.put(cld.getName(), new ArrayList<Row>());
						continue;
					}
					for (int e = 1; e <= cldPageSize; e++) {
						int cldFromIndex = (e - 1) * cld.getPageSize();
						int cldToIndex = e * cld.getPageSize();
						if (cldToIndex > cldRowList.size())
							cldToIndex = cldRowList.size();
						List<Row> cldPageRowList = cldRowList.subList(cldFromIndex, cldToIndex);
						if (e == 1)
							pageData.put(cld.getName(), cldPageRowList);
						else {
							pageData = new HashMap<String, List<Row>>();
							pageData.put(dataSource.getName(), pageRowList);
							pageData.put(cld.getName(), cldPageRowList);
							list.add(pageData);
						}
					}
				}
			}
		}
		return list;
	}

	// 解析页面信息
	private ExcelPageInfo parsePageInfo(JSONObject configJson) {
		ExcelPageInfo pageInfo = (ExcelPageInfo) JSONObject.toJavaObject(configJson, ExcelPageInfo.class);
		return pageInfo;
	}

}
