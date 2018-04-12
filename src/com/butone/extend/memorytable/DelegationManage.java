package com.butone.extend.memorytable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class DelegationManage {
	private Document document;

	public DelegationManage(String tabxml) {
		initData(tabxml);
	}

	public DelegationManage(File file) {
		initData(file);
	}

	//从内存中读取
	private void initData(String tabxml) {
		try {
			document = DocumentHelper.parseText(tabxml); // 将字符串转为XML
		} catch (DocumentException e) {
			e.printStackTrace();
		}
	}

	//从内存中读取
	private void initData(File file) {
		try {
			document = new SAXReader().read(file); // 将字符串转为XML
		} catch (DocumentException e) {
			e.printStackTrace();
		}
	}

	public Map<String, ArrayList<TypeModel>> getXmlToBean() {
		Map<String, ArrayList<TypeModel>> typeMap = new HashMap<String, ArrayList<TypeModel>>();

		for (@SuppressWarnings("rawtypes")
		Iterator j = document.getRootElement().elementIterator(); j.hasNext();) {
			Element typeEmt = (Element) j.next();
			ArrayList<TypeModel> typemodelList;
			/** 区分类型 */
			if (typeMap.get(typeEmt.getName()) == null) {
				typemodelList = new ArrayList<TypeModel>();
				typeMap.put(typeEmt.getName(), typemodelList);
			} else {
				typemodelList = typeMap.get(typeEmt.getName());
			}
			/** 建立 TypeModel 节点实体*/
			TypeModel typemodel = new TypeModel();
			typemodel.setID(typeEmt.attributeValue("id"));
			typemodel.setNodeName(typeEmt.getName());
			//typemodel.setCategory(typeEmt.attributeValue("category"));
			typemodelList.add(typemodel);
			/** 建立 DelegationModel 节点实体*/
			for (@SuppressWarnings("rawtypes")
			Iterator i = typeEmt.elementIterator(); i.hasNext();) {
				Element delegateMet = (Element) i.next();
				DelegationModel delegateionmodel = new DelegationModel();
				delegateionmodel.setClassname(delegateMet.attributeValue("name"));
				delegateionmodel.setLabel(delegateMet.attributeValue("label"));
				typemodel.setDelegation(delegateionmodel);

				/** 建立 PropertyModel 节点实体*/
				for (@SuppressWarnings("rawtypes")
				Iterator k = delegateMet.elementIterator(); k.hasNext();) {
					Element element = (Element) k.next();
					if (element.getName().equals("description")) {
						delegateionmodel.setDescription(element.getText());
					} else if (element.getName().equals("property")) {
						PropertyModel propertymodel = new PropertyModel();
						propertymodel.setName(element.attributeValue("name"));
						propertymodel.setLabel(element.attributeValue("label"));
						propertymodel.setDefaultValue(element.attributeValue("defaultValue"));
						propertymodel.setType(element.attributeValue("type"));
						delegateionmodel.addPropetrymodels(propertymodel);
					}
				}
			}
		}
		return typeMap;
	}

}
