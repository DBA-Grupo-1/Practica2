package practica.trace;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import practica.lib.ErrorLibrary;
import practica.util.GPSLocation;

/**
 * Camino que recorre un drone.
 * @author Daniel
 *
 */

public class Trace extends LinkedList<Choice>{
	
	public static final int DECISION_ONLY = 0;
	public static final int DECISION_ONLY_READABLE = 1;
	public static final int POSITION_ONLY = 2;
	public static final int DECISION_AND_POSITION = 3;
	
	/**
	 * Constructor vacío, crea una traza sin elementos.
	 * @author Daniel
	 */
	public Trace(){
		super();
	}
	
	/**
	 * 
	 * Constructor por copia, crea una traza a partir de una subtraza.
	 * @author Daniel
	 * @param tr traza a copiar.
	 * @param start posición inicial de la copia. 
	 * @param end posición final de la copia.
	 * @throws IllegalArgumentException si end < start.
	 */
	public Trace (Trace tr, int start, int end) throws IllegalArgumentException{
		super();
		if (end < start)
			throw new IllegalArgumentException(ErrorLibrary.TraceEndLowerThanStart);
		//Copio los elementos de la traza antigua.
		for (int i = start; i <= end; i++)
			add(tr.get(i));		
	}
	
	
	/**
	 * Extrae la subtraza desde una posición del GPS hasta el final de la misma.
	 * @author Daniel
	 * @param start posición del GPS inicial de la copia.
	 * @return subtraza desde start hasta el final de la misma.
	 * @throws IllegalArgumentException si no se encuentra start dentro de la traza.
	 */
	public Trace getSubtrace (GPSLocation start) throws IllegalArgumentException{
		Trace tr;
		//Busco la posición
		int startLocation = -1;
		for (int i = 0; i < size(); i++){
			if (get(i).getLocation() == start)
				startLocation = i;
		}
		
		//Si la encuentro copio, si no lanzo excepción.
		if (startLocation == -1)
			throw new IllegalArgumentException(ErrorLibrary.TraceLocationNotFound);
		else
			tr = new Trace (this, startLocation, size() - 1);
		return tr;
	}
	
	/**
	 * Extrae la subtraza entre dos posiciones del GPS.
	 * @author Daniel
	 * @param start posición del GPS inicial de la copia.
	 * @param end posición del GPS final de la copia.
	 * @return subtraza desde start hasta end.
	 * @throws IllegalArgumentException si no se encuentra alguna de las dos posiciones o no siguen un orden correcto.
	 */
	public Trace getSubtrace (GPSLocation start, GPSLocation end) throws IllegalArgumentException{
		Trace tr;
		//Busco las posiciones
		int startLocation = -2;
		int endLocation = -1;
		for (int i = 0; i < size(); i++){
			if (get(i).getLocation() == start)
				startLocation = i;
			else if (get(i).getLocation() == end)
				endLocation = i;
		}
		
		//Si las posiciones se han encontrado y son correctas copio, si no lanzo excepción.
		if (startLocation == -2 || endLocation == -1)
			throw new IllegalArgumentException(ErrorLibrary.TraceLocationNotFound);
		else if (startLocation >= endLocation)
			throw new IllegalArgumentException(ErrorLibrary.TraceEndLowerThanStart);
		else
			tr = new Trace (this, startLocation, endLocation);
		
		return tr;			
	}
	
	
	/**
	 * Devuelve la posición en el mapa que tenía un drone en un momento de la traza.
	 * @author Daniel
	 * @param i índice dentro de la traza de la posición.
	 * @return posición en el mapa que tenía el drone.
	 */	
	public GPSLocation getLocation (int i){
		return get(i).getLocation();
	}
	
	/**
	 * Devuelve el indice del mapa cuya posición (X, Y) sea igual a g:(X, Y).
	 * @author Jahiel
	 * @param g Posicion (X, Y)
	 * @return indice a esa posición
	 */
	public int getIndex(GPSLocation g){
		for(int i=0; i<size(); i++)
			if(get(i).getLocation().equals(g))
				return i;
		
		return -1;
	}
	
	/**
	 * @author Daniel
	 * @param mode modo en el que se imprimirá la traza.
	 * @return cadena de caracteres con la traza.
	 */
	public String toString (int mode){
		String resultado = "";
		switch (mode){
			case DECISION_ONLY:
				for (int i = 0; i < size(); i++){
					resultado += get(i).toString();
				}
				break;
		
			case DECISION_ONLY_READABLE:
				for (int i = 0; i < size(); i++){
					resultado += get(i).toString();
					if (i != size() - 1)
						resultado += ", ";
				}
				break;
				
			case POSITION_ONLY:
				for (int i = 0; i < size(); i++){
					resultado += ("{" + get(i).getLocation().getPositionX() + ", " + get(i).getLocation().getPositionY() + "}");
					if (i != size() - 1)
						resultado += ", ";
				}
				break;
				
			case DECISION_AND_POSITION: 
				for (int i = 0; i < size(); i++){
					resultado += ("{" + get(i).getLocation().getPositionX() + ", " + get(i).getLocation().getPositionY() + "}");
					resultado += (" - " + get(i).toString());
					if (i != size() - 1)
						resultado += ", ";
				}
				break;
			default: throw new IllegalArgumentException(ErrorLibrary.TraceNotAValidMode);
		}
		return resultado;
	}
	
	
}
