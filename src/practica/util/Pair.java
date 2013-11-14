package practica.util;

public class Pair {
	private float first;
	private int second;
	private int t;

	public Pair(float a,int b,int c){
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
	public void sett(int i){
		t =i;
	}

	public float getFirst(){
		return first;
	}
	public int getSecond(){
		return second;
	}
	public int getT(){
		return t;
	}


}
