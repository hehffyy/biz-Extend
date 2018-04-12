package com.butone.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;

import com.alibaba.fastjson.JSONObject;
import com.butone.data.SQLUtils;
import com.butone.flowbiz.FlowBizConsts;
import com.justep.exception.BusinessException;
import com.justep.model.ModelUtils;
import com.justep.system.action.ActionUtils;
import com.justep.system.data.DatabaseProduct;
import com.justep.system.data.Row;
import com.justep.system.data.SQL;
import com.justep.system.data.Table;

public class SysUtils {

	public static String getHttpResponse(String url, Map<String, String> parms) throws Exception {
		String result = "";
		// 构造HttpClient的实例
		HttpClient httpClient = new HttpClient();
		httpClient.getParams().setContentCharset("UTF-8");
		// 创建GET方法的实例
		PostMethod postMethod = null;
		postMethod = new PostMethod(url);
		if (parms != null) {
			NameValuePair[] data = new NameValuePair[parms.keySet().size()];
			Iterator<Entry<String, String>> it = parms.entrySet().iterator();
			int i = 0;
			while (it.hasNext()) {
				Entry<String, String> entry = it.next();
				String key = entry.getKey();
				String value = entry.getValue();
				data[i] = new NameValuePair(key, value);
				i++;
			}
			// 将表单的值放入postMethod中
			postMethod.setRequestBody(data);
			httpClient.executeMethod(postMethod);
			InputStream in = postMethod.getResponseBodyAsStream();
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				int r = -1;
				byte[] buff = new byte[4096];
				while ((r = in.read(buff)) != -1) {
					out.write(buff, 0, r);
				}
				Header header = postMethod.getResponseHeader("Content-Type");
				String charset = "utf-8";
				if (header != null) {
					String[] args = header.getValue().toLowerCase().split(";");
					for (String s : args) {
						if (s != null && s.startsWith("charset=")) {
							charset = s.split("=")[1];
							break;
						}
					}
				}
				result = out.toString(charset);
				out.close();
				//result = postMethod.getResponseBodyAsString();
			} finally {
				try {
					in.close();
				} catch (Exception e) {

				}
			}

		}
		return result;
	}

	public static JSONObject callRest(String url, JSONObject paramJson) {
		//调用服务
		String analResult = null;
		try {
			Map<String, String> paramMap = new HashMap<String, String>();
			Iterator<String> iter = paramJson.keySet().iterator();
			while (iter.hasNext()) {
				String key = iter.next();
				String value = paramJson.get(key).toString();
				paramMap.put(key, value);
			}
			analResult = SysUtils.getHttpResponse(url, paramMap);
			return JSONObject.parseObject(analResult);
		} catch (Exception e) {
			throw new BusinessException("调用Rest服务失败，返回结果：" + analResult);
		}

	}

	public static List<String> parseSqlParams(String str) {
		List<String> list = new ArrayList<String>();
		Pattern p = Pattern.compile("(?<=\\[)(.+?)(?=\\])");
		Matcher m = p.matcher(str);
		while (m.find()) {
			String param = m.group(0);
			if (list.indexOf(param) == -1)
				list.add(param);
			System.out.println(m.group(0));
		}
		return list;
	}

	public static String guid() {
		return UUID.randomUUID().toString().toUpperCase().replaceAll("-", "");
	}

	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		// 目录此时为空，可以删除
		return dir.delete();
	}

	public static void main(String[] args) {
		parseSqlParams("select '[123]' from dual where s=[123]");
	}

	public static void copyFile(InputStream inputStream, String targetFile) throws Exception {
		BufferedInputStream inBuff = null;
		BufferedOutputStream outBuff = null;
		try {
			// 新建文件输入流并对它进行缓冲
			inBuff = new BufferedInputStream(inputStream);

			// 新建文件输出流并对它进行缓冲
			outBuff = new BufferedOutputStream(new FileOutputStream(targetFile));

			// 缓冲数组
			byte[] b = new byte[6144];
			int len;
			while ((len = inBuff.read(b, 0, b.length)) != -1) {
				outBuff.write(b, 0, len);
			}
			// 刷新此缓冲的输出流
			outBuff.flush();
		} finally {
			// 关闭流
			if (inBuff != null)
				inBuff.close();
			if (outBuff != null)
				outBuff.close();
		}
	}

	public static String getTempDir() {
		return System.getProperty("java.io.tmpdir");
	}

	public static Object executeAction(String action, Map<String, Object> params) {
		com.justep.system.context.ActionContext context = ModelUtils.getRequestContext().getActionContext();
		String process = context.getProcess().getFullName();
		String activity = context.getActivity().getName();
		String ex = context.getExecutor();
		return ActionUtils.invokeAction(process, activity, action, ex, params);
	}

	//查询数据
	public static Table query(String sql) {
		Map<String, String> sqpMap = new HashMap<String, String>();
		sqpMap.put(DatabaseProduct.DEFAULT.name(), sql);
		Table table = SQL.select(sqpMap, null, FlowBizConsts.DATA_MODEL_SYSTEM);
		return table;
	}

	public static Table query(String sql, Object... params) {
		List<Object> paramList = new ArrayList<Object>();
		for (int i = 0; i < params.length; i++) {
			paramList.add(params[i]);
		}
		Table table = SQLUtils.select(sql, paramList, FlowBizConsts.DATA_MODEL_SYSTEM);
		return table;
	}

	public static Object queryFldValue(String sql) {
		Table table = query(sql);
		if (!table.iterator().hasNext())
			return null;
		else
			return table.iterator().next().getValue(0);
	}

	public static Object queryFldValue(String sql, Object... params) {
		Table table = query(sql, params);
		if (!table.iterator().hasNext())
			return null;
		else
			return table.iterator().next().getValue(0);
	}

	public static List<Object> queryFldValues(String sql, Object... params) {
		Table table = query(sql, params);
		if (!table.iterator().hasNext())
			return null;
		else {
			List<Object> list = new ArrayList<Object>();
			Iterator<Row> iter = table.iterator();
			while (iter.hasNext())
				list.add(iter.next().getValue(0));
			return list;
		}
	}

	public static Map<String, Object> queryFldsValue(String sql) {
		Table table = query(sql);
		if (!table.iterator().hasNext())
			return null;
		else {
			Row row = table.iterator().next();
			Map<String, Object> result = new HashMap<String, Object>();
			for (int i = 0; i < table.getColumnCount(); i++) {
				String fldName = table.getMetaData().getColumnName(i);
				result.put(fldName, row.getValue(fldName));
			}
			return result;
		}

	}

	public static Map<String, Object> queryFldsValue(String sql, Object... params) {
		Table table = query(sql, params);
		if (!table.iterator().hasNext())
			return null;
		else {
			Row row = table.iterator().next();
			Map<String, Object> result = new HashMap<String, Object>();
			for (int i = 0; i < table.getColumnCount(); i++) {
				String fldName = table.getMetaData().getColumnName(i);
				result.put(fldName, row.getValue(fldName));
			}
			return result;
		}

	}

	public static Map<String, Object> queryFldsValue(String sql, List<Object> paramList) {
		Map<String, String> sqpMap = new HashMap<String, String>();
		sqpMap.put(DatabaseProduct.DEFAULT.name(), sql);
		Table table = SQL.select(sqpMap, paramList, FlowBizConsts.DATA_MODEL_SYSTEM);
		if (!table.iterator().hasNext())
			return null;
		else {
			Row row = table.iterator().next();
			Map<String, Object> result = new HashMap<String, Object>();
			for (int i = 0; i < table.getColumnCount(); i++) {
				String fldName = table.getMetaData().getColumnName(i);
				result.put(fldName, row.getValue(fldName));
			}
			return result;
		}

	}

	//执行Sql
	public static void executeSql(String sql) {
		Map<String, String> sqpMap = new HashMap<String, String>();
		sqpMap.put(DatabaseProduct.DEFAULT.name(), sql);
		SQL.executeUpdate(sqpMap, null, FlowBizConsts.DATA_MODEL_SYSTEM);
	}

	//执行带参数的Sql
	public static void executeSql(String sql, Object... params) {
		List<Object> paramList = new ArrayList<Object>();
		for (int i = 0; i < params.length; i++) {
			paramList.add(params[i]);
		}
		Map<String, String> sqpMap = new HashMap<String, String>();
		sqpMap.put(DatabaseProduct.DEFAULT.name(), sql);
		SQL.executeUpdate(sqpMap, paramList, FlowBizConsts.DATA_MODEL_SYSTEM);
	}

	//执行带参数的Sql
	public static void executeSql(String sql, List<Object> paramList) {
		Map<String, String> sqpMap = new HashMap<String, String>();
		sqpMap.put(DatabaseProduct.DEFAULT.name(), sql);
		SQL.executeUpdate(sqpMap, paramList, FlowBizConsts.DATA_MODEL_SYSTEM);
	}

	//map转FastJson
	public static JSONObject map2FastJson(Map<String, Object> map) {
		JSONObject result = new JSONObject();
		Iterator<String> keys = map.keySet().iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			result.put(key, map.get(key));
		}
		return result;
	}

	public static boolean ifNull(Object obj) {
		if (obj == null)
			return true;
		else if (obj instanceof String) {
			if (obj.toString().trim().equals(""))
				return true;
			else
				return false;
		} else
			return false;
	}

	public static InputStream getInputStream(FileInputStream fileInput) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024 * 4];
		int n = -1;
		InputStream inputStream = null;
		try {
			while ((n = fileInput.read(buffer)) != -1) {
				baos.write(buffer, 0, n);

			}
			byte[] byteArray = baos.toByteArray();
			inputStream = new ByteArrayInputStream(byteArray);
			return inputStream;

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block  
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block  
					e.printStackTrace();
				}
			}
		}
	}

}
