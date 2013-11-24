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
	private final int LIMIT_MOVEMENTS = 500;
	private boolean exit;
	private boolean goal;
	private int estado;
	private int posX;
	private int posY;
	private float angle;
	private float distance;
	private int[] surroundings;
	private Map droneMap;
	private float distanceMin;
	private int counterStop;
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
		distanceMin = 999999;
		counterStop = 0;
	}
	
	/**
	 * Se comprueba si el movimiento realizado por el Drone a mejorado la distancia absoluta con 
	 * respecto a la baliza:
	 *  - Si la mejora se almacena esa nueva distancia minima alcanzada por el drone.
	 *  - En caso contraria se comprueba que no se halla alcanzado el tope de movimientos permitidos
	 *  sin mejorar la distancia. Si se supera el tope de movimientos se finaliza la ejecución sin
	 *  haber encontrado solución al problema.
	 * @param distance Distancia absoluta a la que se encuentra el drone con respecto a la baliza.
	 * @return Se devuelve True si se debe finalizar y False en caso contrario.
	 */
	private boolean stop(float distance){
		
		if(distance < distanceMin){
			distanceMin = distance;
			counterStop = 0;
			return false;
		}else
			counterStop++;
		
		if(counterStop >= LIMIT_MOVEMENTS)
			return true;
		else
			return false;

	}
	/**
	 * Método donde el dron decide a qué dirección mover.
	 * @return dirección a la que se moverá.
	 */
	public int think(){
		/*La estructura del agente esta formada por task accomplishing behaviours (TAB).
		 *Para que se vean mejor cuales son las comprobaciones de estos TAB pondre en los comentarios TABi donde i
		 *es el orden del TAB empezando por el más crítico (i=1) al menos crítico.
		 */
		
		//Comprobacion de que no hemos alcanzado el limite de movimientos sin mejorar la distancia
		
		if(stop(distance)){
			System.out.println("A la mierda esto. No hay quien lo resuelva");
			return END;
		}
		
		//TAB1 Si hemos llegado al objetivo hemos terminado
		if(goal)
			return END;
		
		ArrayList<Pair> mispares, ordenados;
		
		mispares = getAllMovements();		
		
		//Ordenamos el array segun la distancia (de menor a mayor)
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
		
		
		//TAB2 Si estamos esquivando y podemos hacer el movimiento que pretendíamos cuando entramos en el modo entonces lo hacemos
		if(dodging && mispares.get(betterMoveBeforeDodging).getThird()){
			dodging=false;
			System.out.println("Saliendo dodging: " + betterMoveBeforeDodging);
			return betterMoveBeforeDodging;
		}
		
		//TAB3 Si estamos esquivando y podemos hacer el segundo movimiento que teniamos cuando entramos en el modo entonces lo hacemos
		if(dodging && mispares.get(secondMoveBeforeDodging).getThird()){
			return secondMoveBeforeDodging;
		}
		
		
		//TAB4 A partir de aqui comienza la ejecucion del algoritmo de escalada
		
		//Si podemos hacer el mejor movimiento lo hacemos
		//Si no podemos y es debido a que hay un obstaculo pasamos al modo esquivar
		if(ordenados.get(0).getThird()){
			return ordenados.get(0).getSecond();
		}else{
			int [] validMov=getValidMovements();
			if(validMov[ordenados.get(0).getSecond()]==Map.OBSTACULO && !dodging){
				dodging=true;
				betterMoveBeforeDodging=ordenados.get(0).getSecond();
				secondMoveBeforeDodging=ordenados.get(1).getSecond();
			}
		}
		
		int second=-1, third=-1;
		//Para hallar los dos mejores movimientos posibles (si existen) recorremos el array de peor a mejor
		//Si un movimiento es posible entonces hemos encontrado uno mejor que los que encontrasemos antes
		//Desplazamos los valores encontrados antes (siempre se queda en second el mejor posible y en third el segundo mejor posible)
		for(int i=3; i>=0; i--){
			if(ordenados.get(i).getThird()){
				third = second;
				second = ordenados.get(i).getSecond();
			}
		}
		
		//Si third no existe nuestra unica posibilidad es second
		if(third==-1)
			return second;
		
		//Si second no existe (y por lo tanto third tampoco) entonces no tenemos movimientos
		if(second==-1)
			return END;
		
		
		//Ahora comprobamos si existe empate entre ambos (distancias parecidas).
		//Si no hay empate nos quedamos con el segundo
		//El valor de margen de error debe ser ajustado "a mano" en caso de usar distancias.
		//En caso de usar el angulo se puede poneer un valor mejor pero los calculos son mas coñazo
		float error=1.0f;
		int better=ordenados.get(0).getSecond(), decision;
		float distSecond=mispares.get(second).getFirst(), distThird=mispares.get(third).getFirst();
		if(Math.abs(distSecond-distThird)<error && dodging && third==(second+2)%4){
			int cornerSecond = getCorner(better, second), cornerThird = getCorner(better, third);
			
			//El empate se decide por los obstaculos
			//Si la esquina del tercero esta libre pero la del segundo no, nos quedamos con esa
			//En cualquier otro caso nos quedamos con el segundo mejor movimiento
			if(cornerThird==Map.LIBRE && cornerSecond==Map.OBSTACULO){
				decision = third;
			}else{
				decision = second;
			}
				
		}else{
			decision = second;
		}
		
		return decision;
	}

	/**
	 * Calcula la esquina que rodean dos posiciones.
	 * @param mov1 Movimiento que nos dejaria en la primera posición 
	 * @param mov2 Movimiento que nos dejaria en la segunda posición
	 * @return Valor del surrounding para esa esquina
	 */
	private int getCorner(int mov1, int mov2) {
		//por si las moscas
		if(mov1 == (mov2 + 2) % 4)
			return surroundings[4];

		switch(mov1){
			case ESTE:
				return ((mov2==SUR) ? surroundings[8] : surroundings[2]);
			case SUR:
				return ((mov2==OESTE) ? surroundings[6] : surroundings[8]);
			case OESTE:
				return ((mov2==NORTE) ? surroundings[0] : surroundings[6]);
			case NORTE:
				return ((mov2==ESTE) ? surroundings[2] : surroundings[0]);
			default:
				return surroundings[4];
		}
	}

	/**
	 * Calcula las distancias y las condiciones de los cuatro posibles movimientos.
	 * @return Array con los movimientos
	 */
	private ArrayList<Pair> getAllMovements(){
		ArrayList<Pair> mispares=new ArrayList<Pair>();
		int[] validSqr = getValidSquares();
		boolean[] basicond=new boolean[4];

		double posiOX=0,posiOY=0;
		float calculoDist=0;
		

		basicond[ESTE]= 	validSqr[5]==Map.LIBRE	&& !(validSqr[2]==Map.VISITADO || validSqr[8]==Map.VISITADO);
		basicond[SUR]= 		validSqr[7]==Map.LIBRE	&& !(validSqr[6]==Map.VISITADO || validSqr[8]==Map.VISITADO);
		basicond[OESTE]= 	validSqr[3]==Map.LIBRE	&& !(validSqr[0]==Map.VISITADO || validSqr[6]==Map.VISITADO);
		basicond[NORTE]= 	validSqr[1]==Map.LIBRE	&& !(validSqr[0]==Map.VISITADO || validSqr[2]==Map.VISITADO);

		if(!(basicond[ESTE] || basicond[SUR] || basicond[OESTE] || basicond[NORTE])){
			basicond[ESTE]= 	validSqr[5]==Map.LIBRE	&& !(validSqr[2]==Map.VISITADO && validSqr[8]==Map.VISITADO);
			basicond[SUR]= 		validSqr[7]==Map.LIBRE	&& !(validSqr[6]==Map.VISITADO && validSqr[8]==Map.VISITADO);
			basicond[OESTE]= 	validSqr[3]==Map.LIBRE	&& !(validSqr[0]==Map.VISITADO && validSqr[6]==Map.VISITADO);
			basicond[NORTE]= 	validSqr[1]==Map.LIBRE	&& !(validSqr[0]==Map.VISITADO && validSqr[2]==Map.VISITADO);
		}	
		
		posiOX= (posX + (Math.cos(angle) * distance));
		posiOY= (posY + (Math.sin(angle)*distance));

		//Creamos el array con todos los movimientos, incluyendo la distancia al objetivo, el movimiento en si, y si es valido o no
		calculoDist= (float) Math.sqrt(Math.pow((posiOX-(posX+1)),2)+Math.pow((posiOY-posY), 2));
		mispares.add(new Pair(calculoDist,ESTE,basicond[ESTE]));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY+1)), 2));
		mispares.add(new Pair(calculoDist,SUR,basicond[SUR]));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-(posX-1)),2)+Math.pow((posiOY-posY), 2));
		mispares.add(new Pair(calculoDist,OESTE,basicond[OESTE]));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY-1)), 2));
		mispares.add(new Pair(calculoDist,NORTE,basicond[NORTE]));
	
		return mispares;
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
			System.err.println("Agente " + this.getName() + " Error de comunicación");
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
				String campo=null;
				aux = contenido.getJSONObject("gps");
				//actualizamos el mapa del drone antes de recoger las nuevas posiciones X e Y.
				droneMap.setvalue(posX,posY,Map.VISITADO);
				posX = aux.getInt("x");
				posY = aux.getInt("y");

				aux = contenido.getJSONObject("gonio");
				angle = (float) aux.getDouble("alpha");
				//Recoger distancia.
				distance= (float) aux.getDouble("dist");				
				
				//Recogida y comprobación del campo goal.
				campo= contenido.getString("goal");
				if(campo.equals("Si")){
					goal=true;
				}
				else if(campo.equals("No")){
					goal=false;
				}
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

				if (decision < -1 || decision > 3) {
					ACLMessage fallo = new ACLMessage(ACLMessage.FAILURE);
					fallo.setSender(this.getAid());
					fallo.addReceiver(sateliteID);
					fallo.setContent(null);
				} else {
					//En caso de llegar a la meta.
					if(decision==END){
						exit=true;
					}
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
