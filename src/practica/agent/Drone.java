package practica.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import practica.util.Map;
import practica.util.Pair;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;
import es.upv.dsic.gti_ia.core.ACLMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Drone extends SingleAgent {
	private final int ESTADOREQUEST = 0, ESTADOINFORM = 1;
	// NOTA_INTEGRACION (Ismael) exit no tiene sentido fuera del execute
	private boolean exit;
	private boolean goal;
	private int estado;
	private int posX;
	private int posY;
	private float angle;
	private float distancia;
	private int[] surroundings;
	private Map droneMap;
	public static final int NORTE = 3;
	public static final int OESTE = 2;
	public static final int SUR = 1;
	public static final int ESTE = 0;
	public static final int END = -1;

	private AgentID sateliteID;
	
	private boolean dodging = false;
	private int betterMoveBeforeDodging = -1, secondMoveBeforeDodging = -1;

	public Drone(AgentID aid, int mapWidth, int mapHeight, AgentID sateliteID) throws Exception {
		super(aid);
		surroundings = new int[9];
		droneMap = new Map(mapWidth, mapHeight);
		this.sateliteID = sateliteID;
		posX = 0;
		posY = 0;
	}
	
	/**
	 * Método donde el dron decide a qué dirección mover.
	 * @return dirección a la que se moverá.
	 */
	public int think(){
		if(goal)
			return END;
		
		ArrayList<Pair> mispares = new ArrayList<Pair>(), ordenados;
		int[] validSqr = getValidSquares();
		boolean condition;

		double posiOX=0,posiOY=0;
		float calculoDist=0;

		posiOX= (posX + (Math.cos(angle) * distancia));
		posiOY= (posY + (Math.sin(angle)*distancia));

		//Creamos el array con todos los movimientos, incluyendo la distancia al objetivo, el movimiento en si, y si es valido o no
		calculoDist= (float) Math.sqrt(Math.pow((posiOX-(posX+1)),2)+Math.pow((posiOY-posY), 2));
		condition = validSqr[5]==Map.LIBRE
				&& (!(validSqr[2]==Map.VISITADO || validSqr[8]==Map.VISITADO)
					|| (validSqr[1]!=Map.LIBRE && validSqr[3]!=Map.LIBRE && validSqr[7]!=Map.LIBRE));
		mispares.add(new Pair(calculoDist,ESTE,condition));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY+1)), 2));
		condition = validSqr[7]==Map.LIBRE
				&& (!(validSqr[6]==Map.VISITADO || validSqr[8]==Map.VISITADO)
					|| (validSqr[1]!=Map.LIBRE && validSqr[3]!=Map.LIBRE && validSqr[5]!=Map.LIBRE));
		mispares.add(new Pair(calculoDist,SUR,condition));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-(posX-1)),2)+Math.pow((posiOY-posY), 2));
		condition = validSqr[3]==Map.LIBRE
				&& (!(validSqr[0]==Map.VISITADO || validSqr[6]==Map.VISITADO)
					|| (validSqr[1]!=Map.LIBRE && validSqr[5]!=Map.LIBRE && validSqr[7]!=Map.LIBRE));
		mispares.add(new Pair(calculoDist,OESTE,condition));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY-1)), 2));
		condition = validSqr[1]==Map.LIBRE
				&& (!(validSqr[0]==Map.VISITADO || validSqr[2]==Map.VISITADO)
					|| (validSqr[5]!=Map.LIBRE && validSqr[3]!=Map.LIBRE && validSqr[7]!=Map.LIBRE));
		mispares.add(new Pair(calculoDist,NORTE,condition));
		
		//ordenamos el array segun la distancia (de menor a mayor)
		ordenados=new ArrayList<Pair>(mispares);
		Collections.sort(ordenados, new Comparator<Pair>(){
			public int compare(Pair p1, Pair p2){
				if(p1.getFirst()<p2.getFirst()){
					return -1;
				}else{
					if(p1.getFirst()>p2.getFirst()){
						return 1;
					}else{
						return 0;
					}
				}
			}
		});
		
		System.out.println("Dodging: " + dodging);
		for(int i=0; i<4; i++){
			System.out.println(ordenados.get(i).getFirst() + "," + ordenados.get(i).getSecond() + "," + ordenados.get(i).getThird());
		}
		
		if(dodging && mispares.get(betterMoveBeforeDodging).getThird()){
			dodging=false;
			System.out.println("Saliendo dodging: " + betterMoveBeforeDodging);
			return betterMoveBeforeDodging;
		}
		
		//Si el mejor movimiento es posible tenemos un ganador
		if(ordenados.get(0).getThird()){
			//S estamos esquivando y no es el que intentabamos hacer cuando entramos en modo "Esquivar" hacemos el segundo
			if(dodging && ordenados.get(0).getSecond()!=betterMoveBeforeDodging && mispares.get(secondMoveBeforeDodging).getThird()){
				System.out.println("SI O SI");
				return secondMoveBeforeDodging;
			}
			return ordenados.get(0).getSecond();
		}else{
			//Si estamos esquivando y podemos hacer el segundo mejor movimiento de esquivar lo hacemos SI O SI
			if(dodging && mispares.get(secondMoveBeforeDodging).getThird()){
				System.out.println("SI O SI");
				return secondMoveBeforeDodging;
			}
		}
		
		//Si no es asi y no hemos avanzado debido a un obstaculo entramos en el modo "Esquivar"
		int[] validMov = getValidMovements();
		if(validMov[ordenados.get(0).getSecond()]==Map.OBSTACULO && !dodging){
			dodging=true;
			betterMoveBeforeDodging=ordenados.get(0).getSecond();
			secondMoveBeforeDodging=ordenados.get(1).getSecond();
			System.out.println("SecondMOve Dodging: "+ secondMoveBeforeDodging);
			System.out.println("Entrando dodging: " + betterMoveBeforeDodging);
		}
		
		//Obtenemos los otros movimientos posibles. Pueden ser ninguno, uno o dos
		int second=-1, third=-1;
		float distSecond=0, distThird=0;
		
		//Obtenemos el segundo
		for(int i=1; i<4; i++){
			if(ordenados.get(i).getThird()){
				second=ordenados.get(i).getSecond();
				distSecond=ordenados.get(i).getFirst();
				break; //No le enseñeis esta linea a Cubero :P
			}
		}

		System.out.println("Movs: " + second);
		//Si no existe es que no quedan movimientos posibles
		if(second==-1)
			return END;
		
		//Obtenemos el tercero
		for(int i=1; i<4; i++){
			if(ordenados.get(i).getThird() && ordenados.get(i).getSecond()!=second){
				third=ordenados.get(i).getSecond();
				distThird=ordenados.get(i).getFirst();
				break;
			}
		}
		System.out.println("Movs: " + third);
		//Si no existe es que solo podemos realizar el segundo
		if(third==-1){
			//if(enteringDodging)
				//secondMoveBeforeDodging=second;
			return second;
		}
		
		
		//Ahora comprobamos si existe empate entre ambos (distancias parecidas) pero solo si estamos en modo "Esquivar".
		//El valor de margen de error debe ser ajustado "a mano" en caso de usar distancias.
		//En caso de usar el angulo se puede poneer un valor mejor pero los calculos son mas coñazo
		float error=1.0f;
		int better = ordenados.get(0).getSecond(), decision;
		if(Math.abs(distSecond-distThird)<error){
			//En caso de empate y estar en modo esquivar nos quedaremos con el que se "parezca" mas al movimiento que intentabamos hacer al entrar en "Esquivar"
			
			//Si el segundo es el movimiento opuesto al betterMove... nos quedamos con el tercero 
			if((betterMoveBeforeDodging+2)%4==second){
				System.out.println("Empate dodging (second caca): " + betterMoveBeforeDodging);
				decision = third;
			}
			//Viceversa 
			if((betterMoveBeforeDodging+2)%4==third){
				System.out.println("Empate dodging (third caca): " + betterMoveBeforeDodging);
				decision = second;
			}
			
			//Si no el empate se decide por los obstaculos
			int cornerSecond = getCorner(better, second), cornerThird = getCorner(better, third);
			
			//Si la esquina del tercero esta libre pero la del segundo no, nos quedamos con esa
			if(cornerThird==Map.LIBRE && cornerSecond==Map.OBSTACULO){
				decision = third;
			}else{
				//En cualquier otro caso nos quedamos con el segundo mejor movimiento
				decision = second;
			}
				
		}else{
			//Si no hay empate nos quedamos con el segundo
			decision = second;
		}
		
		return decision;
		/*int zonaAngle = 0;
		double angleDeg = Math.toDegrees(angle);
		
		System.out.println("Objetivo: " + angleDeg + ", " + distancia);
		
		if(angleDeg <= 45 || angleDeg > 315)
			zonaAngle=0;
		if(angleDeg > 45 && angleDeg <= 135)
			zonaAngle=1;
		if(angleDeg > 135 && angleDeg <= 225)
			zonaAngle=2;
		if(angleDeg > 225 && angleDeg <= 315)
			zonaAngle=3;
		
		int[] validSquares = getValidSquares();
		
		//TODO Fijate que ahora el tercer componente de Pair no se usa
		//if(droneMap.getValue(posX+1, posY)!=Map.VISITADO && surroundings[5]!=Map.OBSTACULO){
		if(validSquares[5]==Map.LIBRE
				&& (!(validSquares[2]==Map.VISITADO || validSquares[8]==Map.VISITADO)
					|| (validSquares[1]!=Map.LIBRE && validSquares[3]!=Map.LIBRE && validSquares[7]!=Map.LIBRE))){
			if(angleDeg < 180){
				calculoDist = (float) angleDeg; 
			}else{
				calculoDist = (float) (360 - angleDeg);
			}
			calculoDist= (float) Math.sqrt(Math.pow((posiOX-(posX+1)),2)+Math.pow((posiOY-posY), 2));
			mispares.add(new Pair(calculoDist,ESTE,surroundings[5]));
		}
		//if(droneMap.getValue(posX, posY+1)!=Map.VISITADO && surroundings[7]!=Map.OBSTACULO){
		if(validSquares[7]==Map.LIBRE
				&& (!(validSquares[6]==Map.VISITADO || validSquares[8]==Map.VISITADO)
					|| (validSquares[1]!=Map.LIBRE && validSquares[3]!=Map.LIBRE && validSquares[5]!=Map.LIBRE))){
			if(angleDeg > 270){
				calculoDist = (float) (360 - angleDeg + 90); 
			}else{
				calculoDist = (float) Math.abs(angleDeg - 90);
			}
			calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY+1)), 2));
			mispares.add(new Pair(calculoDist,SUR,surroundings[7]));
		}
		
		//if(droneMap.getValue(posX, posY-1)!=Map.VISITADO && surroundings[1]!=Map.OBSTACULO){
		if(validSquares[1]==Map.LIBRE
				&& (!(validSquares[0]==Map.VISITADO || validSquares[2]==Map.VISITADO)
					|| (validSquares[3]!=Map.LIBRE && validSquares[5]!=Map.LIBRE && validSquares[7]!=Map.LIBRE))){
			if(angleDeg < 90){
				calculoDist = (float) angleDeg + 90; 
			}else{
				calculoDist = (float) Math.abs(270 - angleDeg);
			}
			calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY-1)), 2));
			mispares.add(new Pair(calculoDist,NORTE,surroundings[1]));
		}
		
		//if(droneMap.getValue(posX-1, posY)!=Map.VISITADO && surroundings[3]!=Map.OBSTACULO){
		if(validSquares[3]==Map.LIBRE
				&& (!(validSquares[0]==Map.VISITADO || validSquares[6]==Map.VISITADO)
				|| (validSquares[1]!=Map.LIBRE && validSquares[5]!=Map.LIBRE && validSquares[7]!=Map.LIBRE))){
			calculoDist = (float) Math.abs(180 - angleDeg);
			calculoDist=(float) Math.sqrt(Math.pow((posiOX-(posX-1)),2)+Math.pow((posiOY-posY), 2));
			mispares.add(new Pair(calculoDist,OESTE,surroundings[3]));
		}
		

		//Aquí se toma una decisión.
		int dec=decision(mispares);
		return dec;*/
	}

	/**
	 * Calcula la esuina que rodean pos1 y pos2 y devuelve el valor del surrounding
	 * @param pos1 Primera posicion
	 * @param pos2 Segunda posicion
	 * @return Valor del surrounding para esa esquina
	 */
	private int getCorner(int pos1, int pos2) {
		/*
		 * La suma de dos posiciones a los lados de una esquina de la matriz es unica y sigue la siguiente regla
		 * 1+3=4  ==> 0
		 * 1+5=6  ==> 2
		 * 3+7=10  ==> 6
		 * 5+7=12  ==> 8
		 */
		switch(pos1+pos2){
			case 4:
				return surroundings[0];
			case 6:
				return surroundings[2];
			case 10:
				return surroundings[6];
			case 12:
				return surroundings[8];
				
			default: return surroundings[4];
		}
	}

	/**
	 * decision funcion para tomar la decisión de movimiento
	 * se busca la distancia mas pequeña al objetivo en caso de empate se coge siempre la primera.
	 * @param datos
	 * @return -1(error) o decision
	 */
	private int decision(ArrayList<Pair> datos){
		float min=9999999;
		int i=0;
		int po=-1;
		while(i<datos.size()){

			if(datos.get(i).getFirst()<min){
				po=i;
				min=datos.get(i).getFirst();
			}
			i++;
		}
		if(min==9999999){
			return -1;
		}
		else{
			return datos.get(po).getSecond();
		}
	}

	
	/**
	 * Método para obtener un array con los movimientos libres del drone usando la memoria del mismo.
	 * @return Un array con lo que hay en las posiciones Este, Sur, Oeste y Norte a las que se podría mover, en ese orden.
	 */
	// POST DIAGRAMA DE CLASES
	private int[] getValidMovements() {
		int movimientosLibres[] = new int[4];
		/* TODO: Revisar la suma de valores. ¿Qué pasa si el drone ya ha guardado que es una posición
		 * ocupada (un 1) y el satélite le envía otro 1 de que está ocupada? ¿Da un 2 de visitado?
		 * Estos errores ocurrirán cuando el dron guarde en su mapa lo que hay en las posiciones.
		 */
		/* TODO (Alberto)
		 * El drone no guarda los obstaculos en el mapa, solo si los ha visitado o no.
		 * Los posible valores de la suma serian:
		 * Vacio y no visitado = 0
		 * Vacio y visitado = 2
		 * Obstaculo = 1
		 * Para mi estan bien. No veo el fallo.
		 * 
		 * Ya si veo el fallo
		 */
		// CAMBIO REALIZADO: El norte puesto como posY-1 y sur posY+1 (estaba al revés)
		/*movimientosLibres[NORTE] = surroundings[1] + droneMap.getValue(posX, posY - 1);
		// La siguiente línea de código ¡PETA! porque intenta acceder a la posición X = -1 (arreglado)
		movimientosLibres[OESTE] = surroundings[3] + droneMap.getValue(posX - 1, posY);
		movimientosLibres[SUR] = surroundings[7] + droneMap.getValue(posX, posY + 1);
		movimientosLibres[ESTE] = surroundings[5] + droneMap.getValue(posX + 1, posY);
		return movimientosLibres;*/
		
		if(surroundings[1]==Map.LIBRE || surroundings[1]==Map.OBJETIVO){
			movimientosLibres[NORTE] = droneMap.getValue(posX, posY - 1);
		}else{
			movimientosLibres[NORTE] = surroundings[1];
		}
		if(surroundings[3]==Map.LIBRE || surroundings[3]==Map.OBJETIVO){
			movimientosLibres[OESTE] = droneMap.getValue(posX-1, posY);
		}else{
			movimientosLibres[OESTE] = surroundings[3];
		}
		if(surroundings[7]==Map.LIBRE || surroundings[7]==Map.OBJETIVO){
			movimientosLibres[SUR] = droneMap.getValue(posX, posY + 1);
		}else{
			movimientosLibres[SUR] = surroundings[7];
		}
		if(surroundings[5]==Map.LIBRE || surroundings[5]==Map.OBJETIVO){
			movimientosLibres[ESTE] = droneMap.getValue(posX + 1, posY);
		}else{
			movimientosLibres[ESTE] = surroundings[5];
		}
		
		return movimientosLibres;
	}
	
	/**
	 * Método para obtener un array con los valores combinados de surroundings y el mapa
	 * @return Un array con lo que hay en las posiciones de alrededor. Los valores posibles son LIBRE, OBSTACULO y VISITADO
	 */
	// POST DIAGRAMA DE CLASES
	private int[] getValidSquares() {
		int movimientosLibres[] = new int[9];

		for(int i=0; i<3; i++)
			for(int j=0; j<3; j++)
				if(surroundings[i+j*3]==Map.LIBRE || surroundings[i+j*3]==Map.OBJETIVO){
					movimientosLibres[i+j*3]=droneMap.getValue(posX+i-1, posY+j-1);
				}else{
					movimientosLibres[i+j*3]=surroundings[i+j*3];
				}
		
		return movimientosLibres;
	}

	/**
	 * createStatus: Crea estado para un objeto JSON de tipo drone
	 * @return estado
	 * @throws JSONException
	 */
	private JSONObject createStatus() throws JSONException {
		int movimiento = 0;

		JSONObject estado = new JSONObject();
		estado.put("connected", "Yes");
		estado.put("ready", "Yes");
		estado.put("movimiento", movimiento);

		return estado;

	}

	/**
	 * Getter del mapa, usado para el visualizador.
	 * @return el mapa del drone.
	 */
	public Map getDroneMap() {
		return droneMap;
	}

	/**
	 * sendInform se envia señal de confrimación al agente junto con su acción.
	 * @param id
	 * @param dec
	 */

	private void sendInform(AgentID id, JSONObject dec) {
		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		// jsonobject
		msg.setContent(dec.toString());
		this.send(msg);

		try {
			msg = receiveACLMessage();
		} catch (InterruptedException ex) {
			System.err.println("Agente " + this.getName() + " Error de comuncicación");
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		if (msg.getPerformative().equals("INFORM")) {
			System.out.println("Confirmo continuacion");

		} else {
			exit = true;

		}
	}

	/**
	 * receiveStatus metodo para comunicar al satélite que le envie información.
	 * @param id
	 * @param dec
	 */
	private void receiveStatus(AgentID id, JSONObject dec) {
		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

		msg.setSender(this.getAid());
		msg.addReceiver(id);
		msg.setContent(null);
		this.send(msg);
		try {
			msg = receiveACLMessage();

		} catch (InterruptedException ex) {
			System.err.println("Agente " + this.getName() + " Error de comuncicación");
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		if (msg.getPerformative().equals("INFORM")) {

			JSONObject contenido = null;
			try {
				contenido = new JSONObject(msg.getContent());
			} catch (JSONException ex) {
				ex.printStackTrace();
				Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
			}
			try {
				JSONObject aux = new JSONObject();
				aux = contenido.getJSONObject("gps");
				posX = aux.getInt("x");
				posY = aux.getInt("y");

				aux = contenido.getJSONObject("gonio");
				angle = (float) aux.getDouble("alpha");
				// Corregido, alpha estaba en aux y no en contenido

				// surroundings=(int[]) contenido.get("radar"); // No se puede hacer así
				// Una opción sería usando JSONArray, se tendría que mirar como pasarlo a un array normal tras sacarlo
				JSONArray jsArray = contenido.getJSONArray("radar");
				
				/* TODO: recupera bien lo que tiene al rededor (lo muestro por consola bien) 
				 * Pero parece que si lo pongo no termina en el mapa1 y si no lo pongo sí.
				 */
				
				for (int i=0; i < jsArray.length(); i++){
					surroundings[i] = jsArray.getInt(i);
				}
				// Compruebo si se reciben bien los alrededores:
				System.out.println("Alrededores del Dron: ");
				System.out.println("|"+surroundings[0]+", "+surroundings[1]+", "+surroundings[2]+"|");
				System.out.println("|"+surroundings[3]+", "+surroundings[4]+", "+surroundings[5]+"|");
				System.out.println("|"+surroundings[6]+", "+surroundings[7]+", "+surroundings[8]+"|");
						
			} catch (JSONException ex) {
				System.out.println("numeritos");
				ex.printStackTrace();
				Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
			}

		} else {
			exit = true;
		}

	}


	@Override
	public void finalize() {
		System.out.println("Agente " + this.getName() + " ha finalizado");
		super.finalize();
	}

	/*
	 * Método para las acciones del drone.
	 */
	@Override
	protected void execute() {
		ACLMessage message = new ACLMessage();
		JSONObject status = null;
		System.out.println("Agente " + this.getName() + " en ejecución");

		int decision = 0;

		try {
			status = createStatus();
		} catch (JSONException ex) {
			ex.printStackTrace();
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		while (!exit) {
			switch (estado) {
			case ESTADOREQUEST:
				receiveStatus(sateliteID, null);
				estado = ESTADOINFORM;
				break;
			case ESTADOINFORM:
				decision = think();
				if (decision < 0 || decision > 4) {
					ACLMessage fallo = new ACLMessage(ACLMessage.FAILURE);
					fallo.setSender(this.getAid());
					fallo.addReceiver(sateliteID);
					fallo.setContent(null);
				} else {
					try {
						status.remove("decision");
						System.out.println("decision " + decision);
						status.put("decision", decision);
					} catch (JSONException ex) {
						ex.printStackTrace();
						Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
					}
					sendInform(sateliteID, status);
					estado = ESTADOREQUEST;

				}
				break;
			}

		}
	}
}
