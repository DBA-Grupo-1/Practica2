package practica.util;

/**
 * Clase que permite almacenar la decisión tomada en un punto específicado mediante
 * unas coordenadas.
 * 
 * @author jonay
 */
public class Choice {
	private int move;
	private GPSLocation location;
	
	/**
	 * Constructor de Choice que recibe dos parámetros
	 * @param move	La decisión sobre el movimiento que se ha tomado
	 * @param loc	Las coordenadas a las que hace referencia esta decisión
	 * 
	 * @author jonay
	 */
	public Choice (int move, GPSLocation loc){
		this.move = move;
		this.location = loc;
	}
	
	/**
	 * Devuelve la decisión de movimiento
	 * @return La decisión sobre el movimiento tomado.
	 * 
	 * @author jonay
	 */
	public int getMove(){
		return move;
	}
	
	/**
	 * Asigna una decisión de movimiento a este Choice
	 * @param move La decisión de movimiento
	 * 
	 * @author jonay
	 */
	public void setMove(int move){
		this.move = move;
	}
	
	/**
	 * Devuelve las coordenadas donde se tomó la decisión 
	 * @return Las coordenadas
	 * 
	 * @author jonay
	 */
	public GPSLocation getLocation(){
		return location;
	}

	/**
	 * Asigna las coordenadas donde se tomó la decisión
	 * @param location Las coordenadas
	 * 
	 * @author jonay
	 */
	public void setLocation(GPSLocation location){
		this.location = location;
	}
}
