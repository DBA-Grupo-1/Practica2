package Agents;

import java.util.ArrayList;

import Utilities.Map;
import jason.functions.Random;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

public class DroneMetodoThinkDaniTrabajaAqui{
	
	private int posX;
	private int posY;
	private float angle;
	private int [] surroindings;
	private Map droneMap;
	
	private final int NORTE = 3;
	private final int OESTE = 2;
	private final int SUR = 1;
	private final int ESTE = 0;
	int a = 1;

	public DroneMetodoThinkDaniTrabajaAqui(float angulo, int [] alrededores, Map mapa, int posXinicial, int posYinicial){
		angle = angulo;
		surroindings = alrededores;
		droneMap = mapa;
		posX = posXinicial;
		posY = posYinicial;
	}
	
	/**
	 * Método donde el dron decide a qué dirección mover.
	 * @return dirección a la que se moverá.
	 */
	public int think(){
		java.util.Random r = new java.util.Random(); //Por algún motivo no se traga un import, así que lo he tenido que hacer a pelo.
		boolean direccion;
		float rango;
		int ran;
		ArrayList <Integer> movimientos = new ArrayList <Integer> (4);
		int movLibres [] = new int [4];
		
		direccion = (angle / 90) == 0;
		rango = angle % 90;
		ran = r.nextInt(2); //Generar un número entre 0 y 1.
		
		System.out.println(direccion + " " + rango);
		
		
		if (direccion){
			movimientos.add(0, (int) ((ran + rango) % 4));
			movimientos.add(1, (int) ((1 - ran + rango) % 4));
			movimientos.add(2, (int) ((ran + rango + 2) % 4));
			movimientos.add(3, (int) ((3 - ran + rango) % 4));
		}
		else{
			movimientos.add(0, (int) rango);
			movimientos.add(1, (int) ((rango + 2 - ran) % 4)); //En lugar de que lo aleatorio sea donde pongo cada cosa, hago
			movimientos.add(2, (int) ((rango + 1 + ran) % 4)); //que lo aleatorio sea que pongo en donde. Culpa del ArrayList y sus indices.
			movimientos.add(3, (int) ((rango + 2) % 4));
		}		
		
		movLibres = getValidMovements();
		
		for (int i = 0; i < 4; i ++)
			if (movLibres[movimientos.get(i)] != 0) //Si no es 0 la casilla o no está libre o se ha pasado por ella
				movimientos.remove(i);				
		
		if (movimientos.isEmpty())
			return -1; //El agente no se puede mover.
		else
			return movimientos.get(0);
	}
	
	/**
	 * Método para obtener un array con los movimientos libres del drone usando la memoria del mismo.
	 * @return Un array con lo que hay en las posiciones Este, Sur, Oeste y Norte a las que se podría mover, en ese orden.
	 */
	
	//POST DIAGRAMA DE CLASES
	private int [] getValidMovements(){
		int movimientosLibres [] = new int [4];
		
		movimientosLibres [NORTE] = surroindings [1] + droneMap.getValue(posX, posY + 1);
		movimientosLibres [OESTE] = surroindings [3] + droneMap.getValue(posX - 1, posY);
		movimientosLibres [SUR] = surroindings [7] + droneMap.getValue(posX, posY - 1);
		movimientosLibres [ESTE] = surroindings [5] + droneMap.getValue(posX + 1, posY);
		
		return movimientosLibres;		
	}
}
