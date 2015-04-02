package edu.gwu.spatial;

import gov.nasa.worldwind.geom.LatLon;


public class GeoResult 
{
	public final LatLon pos;
	public final String name;
	public GeoResult(LatLon pos, String name)
	{
		this.pos = pos;
		this.name = name;
	}
	
	@Override
	public String toString()
	{
		return name;
	}
}
