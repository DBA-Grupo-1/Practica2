package practica.map;

import java.util.ArrayList;

import practica.trace.BadChoice;
import practica.util.ConflictiveBox;
import es.upv.dsic.gti_ia.core.AgentID;

/**
 * Esta clase modela una casilla para un mapa compartido
 * 
 * @author jonay
 * @author Daniel
 */
public class SharedSquare {
	private ArrayList<AgentID> visitingAgents;
	private ArrayList<BadChoice> badChoices;
	private ArrayList<ConflictiveBox> conflictiveBoxes;
	
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
	
	
	/**
	 * Devuelve la ID del último agente que visitó la casilla.
	 * @author Daniel
	 * @return ID del agente
	 */
	public AgentID getLastVisited (){
		if (visitingAgents.size() == 0)
			return null;
		else return (visitingAgents.get(visitingAgents.size() - 1));
	}

	/**
	 * Añade una casilla conflictiva
	 * @author Jonay
	 * @param cb la casilla conflictiva
	 */
	public void addConflictiveBox(ConflictiveBox cb) {
		conflictiveBoxes.add(cb);
	}
	
	/**
	 * Devuelve la lista de secciones conflictivas que empiezan en esta casilla.
	 * @author Daniel
	 * @return lista de casillas conflictivas.
	 */
	public ArrayList<ConflictiveBox> getConflictiveBoxes (){
		return conflictiveBoxes;
	}
	
	/**
	 * Comprueba si la casilla es conflictiva.
	 * @author Daniel
	 * @return true si es conflictiva, false si no.
	 */
	public boolean isConflictive(){
		return !conflictiveBoxes.isEmpty();
	}
	
}
