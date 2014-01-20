package practica.util;

import es.upv.dsic.gti_ia.core.AgentID;


/**
 * Estado actual de un drone
 * @author Daniel
 *
 */
public class DroneStatus {
	private int battery; 			//Batería del drone, entre 0 y 75.
	private AgentID id; 			//ID del drone.
	private String name; 			//Nombre del drone.
	private boolean goalReached; 	//true si ha llegado al objetivo, false si no.
	private GPSLocation location; 	//Posición en el mapa del drone.
	
	/**
	 * Constructor por defecto, pone la batería en 75.
	 * @author Daniel
	 * @param id Identificador del drone.
	 * @param name Nombre del drone.
	 * @param loc Posición en el mapa del drone.
	 */
	public DroneStatus(AgentID id, String name, GPSLocation loc) {
		this.id = id;
		this.name = name;
		location = loc;
		battery = 75;
	}

	/**
	 * Devuelve la batería del drone.
	 * @author Daniel
	 * @return Batería del drone.
	 */
	public int getBattery() {
		return battery;
	}

	/**
	 * Cambia la batería del drone.
	 * @author Daniel
	 * @param battery cantidad nueva de batería.
	 */
	public void setBattery(int battery) {
		this.battery = battery;
	}

	/**
	 * Devuelve el identificador del drone.
	 * @author Daniel
	 * @return ID del drone.
	 */
	public AgentID getId() {
		return id;
	}

	/**
	 * Cambia el identificador del drone.
	 * @author Daniel
	 * @param id Nuevo ID del drone.
	 */
	public void setId(AgentID id) {
		this.id = id;
	}

	/**
	 * Devuelve el nombre del drone.
	 * @author Daniel
	 * @return Nombre del drone.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Cambia el nombre del drone.
	 * @author Daniel
	 * @param name Nuevo nombre del drone.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Comprueba si el drone ha llegado al objetivo.
	 * @author Daniel
	 * @return true si está en el objetivo, false si no.
	 */
	public boolean isGoalReached() {
		return goalReached;
	}

	/**
	 * Cambia el estado del drone respecto a si ha llegado o no al objetivo.
	 * @author Daniel
	 * @param goalReached true si ha llegado al objetivo, false si no.
	 */
	public void setGoalReached(boolean goalReached) {
		this.goalReached = goalReached;
	}

	/**
	 * Devuelve la posición en el mapa del drone.
	 * @author Daniel
	 * @return Posición GPS del drone.
	 */
	public GPSLocation getLocation() {
		return location;
	}

	/**
	 * Cambia la posición en el mapa del drone.
	 * @author Daniel
	 * @param location Nueva posición del drone.
	 */
	public void setLocation(GPSLocation location) {
		this.location = location;
	}
	
	/**
	 * @author Daniel
	 */
	public String toString(){
		return ("Pos: " + String.valueOf(location.getPositionX()) + "," + String.valueOf(location.getPositionY() + " Battery: "
				+ String.valueOf(battery)));
	}

}
