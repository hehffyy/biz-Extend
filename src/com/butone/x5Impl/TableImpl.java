package com.butone.x5Impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.justep.message.BusinessMessages;
import com.justep.model.Concept;
import com.justep.model.Model;
import com.justep.model.ModelObject;
import com.justep.model.Relation;
import com.justep.security.decrypt.Decrypt;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.UpdateMode;
import com.justep.system.ksql.ConceptMapping;
import com.justep.system.ksql.RelationMapping;
import com.justep.util.Utils;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class TableImpl {
	private static Field f_deletedRows;
	private static Field f_normalRows;
	private static Field f_rows;
	private static Field f_keySearchIndex;

	private static Method m_checkSave;
	private static Field f_metaData;
	static {
		Class<?> cls = Decrypt.instance().getClass("com.justep.system.data.impl.TableImpl");
		try {
			f_normalRows = cls.getDeclaredField("normalRows");
			f_normalRows.setAccessible(true);

			f_rows = cls.getDeclaredField("rows");
			f_rows.setAccessible(true);

			f_deletedRows = cls.getDeclaredField("deletedRows");
			f_deletedRows.setAccessible(true);

			f_keySearchIndex = cls.getDeclaredField("keySearchIndex");
			f_keySearchIndex.setAccessible(true);

			f_metaData = cls.getDeclaredField("metaData");
			f_metaData.setAccessible(true);

			m_checkSave = cls.getDeclaredMethod("checkSave");
			m_checkSave.setAccessible(true);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Table target;

	public Table getTarget() {
		return target;
	}

	public TableImpl(Table target) {
		this.target = target;
	}

	public void empty() {
		Set<?> rows;
		try {
			rows = (Set<?>) f_normalRows.get(target);
			rows.clear();

			rows = (Set<?>) f_deletedRows.get(target);
			rows.clear();

			rows = (Set<?>) f_rows.get(target);
			rows.clear();

			((Map) f_keySearchIndex.get(target)).clear();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Set getNormalRows() {
		try {
			return (Set) f_normalRows.get(target);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Set<Row> getRows() {
		return Collections.unmodifiableSet(getNormalRows());
	}

	public void append(Collection<Row> rows) {
		getNormalRows().addAll(rows);
	}

	public Object getRow(int index) {
		return new ArrayList(getNormalRows()).get(index);
	}

	public boolean contains(Object obj) {
		return getNormalRows().contains(obj);
	}

	public int indexOf(Object obj) {
		return new ArrayList(getNormalRows()).indexOf(obj);
	}

	public void setMetaData(Object value) {
		try {
			f_metaData.set(target, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public Iterator<Row> iteratorAllRows() {
		try {
			return ((Set) f_rows.get(target)).iterator();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public HashMap<String, List<ColumnMetaData>> extractSaveColumn() {
		HashMap<String, List<ColumnMetaData>> conceptColums = new HashMap();
		HashSet<String> concepts = new HashSet();
		Iterator<ColumnMetaData> itor = target.getMetaData().getColumnMetaDatas().iterator();
		while (itor.hasNext()) {
			ColumnMetaData colMeta = itor.next();
			if (colMeta.isStore()) {
				Utils.check(Utils.isNotEmptyString(colMeta.getDefine()), BusinessMessages.class, "JUSTEP150157", colMeta.getName());
				int i = Utils.charCount(colMeta.getDefine(), '.');
				Utils.check((i == 0) || (i == 1), BusinessMessages.class, "JUSTEP150158", colMeta.getDefine());
				String concept = Utils.extractOwner(colMeta.getDefine());
				if (Utils.charCount(colMeta.getDefine(), '.') == 0)
					concepts.add(Utils.extractConcept(colMeta.getDefine()));
				List<ColumnMetaData> localObject = conceptColums.get(concept);
				if (localObject == null) {
					localObject = new ArrayList();
					conceptColums.put(colMeta.getDefine(), localObject);
				}
				localObject.add(colMeta);
			}
		}
		Utils.check(concepts.size() == conceptColums.size(), BusinessMessages.class, "JUSTEP150159", conceptColums.keySet(), concepts);
		return conceptColums;
	}

	public class ConceptSaveInfo {
		String define = null;
		String columnName = null;
		String conceptName = null;
		String versionColumn = null;
		String versionDefine = null;
		Concept concept = null;
		List<Relation> keys = new ArrayList();
		Map<String, String> keyMaps = new HashMap();
		List<String> keyColumns = new ArrayList();
		boolean hasKey = false;
		ConceptMapping mapping = null;
		List<RelationSaveInfo> ownerInfo = new ArrayList();
		List<RelationSaveInfo> friendInfo = new ArrayList();
		List<RelationSaveInfo> relationInfo = new ArrayList();
		String insertPermission = null;
		String deletePermission = null;
		String updatePermission = null;

		public String getDefine() {
			return define;
		}

		public String getColumnName() {
			return columnName;
		}

		public String getConceptName() {
			return conceptName;
		}

		public String getVersionColumn() {
			return versionColumn;
		}

		public String getVersionDefine() {
			return versionDefine;
		}

		public Concept getConcept() {
			return concept;
		}

		public List<Relation> getKeys() {
			return keys;
		}

		public Map<String, String> getKeyMaps() {
			return keyMaps;
		}

		public List<String> getKeyColumns() {
			return keyColumns;
		}

		public boolean isHasKey() {
			return hasKey;
		}

		public ConceptMapping getMapping() {
			return mapping;
		}

		public List<RelationSaveInfo> getOwnerInfo() {
			return ownerInfo;
		}

		public List<RelationSaveInfo> getFriendInfo() {
			return friendInfo;
		}

		public List<RelationSaveInfo> getRelationInfo() {
			return relationInfo;
		}

		public String getInsertPermission() {
			return insertPermission;
		}

		public String getDeletePermission() {
			return deletePermission;
		}

		public String getUpdatePermission() {
			return updatePermission;
		}
	}

	public class RelationSaveInfo {
		String define = null;
		String relationName = null;
		Relation relation = null;
		String rangeType = null;
		String columnName = null;

		public String getDefine() {
			return define;
		}

		public String getRelationName() {
			return relationName;
		}

		public Relation getRelation() {
			return relation;
		}

		public String getRangeType() {
			return rangeType;
		}

		public String getColumnName() {
			return columnName;
		}
	}

	private static String handleDefine(String str) {
		return str.replace('[', '_').replaceAll("]", "");
	}

	public List<ConceptSaveInfo> normalizeSaveInfo(HashMap<String, List<ColumnMetaData>> conceptColumns, Model dataModel) {
		List<ConceptSaveInfo> ret = new ArrayList<ConceptSaveInfo>();
		Iterator<String> itor = conceptColumns.keySet().iterator();
		while (itor.hasNext()) {
			String conceptFlag = (String) itor.next();
			ConceptSaveInfo conceptSaveInfo = new ConceptSaveInfo();
			conceptSaveInfo.define = handleDefine(conceptFlag);
			String concept = Utils.extractConcept(conceptFlag);
			ConceptMapping conceptMapping = ConceptMapping.getConceptMapping(dataModel, concept);
			Utils.check(Utils.isNotEmptyString(concept), BusinessMessages.class, "JUSTEP150161", concept);
			Iterator<ColumnMetaData> itorCol = conceptColumns.get(conceptFlag).iterator();
			while (itorCol.hasNext()) {
				ColumnMetaData colMeta = itorCol.next();
				if (colMeta.getDefine().equals(conceptFlag)) {
					// 概念
					ModelObject obj = colMeta.getDefineModelObject();
					Utils.check(Utils.isNotNull(obj), BusinessMessages.class, "JUSTEP150162", conceptFlag);
					conceptSaveInfo.concept = ((Concept) obj);
					conceptSaveInfo.conceptName = ((Concept) obj).getName();
					conceptSaveInfo.mapping = conceptMapping;
					conceptSaveInfo.keys.addAll(((Concept) obj).getKeyRelations());
					conceptSaveInfo.columnName = colMeta.getName();
				} else {
					// 关系
					RelationSaveInfo relationSaveInfo = new RelationSaveInfo();
					relationSaveInfo.define = handleDefine(colMeta.getDefine());
					relationSaveInfo.relation = ((Relation) colMeta.getDefineModelObject());
					Utils.check(Utils.isNotNull(relationSaveInfo.relation), BusinessMessages.class, "JUSTEP150163", relationSaveInfo.define);
					relationSaveInfo.rangeType = relationSaveInfo.relation.getDataType();
					relationSaveInfo.relationName = relationSaveInfo.relation.getName();
					relationSaveInfo.columnName = colMeta.getName();
					if (relationSaveInfo.relation.isKeyRelation())
						conceptSaveInfo.keyMaps.put(relationSaveInfo.relation.getName(), relationSaveInfo.columnName);
					if (("version".equals(relationSaveInfo.relationName)) && (target.getUpdateMode().equals(UpdateMode.WHERE_VERSION))) {
						conceptSaveInfo.versionColumn = relationSaveInfo.columnName;
						conceptSaveInfo.versionDefine = relationSaveInfo.define;
					} else {
						RelationMapping relationMapping = conceptMapping.getMapping(relationSaveInfo.relationName);
						switch (relationMapping.getStrategy()) {
						case OWNER:
							conceptSaveInfo.ownerInfo.add(relationSaveInfo);
							break;
						case FRIEND:
							conceptSaveInfo.friendInfo.add(relationSaveInfo);
							break;
						default:
							conceptSaveInfo.relationInfo.add(relationSaveInfo);
							break;
						}
					}
				}
			}
			if (target.getUpdateMode().equals(UpdateMode.WHERE_VERSION))
				Utils.check(Utils.isNotEmptyString(conceptSaveInfo.versionColumn), BusinessMessages.class, "JUSTEP150164");
			if (conceptSaveInfo.keyMaps.size() > 0)
				Utils.check(conceptSaveInfo.keys.size() == conceptSaveInfo.keyMaps.size(), BusinessMessages.class, "JUSTEP150165");
			Iterator<Relation> itorKey = conceptSaveInfo.keys.iterator();
			while (itorKey.hasNext()) {
				Relation key = itorKey.next();
				conceptSaveInfo.keyColumns.add(conceptSaveInfo.keyMaps.get(key.getName()));
			}
			ret.add(conceptSaveInfo);
		}
		return ret;
	}

	public void save() {

	}

}
