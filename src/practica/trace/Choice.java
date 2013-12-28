package practica.trace;

import practica.util.GPSLocation;

/**
 * Clase que permite almacenar la decisión tomada en un punto específicado mediante
 * unas coordenadas.
 * 
 * @author jonay
 * @author Daniel
 */
public class Choice {
	private int move;
	private GPSLocation location;
    private static final int NORTE = 3;
    private static final int OESTE = 2;
    private static final int SUR = 1;
    private static final int ESTE = 0;
	
	
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
	
	/**
	 * Convierte la decisión en un String
	 * @author Daniel
	 */
	public String toString (){
		switch (move) {
		case NORTE:
			return "N";
		case SUR:
			return "S";
		case ESTE:
			return "E";
		case OESTE:
			return "O";
		default:
			throw new RuntimeException("Encontrade decisión no válida");
		}
	}
}
