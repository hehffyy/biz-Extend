package com.butone.data;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.butone.utils.StringUtils;
import com.justep.client.DatabaseProduct;
import com.justep.exception.BusinessException;
import com.justep.message.BusinessMessages;
import com.justep.message.CommonMessages;
import com.justep.model.Model;
import com.justep.model.ModelUtils;
import com.justep.model.exception.ModelException;
import com.justep.system.data.ColumnMetaData;
import com.justep.system.data.ColumnTypes;
import com.justep.system.data.Row;
import com.justep.system.data.Table;
import com.justep.system.data.TableUtils;
import com.justep.system.data.Transaction;
import com.justep.system.process.ExpressEngine;
import com.justep.util.Utils;

public class SQLUtils {
	private static final Log logger = LogFactory.getLog(SQLUtils.class);
	private final static String ORACLE_PAGESQLFMT = "Select * from (select rownum C_ROWIDX,Tmp_PQT.* from (%s) Tmp_PQT ) where C_ROWIDX between ? and ?";

	public static String appendCondition(String base, String operator, String condition) {
		if (Utils.isEmptyString(base)) {
			return condition;
		} else {
			if (Utils.isEmptyString(condition)) {
				return base;
			} else {
				return "((" + base + ") " + operator + " (" + condition + "))";
			}
		}
	}

	public static boolean isSupportSqlPageFetch(String productName) {
		return DatabaseProduct.ORACLE.equals(productName);
	}

	public static String getPageSelectSql(String productName, String sql, List<Object> binds, int offset, int limit) {
		if (DatabaseProduct.valueOf(productName).equals(DatabaseProduct.ORACLE)) {
			binds.add(offset);
			binds.add(offset + limit);
			String pageSQL = String.format(ORACLE_PAGESQLFMT, sql);
			return pageSQL;
		}
		throw new RuntimeException(productName + "暂不支持分页取数");
	}

	public static Table select(Map<String, String> sqlMap, List<Object> paramList, String paramString) {
		return (Table) select(sqlMap, paramList, ModelUtils.getModel(paramString), 0, -1);
	}

	public static Table select(String sql, List<Object> paramList, String dataModel) {
		return (Table) select(sql, paramList, ModelUtils.getModel(dataModel), 0, -1);
	}

	public static int executeUpdate(Map<String, String> paramMap, List<Object> paramList, String paramString) {
		return executeUpdate(paramMap, paramList, ModelUtils.getModel(paramString));
	}

	public static int executeUpdate(Map<String, String> paramMap, List<Object> paramList, Model paramModel) {
		Utils.check((Utils.isNotNull(paramMap)) && (!paramMap.isEmpty()), CommonMessages.class, "JUSTEP050006", "sql");
		Utils.check(Utils.isNotNull(paramModel), CommonMessages.class, "JUSTEP050006", "dataModel");
		if (paramList == null)
			paramList = new ArrayList<Object>();
		com.justep.client.DatabaseProduct localDatabaseProduct = ModelUtils.getRequestContext().getTransaction().getDatabaseProduct(paramModel);
		String sql = null;
		if (paramMap.containsKey(localDatabaseProduct.name())) {
			sql = (String) paramMap.get(localDatabaseProduct.name());
		} else {
			Utils.check(paramMap.containsKey(DatabaseProduct.DEFAULT.toString()), BusinessMessages.class, "JUSTEP150037", localDatabaseProduct.name());
			sql = (String) paramMap.get(DatabaseProduct.DEFAULT.toString());
		}
		Utils.check(Utils.isNotEmptyString(sql), BusinessMessages.class, "JUSTEP150038");
		Connection localConnection = null;
		PreparedStatement localPreparedStatement = null;
		try {
			Transaction localTransaction = ModelUtils.getRequestContext().getTransaction();
			localConnection = localTransaction.getConnection(paramModel);
			localTransaction.begin(localConnection);
			localPreparedStatement = localConnection.prepareStatement(sql);
			setParameters(localPreparedStatement, paramList, DatabaseProduct.getProduct(localConnection));
			return localPreparedStatement.executeUpdate();
		} catch (Exception localException1) {
			throw BusinessException.create(localException1, "JUSTEP150039", new Object[] { sql, paramList.toString() });
		} finally {
			try {
				if (localPreparedStatement != null)
					localPreparedStatement.close();
			} catch (Exception localException2) {
			}
			try {
				if (localConnection != null)
					localConnection.close();
			} catch (Exception localException3) {
			}
		}
	}

	// public static List<String> parseSqlParameterNames(String sql) {
	// List<String> paramNames = new ArrayList<String>();
	// // Pattern p = Pattern.compile("(:[a-zA-Z0-9_]*)");
	// Pattern p = Pattern.compile(":(\\w+)\\b");
	// Matcher m = p.matcher(sql);
	// while (m.find()) {
	// paramNames.add(m.group());
	// }
	// return paramNames;
	// }

	public static boolean isSQLFunction(String sql, int pos) {
		for (int i = pos; i < sql.length() - 1; i++) {
			Character c = sql.charAt(i);
			if (!Character.isWhitespace(c)) {
				return c.equals('(');
			}
		}
		return false;

	}

	/**
	 * 解析sql中的KSQL函数
	 * 
	 * @param sql
	 * @param m
	 * @param variables
	 * @return
	 */
	private static int parseSQLFunction(String sql, Matcher m) {
		return parseSQLFunction(sql, m.end());
	}

	private static int parseSQLFunction(String sql, int beginIndex) {
		int n = 0, end = beginIndex;
		for (; end < sql.length(); end++) {
			Character c = sql.charAt(end);
			if (c.equals('(')) {
				n++;
			} else if (c.equals(')')) {
				if (--n == 0)
					break;
			}
		}
		return end;
	}

	public static List<Object> parseSqlParameters(String sql, Map<String, Object> variables) {
		List<Object> paramList = new ArrayList<Object>();
		if (variables == null)
			return paramList;
		Pattern p = Pattern.compile(":([\\w\u4e00-\u9fa5]+)\\b");
		Matcher m = p.matcher(sql);
		int n = 0, end = 0;
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		while (m.find()) {
			if (isSQLFunction(sql, m.end())) {
				if (m.start() >= end) {
					end = parseSQLFunction(sql, m);
					String expr = sql.substring(m.start(), end + 1);
					if (!expr.startsWith(":inClause(")) {
						String fnVar = "_fnVar_" + (n++);
						Object value = ExpressEngine.calculate(expr, variables, fnModel);
						variables.put(fnVar, value);
						paramList.add(value);
					}
				}
			} else if (m.start() > end) {
				paramList.add(variables.get(m.group().substring(1)));
			}
		}
		return paramList;
	}

	/**
	 * 修正SQL语句，替换:inClause函数；如果replaceParam为true，将宿主参数替换为“?”。
	 * 
	 * @param sql
	 * @param variables
	 * @param replaceParam
	 * @return
	 */

	public static String fixSQL(String sql, Map<String, Object> variables, boolean replaceParam) {
		if (Utils.isEmptyString(sql))
			return sql;
		// 1.先替換inClause
		String str = sql;
		Model fnModel = ModelUtils.getModel("/base/core/logic/fn");
		while (str.contains(":inClause(")) {
			int i = str.indexOf(":inClause(");
			int end = parseSQLFunction(str, i + 9);
			String expr = str.substring(i + 1, end + 1);
			String in = (String) ExpressEngine.calculate(expr, variables, fnModel);
			str = str.substring(0, i) + in + str.substring(end + 1);
		}

		// 2.解析KSQL函数 开始结束位置
		if (replaceParam) {
			Pattern p = Pattern.compile(":([\\w\u4e00-\u9fa5]+)\\b");
			Matcher m = p.matcher(str);
			Stack<Integer[]> fn = new Stack<Integer[]>();
			int end = -1;
			while (m.find()) {
				if (isSQLFunction(str, m.end()) && m.start() >= end) {
					end = parseSQLFunction(str, m);
					fn.push(new Integer[] { m.start(), end });
				}
			}

			// 3. 修正SQL
			while (fn.size() > 0) {
				Integer[] pos = fn.pop();
				str = str.substring(0, pos[0]) + "?" + str.substring(pos[1] + 1);
			}
			str = str.replaceAll(":([\\w\u4e00-\u9fa5]+)\\b", "?");
		}
		return str;
	}

	public static Table select(Map<String, String> sqlMap, Map<String, Object> paramMap, Model dataModel, int offset, int limit) {
		Utils.check((Utils.isNotNull(sqlMap)) && (!sqlMap.isEmpty()), CommonMessages.class, "JUSTEP050006", "sql");
		Utils.check(Utils.isNotNull(dataModel), CommonMessages.class, "JUSTEP050006", "dataModel");

		DatabaseProduct localDatabaseProduct = ModelUtils.getRequestContext().getTransaction().getDatabaseProduct(dataModel);
		String sql = null;
		if (sqlMap.containsKey(localDatabaseProduct.name())) {
			sql = sqlMap.get(localDatabaseProduct.name());
		} else {
			Utils.check(sqlMap.containsKey(DatabaseProduct.DEFAULT.toString()), BusinessMessages.class, "JUSTEP150037", localDatabaseProduct.name());
			sql = sqlMap.get(DatabaseProduct.DEFAULT.toString());
		}
		Utils.check(Utils.isNotEmptyString(sql), BusinessMessages.class, "JUSTEP150038");
		List<Object> paramList = parseSqlParameters(sql, paramMap);
		return select(sql, paramList, dataModel, offset, limit);
	}

	public static Table select(Map<String, String> sqlMap, List<Object> paramList, Model dataModel, int offset, int limit) {
		Utils.check((Utils.isNotNull(sqlMap)) && (!sqlMap.isEmpty()), CommonMessages.class, "JUSTEP050006", "sql");
		Utils.check(Utils.isNotNull(dataModel), CommonMessages.class, "JUSTEP050006", "dataModel");
		if (paramList == null)
			paramList = new ArrayList<Object>();
		DatabaseProduct localDatabaseProduct = ModelUtils.getRequestContext().getTransaction().getDatabaseProduct(dataModel);
		String sql = null;
		if (sqlMap.containsKey(localDatabaseProduct.name())) {
			sql = sqlMap.get(localDatabaseProduct.name());
		} else {
			Utils.check(sqlMap.containsKey(DatabaseProduct.DEFAULT.toString()), BusinessMessages.class, "JUSTEP150037", localDatabaseProduct.name());
			sql = sqlMap.get(DatabaseProduct.DEFAULT.toString());
		}
		Utils.check(Utils.isNotEmptyString(sql), BusinessMessages.class, "JUSTEP150038");
		return select(sql, paramList, dataModel, offset, limit);
	}

	public static String getClobText(Clob clob) throws Exception {
		if (clob == null)
			return null;

		Reader reader = clob.getCharacterStream();
		try {
			char[] c = new char[(int) clob.length()];
			reader.read(c);
			return new String(c);
		} finally {
			reader.close();
		}

	}

	public static String blobToBase64(Object blob) throws IOException, SQLException {
		if (blob instanceof byte[])
			return StringUtils.base64Encode((byte[]) blob);
		else if (blob instanceof Blob) {
			InputStream in = ((Blob) blob).getBinaryStream();
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				BufferedOutputStream buffered = new BufferedOutputStream(out);
				byte[] buff = new byte[4096];
				int r = -1;
				while ((r = in.read(buff)) != -1) {
					buffered.write(buff, 0, r);
				}
				buffered.flush();
				String ret = StringUtils.base64Encode(out.toByteArray());
				out.close();
				return ret;
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (Exception e) {
					}
				}
			}
		} else {
			throw new BusinessException(blob + "无法转换为Base64");
		}
	}

	public static String blobToString(byte[] blob) throws SQLException, IOException {
		return new String(blob);
	}

	public static String blobToString(Object blob) throws SQLException, IOException {
		if (blob instanceof byte[])
			return new String((byte[]) blob, "gbk");
		else if (blob instanceof Blob) {
			InputStream in = ((Blob) blob).getBinaryStream();
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				BufferedOutputStream buffered = new BufferedOutputStream(out);
				byte[] buff = new byte[4096];
				int r = -1;
				while ((r = in.read(buff)) != -1) {
					buffered.write(buff, 0, r);
				}
				buffered.flush();
				String ret = out.toString("gbk");
				out.close();
				return ret;
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (Exception e) {
					}

				}
			}
		} else {
			throw new BusinessException(blob + "无法转换为String");
		}
	}

	public static Table select(String sql, List<Object> paramList, Model dataModel, int offset, int limit, Table table) {
		Connection localConnection = null;
		PreparedStatement statement = null;
		long l = System.currentTimeMillis();
		try {
			localConnection = ModelUtils.getConnection(dataModel);
			statement = localConnection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			if (limit >= 0) {
				statement.setMaxRows(offset + limit);// 0 为不限制
			} else {

			}
			DatabaseProduct db = DatabaseProduct.getProduct(localConnection);
			setParameters(statement, paramList, DatabaseProduct.getProduct(localConnection));
			ResultSet resultSet = statement.executeQuery();
			if (offset > 0) {
				resultSet.absolute(offset); // 精确定位
			} else {

			}

			if (table == null) {
				ResultSetMetaData metaData = resultSet.getMetaData();
				List<String> columnNames = new ArrayList<String>();
				List<String> columnTypes = new ArrayList<String>();
				for (int i = 1; i <= metaData.getColumnCount(); i++) {
					String columnName;
					if (db.equals(DatabaseProduct.MYSQL)) {
						columnName = metaData.getColumnLabel(i).toUpperCase();
					} else {
						columnName = metaData.getColumnName(i);
					}
					columnNames.add(columnName);
					if (metaData.getColumnType(i) == java.sql.Types.DATE) {
						columnTypes.add(ColumnTypes.DATETIME);
					} else {
						columnTypes.add(ColumnTypes.transSqlType(metaData.getColumnType(i)));
					}
				}
				table = TableUtils.createTable(dataModel, columnNames, columnTypes);
			}
			String name_ROWID = (String) table.getProperties().get(Table.PROP_NAME_ROWID);
			String keyName = table.getMetaData().getKeyColumnName();
			boolean b = name_ROWID != null && keyName != null && !name_ROWID.equals(keyName);
			List<Object> datas = b ? new ArrayList<Object>() : null;
			String[] keys = b ? keyName.split(":") : null;
			while (resultSet.next()) {
				Row row = table.appendRow();
				// TODO 数据加载行数的监控代码由biz-butone-monitor实现
				Iterator<ColumnMetaData> localIterator = table.getMetaData().getColumnMetaDatas().iterator();
				while (localIterator.hasNext()) {
					ColumnMetaData col = (ColumnMetaData) localIterator.next();
					if (b && name_ROWID.equals(col.getName())) {
						continue;
					}
					try {
						Object obj = resultSet.getObject(col.getName());
						if ("String".equals(col.getType())) {
							if (obj instanceof Clob) {
								row.setString(col.getName(), getClobText((Clob) obj));
							} else if (obj != null) {
								row.setString(col.getName(), obj.toString());
							}
						} else if ("Integer".equals(col.getType())) {
							if (obj != null)
								row.setInt(col.getName(), resultSet.getInt(col.getName()));
						} else if ("Blob".equals(col.getType())) {
							row.setBlob(col.getName(), resultSet.getBlob(col.getName()));
						} else if ("Date".equals(col.getType())) {
							row.setDate(col.getName(), resultSet.getDate(col.getName()));
						} else if ("DateTime".equals(col.getType())) {
							row.setDateTime(col.getName(), resultSet.getTimestamp(col.getName()));
						} else if ("Decimal".equals(col.getType())) {
							row.setDecimal(col.getName(), resultSet.getBigDecimal(col.getName()));
						} else if ("Float".equals(col.getType())) {
							if (obj != null)
								row.setFloat(col.getName(), resultSet.getFloat(col.getName()));
						} else if ("Object".equals(col.getType())) {
							row.setValue(col.getName(), resultSet.getObject(col.getName()));
						} else if ("Text".equals(col.getType())) {
							if (obj instanceof Clob) {
								row.setText(col.getName(), getClobText((Clob) obj));
							} else if (obj != null) {
								row.setText(col.getName(), obj.toString());
							}
						} else if ("Time".equals(col.getType())) {
							row.setTime(col.getName(), resultSet.getTime(col.getName()));
						} else {
							throw new ModelException("接收到错误的columnType：" + col.getType());
						}
					} catch (Exception e) {
						System.out.println("读取错误：" + col.getName() + ":" + col.getType());
					}
				}
				if (b) {
					datas.clear();
					for (String key : keys) {
						datas.add(row.getValue(key).toString());
					}
					row.setString(name_ROWID, ModelUtils.encode(datas));
				}

				row.resetState();
			}
			if ((offset == 0) && (limit != -1)) {
				table.getProperties().put(Table.PROP_DB_COUNT, getTotalCount(sql, paramList, dataModel));
			}
			if (logger.isDebugEnabled()) {
				logger.debug("耗时:" + (System.currentTimeMillis() - l) + "\n" + sql);
			}
			return table;
		} catch (Exception localException1) {
			throw BusinessException.create(localException1, "JUSTEP150039", new Object[] { sql, paramList.toString() });
		} finally {
			try {
				if (statement != null)
					statement.close();
			} catch (Exception localException2) {
			}
			try {
				if (localConnection != null)
					localConnection.close();
			} catch (Exception localException3) {
			}
		}
	}

	public static Integer getTotalCount(String sql, List<Object> paramList, Model dataModel) {
		Connection conn = null;
		PreparedStatement statement = null;
		try {
			conn = ModelUtils.getConnection(dataModel);
			sql = "select count(*) from (" + sql + ") ";
			// DatabaseProduct dbp = DatabaseProduct.getProduct(conn);
			// if (dbp.equals(DatabaseProduct.ORACLE)) {
			// sql += "\"TMP_CNT_\"";
			// } else {
			// sql += "TMP_CNT";
			// }
			sql += "TMP_CNT";
			statement = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			setParameters(statement, paramList, DatabaseProduct.getProduct(conn));
			ResultSet resultSet = statement.executeQuery();
			resultSet.next();
			return resultSet.getInt(1);
		} catch (Exception exception) {
			throw BusinessException.create(exception, "JUSTEP150039", new Object[] { sql, paramList.toString() });
		} finally {
			try {
				if (statement != null)
					statement.close();
			} catch (Exception localException2) {
			}
			try {
				if (conn != null)
					conn.close();
			} catch (Exception localException3) {
			}
		}
	}

	public static Table select(String sql, List<Object> paramList, Model dataModel, int offset, int limit) {
		return select(sql, paramList, dataModel, offset, limit, null);
	}

	public static void setParameterValue(PreparedStatement paramPreparedStatement, int i, Object param, DatabaseProduct paramDatabaseProduct)
			throws SQLException, IOException {
		if (DatabaseProduct.DB2.equals(paramDatabaseProduct)) {
			if (param == null) {
				paramPreparedStatement.setNull(i, getNullType(paramDatabaseProduct));
			} else if ((param instanceof InputStream)) {
				paramPreparedStatement.setBinaryStream(i, (InputStream) param, ((InputStream) param).available());
			} else if ((param instanceof BigDecimal)) {
				paramPreparedStatement.setBigDecimal(i, (BigDecimal) param);
			} else if ((param instanceof Byte)) {
				paramPreparedStatement.setInt(i, ((Byte) param).intValue());
			} else if ((param instanceof String)) {
				paramPreparedStatement.setString(i, (String) param);
			} else if ((param instanceof Short)) {
				paramPreparedStatement.setShort(i, ((Short) param).shortValue());
			} else if ((param instanceof Integer)) {
				paramPreparedStatement.setInt(i, ((Integer) param).intValue());
			} else if ((param instanceof Long)) {
				paramPreparedStatement.setLong(i, ((Long) param).longValue());
			} else if ((param instanceof Float)) {

				paramPreparedStatement.setFloat(i, ((Float) param).floatValue());
			} else if ((param instanceof Double)) {
				paramPreparedStatement.setDouble(i, ((Double) param).doubleValue());
			} else if ((param instanceof byte[])) {
				paramPreparedStatement.setBytes(i, (byte[]) (byte[]) (byte[]) param);
			} else if ((param instanceof java.sql.Date)) {
				paramPreparedStatement.setDate(i, (java.sql.Date) param);
			} else if ((param instanceof Time)) {
				paramPreparedStatement.setTime(i, (Time) param);
			} else if ((param instanceof Timestamp)) {
				paramPreparedStatement.setTimestamp(i, (Timestamp) param);
			} else if ((param instanceof Boolean)) {
				paramPreparedStatement.setBoolean(i, ((Boolean) param).booleanValue());
			} else if ((param instanceof InputStream)) {
				paramPreparedStatement.setBinaryStream(i, (InputStream) param, -1);
			} else if ((param instanceof Blob)) {
				paramPreparedStatement.setBlob(i, (Blob) param);
			} else if ((param instanceof Clob)) {
				paramPreparedStatement.setClob(i, (Clob) param);
			} else if ((param instanceof java.util.Date)) {
				paramPreparedStatement.setTimestamp(i, new Timestamp(((java.util.Date) param).getTime()));
			} else if ((param instanceof BigInteger)) {
				paramPreparedStatement.setString(i, param.toString());
			} else {
				paramPreparedStatement.setObject(i, param);
			}
		} else {
			if (param == null) {
				paramPreparedStatement.setNull(i, getNullType(paramDatabaseProduct));
			} else if ((param instanceof InputStream)) {
				paramPreparedStatement.setBinaryStream(i, (InputStream) param, ((InputStream) param).available());
			} else if ((param instanceof BigDecimal)) {
				paramPreparedStatement.setBigDecimal(i, (BigDecimal) param);
			} else {
				paramPreparedStatement.setObject(i, param);
			}
		}
	}

	public static void setParameters(PreparedStatement paramPreparedStatement, List<Object> paramList, DatabaseProduct paramDatabaseProduct)
			throws SQLException, IOException {
		if (paramList == null)
			return;
		int i = 0;
		Iterator<Object> paramIterator = paramList.iterator();
		while (paramIterator.hasNext()) {
			setParameterValue(paramPreparedStatement, ++i, paramIterator.next(), paramDatabaseProduct);
		}
	}

	private static int getNullType(DatabaseProduct paramDatabaseProduct) {
		if (DatabaseProduct.DB2.equals(paramDatabaseProduct))
			return 12;
		return 0;
	}

	// private static List<Object> testParseSqlParameters(String sql,
	// Map<String, Object> variables) {
	// List<Object> paramList = new ArrayList<Object>();
	// if (variables == null)
	// return paramList;
	// Pattern p = Pattern.compile(":([\\w\u4e00-\u9fa5]+)\\b");
	// Matcher m = p.matcher(sql);
	// int n = 0, end = 0;
	// while (m.find()) {
	// if (isSQLFunction(sql, m.end())) {
	// if (m.start() >= end) {
	// end = parseSQLFunction(sql, m);
	// String expr = sql.substring(m.start(), end + 1);
	// if (!expr.startsWith(":inClause(")) {
	// String fnVar = "_fnVar_" + (n++);
	// Object value = fnVar;
	// variables.put(fnVar, value);
	// paramList.add(value);
	// }
	// }
	// } else {
	// if (variables.containsKey(m.group().substring(1)))
	// paramList.add(variables.get(m.group().substring(1)));
	// else
	// paramList.add(m.group().substring(1));
	// }
	// }
	// return paramList;
	// }

	// private static String testFixSQL(String sql, Map<String, Object>
	// variables, boolean replaceParam) {
	// if (Utils.isEmptyString(sql))
	// return sql;
	// // 1.先替換inClause
	// String str = sql;
	// while (str.contains(":inClause(")) {
	// int i = str.indexOf(":inClause(");
	// int end = parseSQLFunction(str, i + 10);
	// String expr = str.substring(i + 1, end + 1);
	// String in = expr;
	// str = str.substring(0, i) + in + str.substring(str.indexOf(")", i) + 1);
	// }
	//
	// // 2.解析KSQL函数 开始结束位置
	// if (replaceParam) {
	// Pattern p = Pattern.compile(":([\\w\u4e00-\u9fa5]+)\\b");
	// Matcher m = p.matcher(str);
	// Stack<Integer[]> fn = new Stack<Integer[]>();
	// int end = -1;
	// while (m.find()) {
	// if (isSQLFunction(str, m.end()) && m.start() >= end) {
	// end = parseSQLFunction(str, m);
	// fn.push(new Integer[] { m.start(), end });
	// }
	// }
	//
	// // 3. 修正SQL
	// while (fn.size() > 0) {
	// Integer[] pos = fn.pop();
	// str = str.substring(0, pos[0]) + "?" + str.substring(pos[1] + 1);
	// }
	// str = str.replaceAll(":([\\w\u4e00-\u9fa5]+)\\b", "?");
	// }
	// return str;
	// }

	public static void main(String[] args) {
		// String sql =
		// "select 1 from dual where aaa=:param_123我爱你  and :f1(:f2('abc')) and :inClause('ABC',:f2('abc'))";
		// Map<String, Object> map = new HashMap<String, Object>();
		// System.out.println(testParseSqlParameters(sql, map));
		// System.out.println(sql);
		// System.out.println(testFixSQL(sql, map, true));
		// Pattern p = Pattern.compile("(:[a-zA-Z0-9_]*)");
		// Pattern p = Pattern.compile(":([\\w\u4e00-\u9fa5]+)\\b");
		// Matcher m = p.matcher(sql);
		// while (m.find()) {
		// String pname = m.group().substring(1);
		// System.out.println(pname + ":" + isSQLFunction(sql, m.end()));
		// }
	}
}
