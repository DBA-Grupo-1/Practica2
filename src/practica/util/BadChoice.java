package practica.util;

import es.upv.dsic.gti_ia.core.AgentID;

public class BadChoice extends Choice{
	private float distanceLost;
	private String message;
	private AgentID id;
	
	public BadChoice(int move, GPSLocation loc, AgentID id, float dl, String msg) {
		super(move, loc);
		this.id = id;
		this.distanceLost = dl;
		this.message = msg;
	}

	public float getDistanceLost(){
		return distanceLost;
	}
	
	public void setDistanceLost(float distanceLost){
		this.distanceLost = distanceLost;
	}
	
	public String getMessage(){
		return message;
	}
	
	public void setMessage(String message){
		this.message = message;
	}
	
	public AgentID getId(){
		return id;
	}
	
	public void setId(AgentID id){
		this.id = id;
	}
}
