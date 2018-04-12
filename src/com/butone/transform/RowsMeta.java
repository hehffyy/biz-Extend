package com.butone.transform;

import com.justep.model.exception.ModelException;
import com.justep.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RowsMeta {
	public boolean isXmlData = true;
	private List<String> calias = null;
	private List<String> concept = null;
	private Boolean cellNameByRelation = null;
	private String updateMode = null;
	private String idColumnName = null;
	private String idColumnType = null;
	private String idColumnDefine = null;
	private String dataModel = null;
	private List<String> relationAlias = null;
	private List<String> relations = null;
	private List<String> rTypes = null;
	private HashMap<String, String> maps = new HashMap<String, String>();

	public void add(String paramString1, String paramString2) {
		if ((Utils.isNotNull(paramString1)) && (Utils.isNotNull(paramString2)))
			this.maps.put(paramString1, paramString2);
	}

	public String getIdColumnName() {
		if (Utils.isNull(this.idColumnName)) {
			String str = (String) this.maps.get(this.isXmlData ? "id-column-name" : "idColumnName");
			if (Utils.isNotNull(str))
				this.idColumnName = str.trim();
		}
		return this.idColumnName;
	}

	public boolean isCellNameByRelation() {
		if (Utils.isNull(this.cellNameByRelation)) {
			String str = (String) this.maps.get(this.isXmlData ? "cellname-by-relation" : "cellnameByRelation");
			this.cellNameByRelation = Boolean.valueOf("true".equalsIgnoreCase(str));
		}
		return this.cellNameByRelation.booleanValue();
	}

	public String getIdColumnType() {
		if (Utils.isNull(this.idColumnType)) {
			String str = (String) this.maps.get(this.isXmlData ? "id-column-type" : "idColumnType");
			if (Utils.isNotNull(str))
				this.idColumnType = str.trim();
		}
		return this.idColumnType;
	}

	public String getIdColumnDefine() {
		if (Utils.isNull(this.idColumnDefine)) {
			String str = (String) this.maps.get(this.isXmlData ? "id-column-define" : "idColumnDefine");
			if (Utils.isNotNull(str))
				this.idColumnDefine = str.trim();
		}
		return this.idColumnDefine;
	}

	public List<String> getConcept() {
		if (Utils.isNull(this.concept)) {
			String str1 = (String) this.maps.get("concept");
			this.concept = new ArrayList<String>();
			if (Utils.isNotNull(str1))
				for (String str2 : str1.split(",")) {
					if (!Utils.isNotEmptyString(str2))
						continue;
					String str3 = str2.trim();
					this.concept.add(str3);
				}
		}
		return this.concept;
	}

	public List<String> getConceptAlias() {
		if (Utils.isNull(this.calias)) {
			this.calias = new ArrayList<String>();
			String str1 = (String) this.maps.get(this.isXmlData ? "concept-alias" : "conceptAlias");
			if (Utils.isNotNull(str1))
				for (String str2 : str1.split(",")) {
					if (!Utils.isNotEmptyString(str2))
						continue;
					String str3 = str2.trim();
					this.calias.add(str3);
				}
		}
		return this.calias;
	}

	public List<String> getRelationAlias() {
		if (Utils.isNull(this.relationAlias)) {
			this.relationAlias = new ArrayList<String>();
			String str1 = (String) this.maps.get(this.isXmlData ? "relation-alias" : "relationAlias");
			Object localObject;
			if (Utils.isNull(str1)) {
				localObject = "Can't find relation alias userdata in rows.";
				throw new ModelException((String) localObject);
			}
			for (String str2 : str1.split(",")) {
				String str3 = str2.trim();
				this.relationAlias.add(str3);
			}
		}
		return (List<String>) this.relationAlias;
	}

	public List<String> getRelations() {
		if (Utils.isNull(this.relations)) {
			this.relations = new ArrayList<String>();
			String str1 = (String) this.maps.get("relations");
			Object localObject;
			if (Utils.isNull(str1)) {
				localObject = "Can't find relations userdata in rows.";
				throw new ModelException((String) localObject);
			}
			for (String str2 : str1.split(",")) {
				if (Utils.isEmptyString(str2))
					str2 = "";
				String str3 = str2.trim();
				this.relations.add(str3);
			}
		}
		return (List<String>) this.relations;
	}

	public List<String> getRelationTypes() {
		if (Utils.isNull(this.rTypes)) {
			this.rTypes = new ArrayList<String>();
			String str1 = (String) this.maps.get(this.isXmlData ? "relation-types" : "relationTypes");
			Object localObject;
			if (Utils.isNull(str1)) {
				localObject = "Can't find relation types userdata in rows.";
				throw new ModelException((String) localObject);
			}
			for (String str2 : str1.split(",")) {
				String str3 = str2.trim();
				this.rTypes.add(str3);
			}
		}
		return (List<String>) this.rTypes;
	}

	public String getUpdateMode() {
		if (Utils.isNull(this.updateMode)) {
			String str = (String) this.maps.get(this.isXmlData ? "update-mode" : "updateMode");
			if (Utils.isNotNull(str))
				this.updateMode = str.trim();
		}
		return this.updateMode;
	}

	public String getModel() {
		if (Utils.isNull(this.dataModel)) {
			String str = (String) this.maps.get("model");
			if (Utils.isNotNull(str))
				this.dataModel = str.trim();
		}
		return this.dataModel;
	}
}