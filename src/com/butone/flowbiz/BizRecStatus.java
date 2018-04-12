package com.butone.flowbiz;

public enum BizRecStatus {
	bsProcessing, bsFinished, bsAborted, bsSuspended;
	public static boolean isBizRecStatus(String name) {
		for (BizRecStatus item : BizRecStatus.values()) {
			if (item.name().equals(name))
				return true;
		}
		return false;
	}

	public String getDisplayName() {
		switch (this) {
		case bsFinished:
			return "已办结";
		case bsAborted:
			return "已作废";
		case bsSuspended:
			return "已挂起";
		default:
			return "办理中";
		}
	}

}
