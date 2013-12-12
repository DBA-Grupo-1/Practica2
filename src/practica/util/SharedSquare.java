package practica.util;

import java.util.ArrayList;

import es.upv.dsic.gti_ia.core.AgentID;

public class SharedSquare {
	private ArrayList<AgentID> visitingAgents;
	private ArrayList<BadChoice> badChoices;
	
	public SharedSquare(){
		visitingAgents = new ArrayList<AgentID>();
		badChoices = new ArrayList<BadChoice>();
	}
	
	public ArrayList<AgentID> getVisitingAgents(){
		return visitingAgents;
	}
	
	public void addVisitingAgent(AgentID id){
		visitingAgents.add(id);
	}
	
	public ArrayList<BadChoice> getBadChoices(){
		return badChoices;
	}
	
	public void addBadChoice(BadChoice bc){
		badChoices.add(bc);
	}
	
	public void removeBadChoice(AgentID id){
		for(BadChoice bc : badChoices){
			if(bc.getId().equals(id)){
				badChoices.remove(bc);
			}
		}
	}
	
	public boolean hasBadChoices(){
		return !badChoices.isEmpty();
	}
}
