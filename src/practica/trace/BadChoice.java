package practica.trace;

import practica.util.GPSLocation;
import es.upv.dsic.gti_ia.core.AgentID;

/**
 * Esta clase se usa para poder dejar constancia de una mala decisión,
 * indicando las coordenadas, la decisión tomada, etc.
 *
 * @author jonay
 */
public class BadChoice extends Choice{
	private float distanceLost;
	private String message;
	private AgentID id;
	
	/**
	 * Constructor de BadChoice
	 * @param move	movimiento que es una mala decisión
	 * @param loc	coordenadas de la casilla a la que se hace referencia
	 * @param id	identificador del Agente que ha indicado el BadChoice
	 * @param dl	distancia perdida por culpa de la mala decisión
	 * @param msg	un mensaje que se puede indicar sobre la mala decisión en esa casilla
	 * 
	 * @author jonay
	 */
	public BadChoice(int move, GPSLocation loc, AgentID id, float dl, String msg) {
		super(move, loc);
		this.id = id;
		this.distanceLost = dl;
		this.message = msg;
	}

	/**
	 * Devuelve la distancia perdida por motivo de la mala decisión
	 * @return La distancia perdida
	 * 
	 * @author jonay
	 */
	public float getDistanceLost(){
		return distanceLost;
	}
	
	/**
	 * Permite asignar cuanta distancia se ha perdido por motivo de la mala decisión
	 * @param distanceLost La distancia perdida
	 * 
	 * @author jonay
	 */
	public void setDistanceLost(float distanceLost){
		this.distanceLost = distanceLost;
	}
	
	/**
	 * Devuelve el mensaje guardado sobre esta mala decisión
	 * @return	El mensaje que se guarda sobre esta mala decisión
	 * 
	 * @author jonay
	 */
	public String getMessage(){
		return message;
	}
	
	/**
	 * Guarda el mensaje indicado sobre la mala decisión
	 * @param message El mensaje a guardar
	 * 
	 * @author jonay
	 */
	public void setMessage(String message){
		this.message = message;
	}
	
	/**
	 * Devuelve el identificador del agente que ha puesto esta mala decisión
	 * @return El identificador del agente
	 * 
	 * @author jonay
	 */
	public AgentID getId(){
		return id;
	}
	
	/**
	 * Permite asignar un identificador de agente para guardarlo como creador del
	 * aviso de esta mala decisión
	 * @param id El identificador del agente
	 * 
	 * @author jonay
	 */
	public void setId(AgentID id){
		this.id = id;
	}
}
