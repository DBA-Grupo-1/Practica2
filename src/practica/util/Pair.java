
package practica.util;

/**
 * 
 * @author Ismael
 * Clase Par para tratar los datos como un todo (mas tarde modificado a una tupla de 3 elementos) para contol de distancia al objetivo
 * si esta en meta o no, y second (que por desgracia ya no me acuerdo)
 */
public class Pair{
	private float first;
	private int second;
	private boolean t;

	/**
	 * @author Ismael
	 * Constructor
	 * @param a
	 * @param b
	 * @param c
	 */
	public Pair(float a,int b, boolean c){
		this.first = a;
		this.second = b;
		this.t=c;

	}
	/**
	 * @author Ismael
	 * Introduce un valor en el primer elemento 
	 * @param i
	 */
	public void setFirst(int i){
		first =i;
	}
	
	/**
	 * @author Ismael 
	 * Introduce un valor en el segundo elemento
	 * @param i
	 */
	public void setSecond(int i){
		second =i;
	}
	
	/**
	 * @author
	 * Introduce un valor en el tercer elemento
	 * @param i
	 */
	public void setThird(boolean i){
		t =i;
	}
	/**
	 * @author Ismael
	 * devuelve el valor del primer elemento
	 * @return first
	 */
	public float getFirst(){
		return first;
	}
	
	/**
	 * @author Ismael
	 * Devuelve el valor del segundo elemento
	 * @return second
	 */
	public int getSecond(){
		return second;
	}
	
	/**
	 * @author Ismael
	 * Devuelve el valor del tercer elemento
	 * @return t
	 */
	public boolean getThird(){
		return t;
	}
}