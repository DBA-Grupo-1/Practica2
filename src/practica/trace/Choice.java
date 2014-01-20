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
	private int move;						//Decisión tomada (Norte, Sur, Este u Oeste).
	private GPSLocation location;			//Posición en la cual se tomó la decisión.
    private static final int NORTH = 3;
    private static final int WEST = 2;
    private static final int SOUTH = 1;
    private static final int EAST = 0;
	
	
	/**
	 * Constructor de Choice que recibe dos parámetros.
	 * @author jonay
	 * @param move	La decisión sobre el movimiento que se ha tomado
	 * @param loc	Las coordenadas a las que hace referencia esta decisión
	 */
	public Choice (int move, GPSLocation loc){
		this.move = move;
		this.location = loc;
	}
	
	/**
	 * Devuelve la decisión de movimiento.
	 * @author jonay
	 * @return La decisión sobre el movimiento tomado.
	 */
	public int getMove(){
		return move;
	}
	
	/**
	 * Asigna una decisión de movimiento a este Choice.
	 * @author jonay
	 * @param move La decisión de movimiento.
	 */
	public void setMove(int move){
		this.move = move;
	}
	
	/**
	 * Devuelve las coordenadas donde se tomó la decisión.
	 * @author jonay
	 * @return Las coordenadas
	 */
	public GPSLocation getLocation(){
		return location;
	}

	/**
	 * Asigna las coordenadas donde se tomó la decisión
	 * @author jonay
	 * @param location Las coordenadas
	 */
	public void setLocation(GPSLocation location){
		this.location = location;
	}
	
	/**
	 * @author Daniel
	 */	
	public String toString (){
		switch (move) {
		case NORTH:
			return "N";
		case SOUTH:
			return "S";
		case EAST:
			return "E";
		case WEST:
			return "O";
		default:
			throw new RuntimeException("Encontrada decisión no válida");
		}
	}
}
