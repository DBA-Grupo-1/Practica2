package practica.map;

import java.util.ArrayList;

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
	private ArrayList<ConflictiveBox> conflictiveBoxes;
	
	/**
	 * Constructor de SharedSquare que inicializa las variables.
	 * 
	 * @author jonay
	 */
	public SharedSquare(){
		visitingAgents = new ArrayList<AgentID>();
		conflictiveBoxes = new ArrayList<ConflictiveBox>();
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
		ArrayList<ConflictiveBox> auxCB = ss.getConflictiveBoxes();
		for(AgentID aid : auxAid){
			this.visitingAgents.add(aid);
		}
		for(ConflictiveBox bc : auxCB){
			this.conflictiveBoxes.add(bc);
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
	 * @author Jonay
	 * @author Daniel
	 * @return lista de casillas conflictivas.
	 */
	public ArrayList<ConflictiveBox> getConflictiveBoxes (){
		return conflictiveBoxes;
	}
	
	/**
	 * Comprueba si la casilla es conflictiva.
	 * @author Jonay
	 * @author Daniel
	 * @return true si es conflictiva, false si no.
	 */
	public boolean isConflictive(){
		return !conflictiveBoxes.isEmpty();
	}
	
}
