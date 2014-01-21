package practica.util;

/**
 * Posición GPS en el mapa.
 * @author Daniel
 * @author Alberto
 */

public class GPSLocation {
	private int positionX;	//Coordenada en el eje X.
	private int positionY;	//Coordenada en el eje Y.
	
	/**
	 * Constructor por defecto. Las posicion X e Y por defecto es (0, 0).
	 * @author Daniel
	 */
	public GPSLocation(){
		
		positionX = positionY = 0;
	}
	
	/**
	 * Constructor a partir de dos enteros.
	 * @author Daniel
	 * @param x Coordenada en el eje X
	 * @param y Coordenada en el eje Y
	 */
	public GPSLocation(int x, int y) {
		positionX = x;
		positionY = y;
	}

	/**
	 * Setter de la posición X
	 * @author Daniel
	 * @param x Coordenada X en el mapa
	 */
	public void setPositionX(int x){
		
		positionX = x;
	}
	
	/**
	 * Getter de la posición X
	 * @author Daniel
	 * @return Valor de la posición X
	 */
	public int getPositionX(){
		
		return positionX; 
	}
	
	/**
	 * Setter de la posición Y
	 * @author Daniel
	 * @param x Coordenada Y en el mapa
	 */
	public void setPositionY(int y){
	
		positionY = y;
	}
	
	/**
	 * Getter de la posición Y
	 * @author Daniel
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
	 * @author Alberto
	 * @param other El otro objeto GPSLocation con el que comparar
	 * @return True si son iguales, false en caso contrario
	 */
	@Override
	public boolean equals(Object other){
		GPSLocation otherLoc = (GPSLocation) other;
		return (otherLoc.positionX == this.positionX) && (otherLoc.positionY == this.positionY);
	}
	
}
