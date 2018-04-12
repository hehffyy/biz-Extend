package com.butone.workdate;

import java.io.Serializable;
import java.util.Date;

//import java.sql.Date;

public class WorkDate implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private Date date;
	private int year;
	private int month;
	private int day;
	private int weekIndex;
	private String desc;
	private String changeKind;
	
	public WorkDate(Date date) {
		//super();
		this.date = date;
	}

	public WorkDate(int year, int month, int day) {
		//super();
		this.year = year;
		this.month = month;
		this.day = day;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getChangeKind() {
		return changeKind;
	}

	public void setChangeKind(String changeKind) {
		this.changeKind = changeKind;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public int getMonth() {
		return month;
	}

	public void setMonth(int month) {
		this.month = month;
	}

	public int getDay() {
		return day;
	}

	public void setDay(int day) {
		this.day = day;
	}

	public int getWeekIndex() {
		return weekIndex;
	}

	public void setWeekIndex(int weekIndex) {
		this.weekIndex = weekIndex;
	}

	public String getDesc() {
		return desc;
	}


	public void setDesc(String desc) {
		this.desc = desc;
	}

	
	@Override
	public int hashCode() {
		return 360 * year + 30 * month + day;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof WorkDate)
			return false;
		return obj != null && obj.hashCode() == this.hashCode();
	}

	@Override
	public String toString() {
		return "WorkDate [date=" + date + "]";
	}

}
