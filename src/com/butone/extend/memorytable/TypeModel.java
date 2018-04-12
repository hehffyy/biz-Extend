package com.butone.extend.memorytable;

import java.util.Calendar;

/**
 * 
 * @author HQ
 *
 */
public class TypeModel {
	private String nodeName;
	private String ID;
	private DelegationModel delegation;

	public TypeModel(){
		
	}
	
	public TypeModel(boolean iscreatID){
		if(iscreatID){
			this.ID = String.valueOf(Calendar.getInstance().getTimeInMillis());
		}
	}

	public String getNodeName() {
		return nodeName;
	}
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public DelegationModel getDelegation() {
		return delegation;
	}
	public void setDelegation(DelegationModel delegation) {
		this.delegation = delegation;
	}
	
	public void setID(String id){
		this.ID = id;
	}
	public String getID() {
		return ID;
	}

}
