package practica.map;

import java.util.ArrayList;

import practica.trace.BadChoice;
import es.upv.dsic.gti_ia.core.AgentID;

/**
 * Esta clase crea un mapa que podrá guardar las casillas por donde ha pasado cada uno de los drones,
 * así como marcar casillas con malas decisiones.
 * 
 * @author jonay
 * @author Daniel
 */
public class SharedMap extends Map{
	private SharedSquare sharedSquares[][];

	/**
	 * Constructor de SharedMap en el que se inicializan las casillas como vacías 
	 * @param heigh	Altura del mapa
	 * @param width Anchura del mapa
	 * 
	 * @author jonay
	 */
	public SharedMap(int heigh, int width) {
		super(heigh, width);
		initialize(heigh, width);
	}

	/**
	 * Constructor por copia de un Map, se inicializarán los obstáculos y la meta
	 * @param other	El mapa del que se obtendrán los datos
	 */
	public SharedMap(Map other) {
		super(other);
		initialize(other.getHeigh(),other.getWidth());
	}
	
	/**
	 * Inicializador de las casillas con SharedSquare vacíos
	 * @param heigh	La altura del mapa 
	 * @param width La anchura del mapa
	 * 
	 * @author jonay
	 */
	private void initialize(int heigh, int width){
		sharedSquares = new SharedSquare[heigh][width];
		
		// Inicializo todas las casillas con SharedSquare vacíos
		for(int x=0; x<width; x++){
			for(int y=0; y<heigh; y++){
				sharedSquares[y][x] = new SharedSquare();
			}
		}
	}
	
	/**
	 * Constructor por copia de otro SharedMap, se copiarán todos sus datos.
	 * @param other El SharedMap del que copiar los datos
	 * 
	 * @author jonay
	 */
	public SharedMap(SharedMap other) {
		super(other.getHeigh(), other.getWidth());
		int width, heigh;
		width = other.getWidth();
		heigh = other.getHeigh();
		sharedSquares = new SharedSquare[heigh][width];
		SharedSquare otherSS[][] = other.getSharedSquares();
		
		for(int x=0; x<width; x++){
			for(int y=0; y<heigh; y++){
				sharedSquares[y][x] = new SharedSquare(otherSS[x][y]);
			}
		}
	}
	
	/**
	 * Permite guardar información sobre una casilla, ya sea si hay un obstáculo
	 * o ha sido visitada, guardando el drone que visitó la casilla.
	 * @param x	Coordenada X de la casilla
	 * @param y	Coordenada Y de la casilla
	 * @param value	El valor a guardar (Constante estática de Map)
	 * @param id Identificador del drone que quiere asignar el valor
	 * @throws Exception Lanza una excepción si las coordenadas no se encuentran dentro
	 * 			de los límites del mapa
	 * 
	 * @author jonay
	 * @author Dani
	 */
	public void setValue(int x, int y, int value, AgentID id) throws Exception{
		super.setValue(x, y, value);
		if(value == VISITADO){
			sharedSquares[x][y].addVisitingAgent(id);
		}
	}
	
	/**
	 * @throws Exception Lanza una excepción si las coordenadas no se encuentran dentro
	 * 			de los límites del mapa o si se quiere indicar como VISITADO una casilla,
	 * 			pues se debe indicar el id del agente para las visitadas.
	 * 
	 * @author jonay
	 */
	@Override
	public void setValue(int x, int y, int value) throws Exception {
		if(value == VISITADO){
			throw new Exception("Excepción en SharedMap.setValue - Debe indicarse el ID del agente");
		}
		super.setValue(x, y, value);
	}
	
	/**
	 * Getter de un SharedSquare concreto.
	 * @author Daniel
	 * @param x coordenada X del SharedSquare
	 * @param y coordenada Y del SharedSquare
	 * @return SharedSquare en la posición [x][y]
	 */
	public SharedSquare getSharedSquare (int x, int y){
		return (sharedSquares[x][y]);
	}

	/**
	 * Devuelve los agentes que han visitado una coordenada específica
	 * @param x	Coordenada X
	 * @param y Coordenada Y
	 * @return Array con los idenficadores de los agentes que han visitado la casilla
	 * 
	 * @author jonay
	 */
	public ArrayList<AgentID> getVisitingAgents(int x, int y){
		return sharedSquares[y][x].getVisitingAgents();
	}
	
	/**
	 * Devuelve las indicaciones sobre malas decisiones tomadas en una casilla
	 * @param x Coordenada X
	 * @param y Coordenada Y
	 * @return Array con las malas decisiones de la casilla
	 * 
	 * @author jonay
	 */
	public ArrayList<BadChoice> getBadChoices(int x, int y){
		return sharedSquares[y][x].getBadChoices();
	}
	
	/**
	 * Devuelve el array con el contenido de las casillas de tipo SharedSquare
	 * @return Array de SharedSquare
	 * 
	 * @author jonay
	 */
	public SharedSquare[][] getSharedSquares() {
		return sharedSquares;
	}
	
	/**
	 * Añade un indicador de mala decisión a una casilla
	 * @param x Coordenada X
	 * @param y Coordenada Y
	 * @param bc La mala elección
	 * 
	 * @author jonay
	 */
	public void addBadChoice(int x, int y, BadChoice bc){
		sharedSquares[y][x].addBadChoice(bc);
	}
	
	/**
	 * Elimina las malas decisiones anotadas por un agente en una casilla
	 * @param x Coordenada X
	 * @param y Coordenada Y
	 * @param id El id del agente
	 * 
	 * @author jonay
	 */
	public void removeBadChoice(int x, int y, AgentID id){
		sharedSquares[y][x].removeBadChoice(id);
	}
	
	/**
	 * Devuelve true si existe alguna casilla con indicador de BadChoice, o false en caso contrario
	 * @return Si tiene o no BadChoice en alguna casilla
	 * 
	 * @author jonay
	 */
	public boolean hasBadChoices(){
		for(int x=0; x<this.getWidth(); x++){
			for(int y=0; y<this.getHeigh(); y++){
				if (sharedSquares[y][x].hasBadChoices()){
					return true;
				}
			}
		}
		return false;
	}
	
}
