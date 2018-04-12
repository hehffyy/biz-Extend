package com.butone.utils;

import com.gdcalib.PSIApp;
import com.justep.exception.BusinessException;

public class GDCAUtils {
	private static PSIApp psiapp;

	private static PSIApp getPSIApp() {
		try {
			if (psiapp == null)
				psiapp = new PSIApp();
		} catch (Exception e) {
			throw new BusinessException("加载CA服务器类库失败", e);
		}
		int curStatus = psiapp.GetCurStatus();
		if (curStatus != 0)
			throw new BusinessException("CA服务器状态异常\nCurStatus =" + curStatus);
		return psiapp;

	}

	/**
	 * 检查证书有效性
	 * 
	 * @param userCert
	 * @return
	 */
	public static boolean checkGDCACert(String userCert) {
		PSIApp psij = getPSIApp();
		try {
			return psij.AdvCheckCert(userCert);
		} finally {
			psij.release();
		}

	}

	/**
	 * 验证密文有效性
	 * 
	 * @param signCert
	 * @param inData
	 *            明文
	 * @param encData
	 *            密文
	 * @return
	 */
	public static boolean verifySignData(String signCert, String inData, String encData) {
		PSIApp psij = getPSIApp();
		try {
			try {
				if(inData==null) inData="";
				// i_algoType : 签名方式 GDCA_ALGO_SHA1 = 32772
				// i_signType : 签名类型 0 不含原文
				return psij.AdvVerifySign(signCert, inData.getBytes("gb2312"), encData, 32772, 0);
			} catch (Exception e) {
				throw new BusinessException("验证签名数据失败", e);
			}
		} finally {
			psij.release();
		}

	}
}
