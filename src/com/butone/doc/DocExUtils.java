package com.butone.doc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;

import com.butone.system.SystemConst;
import com.butone.utils.SysUtils;

/**
 * 内部文档库   UIServer
 * @author Administrator
 *
 */
public class DocExUtils {
	public static String dtOffice = "office";
	public static String uploadFile(String type, InputStream inStream, String ext) throws Exception {
		Calendar now = GregorianCalendar.getInstance();
		now.setTimeInMillis(System.currentTimeMillis());
		String relativePath = File.separator + type + File.separator + now.get(Calendar.YEAR) + File.separator + (now.get(Calendar.MONTH) + 1)
				+ File.separator + now.get(Calendar.DAY_OF_MONTH) + File.separator + SysUtils.guid() + ext;
		;
		String fileName = SystemConst.getDocExPath() + relativePath;
		File newFile = new File(fileName);
		newFile.getParentFile().mkdirs();
		int byteread = 0;
		FileOutputStream fs = null;
		try {
			newFile.createNewFile();

			fs = new FileOutputStream(newFile);
			byte[] buffer = new byte[1204];
			while ((byteread = inStream.read(buffer)) != -1) {
				fs.write(buffer, 0, byteread);
			}
			return relativePath;
		} finally {
			if (inStream != null)
				try {
					inStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			if (fs != null)
				try {
					fs.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}

	public static InputStream downFile(String relativePath) throws Exception {
		String filePath = SystemConst.getDocExPath() + File.separator + relativePath;
		File file = new File(filePath);
		InputStream inputStream = new FileInputStream(file);
		return inputStream;
	}
	
	/**
	 * 获得文档临时路径 
	 * @return
	 * @throws Exception
	 */
	public static String getDocTempDir() throws Exception{
		String docTempPath =SystemConst.getDocExPath()+ File.separator+"Temp";
		File f = new File(docTempPath);
		if(!f.exists())
			f.mkdirs();
		return docTempPath;
	}
}
