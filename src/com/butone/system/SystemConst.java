package com.butone.system;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.justep.common.SystemUtils;
import com.justep.exception.BusinessException;

/**
 * 系统全局常量
 * @author Administrator
 *
 */
public class SystemConst {
	private static Map<String, String>  _sysConst =null;
	
	private static  Map<String, String> getSysParams(){
		if(SystemConst._sysConst==null)
			SystemConst._sysConst =  new HashMap<String, String>();
		else
			return _sysConst;
		try{
				String path = SystemUtils.getAppHome() + "/SystemConst.xml";
				SAXReader builder = new SAXReader();
				File parseFile = new File(path);
				if (!parseFile.exists()) {
					throw new Exception("SystemConst.xml系统 全局参数文件 不 存在！");
				}
				Document document= builder.read(parseFile);
				Element rootElement = document.getRootElement();
				List<?> list = rootElement.elements();
				for (int i = 0; i < list.size(); i++) {
				Element Element = (Element) list.get(i);
					String name = Element.getName();
					String text = Element.getText();
					_sysConst.put(name, text);
				}
		}catch(Exception e){
			e.printStackTrace();
		}
		return _sysConst;
	}
	
	public static String getParamValue(String name){
		Map<String,String> params = getSysParams();
		if(params.containsKey(name))
			return params.get(name);
		else
			throw new BusinessException(name+"系统常量未定义");
	}
	
	public static String getOneMapSrvUrl(){
		return getSysParams().get("oneMapSrvUrl");
	}

	public static String getDocExPath() throws Exception{
		String value = getSysParams().get("docExPath");
		if(value==null)
			throw new Exception("docExPath系统常量未设置");
		return value;
	}

}
