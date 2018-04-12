package com.butone.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;
import com.butone.system.SystemConst;
import com.butone.utils.SysUtils;
import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import com.justep.exception.BusinessException;

/**
 * Excel报表函数库V1.0
 * 页面布局  考虑先布局  再设置数据
 * @author Administrator
 *
 */
public class ExcelReportUtils {

	/**
	 * config:{name:
	 * '',sql:'',params:[{key:'',value:''}],children:[{name:'',action:'',params:[],keyFldName:'',masterFldName}}
	 * ]
	 * 
	 * @throws Exception
	 */
	public static String getReportFile(String templateKey, String sqlParam, String outFileType, String extendParam,String customSql) throws Exception {
		System.out.println("开始绘制：");
		long start = System.currentTimeMillis();
		Map<String, Object> configParam = SysUtils.queryFldsValue(
				"select FSql,FPath from b_Officetemplate t,b_officeversion v  where t.fid=v.fbizkey and FTemplateKey=? ", templateKey);
		String template = SystemConst.getDocExPath() + configParam.get("FPATH").toString();
		String config = configParam.get("FSQL").toString();
		JSONObject sqlParamJson = JSONObject.parseObject(sqlParam);
		CommonExcelReport report = new CommonExcelReport();
		report.initConfig(config, template, sqlParamJson, extendParam,customSql);

		long end = System.currentTimeMillis();
		System.out.println("初始化耗时:" + (end - start) + " ms");

		String result = report.genReport(outFileType);

		end = System.currentTimeMillis();
		System.out.println("绘制耗时:" + (end - start) / 1000 + " s");
		return result;
	}

	/**
	 * 产生报表流
	 * @param templateKey 模板key	
	 * @param sqlParam	  sql参数
	 * @param outFileType  输出文件类型excel pdf
	 * @param extendParam 扩展参数  hideCols
	 * @return
	 * @throws Exception
	 */
	public static InputStream genReportStream(String templateKey, Map<String, Object> sqlParam, String outFileType, Map<String, Object> extendParam,String customSql)
			throws Exception {
		String fileName = "";
		InputStream result = null;
		try {
			if (sqlParam == null)
				sqlParam = new HashMap<String, Object>();
			if (extendParam == null)
				extendParam = new HashMap<String, Object>();
			String sqlParamStr = SysUtils.map2FastJson(sqlParam).toString();
			String extendParamStr = SysUtils.map2FastJson(extendParam).toString();
			fileName = getReportFile(templateKey, sqlParamStr, outFileType, extendParamStr,customSql);
			result = SysUtils.getInputStream(new FileInputStream(fileName));
		} finally {
			SysUtils.deleteDir(new File(fileName));
		}
		return result;
	}

	public static void main(String[] args) {
		System.out.println(System.getProperty("java.library.path"));
		excel2pdf("C:\\test123.xlsx", "c:\\test123.pdf");
	}

	public static void excel2pdf(String els, String pdf) {
		long start = System.currentTimeMillis();

		ActiveXComponent app = new ActiveXComponent("Excel.Application");

		try {

			app.setProperty("Visible", false);

			Dispatch workbooks = app.getProperty("Workbooks").toDispatch();

			System.out.println("opening document:" + els);

			Dispatch workbook = Dispatch.invoke(workbooks, "Open", Dispatch.Method, new Object[] { els, new Variant(false), new Variant(false) },
					new int[3]).toDispatch();

			Dispatch.invoke(workbook, "SaveAs", Dispatch.Method, new Object[] {

			pdf, new Variant(57), new Variant(false),

			new Variant(57), new Variant(57), new Variant(false),

			new Variant(true), new Variant(57), new Variant(true),

			new Variant(true), new Variant(true) }, new int[1]);

			Variant f = new Variant(false);

			System.out.println("to pdf " + pdf);

			Dispatch.call(workbook, "Close", f);

			long end = System.currentTimeMillis();

			System.out.println("completed..used:" + (end - start) / 1000 + " s");

		} catch (Exception e) {
			System.out.println("========Error:Operation fail:" + e.getMessage());
			throw new BusinessException(e.getMessage());

		} finally {
			if (app != null) {
				app.invoke("Quit", new Variant[] {});
			}
			ComThread.Release();
			System.out.println("转换PDF完成");
		}
	}

}
