package practica.util;

import java.util.ArrayList;

import es.upv.dsic.gti_ia.core.AgentID;

/**
 * Esta clase modela una casilla para un mapa compartido
 * 
 * @author jonay
 */
public class SharedSquare {
	private ArrayList<AgentID> visitingAgents;
	private ArrayList<BadChoice> badChoices;
	
	/**
	 * Constructor de SharedSquare que inicializa las variables.
	 * 
	 * @author jonay
	 */
	public SharedSquare(){
		visitingAgents = new ArrayList<AgentID>();
		badChoices = new ArrayList<BadChoice>();
	}
	
	/**
	 * Constructor por copia de SharedSquare
	 * @param ss El objeto SharedSquare del que queremos obtener los datos
	 * 
	 * @author jonay
	 */
	public SharedSquare(SharedSquare ss){
		this();
		ArrayList<AgentID> auxAid = ss.getVisitingAgents();
		ArrayList<BadChoice> auxBC = ss.getBadChoices();
		for(AgentID aid : auxAid){
			this.visitingAgents.add(aid);
		}
		for(BadChoice bc : auxBC){
			this.badChoices.add(bc);
		}
	}
	
	/**
	 * Devuelve los agentes que han visitado la casilla
	 * @return Un array con el identificador de los agentes
	 * 
	 * @author jonay
	 */
	public ArrayList<AgentID> getVisitingAgents(){
		return visitingAgents;
	}
	
	/**
	 * Añade un agente como visitante de esta casilla
	 * @param id El identificador del agente
	 * 
	 * @author jonay
	 */
	public void addVisitingAgent(AgentID id){
		visitingAgents.add(id);
	}
	
	/**
	 * Devuelve un array con las malas decisiones anotadas en esta casilla
	 * @return Array con los BadChoice
	 * 
	 * @author jonay
	 */
	public ArrayList<BadChoice> getBadChoices(){
		return badChoices;
	}
	
	/**
	 * Añade una mala decisión a esta casilla
	 * @param bc La mala decisión
	 * 
	 * @author jonay
	 */
	public void addBadChoice(BadChoice bc){
		badChoices.add(bc);
	}
	
	/**
	 * Elimina las malas decisiones que pudiera haber anotado el agente indicado en esta casilla
	 * @param id El identificador del agente
	 * 
	 * @author jonay
	 */
	public void removeBadChoice(AgentID id){
		for(BadChoice bc : badChoices){
			if(bc.getId().equals(id)){
				badChoices.remove(bc);
			}
		}
	}
	
	/**
	 * Devuelve si hay alguna mala decisión anotada en la casilla
	 * @return true si hay alguna mala decisión, false si no
	 * 
	 * @author jonay
	 */
	public boolean hasBadChoices(){
		return !badChoices.isEmpty();
	}
}
