package practica.util;


public class GPSLocation {
	private int positionX, positionY;
	
	/**
	 * Constructor por defecto. Las posicion X e Y por defecto es (0, 0).
	 */
	public GPSLocation(){
		
		positionX = positionY = 0;
	}
	
	public GPSLocation(int x, int y) {
		positionX = x;
		positionY = y;
	}

	/**
	 * Setter de la posición X
	 * @param x Coordenada X en el mapa
	 */
	public void setPositionX(int x){
		
		positionX = x;
	}
	
	/**
	 * Getter de la posición X
	 * @return Valor de la posición X
	 */
	public int getPositionX(){
		
		return positionX; 
	}
	
	/**
	 * Setter de la posición Y
	 * @param x Coordenada Y en el mapa
	 */
	public void setPositionY(int y){
	
		positionY = y;
	}
	
	/**
	 * Getter de la posición Y
	 * @return Valor de la posición Y
	 */
	public int getPositionY(){
	
		return positionY;
	}
	
}
