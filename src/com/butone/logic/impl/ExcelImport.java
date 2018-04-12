package com.butone.logic.impl;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.dom4j.Document;
import org.dom4j.Element;

import com.butone.data.BizDataUtils;
import com.justep.exception.BusinessException;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.transform.SimpleTransform;
import com.justep.util.Utils;

public class ExcelImport {
	class ImportRelationConfig {
		private String metaName;
		private String metaLabel;
		private String type;
		private int excelCellIndex;
		private String datePattern;
		private boolean isCheck;

		public ImportRelationConfig(Element mappingRelationElement) {
			metaName = mappingRelationElement.attributeValue("name");
			type = mappingRelationElement.attributeValue("value-type");
			if (null == type || "".equals(type))
				type = "String";
			else
				type = type.toUpperCase();
			metaLabel = metaName;
			excelCellIndex = Integer.parseInt(mappingRelationElement.attributeValue("cell-number")) - 1;
			datePattern = mappingRelationElement.attributeValue("date-pattern");
			isCheck = "true".equalsIgnoreCase(mappingRelationElement.attributeValue("check"));
		}

		public String getMetaName() {
			return metaName;
		}

		public void setMetaName(String metaName) {
			this.metaName = metaName;
		}

		public String getMetaLabel() {
			return metaLabel;
		}

		public void setMetaLabel(String metaLabel) {
			this.metaLabel = metaLabel;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public int getExcelCellIndex() {
			return excelCellIndex;
		}

		public void setExcelCellIndex(int excelCellIndex) {
			this.excelCellIndex = excelCellIndex;
		}

		public String getDatePattern() {
			return datePattern;
		}

		public void setDatePattern(String datePattern) {
			this.datePattern = datePattern;
		}

		public boolean isCheck() {
			return isCheck;
		}

		public void setCheck(boolean isCheck) {
			this.isCheck = isCheck;
		}
	}

	class ImportRange {
		private int start;
		private int end;

		public int getStart() {
			return start;
		}

		public void setStart(int start) {
			this.start = start;
		}

		public int getEnd() {
			return end;
		}

		public void setEnd(int end) {
			this.end = end;
		}
	}

	class ImportConceptConfig {
		private List<ImportRelationConfig> relationConfigs = new ArrayList<ImportRelationConfig>();
		private String name;
		private boolean useKey = true;
		private List<Integer> cellNumbers = new ArrayList<Integer>();
		private List<String> types = new ArrayList<String>();
		private List<String> datePatterns = new ArrayList<String>();
		private List<Boolean> checks = new ArrayList<Boolean>();

		public ImportConceptConfig(Element mappingElement) {
			Element mappConceptE = mappingElement.element("concept");
			if (null == mappConceptE)
				throw new BusinessException("Excel导入缺少目标表配置信息");
			name = mappConceptE.attributeValue("name");
			try {
				List<?> keyEs = mappConceptE.element("primary-key").elements("key-value");
				for (Object keyE : keyEs) {
					Element e = ((Element) keyE);
					cellNumbers.add(new Integer(Integer.parseInt(e.attributeValue("cell-number")) - 1));
					String type = e.attributeValue("value-type");
					checks.add("true".equalsIgnoreCase(e.attributeValue("check")));
					if (null == type || "".equals(type))
						type = "String";
					types.add(type);
					datePatterns.add(e.attributeValue("date-pattern"));
				}
			} catch (Exception e) {
				useKey = false;
			}
			List<?> relationEs = mappConceptE.elements("relation");
			for (Object relationE : relationEs) {
				ImportRelationConfig o = new ImportRelationConfig((Element) relationE);
				relationConfigs.add(o);
			}
		}

		public List<ImportRelationConfig> getRelationConfigs() {
			return relationConfigs;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isUseKey() {
			return useKey;
		}

		public void setUseKey(boolean useKey) {
			this.useKey = useKey;
		}

		public List<Integer> getCellNumbers() {
			return cellNumbers;
		}

		public void setCellNumbers(List<Integer> cellNumbers) {
			this.cellNumbers = cellNumbers;
		}

		public List<String> getTypes() {
			return types;
		}

		public List<String> getDatePatterns() {
			return datePatterns;
		}

		public List<Boolean> getChecks() {
			return checks;
		}

	}

	class ImportConfig {
		private Element mappingE = null;
		private ImportConceptConfig conceptConfig;

		public ImportConfig(Element mappingElement) {
			mappingE = mappingElement;
			conceptConfig = new ImportConceptConfig(mappingElement);
		}

		public ImportRange getRowRange(int sheet, Workbook workBook) {
			ImportRange result = new ImportRange();
			int start = 1;
			int end = workBook.getSheetAt(sheet).getPhysicalNumberOfRows();
			Element E = mappingE.element("default-config");
			if (null != E) {
				Element rowE = E.element("row");
				if ("false".equalsIgnoreCase(rowE.attributeValue("all"))) {
					try {
						int i = Integer.parseInt(rowE.attributeValue("start"));
						start = i > start ? i : start;
						i = Integer.parseInt(rowE.attributeValue("end"));
						end = i < end ? i : end;
					} catch (Exception e) {
					}
				}
			}
			result.setStart(start);
			result.setEnd(end);
			return result;
		}

		public ImportRange getSheetRange(int sheetNum) {
			ImportRange result = new ImportRange();
			int start = 1;
			int end = sheetNum;
			Element E = mappingE.element("default-config");
			if (null != E) {
				Element sheetE = E.element("sheet");
				if ("false".equalsIgnoreCase(sheetE.attributeValue("all"))) {
					try {
						int i = Integer.parseInt(sheetE.attributeValue("start"));
						start = i > start ? i : start;
						i = Integer.parseInt(sheetE.attributeValue("end"));
						end = i < end ? i : end;
					} catch (Exception e) {
					}
				}
			}
			result.setStart(start);
			result.setEnd(end);
			return result;
		}

		public ImportConceptConfig getConceptConfig() {
			return conceptConfig;
		}

	}

	private Workbook workBook = null;
	private FormulaEvaluator evaluator = null;
	private ImportConfig importConfig = null;

	public ExcelImport() {
	}

	private void init(InputStream excelFile, Document configDocument) {
		LoadExcel(excelFile);
		importConfig = new ImportConfig(configDocument.getRootElement());
	}

	private void LoadExcel(InputStream excelFile) {
		try {
			excelFile.reset();
			workBook = org.apache.poi.ss.usermodel.WorkbookFactory.create(excelFile);
			evaluator = workBook.getCreationHelper().createFormulaEvaluator();
		} catch (Exception e) {
			throw new BusinessException("打开Excel文件失败", e);
		}
	}

	private boolean isMemoryTable(Table table) {
		try {
			table.getMetaData().getKeyColumnMetaData().getDefineModelObject();
			return false;
		} catch (Exception e) {
			return true;
		}

	}

	public int generate(InputStream excelFile, Document configDocument, Table table) {
		init(excelFile, configDocument);

		ImportConfig importConfig = getImportConfig();
		ImportRange sheetRange = importConfig.getSheetRange(workBook.getNumberOfSheets());
		for (int i = sheetRange.getStart() - 1; i <= sheetRange.getEnd() - 1; i++) {
			ImportRange rowRange = importConfig.getRowRange(i, workBook);
			generateSheet(i, rowRange.getStart() - 1, rowRange.getEnd() - 1, table);
		}
		return table.size();
	}

	private boolean cellIsBlank(Cell cell) {
		if (null == cell)
			return true;
		int cellType = cell.getCellType();
		if (Cell.CELL_TYPE_FORMULA == cellType)
			cellType = evaluator.evaluateFormulaCell(cell);
		return cellType == Cell.CELL_TYPE_BLANK;
	}

	/*
	 * 判断导入的excel行数据是否有效
	 */
	private boolean isValidRowData(org.apache.poi.ss.usermodel.Row row, ImportConfig importConfig) {
		if (null != row) {
			ImportConceptConfig conceptConfig = importConfig.getConceptConfig();
			for (int i = 0; i < conceptConfig.getChecks().size(); i++) {
				if (conceptConfig.getChecks().get(i)) {
					Cell cell = row.getCell(conceptConfig.getCellNumbers().get(i));
					if (cellIsBlank(cell))
						return false;
				}
			}
			List<ImportRelationConfig> relationConfigs = conceptConfig.getRelationConfigs();
			for (ImportRelationConfig rc : relationConfigs) {
				if (rc.isCheck()) {
					Cell cell = row.getCell(rc.getExcelCellIndex());
					if (cellIsBlank(cell))
						return false;
				}
			}
			return true;
		} else
			return false;
	}

	private void generateSheet(int sheetIndex, int rowStart, int rowEnd, Table t) {
		Sheet sheet = this.workBook.getSheetAt(sheetIndex);
		boolean isMemoryTable = isMemoryTable(t);
		for (int i = rowStart; i <= rowEnd; i++) {
			org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
			if (!isValidRowData(row, getImportConfig()))
				continue;
			Row newData = null;
			if (isMemoryTable)
				newData = BizDataUtils.createMemoryRow(t);
			else
				newData = BizDataUtils.createRow(t, this.importConfig.conceptConfig.getName(), null, "/base/core/logic/fn");
			generateRecord(newData, row, sheetIndex, i);
		}
	}

	private void generateRecord(Row tableRow, org.apache.poi.ss.usermodel.Row row, int sheetIndex, int rowIndex) {
		setCellsValue(tableRow, row, sheetIndex, rowIndex);
		setPrimarykey(tableRow, row, sheetIndex, rowIndex);
	}

	private void setPrimarykey(Row tableRow, org.apache.poi.ss.usermodel.Row row, int sheetIndex, int rowIndex) {
		ImportConceptConfig conceptConfig = getImportConfig().getConceptConfig();
		if (Utils.isNotNull(tableRow)) {
			Table t = tableRow.getTable();
			if (conceptConfig.isUseKey()) {
				// TODO lzg 只是支持单主键，多主键通过keyrelation方式完成
				if (1 == conceptConfig.getCellNumbers().size()) {
					String idColumn = (String) t.getProperties().get(Table.PROP_NAME_ROWID);
					Cell cell = row.getCell(conceptConfig.getCellNumbers().get(0).intValue());
					if (t.getMetaData().containsColumn(idColumn)) {
						String dataType = t.getMetaData().getColumnMetaData(idColumn).getType();
						Object keyValue = getValue(cell, dataType, conceptConfig.getDatePatterns().get(0), sheetIndex, rowIndex);
						if (Utils.isNotNull(keyValue))
							tableRow.setValue(idColumn, keyValue);
					}
				}
			}
		}
	}

	private void setCellsValue(Row tableRow, org.apache.poi.ss.usermodel.Row row, int sheetIndex, int rowIndex) {
		if (Utils.isNotNull(tableRow)) {
			Table t = tableRow.getTable();
			ImportConfig mappingConfig = getImportConfig();
			ImportConceptConfig conceptConfig = mappingConfig.getConceptConfig();
			List<ImportRelationConfig> relationConfigs = conceptConfig.getRelationConfigs();
			for (ImportRelationConfig rc : relationConfigs) {
				String rName = rc.getMetaName();
				Cell cell = row.getCell(rc.getExcelCellIndex());
				if (t.getMetaData().containsColumn(rName)) {
					String dataType = t.getMetaData().getColumnMetaData(rName).getType();
					Object value = getValue(cell, dataType, rc.getDatePattern(), sheetIndex, rowIndex);
					if (Utils.isNotNull(value))
						tableRow.setValue(rName, value);
				}
			}
		}
	}

	private ImportConfig getImportConfig() {
		return importConfig;
	}

	private Object formatDate(String dateType, String value, String pattern) {
		Locale locale = Locale.ENGLISH;
		SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
		Date date = null;
		try {
			date = formatter.parse(value);
		} catch (ParseException e) {
			throw new BusinessException("时间类型转换失败,value：" + value, e);
		}
		return date;
	}

	private Object getValue(Cell cell, String dataType, String datePattern, int sheetIndex, int rowIndex) {
		if (null == cell)
			return null;
		Object value = null;
		int cellType = cell.getCellType();
		if (Cell.CELL_TYPE_FORMULA == cellType)
			cellType = evaluator.evaluateFormulaCell(cell);
		switch (cellType) {
		case Cell.CELL_TYPE_BOOLEAN: {
			value = cell.getBooleanCellValue();
			break;
		}
		case Cell.CELL_TYPE_NUMERIC: {
			if ("date".equalsIgnoreCase(dataType)) {
				value = new java.sql.Date(cell.getDateCellValue().getTime());
			} else if ("time".equalsIgnoreCase(dataType)) {
				value = new java.sql.Time(cell.getDateCellValue().getTime());
			} else if ("dateTime".equalsIgnoreCase(dataType)) {
				value = new java.sql.Timestamp(cell.getDateCellValue().getTime());
			} else
				value = new DecimalFormat("0.##########").format(cell.getNumericCellValue());
			break;
		}
		case Cell.CELL_TYPE_ERROR: {
			value = null;
			break;
		}
		case Cell.CELL_TYPE_FORMULA: {
			break;
		}
		case Cell.CELL_TYPE_BLANK:
		case Cell.CELL_TYPE_STRING: {
			String s = cell.getStringCellValue();
			if ("date".equalsIgnoreCase(dataType) || "time".equalsIgnoreCase(dataType) || "dateTime".equalsIgnoreCase(dataType)) {
				if (null != datePattern && !"".equals(datePattern))
					value = formatDate(dataType, s, datePattern);
				else
					value = null;
			} else
				try {
					value = SimpleTransform.transToObj(dataType, s);
				} catch (Exception e) {
					throw new BusinessException("excel数据与mapping声明的类型不一致，对应excel的sheet index: " + sheetIndex + " ;row index: " + rowIndex
							+ " ;column index: " + cell.getColumnIndex(), e);
				}
			break;
		}
		}
		// 数据类型检查，处理
		if (Utils.isNotEmptyString(dataType) && Utils.isNotNull(value)) {
			try {
				if ("integer".equalsIgnoreCase(dataType)) {
					Double d = null;
					if (value instanceof String) {
						d = Double.parseDouble((String) value);
						value = d.intValue();
					} else if (value instanceof Double) {
						d = (Double) value;
						value = d.intValue();
					}
					if (!(value instanceof Integer))
						throw new BusinessException(value + "不是整数");
				} else if ("float".equalsIgnoreCase(dataType)) {
					if (value instanceof String)
						value = Double.parseDouble((String) value);
					else if (!(value instanceof Double))
						throw new BusinessException(value + "不是双精度浮点数");
				} else if ("decimal".equalsIgnoreCase(dataType)) {
					if (value instanceof String)
						value = BigDecimal.valueOf(Double.parseDouble((String) value));
					else if (!(value instanceof BigDecimal))
						throw new BusinessException(value + "不是数字");
				} else if ("boolean".equalsIgnoreCase(dataType)) {
					if (value instanceof String)
						value = Boolean.parseBoolean((String) value);
					else if (!(value instanceof Boolean))
						throw new BusinessException(value + "不是布尔值");
				}
			} catch (Exception e) {
				throw new BusinessException("解析单元格错误,对应excel的sheet index: " + sheetIndex + " ;row index: " + rowIndex + " ;column index: "
						+ cell.getColumnIndex(), e);
			}
		}
		return value;
	}
}
