package Utilities;

import es.upv.dsic.gti_ia.core.AgentID;
import Agents.DroneMetodoThinkDaniTrabajaAqui;


//Clase para que probemos las cosas. No es necesario ponerla en el diagrama.

public class TestWithMainDani {
	public static void main (String args []){
		Map map = ImgMapConverter.imgToMap("src/Maps/map1.png");
		
		//Supongamos que empieza con todo libre
		int [] s = new int [9];
		for (int i = 0; i < 9; i ++)
			s [i] = Map.LIBRE;
		
		//90 grados, todo libre, map1, empieza en la 1, 1
		DroneMetodoThinkDaniTrabajaAqui d = new DroneMetodoThinkDaniTrabajaAqui(90, s, map, 1, 1);
		printMovement(d.think());
		
		
	}
	
	public static void printMap (Map m){
		for (int i = 0; i < m.getHeigh(); i++){
			System.out.println("");
			for (int j = 0; j < m.getWidth(); j++)
				System.out.print(m.getValue(i, j));
		}
	}
	
	public static void crearRutaFicticia(Map map){
		int j =0;
		for(int i=0; i<300;i++){
			map.setvalue(i, j, Map.VISITADO);
			j++;
			map.setvalue(i, j, Map.VISITADO);
		}
	}
	
	public static void printMovement(int movement){
		String [] movements = {"Este", "Sur", "Oeste", "Norte"};
		System.out.println(movements[movement]);
	}
}