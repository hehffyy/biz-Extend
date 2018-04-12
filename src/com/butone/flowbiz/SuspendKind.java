package com.butone.flowbiz;

public enum SuspendKind {
	skApprize, skSpecialProcedure, skSubmit, skSuspend;

	public String getDisplayName() {
		switch (this) {
		case skApprize:
			return "补正告知";
		case skSpecialProcedure:
			return "特别程序";
		case skSubmit:
			return "转报办结";
		default:
			return "挂起";
		}
	}

	public static boolean isSuspendKind(String name) {
		for (SuspendKind item : SuspendKind.values()) {
			if (item.name().equals(name))
				return true;
		}
		return false;
	}

}
