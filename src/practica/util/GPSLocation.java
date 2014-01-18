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
	
	/**
	 * @author Daniel
	 */
	public String toString(){
		return positionX + ", " + positionY;
	}
	
	/**
	 * Compara este GPSLocation con otro para ver si son iguales.
	 * 
	 * @param other El otro objeto GPSLocation con el que comparar
	 * @return True si son iguales, false en caso contrario
	 * @author Alberto
	 */
	@Override
	public boolean equals(Object other){
		GPSLocation otherLoc = (GPSLocation) other;
		return (otherLoc.positionX == this.positionX) && (otherLoc.positionY == this.positionY);
	}
	
}
