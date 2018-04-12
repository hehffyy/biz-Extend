package com.butone.extend;

import java.io.File;

import com.justep.model.Concept;

public class ModelPathHelper {

	public static String getProcessOntology(com.justep.model.Process process) {
		File file = new File(process.getFullName());
		String fullName = file.getParentFile().getParentFile().getParent() + "/ontology";
		fullName = fullName.replace('\\', '/');
		return fullName;
	}

	public static String getProcessDataModel(com.justep.model.Process process) {
		File file = new File(process.getFullName());
		String fullName = file.getParentFile().getParentFile().getParent() + "/data";
		fullName = fullName.replace('\\', '/');
		return fullName;
	}

	public static String getBizPath(com.justep.model.Process process) {
		File file = new File(process.getFullName());
		return file.getParentFile().getParentFile().getParent().replace('\\', '/');
	}

	public static String getProcessBizPath(String process) {
		File file = new File(process);
		return file.getParentFile().getParentFile().getParent().replace('\\', '/');
	}

	public static String getConceptOntology(Concept concept) {
		File file = new File(concept.getFullName());
		String url = file.getParentFile().getParent() + "/ontology";
		url = url.replace('\\', '/');
		return url;
	}

	public static String getConceptDataModel(Concept concept) {
		File file = new File(concept.getFullName());
		String url = file.getParentFile().getParent() + "/data";
		url = url.replace('\\', '/');
		return url;
	}
}
