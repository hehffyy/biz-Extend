package com.butone.flowbiz;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.butone.data.BizDataUtils;
import com.butone.extend.memorytable.DelegationManage;
import com.butone.extend.memorytable.PropertyModel;
import com.butone.extend.memorytable.TypeModel;
import com.butone.imp.file.TableDataExporter;
import com.butone.imp.file.TableDataImport;
import com.butone.logic.config.CalcLogicConfig;
import com.butone.logic.impl.ProcessLogicPluginContext;
import com.butone.logic.impl.TableControlObject;
import com.butone.model.FileImportPlugin;
import com.butone.xml.JaxbUtils;
import com.justep.exception.BusinessException;
import com.justep.security.decrypt.Decrypt;
import com.justep.system.data.Table;
import com.justep.system.data.TableMetaData;
import com.justep.system.data.TableUtils;
import com.justep.util.Utils;

public class FileImportExoprtRuntime {
	private static final Log logger = LogFactory.getLog(FileImportExoprtRuntime.class);

	private ProcessLogicPluginContext pluginContext;

	private FileImportPlugin plugin;

	private Object dataParser;

	private Map<String, Object> variants = new HashMap<String, Object>();

	public void setParserClassName(String parserClassName) {
		dataParser = Decrypt.instance().createObject(parserClassName);
	}

	public void setVariants(Map<String, Object> variants) {
		if (variants != null)
			this.variants.putAll(variants);
	}

	public void setPlugin(FileImportPlugin plugin) {
		this.plugin = plugin;
	}

	public FileImportExoprtRuntime(ProcessLogicPluginContext pluginContext) {
		this.pluginContext = pluginContext;
	}

	/**
	 * 
	 * @param dataLoader
	 * @return
	 */
	private Map<String, Table> createTable(String dataModels) {
		Map<String, Table> ret = new HashMap<String, Table>();
		DelegationManage delegationmanage = new DelegationManage(plugin.getDataModels());
		Map<String, ArrayList<TypeModel>> typeMap = delegationmanage.getXmlToBean();
		for (Entry<String, ArrayList<TypeModel>> entry : typeMap.entrySet()) {
			ArrayList<TypeModel> list = entry.getValue();
			for (TypeModel typeModel : list) {
				List<String> names = new ArrayList<String>();
				List<String> types = new ArrayList<String>();
				names.add(BizDataUtils.MemoryTableKeyColumnName);
				types.add(BizDataUtils.MemoryTableKeyColumnType);
				Map<String, String> defaultValues = new HashMap<String, String>();
				List<PropertyModel> propetrymodels = typeModel.getDelegation().getPropetrymodels();
				for (PropertyModel propertyModel : propetrymodels) {
					if (names.contains(propertyModel.getName()))
						continue;
					names.add(propertyModel.getName());
					types.add(propertyModel.getType());
					if (Utils.isNotEmptyString(propertyModel.getDefaultValue())) {
						defaultValues.put(propertyModel.getName(), propertyModel.getDefaultValue());
					}
				}
				Table table = TableUtils.createTable(null, names, types);
				TableMetaData meta = table.getMetaData();
				meta.setKeyColumn(BizDataUtils.MemoryTableKeyColumnName);
				meta.getColumnMetaData(BizDataUtils.MemoryTableKeyColumnName).setDefine("guid()");
				for (String key : defaultValues.keySet()) {
					meta.getColumnMetaData(key).setDefine(defaultValues.get(key));
				}
				table.getProperties().put(Table.PROP_NAME_ROWID, BizDataUtils.MemoryTableKeyColumnName);
				ret.put(typeModel.getDelegation().getClassname(), table);
			}
		}
		return ret;
	}

	/**
	 * 为插件执行加载业务表
	 */
	private void loadBizTables() {
		String relBizDatas = plugin.getRelBizDatas();
		if (relBizDatas == null) {
			// TODO 兼容老的资源
			pluginContext.loadAllBizTable(variants);
		} else {
			String[] tableNames = relBizDatas.split(",");
			for (String tableName : tableNames) {
				pluginContext.loadBizTable(tableName, variants);
			}
		}
	}

	/**
	 * 导入数据
	 * 
	 * @param in
	 * @throws Exception
	 */
	public Map<String, Table> importData(InputStream in) throws Exception {
		// 创建文件类型数据模型实体
		Map<String, Table> tables = new HashMap<String, Table>();
		if (Utils.isNotEmptyString(plugin.getDataModels())) {
			tables = createTable(plugin.getDataModels());
		}
		// 获得数据加载器
		if (dataParser instanceof TableDataImport) {
			// 装载文件类型数据实体数据
			((TableDataImport) dataParser).loadToTable(in, tables, variants);
		} else {
			variants.put("_importFileStream", in);
			if (logger.isDebugEnabled()) {
				logger.debug(plugin.getName() + "的数据解析器不支持数据加载接口，需在计算组件内手动加载数据(参数:_importFileStream)");
			}
		}

		boolean b = Utils.isNotEmptyString(plugin.getMappingLogic());
		if (b) {
			// 为通用计算组件创建控制对象
			List<TableControlObject> memoryTables = new ArrayList<TableControlObject>();
			for (String id : tables.keySet()) {
				TableControlObject controlObject = new TableControlObject(null, tables.get(id), null);
				controlObject.setMemoryTable(true);
				controlObject.setObjectId(id);
				memoryTables.add(controlObject);
			}
			// 执行组件
			CalcLogicConfig logicConfig = (CalcLogicConfig) JaxbUtils.unMarshal(new ByteArrayInputStream(plugin.getMappingLogic().getBytes("utf-8")), "utf-8", CalcLogicConfig.class);
			this.loadBizTables();
			pluginContext.execute(logicConfig, variants, memoryTables);
		}
		return tables;
	}

	public static String getTempDir() {
		return System.getProperty("java.io.tmpdir");
	}

	public static String getTempExtName() {
		return ".exp.tmp";
	}

	public static File newTempFile() {
		String name = java.util.UUID.randomUUID().toString();
		File file = new File(getTempDir() + "/" + name + getTempExtName());
		return file;
	}

	private static String createFile(InputStream in) {
		File file = newTempFile();
		createFile(file, in);
		return file.getAbsolutePath();
	}

	private static void createFile(File file, InputStream in) {
		if (file.exists())
			file.delete();
		FileOutputStream output;
		try {
			output = new FileOutputStream(file);
			try {
				in.reset();
				byte[] buff = new byte[8 * 1024];
				int l = 0;
				while ((l = in.read(buff)) != -1) {
					output.write(buff, 0, l);
				}
				output.flush();
			} finally {
				output.close();
			}
		} catch (Exception e) {
			throw new BusinessException("创建导出文件失败", e);
		}
	}

	public String exportData() throws Exception {
		Map<String, Table> tables = new HashMap<String, Table>();
		if (Utils.isNotEmptyString(plugin.getDataModels())) {
			tables = createTable(plugin.getDataModels());
		}
		boolean b = Utils.isEmptyString(plugin.getMappingLogic());
		if (b) {
			throw new BusinessException(plugin.getName() + "未配置映射逻辑");
		}
		// 为通用计算组件创建控制对象
		List<TableControlObject> memoryTables = new ArrayList<TableControlObject>();
		for (String id : tables.keySet()) {
			TableControlObject controlObject = new TableControlObject(null, tables.get(id), null);
			controlObject.setMemoryTable(true);
			controlObject.setObjectId(id);
			memoryTables.add(controlObject);
		}
		// 执行组件
		CalcLogicConfig logicConfig = (CalcLogicConfig) JaxbUtils.unMarshal(new ByteArrayInputStream(plugin.getMappingLogic().getBytes("utf-8")), "utf-8", CalcLogicConfig.class);
		this.loadBizTables();
		Object ret = pluginContext.execute(logicConfig, variants, memoryTables);
		if (ret instanceof InputStream) {
			return createFile((InputStream) ret);
		} else {
			if (dataParser instanceof TableDataExporter)
				return createFile(((TableDataExporter) dataParser).tableToStream(tables, variants));
			else {
				throw new BusinessException(plugin.getFileType() + "不存在支持数据导出");
			}
		}
	}

	public void exportData(File file) throws Exception {
		Map<String, Table> tables = new HashMap<String, Table>();
		if (Utils.isNotEmptyString(plugin.getDataModels())) {
			tables = createTable(plugin.getDataModels());
		}
		boolean b = Utils.isEmptyString(plugin.getMappingLogic());
		if (b) {
			throw new BusinessException(plugin.getName() + "未配置映射逻辑");
		}
		// 为通用计算组件创建控制对象
		List<TableControlObject> memoryTables = new ArrayList<TableControlObject>();
		for (String id : tables.keySet()) {
			TableControlObject controlObject = new TableControlObject(null, tables.get(id), null);
			controlObject.setMemoryTable(true);
			controlObject.setObjectId(id);
			memoryTables.add(controlObject);
		}
		// 执行组件
		CalcLogicConfig logicConfig = (CalcLogicConfig) JaxbUtils.unMarshal(new ByteArrayInputStream(plugin.getMappingLogic().getBytes("utf-8")), "utf-8", CalcLogicConfig.class);
		this.loadBizTables();
		Object ret = pluginContext.execute(logicConfig, variants, memoryTables);
		if (ret instanceof InputStream) {
			createFile(file, (InputStream) ret);
		} else {
			if (dataParser instanceof TableDataExporter)
				createFile(file, ((TableDataExporter) dataParser).tableToStream(tables, variants));
			else {
				throw new BusinessException(plugin.getFileType() + "不存在支持数据导出");
			}
		}
	}
}
