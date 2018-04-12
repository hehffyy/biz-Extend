package com.butone.gis;

import java.util.ArrayList;

public class Ring {
	private ArrayList<Point> points = new ArrayList<Point>();

	public ArrayList<Point> getPoints() {
		return points;
	}

	public void setPoints(ArrayList<Point> points) {
		this.points = points;
	}
	
	public void addPoint(Point p){
		points.add(p);
	}
}
