package com.butone.x5Impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.justep.model.Model;
import com.justep.security.decrypt.Decrypt;
import com.justep.system.data.DataPermission;

public class QueryParserImpl {
	private static Class<?> Class_QueryParser;
	private static Constructor<?> Constructor_QueryParser;
	static {
		Class_QueryParser = Decrypt.instance().getClass("com.justep.system.ksql.parser.QueryParser");
		try {
			if (Class_QueryParser != null) {
				Constructor_QueryParser = Class_QueryParser.getConstructor(Model.class, Model.class, String.class, String.class, Collection.class,
						List.class);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Object target;

	public QueryParserImpl(Model dataModel, Model fnModel, String columns, String logicOperation, Collection<String> conidtion,
			List<DataPermission> paramList) {
		try {
			target = Constructor_QueryParser.newInstance(new Object[] { dataModel, fnModel, columns, logicOperation, conidtion, paramList });
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setVarMap(Map<String, Object> paramMap) {
		try {
			Method method = Class_QueryParser.getMethod("setVarMap", Map.class);
			method.invoke(target, paramMap);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@SuppressWarnings("rawtypes")
	public HashMap getSymTab() {
		try {
			Class parserCls = Decrypt.instance().getClass("com.justep.system.ksql.parser.AbstractParser");
			Field symTab = parserCls.getDeclaredField("symTab");
			symTab.setAccessible(true);
			return (HashMap) symTab.get(target);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Object parse(String sql) {
		try {
			Class parserCls = Decrypt.instance().getClass("com.justep.system.ksql.parser.AbstractParser");
			Method m = parserCls.getDeclaredMethod("parse", String.class);
			return m.invoke(target, sql);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Object parseEx(String sql) {
		try {
			Class parserCls = Decrypt.instance().getClass("com.justep.system.ksql.parser.AbstractParser");
			Field decAlias = parserCls.getDeclaredField("decAlias");
			decAlias.setAccessible(true);
			decAlias.set(target, new ArrayList<String>());

			Class charStreamCls = Decrypt.instance().getClass("com.justep.system.ksql.token.CharStream");
			Field stream = parserCls.getDeclaredField("stream");
			stream.setAccessible(true);
			Object streamInstance = charStreamCls.getConstructor(String.class).newInstance(sql);
			stream.set(target, streamInstance);

			Field symTab = parserCls.getDeclaredField("symTab");
			symTab.setAccessible(true);
			symTab.set(target, new HashMap());

			Object node = start();

			//confirmSymTable();
			//checkSymTable();
			return node;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected Object start() {
		try {
			Method start = Class_QueryParser.getDeclaredMethod("start", new Class[] {});
			start.setAccessible(true);
			return start.invoke(target, new Object[] {});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//	public void confirmSymTable() {
	//		Iterator localIterator = this.getSymTab().values().iterator();
	//		while (localIterator.hasNext()) {
	//			SymDescriptorImpl localSymDescriptor1 = new SymDescriptorImpl(localIterator.next());
	//			if (localSymDescriptor1.getKind().equals("CONCEPT_ALIAS")) {
	//				if (localSymDescriptor1.getConcept() == null) {
	//					localSymDescriptor1.concept = this.dataModel.getUseableConcept(localSymDescriptor1.conceptName);
	//					localSymDescriptor1.conceptMapping = ConceptMapping.getConceptMapping(this.dataModel, localSymDescriptor1.conceptName);
	//					Utils.check((Utils.isNotNull(localSymDescriptor1.concept)) && (Utils.isNotNull(localSymDescriptor1.conceptMapping)),
	//							BusinessMessages.class, "JUSTEP154035", localSymDescriptor1.getName(), localSymDescriptor1.conceptName);
	//					localSymDescriptor1.tableAlias.put(localSymDescriptor1.conceptMapping.getTableName(), localSymDescriptor1.getName());
	//					localSymDescriptor1.columnNames = localSymDescriptor1.conceptMapping.getColumns();
	//					localSymDescriptor1.columnTypes = localSymDescriptor1.conceptMapping.getTypes();
	//				}
	//			}
	//		}
	//		localIterator = this.symTab.values().iterator();
	//		while (localIterator.hasNext()) {
	//			localSymDescriptor1 = (SymDescriptor) localIterator.next();
	//			if (localSymDescriptor1.kind.equals(SymKind.UNKNOW)) {
	//				Utils.check(localSymDescriptor1.getName().indexOf('.') != -1, BusinessMessages.class, "JUSTEP154036", localSymDescriptor1.getName());
	//				String str1 = localSymDescriptor1.getName().split("[.]")[0];
	//				SymDescriptor localSymDescriptor2 = (SymDescriptor) this.symTab.get(str1);
	//				if (localSymDescriptor2 == null)
	//					continue;
	//				if (localSymDescriptor2.kind.equals(SymKind.CONCEPT_ALIAS)) {
	//					localSymDescriptor1.kind = SymKind.RELATION;
	//					localSymDescriptor1.conceptName = localSymDescriptor2.conceptName;
	//					localSymDescriptor1.relationName = localSymDescriptor1.getName().split("[.]")[1];
	//					localSymDescriptor1.concept = localSymDescriptor2.concept;
	//					Utils.check(Utils.isNotNull(localSymDescriptor2.concept), BusinessMessages.class, "JUSTEP154037", localSymDescriptor1.getName(),
	//							localSymDescriptor2.getName());
	//					localSymDescriptor1.relation = localSymDescriptor2.concept.getRelation(localSymDescriptor1.getName());
	//					localSymDescriptor1.relationMapping = localSymDescriptor2.conceptMapping.getMapping(localSymDescriptor1.relationName);
	//					Utils.check(Utils.isNotNull(localSymDescriptor1.relationMapping), BusinessMessages.class, "JUSTEP154038",
	//							localSymDescriptor1.name);
	//					String str2 = localSymDescriptor1.relationMapping.getTableName();
	//					if (localSymDescriptor1.relationMapping.getStrategy().isOwnerStore()) {
	//						Utils.check(localSymDescriptor2.tableAlias.containsKey(str2), BusinessMessages.class, "JUSTEP154039",
	//								localSymDescriptor1.getName());
	//						localSymDescriptor1.tableAlias.put(str2, localSymDescriptor2.tableAlias.get(str2));
	//					} else {
	//						localSymDescriptor1.tableAlias.put(str2, str2 + "__" + this.aliasIndex++);
	//					}
	//					localSymDescriptor1.owner = localSymDescriptor2.getName();
	//					localSymDescriptor2.ownerBy.add(localSymDescriptor1);
	//					localSymDescriptor1.columnNames = localSymDescriptor1.relationMapping.getRangeColumns();
	//					localSymDescriptor1.columnTypes = localSymDescriptor1.relationMapping.getRangeTypes();
	//				} else if (localSymDescriptor2.kind.equals(SymKind.QUERY_ALIAS)) {
	//					localSymDescriptor1.kind = SymKind.COLUMN;
	//					localSymDescriptor1.owner = localSymDescriptor2.getName();
	//					localSymDescriptor2.ownerBy.add(localSymDescriptor1);
	//				} else {
	//					throw KSQLException.create("JUSTEP154040", new Object[] { localSymDescriptor2.getName(), localSymDescriptor2.kind });
	//				}
	//			}
	//		}
	//	}
}
