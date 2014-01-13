package practica.util;

/**
 * Clase que modela las casillas conflictivas, es decir, las que pudieran tener varias resoluciones,
 * y su contenido.
 * @author Jonay
 */
public class ConflictiveBox {
	private boolean dangerous;
	private GPSLocation posInicial;
	private GPSLocation posFinal;
	private int length;
	private int decision;
	
	/**
	 * Constructor que inicializa las variables
	 * @author Jonay
	 */
	public ConflictiveBox(){
		dangerous = false;
		posInicial = null;
		posFinal = null;
		length = 0;
		decision = -1;
	}
	
	/**
	 * Devuelve si la elección de esta casilla es peligrosa (te puedes quedar rezagado)
	 * @author Jonay
	 * @return si es peligrosa
	 */
	public boolean isDangerous() {
		return dangerous;
	}
	
	/**
	 * Setter para indicar si es o no peligrosa
	 * @author Jonay
	 * @param dangerous
	 */
	public void setDangerous(boolean dangerous) {
		this.dangerous = dangerous;
	}
	
	/**
	 * Devuelve la posición inicial
	 * @author Jonay
	 * @return la posición inicial
	 */
	public GPSLocation getPosInicial() {
		return posInicial;
	}
	
	/**
	 * Setter de la posición inicial
	 * @author Jonay
	 * @param posInicial la posición inicial
	 */
	public void setPosInicial(GPSLocation posInicial) {
		this.posInicial = posInicial;
	}
	
	/**
	 * Devuelve la posición final
	 * @author Jonay
	 * @return la posición final
	 */
	public GPSLocation getPosFinal() {
		return posFinal;
	}
	
	/**
	 * Setter de la posición final
	 * @author Jonay
	 * @param posFinal La posición final
	 */
	public void setPosFinal(GPSLocation posFinal) {
		this.posFinal = posFinal;
	}
	
	/**
	 * Devuelve la longitud de la zona conflictiva
	 * @author Jonay
	 * @return la longitud
	 */
	public int getLength() {
		return length;
	}
	
	/**
	 * Setter de la longitud
	 * @author Jonay
	 * @param length la longitud
	 */
	public void setLength(int length) {
		this.length = length;
	}
	
	/**
	 * Devuelve la decisión de esta zona conflictiva
	 * @author Jonay
	 * @return La decisión
	 */
	public int getDecision() {
		return decision;
	}
	
	/**
	 * Setter de la decisión
	 * @author Jonay
	 * @param decision la decisión
	 */
	public void setDecision(int decision) {
		this.decision = decision;
	}

}
