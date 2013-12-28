package practica.util;

import es.upv.dsic.gti_ia.core.AgentID;

public class DroneStatus {

	private int battery;
	private AgentID id;
	private String name;
	private boolean goalReached;
	private GPSLocation location;
	
	public DroneStatus(AgentID id, String name, GPSLocation loc) {
		this.id = id;
		this.name = name;
		location = loc;
		battery = 75;
	}

	public int getBattery() {
		return battery;
	}

	public void setBattery(int battery) {
		this.battery = battery;
	}

	public AgentID getId() {
		return id;
	}

	public void setId(AgentID id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isGoalReached() {
		return goalReached;
	}

	public void setGoalReached(boolean goalReached) {
		this.goalReached = goalReached;
	}

	public GPSLocation getLocation() {
		return location;
	}

	public void setLocation(GPSLocation location) {
		this.location = location;
	}

}
