package com.butone.extend;

import java.io.Serializable;

/**
 * 流程工作表字段扩展信息
 * @author Administrator
 *
 */
public class FieldInfo implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 7469594343310098381L;
	private String name;
	private String label;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

}
