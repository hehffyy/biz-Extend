package com.butone.gis;

import java.util.ArrayList;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;


public class Polygon {
	private ArrayList<Ring> rings = new ArrayList<Ring>();
	private Object spatialReference ="";

	public ArrayList<Ring> getRings() {
		return rings;
	}


	public Object getSpatialReference() {
		return spatialReference;
	}

	public void setSpatialReference(Object spatialReference) {
		this.spatialReference = spatialReference;
	}

	public void setRings(ArrayList<Ring> rings) {
		this.rings = rings;
	}
	
	public void addRing(Ring ring){
		this.rings.add(ring);
	}
	
	/** 创建空间参考*/
	private   JSONObject createSpatialReference() {
		JSONObject ret = new JSONObject();
		if (spatialReference instanceof Number)
			ret.put("wkid", spatialReference);
		else if (spatialReference instanceof String)
			// wkt 表示自定义的坐标系，非标准空间参考，一般中央经线非3整数倍。
			ret.put("wkt", spatialReference);
		else {
			throw new RuntimeException("不支持的wk参数类型:" + spatialReference.getClass().getName());
		}
		return ret;

	}
	
	private JSONArray createRings(){
		JSONArray ringsJson = new JSONArray();
		for(Ring ring:rings){
			JSONArray ringJson = new JSONArray();
			for(Point point:ring.getPoints()){
				JSONArray pointJson = new JSONArray();
				pointJson.add(point.getX());
				pointJson.add(point.getY());
				ringJson.add(pointJson);
			}
			ringsJson.add(ringJson);
		}
		return ringsJson;
	}
	
	public JSONObject toJson(){
		JSONObject pol = new JSONObject();
		pol.put("spatialReference",this.createSpatialReference());
		pol.put("rings", this.createRings());
		return pol;
	}
	public String toJsonStr(){
		JSONObject pol = toJson();
		return  pol.toString();
	}
	
	public int getPointCount(){
		int i = 0;
		for(Ring ring:this.getRings()){
			i = i + ring.getPoints().size();
		}
		return i;
	}
}
