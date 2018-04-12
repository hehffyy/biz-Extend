package com.butone.flowbiz;

public enum FinishKind {
	fkCertification, fkUntread, fkAbort, fkDelete, fkPaper,fkSubmit, fkApprizeAbort, fkNormal;

	public String getDisplayName() {
		switch (this) {
		case fkCertification:
			return "发证办结";
		case fkUntread:
			return "退回办结";
		case fkAbort:
			return "作废办结";
		case fkDelete:
			return "删除办结";
		case fkPaper:
			return "纸质办结";
		case fkSubmit:
			return "转报办结";
		case fkApprizeAbort:
			return "补正不来办结";
		default:
			return "办结";
		}
	}

	public boolean isMatch(BizRecStatus status) {
		if (BizRecStatus.bsAborted.equals(status)) {
			return this.equals(fkAbort) || this.equals(fkDelete) || this.equals(fkApprizeAbort) || this.equals(fkUntread) || this.equals(fkPaper);
		} else if (BizRecStatus.bsFinished.equals(status)) {
			return this.equals(fkCertification) || this.equals(fkNormal);
		} else if (BizRecStatus.bsSuspended.equals(status)) {
			return this.equals(fkSubmit);
		}
		return false;
	}
}
