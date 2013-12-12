package practica.util;

public class Choice {
	private int move;
	private GPSLocation location;
	
	public Choice (int move, GPSLocation loc){
		this.move = move;
		this.location = loc;
	}
	
	public int getMove(){
		return move;
	}
	
	public void setMove(int move){
		this.move = move;
	}
	
	public GPSLocation getLocation(){
		return location;
	}
	
	public void setLocation(GPSLocation location){
		this.location = location;
	}
}
