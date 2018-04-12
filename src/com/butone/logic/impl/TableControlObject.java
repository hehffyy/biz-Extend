package com.butone.logic.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.butone.data.BizDataUtils;
import com.butone.logic.ListControlObject;
import com.butone.logic.config.BaseConfig;
import com.butone.x5Impl.TableImpl;
import com.justep.exception.BusinessException;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.util.Utils;

/**
 * 列表控制对象的Table实现类。
 * 
 * 对于内存表，ColumnMetaData.getDefine为默认值
 * 
 * @author Administrator
 * 
 */
public class TableControlObject implements ListControlObject {

	static interface ScrollListener {
		void scroll(Row row);
	}

	static interface DeleteListener {
		void delete(Row row);
	}

	private TableImpl tableImpl;
	private Model dataModel;

	// 当前行
	private Object currentObject;
	private String objectId;
	private String store;

	private TableControlObject master;
	private List<ScrollListener> scrollListeners = new ArrayList<ScrollListener>();
	private List<DeleteListener> deleteListeners = new ArrayList<DeleteListener>();
	private Map<String, Table> details;
	private TableLoader tableLoader;
	// private String keyFields;
	private int currIdx = -1;
	private boolean memoryTable;

	public Table getTarget() {
		checkDetialTable();
		return tableImpl.getTarget();
	}

	/**
	 * 主表操作对象
	 * 
	 * @param tab
	 * @param model
	 */
	public TableControlObject(String store, Table table, Model dataModel) {
		this.store = store;
		this.dataModel = dataModel;
		setTable(table);
	}

	/**
	 * 从表操作对象
	 * 
	 * @param master
	 * @param tableLoader
	 * @param masterfID
	 */
	public TableControlObject(TableControlObject master, String store, TableLoader tableLoader, Model dataModel) {
		this.master = master;
		this.tableLoader = tableLoader;
		this.dataModel = dataModel;
		this.details = new HashMap<String, Table>();
		this.store = store;
		this.master.scrollListeners.add(new ScrollListener() {
			@Override
			public void scroll(Row row) {
				doMasterScrolled(row);

			}
		});
		// 删除前
		if (Utils.isNotEmptyString(tableLoader.getCascade())) {
			this.master.deleteListeners.add(new DeleteListener() {
				@Override
				public void delete(Row row) {
					doBeforeMasterDelete(row);
				}
			});
		}
		checkDetialTable();
	}

	public void setMemoryTable(boolean memoryTable) {
		this.memoryTable = memoryTable;
	}

	// private static String getTableKeyColumns(Table table, String store) {
	// if (table.getMetaData().getKeyColumnName() != null)
	// return table.getMetaData().getKeyColumnName();
	// StringBuffer keys = new StringBuffer();
	// Iterator<ColumnMetaData> i =
	// table.getMetaData().getColumnMetaDatas().iterator();
	// while (i.hasNext()) {
	// ColumnMetaData colMeta = (ColumnMetaData) i.next();
	// ModelObject defineModelObject = colMeta.getDefineModelObject();
	// if (defineModelObject == null)
	// continue;
	// if ((defineModelObject instanceof Concept) &&
	// defineModelObject.getName().equals(store)) {
	// return colMeta.getName();
	// } else {
	// if (colMeta.getDefine().startsWith(store + ".") &&
	// BizDataUtils.isKeyRelation(colMeta)) {
	// keys.append(colMeta.getName()).append(":");
	// }
	// }
	// }
	// if (keys.length() == 0)
	// return null;
	// else
	// return keys.substring(0, keys.length() - 1);
	// }

	public void setTable(Table table) {
		// if (this.store != null) {
		// if (Utils.isEmptyString(table.getMetaData().getKeyColumnName())) {
		// String keyColumnName = TableControlObject.getTableKeyColumns(table,
		// this.store);
		// if (keyColumnName == null)
		// throw new RuntimeException("数据对象" + this.objectId + "缺少主键定义");
		// else if (keyColumnName.contains(":")) {
		// throw new RuntimeException("数据对象" + this.objectId +
		// "多个主键，select中必须使用表名作为idColumn");
		// }
		// table.getMetaData().setKeyColumn(keyColumnName);
		// }
		// }
		this.tableImpl = new TableImpl(table);
		this.first();
	}

	private Table getDetailTable(Row masterRow) {
		String key = masterRow == null ? "" : this.tableLoader.getMasterKeysValue(masterRow);
		Table table = this.details.get(key);
		if (table == null) {
			table = this.tableLoader.loadDetailTable(masterRow, this.dataModel);
			this.details.put(key, table);
		}
		return table;
	}

	public void reloadDetailTable(Row masterRow) {
		if (this.details != null) {
			String key = this.tableLoader.getMasterKeysValue(masterRow);
			this.details.remove(key);
		}
		doMasterScrolled(masterRow);
	}

	private void doMasterScrolled(Row masterRow) {
		this.setTable(this.getDetailTable(masterRow));
	}

	private void doBeforeMasterDelete(Row masterRow) {
		Table table = this.getDetailTable(masterRow);
		String keyColumnName = getKeyColumnName();
		if (Utils.isEmptyString(keyColumnName)) {
			// 视图无主键，不进行级联删除
			return;
		}
		Set<Row> normals = new HashSet<Row>();
		normals.addAll(new TableImpl(table).getRows());
		Iterator<Row> i = normals.iterator();
		while (i.hasNext()) {
			if ("delete".equals(tableLoader.getCascade())) {
				// 删除明细表所有数据
				table.deleteRows(i.next().getOldValue(keyColumnName));
			} else if ("setNull".equals(tableLoader.getCascade())) {
				// 外键字段设置为null
				i.next().setValue(tableLoader.getForignKeys(), null);
			}
		}
	}

	private void scroll() {
		for (ScrollListener listener : this.scrollListeners)
			listener.scroll((Row) this.currentObject);
	}

	@Override
	public boolean contains(Object obj) {
		return this.tableImpl.contains(obj);
	}

	@Override
	public Object getCurrentObject() {
		return currentObject;
	}

	@Override
	public void setCurrentObject(Object value) {
		if (value == null) {
			throw new RuntimeException("cannt currentObject to null");
		}
		if (value == currentObject) {
			return;
		}
		if (!this.contains(value)) {
			this.currentObject = null;
			this.currIdx = -1;
		} else {
			this.currIdx = this.tableImpl.indexOf(value);
			if (this.currIdx >= 0)
				this.currentObject = (Row) value;
			else
				throw new RuntimeException("计算逻辑异常(setCurrentObject):数据对象不在当前列表内");
		}
		scroll();
	}

	public void setCursorIndex(int index) {
		boolean changed = false;
		if (index < 0) {
			currIdx = -1;
			changed = currentObject != null;
			currentObject = null;
		} else {
			Object tmp = (Row) this.tableImpl.getRow(index);
			changed = tmp != currentObject;
			currentObject = tmp;
		}
		if (changed)
			scroll();
	}

	private void checkDetialTable() {
		if (master != null) {
			if (this.tableImpl == null || this.tableImpl.getTarget() != this.getDetailTable((Row) master.getCurrentObject()))
				doMasterScrolled((Row) master.getCurrentObject());
		}
	}

	@Override
	public void first() {
		checkDetialTable();
		if (this.tableImpl.getTarget().size() == 0) {
			this.currIdx = -1;
			this.currentObject = null;
			scroll();
		} else
			setCurrentObject(this.tableImpl.getTarget().iterator().next());
	}

	@Override
	public Object next() {
		// checkDetialTable();
		setCurrentObject(this.tableImpl.getRow(++currIdx));
		return currentObject;
	}

	@Override
	public boolean locate(Map<BaseConfig, Object> params) {
		if (params == null || params.isEmpty()) {
			return false;
		}

		Iterator<Row> it = this.tableImpl.getTarget().iterator();
		while (it.hasNext()) {
			Row temp = it.next();
			if (isSameAttributeValues(temp, params)) {
				setCurrentObject(temp);
				return true;
			}
		}
		setCursorIndex(-1);
		return false;
	}

	private boolean isSameAttributeValue(Row row, String column, Object value) {
		try {
			if (column.indexOf(".") >= 0)
				column = column.substring(column.indexOf(".") + 1, column.length());
			Object colValue = row.getValue(column);
			return colValue == null && value == null || colValue != null && colValue.equals(value) || value != null && value.equals(colValue);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

	private boolean isSameAttributeValues(Row row, Map<BaseConfig, Object> params) {
		boolean result = true;
		for (Iterator<Entry<BaseConfig, Object>> i = params.entrySet().iterator(); i.hasNext();) {
			Entry<BaseConfig, Object> e = i.next();
			result = result && isSameAttributeValue(row, e.getKey().getObjectId(), e.getValue());
			if (!result) {
				return result;
			}
		}
		return result;
	}

	@Override
	public String getObjectId() {
		return this.objectId;
	}

	@Override
	public void setObjectId(String objectId) {
		this.objectId = objectId;

	}

	@Override
	public long getCount() {
		return this.tableImpl.getTarget().size();
	}

	@Override
	public void append() {
		if (master != null && master.getCurrentObject() == null)
			throw new RuntimeException("主表对象" + master.objectId + "当前行为空记录，不能新增");
		Row rec;
		if (memoryTable) {
			rec = BizDataUtils.createMemoryRow(this.tableImpl.getTarget());
		} else {
			rec = BizDataUtils.createRow(this.tableImpl.getTarget(), store, null, null);
			if (master != null) {
				this.tableLoader.setDetailForignKeyValues((Row) master.getCurrentObject(), rec);
			}
		}

		setCurrentObject(rec);
	}

	private String getKeyColumnName() {
		return this.tableImpl.getTarget().getMetaData().getKeyColumnName();
	}

	public void delete() {
		if (currentObject == null) {
			throw new RuntimeException("空数据集无法执行删除");
		}

		if (tableLoader instanceof QueryDataLoader)
			Utils.check(!((QueryDataLoader) tableLoader).getDesc().isQuery(), TableControlObject.class, "查询数据集不支持DDL操作");
		String keyColumnName = getKeyColumnName();
		if (Utils.isEmptyString(keyColumnName)) {
			throw new BusinessException(this.getObjectId() + "未设置主键，不允许删除");
		}

		for (DeleteListener listener : this.deleteListeners)
			listener.delete((Row) this.currentObject);

		Object obj = ((Row) currentObject).getValue(keyColumnName);
		this.tableImpl.getTarget().deleteRows(obj);
		// 计算新索引号
		if (currIdx >= this.tableImpl.getTarget().size()) {
			currIdx = this.tableImpl.getTarget().size() - 1;
		}
		setCursorIndex(currIdx);
	}

	@Override
	public boolean hasNext() {
		checkDetialTable();
		return this.tableImpl.getTarget().size() > 0 && currIdx < this.tableImpl.getTarget().size() - 1;
	}

	public void save(SaveCallback callback) {
		if (store == null)
			return;
		if (master == null) {
			if (callback != null)
				callback.save(this.tableImpl.getTarget());
			else
				this.tableImpl.getTarget().save(dataModel, ModelUtils.getModel("/base/core/logic/fn"), true, false);
			BizDataUtils.mergeStateAfterSave(this.tableImpl.getTarget());
		} else {
			for (Table table : details.values()) {
				if (callback != null)
					callback.save(table);
				else
					table.save(dataModel, ModelUtils.getModel("/base/core/logic/fn"), true, false);
				BizDataUtils.mergeStateAfterSave(table);
			}
		}
	}

	/**
	 * 重置所有Table状态
	 */
	public void resetStatus() {
		if (master == null) {
			BizDataUtils.mergeStateAfterSave(this.tableImpl.getTarget());
		} else {
			for (Table table : details.values()) {
				BizDataUtils.mergeStateAfterSave(table);
			}
		}
	}

	/**
	 * 清空所有数据
	 */
	public void emptyAllData() {
		// 设置索引位
		this.setCursorIndex(-1);
		if (master == null) {
			this.tableImpl.empty();
		} else {
			for (Table table : details.values()) {
				new TableImpl(table).empty();
			}
		}
	}

	/**
	 * 删除数据
	 * 
	 * @return
	 */
	public long clear() {
		long ret = this.getCount();
		while (this.getCount() > 0) {
			first();
			delete();
		}
		return ret;
	}

	private void rowInfoOut(Table table) {
		int k = 1;
		for (Iterator<Row> it = table.iterator(); it.hasNext();) {
			Row r = it.next();
			System.out.println((k++) + r.toString());
		}
	}

	/**
	 * 输出操作日志
	 * 
	 * @param row
	 * @param type
	 */
	public void rowInfoOut() {
		System.out.println("数据对象[" + this.objectId + "]数据状态：");
		if (master == null) {
			rowInfoOut(this.tableImpl.getTarget());
		} else {
			for (Table table : details.values()) {
				rowInfoOut(table);
			}
		}

	}
}
