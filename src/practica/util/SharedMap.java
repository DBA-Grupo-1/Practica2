package practica.util;

import java.util.ArrayList;

import es.upv.dsic.gti_ia.core.AgentID;

public class SharedMap extends Map{
	private SharedSquare sharedSquares[][];

	public SharedMap(int heigh, int width) {
		super(heigh, width);
		sharedSquares = new SharedSquare[heigh][width];
		
		// Inicializo todas las casillas con SharedSquare vacíos
		for(int x=0; x<width; x++){
			for(int y=0; y<heigh; y++){
				sharedSquares[y][x] = new SharedSquare();
			}
		}
	}

	public SharedMap(Map other) {
		this(other.getHeigh(), other.getWidth());
	}
	
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
	
	public void setValue(int x, int y, int value, AgentID id) throws Exception{
		//FIXME
		//Esto esta ya en la clase Map.
		if(x < 0 || x >= this.getWidth() || y < 0 || y >= this.getHeigh()){
			throw new Exception("La posición ("+x+", "+y+") no se encuentra dentro de los límites del mapa");
		}
		
		//FIXME
		//Deberias llamar a la funcion de la clase superior para poner el valor.
		
		//FIXME
		//Esto solo si value == VISITADO
		sharedSquares[y][x].addVisitingAgent(id);
	}
	
	@Override
	public void setValue(int x, int y, int value) throws Exception {
		//FIXME
		//ESto solo si value == VISITADO
		throw new Exception("Excepción en SharedMap.setValue - Debe indicarse el ID del agente");
		
		//FIXME Si value!= VISITADO entonces llama a la funcion de la clase superior
	}

	
	public ArrayList<AgentID> getVisitingAgents(int x, int y){
		return sharedSquares[y][x].getVisitingAgents();
	}
	
	public ArrayList<BadChoice> getBadChoices(int x, int y){
		return sharedSquares[y][x].getBadChoices();
	}
	
	public SharedSquare[][] getSharedSquares() {
		return sharedSquares;
	}
	
	public void addBadChoice(int x, int y, BadChoice bc){
		sharedSquares[y][x].addBadChoice(bc);
	}
	
	public void removeBadChoice(int x, int y, AgentID id){
		sharedSquares[y][x].removeBadChoice(id);
	}
	
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
