package com.butone.extend;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson.JSONObject;
import com.butone.codeinf.model.CodeDef;
import com.butone.model.xmlconfig.TableConfig;
import com.butone.utils.ModelExtUtils;
//import com.butone.x5.extend.BusinessActionConfig;
//import com.butone.x5.extend.BusinessActionConfig.BizAction;
import com.butone.x5Impl.ModelObjectImpl;
import com.butone.xml.JaxbUtils;
import com.justep.filesystem.FileSystemWrapper;
import com.justep.model.Action;
import com.justep.model.Concept;
import com.justep.model.Process;
import com.justep.system.util.BizSystemException;
import com.justep.util.Utils;

/**
 * 缓存管理 后面改成 EnCaches 或者 MMemoryCache
 * 
 * @author Administrator
 * 
 */
public class CacheManager {
	// 任务中心缓存的业务信息
	private static Map<String, BizInfo> bizInfos = new ConcurrentHashMap<String, BizInfo>();

	private static Map<String, CodeDef> codeDefs = new ConcurrentHashMap<String, CodeDef>();
	//自定义缓存信息
	private static Map<String, JSONObject> customInfos = new HashMap<String, JSONObject>();

	/**
	 * 获得通用编码定义
	 * 
	 * @param guid
	 * @return
	 */
	public static CodeDef getCodeDef(String guid) {
		CodeDef def = codeDefs.get(guid);
		if (def == null) {
			def = loadCodeDef(guid);
			codeDefs.put(guid, def);
		}
		return def;
	}

	private static CodeDef loadCodeDef(String guid) {
		String bizRoot = FileSystemWrapper.instance().getBase();
		String codeDefPath = bizRoot + "/codedef/";
		File file = new File(codeDefPath + guid + ".xml");
		if (!file.exists()) {
			throw new RuntimeException("通用编码[" + guid + "]不存在");
		}
		FileInputStream in;
		try {
			in = new FileInputStream(file);
			CodeDef def = (CodeDef) JaxbUtils.unMarshal(in, "utf-8", CodeDef.class);
			in.close();
			return def;
		} catch (Exception e) {
			throw new BizSystemException("加载通用编码错误", e);
		}
	}

	public static TableConfig getConceptTableConfig(Concept concept) {
		TableConfig config = (TableConfig) concept.getExtAttributeValue(ModelExtUtils.MODEL_EXT_URI, "tableDef");
		if (config == null) {
			config = new TableConfig();
			InputStream in = null;
			try {
				String filePath = concept.getModel().getFullName() + "/" + concept.getName() + ".xml";
				File file = new File(FileSystemWrapper.instance().getRealPath(filePath));
				in = new FileInputStream(file);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buff = new byte[4096];
				int l = -1;
				while ((l = in.read(buff)) != -1) {
					out.write(buff, 0, l);
				}
				config.parseXml(out.toString("utf-8"));
			} catch (Exception e) {
				throw new RuntimeException("加载查询定义失败", e);
			} finally {
				if (in != null)
					try {
						in.close();
					} catch (IOException e) {
					}
			}
			new ModelObjectImpl(concept).setExtAttributeValue(ModelExtUtils.MODEL_EXT_URI, "tableDef", config);
		}
		return config;
	}

	public static void reloadBizInfo(String process, boolean whole, boolean tableInfo, boolean material, boolean plugin, boolean bizRule,
			boolean processInfo) {
		if (Utils.isEmptyString(process)) {
			bizInfos.clear();
		} else {
			String bizPath = ModelPathHelper.getProcessBizPath(process);
			bizInfos.remove(bizPath);
			getBizInfoByProcess(process);
		}
		codeDefs.clear();
	}

	public static ProcessInfo getProcessInfo(Process process) {
		BizInfo bizInfo = getBizInfo(process);
		return bizInfo.loadProcessInfo(process);
	}

	/**
	 * 获得Process【任务中心】的业务信息
	 * 
	 * @param processUrl
	 * @return
	 */
	public static BizInfo getBizInfo(Process process) {
		// 任务中心缓存
		String bizPath = ModelPathHelper.getBizPath(process);
		BizInfo bizInfo = bizInfos.get(bizPath);
		if (bizInfo == null) {
			bizInfo = new BizInfo(bizPath);
			bizInfos.put(bizPath, bizInfo);
		}
		return bizInfo;
	}

	public static BizInfo getBizInfoByProcess(String process) {
		String bizPath = ModelPathHelper.getProcessBizPath(process);
		BizInfo bizInfo = bizInfos.get(bizPath);
		if (bizInfo == null) {
			bizInfo = new BizInfo(bizPath);
			bizInfos.put(bizPath, bizInfo);
		}
		return bizInfo;
	}

	public static BizInfo getBizInfo(Action action) {
		String bizPath = new File(action.getFullName()).getParentFile().getParentFile().getParent().replace('\\', '/');
		BizInfo bizInfo = bizInfos.get(bizPath);
		if (bizInfo == null) {
			bizInfo = new BizInfo(bizPath);
			bizInfos.put(bizPath, bizInfo);
		}
		return bizInfo;
	}

	public static BizInfo getBizInfo(String bizPath) {
		BizInfo bizInfo = bizInfos.get(bizPath);
		if (bizInfo == null) {
			bizInfo = new BizInfo(bizPath);
			bizInfos.put(bizPath, bizInfo);
		}
		return bizInfo;
	}

	public static JSONObject getCustomInfo(String key) {
		JSONObject result = customInfos.get(key);
		if (result == null) {
			result = new JSONObject();
		}
		return result;
	}

	public static void clearCustomeInfos() {
		customInfos.clear();
	}
}
