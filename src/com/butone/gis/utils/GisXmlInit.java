package com.butone.gis.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import com.justep.system.context.ContextHelper;

public class GisXmlInit {
	private static GisXmlInit INSTANCE = null;
	private SAXReader saxReader;
	private Document document;
	private String filepath;
	private Map<String, Map<String, String>> groupMap;

	private GisXmlInit() {
	}

	public static final GisXmlInit getInstance() throws Exception {
		if (INSTANCE == null) {
			INSTANCE = new GisXmlInit();
			try {
				INSTANCE.initData();
			} catch (Exception e) {
				INSTANCE = null;
				throw e;
			}
		}
		return INSTANCE;
	}

	@SuppressWarnings("rawtypes")
	private void initData() throws FileNotFoundException, UnsupportedEncodingException, DocumentException {
		if (groupMap != null)
			return;
		ContextHelper.getRequestContext().getRequest().getRealPath("/WEB-INF/gisconfig.xml");
		saxReader = new SAXReader();
		File gisxml = new File(filepath);
		if (gisxml.exists()) {
			FileInputStream in = new FileInputStream(gisxml);
			Reader re = new InputStreamReader(in, "gb2312");
			document = saxReader.read(re);
		}
		groupMap = new HashMap<String, Map<String, String>>();
		Iterator itorGroup = document.selectNodes("/root/group").iterator();
		while (itorGroup.hasNext()) {
			Element eleGroup = (Element) itorGroup.next();
			Map<String, String> group = new HashMap<String, String>();
			groupMap.put(eleGroup.attributeValue("id"), group);
			Iterator itorParam = eleGroup.selectNodes("param").iterator();
			while (itorParam.hasNext()) {
				Element eleParam = (Element) itorParam.next();
				group.put(eleParam.attributeValue("id"), eleParam.attributeValue("value"));
			}
		}
	}

	/**
	 * 处理器查找跌代分类收有的处理器
	 * @throws DocumentException 
	 * @throws UnsupportedEncodingException 
	 * @throws FileNotFoundException 
	 * @throws Exception 
	 */
	public String getParameterValue(String groupId, String paramId) throws FileNotFoundException, UnsupportedEncodingException, DocumentException {
		Map<String, String> group = groupMap.get(groupId);
		if (group != null)
			return group.get(paramId);
		return null;
	}

}
