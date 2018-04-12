package com.butone.x5Impl;

import java.lang.reflect.Field;

import com.justep.model.Concept;
import com.justep.system.ksql.ConceptMapping;

public class SymDescriptorImpl {

	private Object target;

	public SymDescriptorImpl(Object target) {
		this.target = target;
	}

	public String getKind() {
		try {
			Field fld = target.getClass().getField("kind");
			fld.setAccessible(true);
			return fld.get(target).toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getConceptName() {
		try {
			Field fld = target.getClass().getField("conceptName");
			fld.setAccessible(true);
			return (String) fld.get(target);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Concept getConcept() {
		try {
			Field fld = target.getClass().getField("concept");
			fld.setAccessible(true);
			return (Concept) fld.get(target);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setConcept(Concept concept) {
		try {
			Field fld = target.getClass().getField("concept");
			fld.setAccessible(true);
			fld.set(target, concept);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public ConceptMapping getConceptMapping() {
		try {
			Field fld = target.getClass().getField("conceptMapping");
			fld.setAccessible(true);
			return (ConceptMapping) fld.get(target);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setConceptMapping(ConceptMapping conceptMapping) {
		try {
			Field fld = target.getClass().getField("conceptMapping");
			fld.setAccessible(true);
			fld.set(target, conceptMapping);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
