
package practica.util;

public class Pair{
	private float first;
	private int second;
	private boolean t;

	public Pair(float a,int b, boolean c){
		this.first = a;
		this.second = b;
		this.t=c;

	}
	public void setFirst(int i){
		first =i;
	}
	public void setSecond(int i){
		second =i;
	}
	public void setThird(boolean i){
		t =i;
	}

	public float getFirst(){
		return first;
	}
	public int getSecond(){
		return second;
	}
	public boolean getThird(){
		return t;
	}
}