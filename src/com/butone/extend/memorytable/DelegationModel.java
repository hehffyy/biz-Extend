package com.butone.extend.memorytable;

import java.util.ArrayList;
import java.util.List;
/**
 * 
 * @author HQ
 */

public class DelegationModel {
	private String description;
	private String classname;
	private String label;
	private List<PropertyModel> propetrymodels = new ArrayList<PropertyModel>();
	
	public String getClassname() {
		return classname;
	}
	public void setClassname(String classname) {
		this.classname = classname;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public List<PropertyModel> getPropetrymodels() {
		return propetrymodels;
	}
	public void addPropetrymodels(PropertyModel propetrymodel) {
		propetrymodels.add(propetrymodel);
	}
}
