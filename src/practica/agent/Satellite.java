package practica.agent;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import edu.emory.mathcs.backport.java.util.concurrent.PriorityBlockingQueue;
import es.upv.dsic.gti_ia.architecture.FIPAException;
import es.upv.dsic.gti_ia.architecture.NotUnderstoodException;
import es.upv.dsic.gti_ia.architecture.RefuseException;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.Gson;

import practica.gui.Log;
import practica.gui.Visualizer;
import practica.lib.ErrorLibrary;
import practica.lib.JSONKeyLibrary;
import practica.lib.ProtocolLibrary;
import practica.lib.SubjectLibrary;
import practica.map.Map;
import practica.map.SharedMap;
import practica.trace.Trace;
import practica.util.ConflictiveBox;
import practica.util.GPSLocation;
import practica.util.ImgMapConverter;
import practica.util.MessageQueue;
import practica.util.DroneStatus;

public class Satellite extends SuperAgent {
	private final int  LIMIT_DRONES_SCOUT = 1,
						LIMIT_DRONES_SCOUTIMPROBER = 2;
	
	private SharedMap mapOriginal;						//Mapa original a partir del cual transcurre todo.
	private SharedMap mapSeguimiento;						//Mapa que se va actualizando a medida que los drones se muevan.
	private double goalPosX;						//Coordenada X del objetivo.
	private double goalPosY;						//Cordenada Y del objetivo.
	private AgentID [] drones;						//Array que contiene las IDs de los drones.
	private DroneStatus [] droneStuses;				//Array que contiene los estados de los drones.
	private int maxDrones;							//Número máximo de drones que acepta el satélite.
	private int connectedDrones;					//Número de drones conectados.
	private LinkedBlockingQueue<ACLMessage> requestQueue, answerQueue;		//Cola de mensajes
	private Visualizer visualizer;					//Visualizador.
	private boolean usingVisualizer;				//Variable para controlar si se está usando el visualizador.
	private boolean exit;							//Variable para controlar la terminación de la ejecución del satélite.
	private static List<Integer> posXIniciales;
	private int finalize;
	private int n_requestStart;
	private HashMap<String, HashMap<String, String>> subscriptions;  // <tipoSubcripcion, < IDAgent, ID-combersation>>
	private AgentID cargador;			//Añadida variable para que el satelite se comunique con el cargador
	private int countDronesReachedGoal;
	private int droneScout, droneScout_Improber;    //Contador de exploradores y exploradores mejoradores
	private ArrayList<AgentID> dronesLagger;
	
	/**
	 * Constructor
	 * @author Jahiel
	 * @author Dani
	 * FIXME otros autores añadiros.
	 * @author Ismael (añadida variable charger)
	 * @param sat ID del satélite.
	 * @param charger ID del cargador.
	 * @param mapa mapa que se usará.
	 * @param maxDrones número máximo de drones que aceptará el satélite.
	 * @throws Exception
	 */
	public Satellite(AgentID sat,AgentID charger, Map map, int maxDrones) throws Exception{
		//Inicialización de atributos.
		super(sat);
		countDronesReachedGoal=0;
		cargador=charger;
		exit = false;
		mapOriginal = new SharedMap(map);
		mapSeguimiento = new SharedMap(map);
		drones = new AgentID [maxDrones];
		droneStuses = new DroneStatus [maxDrones];
		this.maxDrones = maxDrones;
		connectedDrones = 0;	
		//TODO cambiar a PriorityBlockingQueue.
		requestQueue = new LinkedBlockingQueue<ACLMessage>();
		answerQueue = new LinkedBlockingQueue<ACLMessage>();
		subscriptions =new HashMap<String, HashMap<String, String>>();
		subscriptions.put("DroneReachedGoal", new HashMap<String, String>());
		subscriptions.put("AllMovements", new HashMap<String, String>());
		subscriptions.put("ConflictiveSections", new HashMap<String, String>());
		finalize=0;
		n_requestStart = 0;
		droneScout = droneScout_Improber = 0;
		dronesLagger = new ArrayList<AgentID>();

		//Calcular la posición del objetivo.
		//Se suman todas las posiciones que contienen un objetivo y se halla la media.
		float horizontalPositions = 0, verticalPositions = 0, adjacentSquares=0;
		for(int i = 0; i < mapOriginal.getHeigh(); i ++)
		    for(int j = 0; j < mapOriginal.getWidth(); j ++){
		        if(mapOriginal.getValue(j,i) == Map.OBJETIVO){
		            horizontalPositions += j;
		            verticalPositions += i;
		            adjacentSquares ++;
		        }
		    }
		
		goalPosX = horizontalPositions / adjacentSquares;
		goalPosY = verticalPositions / adjacentSquares;
		
		usingVisualizer = false;
		
		posXIniciales = new ArrayList<Integer>();
		for(int i=0; i<maxDrones; i++)
			posXIniciales.add(new Integer(i*5));
	}
	
	/**
	 * Constructor con un visualizador
	 * @author Dani
	 * @author Ismael (añadida variable charger al constructor)
	 * @param sat 			ID del satélite.
	 * @param charger		ID del cargador.
	 * @param mapa 			mapa que se usará.
	 * @param maxDrones 	número máximo de drones que aceptará el satélite.
	 * @param v 			visualizador.
	 * @throws Exception
	 */
	public Satellite(AgentID sat,AgentID charger, Map mapa, int maxDrones, Visualizer v) throws Exception{
		this (sat,charger, mapa, maxDrones);		
		visualizer = v;
		usingVisualizer = true;
	}
	
	/**
	 * Hebra de recepción de mensajes
	 * @author Daniel
	 * @param msg mensaje recibido.
	 */
	public void onMessage (ACLMessage msg){	
		try{
		JSONObject content;
		String subject = null;
		BlockingQueue<ACLMessage> queue = null;
		
		try{
			content = new JSONObject(msg.getContent());
			subject = content.getString(JSONKeyLibrary.Subject);
		}
		catch (JSONException e){
			e.printStackTrace();
		}
		
		switch(subject)
		{
			case SubjectLibrary.ChargerBattery:
			case SubjectLibrary.Charge:
			case SubjectLibrary.DetailedCharges:
			case SubjectLibrary.Trace:
			case SubjectLibrary.Steps:
			case SubjectLibrary.BatteryLeft:
				queue = answerQueue;
				break;
			
			default:
				queue = requestQueue;
		}
		
		try {
			System.out.println(msg.getPerformative() + " " + msg.getContent());
			
			queue.put(msg);
			System.out.println("mensaje recibido!");	
		} catch (InterruptedException e) {
			e.printStackTrace();
		}	
		}catch(RuntimeException e){
			e.printStackTrace();
		}
	}


	/**
	 * Se crea un mensaje del tipo FAIL para informar de algun fallo al agente dron.
	 * @author Jahiel
	 * FIXME Este método ahora mismo creo que no se debe de usar. No lo borro, sino que dejo comentada la línea del send para que el programa compile.
	 * @author Dani.
	 * @param dron 			Identificador del agente dron.
	 * @param cad_error 	Cadena descriptiva del error producido.
	 */
	private void sendError(String protocol, AgentID dron, String cad_error) {
		JSONObject error = new JSONObject();

		try {
			error.put("fail", cad_error);
		} catch (JSONException e) {
			e.printStackTrace(); // esta excepcion nunca va a suceder porque la clave siempre es fail aun asi hay que capturarla y por eso no se lo comunico al dron
		}
		System.err.println("Agente " + this.getName() + " " + cad_error);

		//send(ACLMessage.FAILURE, "", dron, error);
	}
	
	/**
	 * Busca el status correspondiente a un drone.
	 * @author Dani
	 * @param droneID id del drone cuyo estatus se quiere obtener.
	 * @return el status encontrado.
	 */
	private DroneStatus findStatus (AgentID droneID){
		DroneStatus status = null;

		for (int i = 0; i < connectedDrones; i++){
			if (drones[i].toString().equals(droneID.toString()))
				status =  droneStuses[i];
		}
		
		return status;
	}
	
	/**
	 * Se calcula el valor del ángulo que forma la baliza y el EjeX horizontal tomando como centro
	 * a el agente drone.
	 * @author Jahiel
	 * @param posX Posición relativa de la baliza con respecto al drone.
	 * @param posY Posición relativa de la baliza con respecto al drone.
	 * @return valor del ángulo.
	 */
	private double calculateAngle(double posX, double posY){
		double angle = 0;
		
		if(posX > 0 && posY >= 0)
			angle = Math.atan(posY / posX);
		else if(posX > 0 && posY < 0)
			angle = Math.atan(posY / posX) + (2.0*Math.PI);
		else if(posX == 0 && posY > 0)
			angle = Math.PI / 2.0;
		else if(posX == 0 && posY < 0)
			angle = (3*Math.PI) / 2.0;
		else if(posX < 0)
			angle = Math.atan(posY / posX) + Math.PI;
		
		return angle;
	}
	

	/**
	 * Este método obtiene los valores de las celdas en las 9 casillas que rodean el drone 
	 * (incluyendo en la que se encuentra el drone)
	 * FIXME autores añadiros.
	 * @return Array de enteros con las inmediaciones del drone.
	 */
	private int[] getSurroundings(DroneStatus status){
		GPSLocation gps = status.getLocation();
		int[] surroundings = new int[9];
		int posX = gps.getPositionX();
		int posY = gps.getPositionY();
		
		// Recorre desde la posición dron -1  hasta la del dron + 1, tanto en X como en Y
		for (int i = 0; i< 3; i++){
			for(int j = 0; j < 3; j++){
				//Si la casilla esta visitada hay que comprobar si ha sido visitada por el drone que pide su status para evitar conflictos
				if(mapSeguimiento.getValue(posX-1+i, posY-1+j) == Map.VISITADO){
					boolean visited=false;
					List<AgentID> visitingAgents = new ArrayList<AgentID>(mapSeguimiento.getVisitingAgents(posX-1+i, posY-1+j));
					//Recorremos la lista de drones que han pasado por esa casilla buscando al drone en cuestion. 
					for(AgentID id : visitingAgents)
						if(id.toString().equals(status.getId().toString()))
							visited=true;

					//Si el drone esta en la lista es que ya ha pasado por esa casilla y por lo tanto se le pone un valor de visitado
					if(visited){
						surroundings[i+j*3] = Map.VISITADO;
					}else{
						//Si no lo esta se le da el valor original de esa casilla
						surroundings[i+j*3] = mapOriginal.getValue(posX-1+i, posY-1+j);
					}
				}else{
					//Si no ha sido visitada no nos complicamos
					surroundings[i+j*3] = mapSeguimiento.getValue(posX-1+i, posY-1+j);
				}
			}
		}
		
		return surroundings;
	}
	
	/**
	 * Creamos el objeto JSON status:
	 * Status: {“connected”:”YES”, “ready”:”YES”, “gps”:{“x”:10,”y”:5},
	 * “goal”:”No”, “gonio”:{“alpha”:0, “dist”:4.0}, “battery”:100,
	 * “radar”:[0,0,0,0,0,0,0,1,1], "isConflictive":"True/False", "ConflictiveData":ConflictiveBox}
	 * ConflictiveBox es un objeto de la clase ConflictiveBox serializado en formato JSON.
	 * @author Jahiel
	 * @author Daniel
	 * @return Objeto JSon con el contenido de Status
	 * @throws JSONException  Si la clave es null
	 */
	private JSONObject createJSONStatus(DroneStatus droneStatus) throws JSONException {
		
		GPSLocation gps = droneStatus.getLocation();
		
		int posXDrone = gps.getPositionX(), posYDrone = gps.getPositionY();
		double distance = Math.sqrt(Math.pow(goalPosX - posXDrone, 2) + Math.pow(goalPosY - posYDrone, 2));
		double angle = calculateAngle(goalPosX - posXDrone, goalPosY - posYDrone);

		JSONObject status = new JSONObject();
		status.put("connected", "Yes");
		status.put("ready", "Yes");
		
		JSONObject aux = new JSONObject();
		aux.put("x", gps.getPositionX());
		aux.put("y", gps.getPositionY());

		status.put("gps", aux);

		if(mapOriginal.getValue(posXDrone, posYDrone) == Map.OBJETIVO)
			status.put("goal", "Si");
		else
			status.put("goal", "No");

		JSONObject angleAndDistance = new JSONObject();
		angleAndDistance.put("alpha", angle);
		angleAndDistance.put("dist", distance);
		status.put("gonio", angleAndDistance);
		status.put("battery", droneStatus.getBattery());
		
		int[] surroundings = getSurroundings(droneStatus);
		JSONArray jsArray = new JSONArray(surroundings);
		status.put("radar", jsArray);
		
		//Compruebo si la casilla es conflictiva
		if (mapSeguimiento.isConflictive(gps.getPositionX(), gps.getPositionY())){
			//Creo el string JSON
			String conflictiveData = new Gson().toJson(mapSeguimiento.getSharedSquare(gps.getPositionX(), gps.getPositionY()).getConflictiveBoxes());
			//Lo añado
			status.put(JSONKeyLibrary.ConflictiveBox, conflictiveData);
			System.out.println("ZZZ AÑADIENDO CASILLA CONFLICTIVA AL STATUS pos" + status.toString());
		}

		return status;
	}



	/**
	 * En función del valor recibido por el dron se actualiza el mapa interno
	 * del satelite con la nueva posición del drone (x, y en funcion de la
	 * dirección elegida) o se da por finalizada la comunicación.
	 * @author Jahiel
	 * @author Dani
	 * @param msg mensaje que contiene la decisión del drone
	 * @return Se devuelve "true" si se debe finalizar la comunicación y "false" en caso contrario.
	 */
	private boolean evalueDecision(ACLMessage msg) {
		//Busco el status del drone
		AgentID droneID = msg.getSender();
		DroneStatus droneStatus = findStatus(droneID);
		GPSLocation gps = droneStatus.getLocation();
		
		int decision, x = -1, y = -1;

		try {
			JSONObject ob = new JSONObject(msg.getContent());
			decision = ob.getInt(JSONKeyLibrary.Decision);
		} catch (JSONException e) {
			//Cambio de P3: si el JSON no está creado el satélite devuelve NOT_UNDERSTOOD en lugar de FAILURE, ya que no es culpa del satélite.
			sendError(new NotUnderstoodException(ErrorLibrary.BadlyStructuredContent), msg);
			return true;
		}


		String decisionString = ""; //Para guardar el log.
		
		switch (decision) {
			case Drone.ESTE: // Este
				x = gps.getPositionX() + 1;
				y = gps.getPositionY();
				decisionString = "East";
				break;
	
			case Drone.SUR: // Sur
				x = gps.getPositionX();
				y = gps.getPositionY() + 1;
				decisionString = "South";
				break;
	
			case Drone.OESTE: // Oeste
				x = gps.getPositionX() - 1;
				y = gps.getPositionY();
				decisionString = "West";
				break;
	
			case Drone.NORTE: // Norte
				x = gps.getPositionX();
				y = gps.getPositionY() - 1;
				decisionString = "North";
				break;
			case Drone.END_SUCCESS:
        		//Meter mensaje en el log
            	addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.IMoved, "Success! ^_^ ");
				return true;
			case Drone.END_FAIL:
        		//Meter mensaje en el log
            	addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.IMoved, "Fail u_u");
				return true;
			default:
				return false;
		}
		
		//Actualizar status
		//Si se movió, consumir una unidad de batería.
		if (decision == Drone.ESTE || decision == Drone.SUR || decision == Drone.OESTE || decision == Drone.NORTE)
			droneStatus.setBattery(droneStatus.getBattery() - 1);

		try {
			gps.setPositionX(x);
			gps.setPositionY(y);
			//droneStatus.setLocation(gps);
			mapSeguimiento.setValue(x, y, Map.VISITADO, droneID);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}


	/**
	 * Hebra de ejecución del satélite. 
	 * @author Jahiel
	 * @author Dani
	 * FIXME otros autores añadiros.
	 */
	@Override
	protected void execute() {
		ACLMessage proccesingMessage = null;
		System.out.println("Agente " + this.getName() + " en ejecución");
		try{
		
		while (!exit) {
			//Si la cola de mensajes no está vacía, saca un elemento y lo procesa.
			if (!requestQueue.isEmpty()){				
				try {
					proccesingMessage = (ACLMessage) requestQueue.take();
				} catch (InterruptedException e) {
					System.out.println("¡Cola vacía!");
					e.printStackTrace();
				}
				System.out.println("Procesando mensaje: protocolo " + proccesingMessage.getProtocol());
				
				try{
					switch (proccesingMessage.getProtocol()){
					
					case ProtocolLibrary.Registration : onRegister(proccesingMessage); break;
					case ProtocolLibrary.Information : onInformation (proccesingMessage); break;
					case ProtocolLibrary.DroneMove : onDroneMoved(proccesingMessage); break;
					case ProtocolLibrary.Subscribe : onSubscribe(proccesingMessage);break;
					case ProtocolLibrary.Finalize : onFinalize(proccesingMessage); break;
					case ProtocolLibrary.Reload : onReload(proccesingMessage); break;
					case ProtocolLibrary.Scout: onStart(proccesingMessage); break;
					case ProtocolLibrary.Notification : onNotification(proccesingMessage);break;
					default:
						throw new NotUnderstoodException("");
					}		
			
				}catch(FIPAException fe){
					sendError(fe, proccesingMessage);
				} catch (JSONException e) {
					e.printStackTrace();
				}	
			}
		}
		}catch(RuntimeException e){
			e.printStackTrace();
		}
	}
	
	@Override
	/**
	 * Método finalizador del satélite.
	 * @author Jahiel
	 */
	public void finalize() {
		System.out.println("Agente " + this.getName() + " ha finalizado");
		ImgMapConverter.mapToImg("src/maps/resutado.png", mapSeguimiento);
	}

	/**
	 * Getter del mapa original.
	 * @return el mapa original.
	 */
	public Map getMapOriginal() {
		return mapOriginal;
	}

	/**
	 * Getter del mapa de seguimiento.
	 * @return el mapa de seguimiento.
	 */
	public SharedMap getMapSeguimiento() {
		return mapSeguimiento;
	}
	
	/**
	 * Tratamiento de un mensaje con el protocolo Registration
	 * @param msg
	 */
	public void onRegister (ACLMessage msg){
		try{
			System.out.println("Recibido registro de: " + msg.getSender().toString());
			//Meter mensaje en el log
			addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Register, "");
			drones[connectedDrones] = msg.getSender();
			Random r=new Random();
			int randomPos = r.nextInt(posXIniciales.size());
			GPSLocation location = new GPSLocation(posXIniciales.get(randomPos).intValue(), 0);
			droneStuses[connectedDrones] = new DroneStatus(msg.getSender(), drones[connectedDrones].name, location);
			connectedDrones ++;

			if(connectedDrones == maxDrones){
				List<String> idsList = new ArrayList<String>();
				for(int i=0; i<maxDrones; i++)
					idsList.add(drones[i].toString());

				String receiver;
				for(int i=0; i<maxDrones; i++){
					receiver = drones[i].toString();
					idsList.remove(receiver);
					JSONObject content = new JSONObject();
					content.put("ids", idsList);
					content.put(JSONKeyLibrary.Subject, SubjectLibrary.Register);
					send(ACLMessage.INFORM, drones[i], "Register", null, msg.getReplyWith(), msg.getConversationId(), content);
					//Meter mensaje en el log
					addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Register, "You are registered!");
				}
			}
		}catch(Exception e){
			e.printStackTrace();
			RefuseException error = new RefuseException(ErrorLibrary.FailureCommunication);
			ACLMessage errorMsg = error.getACLMessage();
			for(int i=0; i<connectedDrones; i++)
				if(!drones[i].toString().equals(msg.getSender().toString()))
					errorMsg.addReceiver(drones[i]);
			
			this.sendError(error, msg);
			throw new RuntimeException(ErrorLibrary.FailureCommunication + " (Satelite)");
		}
	}
	
	
	/**
	 * Rutina de tratamiento de un mensaje con el protocolo "SendMeMyStatus".
	 * Los posibles mensajes que se mandan son:
	 * - La performativa no es REQUEST => NOT_UNDERSTOOD.
	 * - Hubo error al crear el JSON con el status => FAILURE + "Error al crear Status".
	 * - Todo va bien => INFORM + JSON con el status del drone.
	 * @author Dani
	 * @param msg mensaje a tratar
	 * @return objeto JSON a mandar.
	 */
	private void onStatusQueried(ACLMessage msg) {
		//Si hay visualizador, manda actualizar su mapa.
		if (usingVisualizer){
			visualizer.updateMap();
			//Si no está pulsado "Find Target" y está pulsado "Think Once" hay que habilitar "Think Once". Si "Find Target" está pulsado, no se debe de hacer nada.
			//if (visualizer.isBtnFindTargetEnabled() && !visualizer.paused())
				//visualizer.enableThinkOnce();
		}
		
		if (msg.getPerformative().equals("REQUEST")){			
			//Construcción del objeto JSON			
			
			try {				
				//Mando el status en formato JSON del drone que me lo solicitó.
				send(ACLMessage.INFORM, msg.getSender(), "SendMeMyStatus", null, msg.getInReplyTo(), msg.getConversationId(), createJSONStatus(findStatus(msg.getSender())));
				System.out.println("Mensaje mandado con su status.");
			} catch (JSONException e) {
				//Si hubo error al crear el objeto JSOn se manda un error.
				e.printStackTrace();
				//TODO enviar error.
				//sendError("SendMeMyStatus", msg.getSender(), "Error al crear Status");
			}
		}
		else{
			// El mensaje recibido es de tipo distinto a Request, se manda un not understood.
			send(ACLMessage.NOT_UNDERSTOOD, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), null);
			//Meter mensaje en el log
			addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Register, "Not understood");
		}
	}
	
	/**
	 * Rutina de tratamiento de un mensaje con el protocolo "IMoved"
	 * Los posibles mensajes que se mandan son:
	 * - La performativa no es REQUEST => NOT_UNDERSTOOD.
	 * - Falla al crear el objeto JSON con el contenido del mensaje => FAILURE + "Error al crear objeto JSON con la decision".
	 * - El drone ha metido un valor inválido en la decisión => NOT_UNDERSTOOD (se manda en evalueDecision).
	 * - Hay un fallo al actualizar el mapa => FAILURE + "Error al actualizar el mapa".
	 * - Todo va bien => INFORM.
	 * @author Dani
	 * @author Jahiel
	 * @param msg mensaje a tratar.
	 */
	public void onDroneMoved(ACLMessage msg) {		
		if (msg.getPerformative().equals("REQUEST")){
			if (usingVisualizer)
				if (visualizer.paused())
					while (visualizer.paused()){
						System.out.print("");//Necesario para volver a comprobar la condición del while.
					}

			
			//@author Jahiel
			AgentID droneID = msg.getSender();  //obtenemos la posicion actual
			DroneStatus droneStatus = findStatus(droneID);
			GPSLocation currentPosition = droneStatus.getLocation();
			
			exit = evalueDecision(msg);
			
			JSONObject content = new JSONObject();
			try {
				content.put(JSONKeyLibrary.Subject, SubjectLibrary.IMoved);
			} catch (JSONException e1) {
				e1.printStackTrace();
			}
			send(ACLMessage.INFORM, msg.getSender(), ProtocolLibrary.DroneMove, null, msg.getReplyWith(), msg.getConversationId(), content);	
			
			//Actualizar mapa del visualizador si lo está usando
			if (usingVisualizer){
				visualizer.updateMap();
				//visualizer.setDroneBattery(msg.getSender(), findStatus(msg.getSender()).getBattery());
			}
			
			JSONObject o;
			try {
				o = new JSONObject(msg.getContent());
				if(o.getInt(JSONKeyLibrary.Decision) == Drone.END_SUCCESS){
					findStatus(droneID).setGoalReached(true);
					countDronesReachedGoal++;
					sendInformSubscribeFinalize(msg);
				}else
					sendInformSubscribeAllMovement(msg, currentPosition, o.getInt(JSONKeyLibrary.Decision));
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			
			/**
			 * @TODOauthor Dani
			 * Cambiar esta línea por la gestión de la finalización
			 */
			exit=false;
				
		}
		else{
			// El mensaje recibido es de tipo distinto a Request, se manda un not understood.
			send(ACLMessage.NOT_UNDERSTOOD, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), null);
		}		
	}
	
	/**
	 * Se tratan las peticiones de subscripciones recibidas. Se rechaza si ocurre lo siguiente:
	 *  - AlreadySubscribe: ya se encuentra subscrito a este tipo de subscripción.
	 *  - MissingAgent: aun no está todos los agentes registrados en el satélite.
	 * 
	 * @author Jahiel
	 * @param msg Mensaje de petición de subscripción.
	 * @throws RefuseException 
	 * @throws NotUnderstoodException
	 */
	public void onSubscribe (ACLMessage msg)throws RefuseException, NotUnderstoodException{
		JSONObject content = null;
		try {
			content = new JSONObject(msg.getContent());
		} catch (JSONException e1) {
			throw new NotUnderstoodException("Satellite: Error en el content del Subscribe");
		}
		
		try {
			if(subscriptions.containsKey(content.get(JSONKeyLibrary.Subject))){
				if(subscriptions.get(content.get(JSONKeyLibrary.Subject)).containsKey(msg.getSender().toString())){
					throw new RefuseException(ErrorLibrary.AlreadySubscribed);
				}else if(drones.length != this.maxDrones){
					throw new RefuseException(ErrorLibrary.MissingAgents);
				}else{
					subscriptions.get(content.get(JSONKeyLibrary.Subject)).put(msg.getSender().toString(), msg.getConversationId());
					//Meter mensaje en el log
					String subject = content.getString(JSONKeyLibrary.Subject);
					addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), subject, "");
					
					send(ACLMessage.ACCEPT_PROPOSAL, msg.getSender(), ProtocolLibrary.Subscribe, null, "confirmation", msg.getConversationId(), content);
					
					//Meter mensaje en el log
					addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
				}
			}
		} catch (JSONException e1) {
			// no se ejecuta nunca
			e1.printStackTrace();
		}
			
	}
	
	/**
	 * Se notifica a los subscriptores que el drone (ID-Drone) se a movido.
	 * 
	 * @author Jahiel
	 * @param msg Mensaje del drone ID-Drone con su decision de moverse.
	 * @param currentPosition Posición antes de moverse.
	 * @param newPosition  Posición actualizada con la decisción del drone.
	 */
	private void sendInformSubscribeAllMovement(ACLMessage msg, GPSLocation currentPosition,
												  int decision){
		JSONObject contentSub = new JSONObject();
		
		try {
			contentSub.put(JSONKeyLibrary.Subject, SubjectLibrary.AllMovements);
			contentSub.put("ID-Drone", msg.getSender().toString());
			int[] posPr = {currentPosition.getPositionX(), currentPosition.getPositionY()};
			contentSub.put("PreviousPosition", new JSONArray(posPr));
			JSONObject ob = new JSONObject(msg.getContent());
			contentSub.put("Decision", decision);
			
		} catch (JSONException e) {
			// no sudece nunca
			e.printStackTrace();
		}

		for(String name: this.subscriptions.get(SubjectLibrary.AllMovements).keySet()){
			send(ACLMessage.INFORM, new AgentID(name), ProtocolLibrary.Subscribe, null, null,  this.subscriptions.get(SubjectLibrary.AllMovements).get(name), contentSub);
			System.out.println("informado drone"+name);
			//Meter mensaje en el log
			//TODO si se llega a hacer, es un mensaje un poco complejo.
		}
	}
	
	/**
	 * Se notifica a los subscriptores de que un drone a llegado a la meta.
	 * 
	 * @author Jahiel
	 * @param msg
	 */
	private void sendInformSubscribeFinalize(ACLMessage msg){
		JSONObject contentSub = new JSONObject();
		String sender = msg.getSender().toString();
		
		try {
			contentSub.put(JSONKeyLibrary.Subject, SubjectLibrary.DroneReachedGoal);
			contentSub.put(JSONKeyLibrary.DroneID, sender);
			
		} catch (JSONException e) {
			// no sudece nunca
			e.printStackTrace();
		}
		
		for(String name: this.subscriptions.get(SubjectLibrary.DroneReachedGoal).keySet()){
			if(!sender.equals(name)){
				send(ACLMessage.INFORM, new AgentID(name), ProtocolLibrary.Subscribe, null, null, 
						this.subscriptions.get(SubjectLibrary.DroneReachedGoal).get(name), contentSub);
				//Meter mensaje en el log
				addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.DroneReachedGoal, msg.getSender().name);	
			}
		}
		
	}
	
	/**
	 * Rutina de tratamiento de un mensaje con el protocolo "Reload".
	 * En este caso solo se puede recibir un único mensaje: la recarga de un drone. No se envían mensajes.
	 * @author Dani
	 * @param msg
	 */
	public void onReload (ACLMessage msg){
		if (msg.getPerformativeInt() == ACLMessage.INFORM){
			try {
				JSONObject content = new JSONObject(msg.getContent());
				//Busco el status del drone
				AgentID rechargedDrone = new AgentID (content.getString(JSONKeyLibrary.DroneID));
				DroneStatus rechargedDroneStatus = findStatus(rechargedDrone);
				//Lo actualizo
				int rechargedAmmount = content.getInt(JSONKeyLibrary.AmountGiven);
				rechargedDroneStatus.setBattery(rechargedDroneStatus.getBattery() + rechargedAmmount);
				
				//Meter mensaje en el log
				//String contentString = rechargedDrone.name + " was recharged " + String.valueOf(rechargedAmmount);
				//addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.BatteryRequest, contentString);	
			} catch (JSONException e) {
				System.out.println("onReload - Error en JSON");
				e.printStackTrace();
			}
		}
		else throw new RuntimeException("onReload - Perforbehavior = mode;mativa no inform.");
	}
	/**
	 * @author Ismael
	 * permite encontrar un estado a partir del nombre de un drone
	 * @param Name
	 * @return DroneStatus
	 */
	public DroneStatus searchByName(String Name){
		DroneStatus aux= droneStuses[0];
		boolean find=false;
				int i=0;
				for(;i<maxDrones&&!find;i++){
					if(droneStuses[i].getName().equals(Name)){
						find=true;
						aux=droneStuses[i];
					}
				}
				
		return aux;
	}
	
	/**
	 * @author Ismael
	 * Convierte un objeto DroneStatus a un JSONObject con lo siguiente:
	 * content{ id,name, baterry, location{x,y}}
	 * @param nm
	 * @return rs
	 */
	
	public JSONObject StatusToJSON(DroneStatus nm){
		JSONObject rs= new JSONObject();
		try{
			rs.put("id",nm.getId());
			rs.put("name", nm.getName());
			rs.put("battery", nm.getBattery());
			JSONObject aux = new JSONObject();
			GPSLocation aux2 = nm.getLocation();
			aux.put("x", aux2.getPositionX());
			aux.put("y", aux2.getPositionY());
			rs.put("location", aux);
		}catch(JSONException e){
			throw new RuntimeException("Fallo en paso String To JSON");
		}
		
			return rs;
	}
	
	/**
	 * @author Ismael
	 * metodos de inicio y straggler
	 * @param msg
	 * @throws JSONException 
	 */
	public void onStart(ACLMessage msg) throws FIPAException{
		
		
		try {
		JSONObject content = new JSONObject(msg.getContent());
		JSONObject res = new JSONObject();
		ArrayList<AgentID> listOfDrones = new ArrayList<AgentID>();
		
		
		
		String subject = content.getString(JSONKeyLibrary.Subject);
		/*
		if (content.length() == 0){
            throw new RefuseException(ErrorLibrary.EmptyContent);
		}
		if (!content.has(JSONKeyLibrary.Subject) ){
            throw new RefuseException(ErrorLibrary.BadlyStructuredContent);
		}
		if(!search(msg.getSender())){
			throw new RefuseException(ErrorLibrary.FailureAgentID);
		}
		*/
			
		switch(subject){
		
		case SubjectLibrary.Start:
			onStartDrone(msg);
			break;
		
		case SubjectLibrary.Straggler:
				//Meter mensaje en el log
				addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Straggler, "");
						
				listOfDrones = getDronesNoGoal();
				listOfDrones.remove(msg.getSender());
				res.put(JSONKeyLibrary.Subject, SubjectLibrary.Straggler);
				
				send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Scout,"default",null,buildConversationId(), res);
				//Meter mensaje en el log
				addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Straggler, "");	
				
				dronesLagger.add(msg.getSender());
				
				res.remove(JSONKeyLibrary.Subject);
				res.put(JSONKeyLibrary.Subject, SubjectLibrary.StragglerNotification);
				res.put(JSONKeyLibrary.Straggler, msg.getSender().toString());
				
				for(int i=0;i<listOfDrones.size();i++){
					System.out.println("MANDANDO STRAGGLER NOT a " + listOfDrones.get(i).toString());
					send(ACLMessage.INFORM,listOfDrones.get(i),ProtocolLibrary.Scout,"default",null,buildConversationId(), res);
					//Meter mensaje en el log
					addMessageToLog(Log.SENDED, listOfDrones.get(i), msg.getProtocol(), SubjectLibrary.StragglerNotification, "Straggler: " + msg.getSender().name);	
				}
			break;
			
			default:
				
                    throw new NotUnderstoodException(ErrorLibrary.NotUnderstood);
				
		}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			RefuseException error = new RefuseException(ErrorLibrary.FailureCommunication);
		}
	}
	
	/**
	 * Se devuelve la de la traza óptima desde la que partirán los drones para seguir la traza. Esta posición es el comienzo de cuando el drone
	    * inicia la bajada por primera vez. En caso de no existir tal punto (el goal se encuantra en un punto (x, 0) de devuelve el punto final de la traza.
	    * @Jahiel 
	    * @return Punto de partida.
	    */
	public int getInitialPosition(Trace t){
	   	int i, size = t.size(); 
	   		
	   	for(i=0; i<size; ++i){
	   		if(t.getLocation(i).getPositionY()>0)
	   			return i-1;
	   	}
	   		
	   	return i-1; 
	}
	      	
	/**
	 * Se devuelve el indice del drone mas cerca del objetivo. Si el parametro rescueStragglers es False entonces solo se tienen
	 * en cuenta a los drones que aun no han salido en caso contrario se tiene en cuenta a todos los drones (rezagados o drones que aun no
	 * han salido).
	 * 
	 * @author Jahiel
	 * @param rescueStragglers
	 * @return
	 */
	public int findNerestDrone(boolean rescueStragglers){
		int dist = 99999;
		int distXAux, distYAux, distAux;
		int droneSelected = -1;
		
		if(rescueStragglers){
			for(int i=0; i<droneStuses.length; ++i){
				distXAux = (int) Math.abs(goalPosX - droneStuses[i].getLocation().getPositionX());
				distYAux = (int) Math.abs(goalPosY - droneStuses[i].getLocation().getPositionY());
				distAux = distXAux + distYAux;
				
				if( (distAux < dist) && !droneStuses[i].isGoalReached() ){
					dist = distAux;
					droneSelected = i;
				}
			}
		}else{
			for(int i=0; i<droneStuses.length; ++i){
				distXAux = (int) Math.abs(goalPosX - droneStuses[i].getLocation().getPositionX());
				if( (droneStuses[i].getLocation().getPositionY() == 0) && ( distXAux < dist) && !droneStuses[i].isGoalReached() ){
					dist = distXAux;
					droneSelected = i;
				}
			}
		}
		
		return droneSelected;
		
	}
	
	/**
	 * Rutina de tratamiento para la petición de salida por parte de los drones.
	 * @author Jahiel
	 * @param msg
	 */
	public void onStartDrone(ACLMessage msg) throws FIPAException{
		
		try{
			//Meter mensaje en el log
			addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Start, "");

			ACLMessage answer = null;
			Trace optimalTrace =  null;
			Trace traceAux;
			ArrayList<DroneStatus> dronesWithoutLeaving = new ArrayList<DroneStatus>();
			int batteryInCharger = 0, index = 0, behavior;

			n_requestStart++;

			if(n_requestStart == drones.length - countDronesReachedGoal){
				n_requestStart = 0;
				// Se calcula que drones han acabado y se coje la traza optima (la mas corta). Se coje solo el tamaño de la traza
				// desde el punto de partida hasta el final.

				for(int i=0; i<droneStuses.length; i++){
					if(droneStuses[i].isGoalReached()){
						System.out.println("PIDIENDO TRAZA A: " + droneStuses[i].getId().toString());
						traceAux = askForDroneTrace(droneStuses[i].getId());
						if(optimalTrace == null)
							optimalTrace = traceAux; 
						if(traceAux.size() < optimalTrace.size())
							optimalTrace = traceAux;
					}else{
						if(droneStuses[i].getLocation().getPositionY() == 0) // Se recogen los drones que aun no han salido
							dronesWithoutLeaving.add(droneStuses[i]);
					}
				}

				if(optimalTrace!=null){
					GPSLocation start = new GPSLocation(optimalTrace.getLocation(0).getPositionX(), optimalTrace.getLocation(0).getPositionY());
					int sizeTrace = optimalTrace.getSubtrace(start).size() * dronesWithoutLeaving.size(); // el tamaño de esta traza se multiplica por el numero
					// de drones que deben recorrerla.

					// Se calcula cuanto gastan los drones (que aun no han salido) de bateria en ir hacia el punto de partida de la traza optima

					int positionInic = getInitialPosition(optimalTrace);
					GPSLocation end = new GPSLocation(optimalTrace.getLocation(positionInic).getPositionX(), optimalTrace.getLocation(positionInic).getPositionY());

					for(DroneStatus status: dronesWithoutLeaving){
						if(status.getLocation().getPositionY() == 0 && !status.isGoalReached())
							sizeTrace += Math.abs(end.getPositionX() - status.getLocation().getPositionX());
					}

					//Se calcula cuanto gastan de bateria en rescatar a un rezagado (cuanto se gasta en retroceder y en llegar al goal)

					ArrayList<ConflictiveBox> conflictiveList = new ArrayList<ConflictiveBox>();

					for(ConflictiveBox box: conflictiveList){
						if(box.isDangerous()){
							sizeTrace+=box.getLength() + optimalTrace.getSubtrace(box.getPosInicial()).size();
						}
					}




					// Se pide la bateria restante que le queda al cargador
					batteryInCharger = askBattery();
				

					// Se comprueba si pueden llegar todos los drones.
					int batteryInDrones = dronesWithoutLeaving.size() * 75; // Se calcula cuanto bateria tienen los drones que quedan por salir

					if( (sizeTrace - batteryInDrones) <= batteryInCharger){
						System.out.println("SALLLLLLLLL FOLLOWER MADAFAKAR: " + (sizeTrace - batteryInDrones));
						index = findNerestDrone(true);
						behavior = Drone.FOLLOWER;
					}else{
						// Se selecciona el drone mas cercano dependiendo de los drones que hayan salido

						boolean rescueStragglers = false;  // variable que determina si se rescatan o no a los rezagados

						if(droneScout < LIMIT_DRONES_SCOUT)
							behavior = Drone.SCOUT;
						else if(droneScout_Improber < LIMIT_DRONES_SCOUTIMPROBER){
							behavior = Drone.SCOUT_IMPROVER;
							droneScout_Improber++;
						}else{
							behavior = Drone.FOLLOWER;
							rescueStragglers = true;
						}

						index = findNerestDrone(rescueStragglers);

						// Si el drone elegido es rezagado comprobamos si no hay bateria para que de la vuelta y siga la traza y se le asigna
						// el modo libre.

						if(rescueStragglers){
							int pos = 0; // posición del drone rezagado elegido

							for(AgentID id: dronesLagger){
								if(droneStuses[index].getId().equals(id)){
									for(ConflictiveBox box: conflictiveList){
										if(box.isDangerous() && box.getDroneID().equals(id)){
											if( (box.getLength() + optimalTrace.getSubtrace(box.getPosInicial()).size()) > batteryInCharger)
												behavior = Drone.FREE;
										}
									}
								}else
									pos++;
							}

							dronesLagger.remove(pos); // lo sacamos de la lista puesto que ya no será rezagado
						}
					}
				}else{
					behavior = Drone.SCOUT;
					droneScout++;
					index = findNerestDrone(false);
				}
				
				JSONObject contentSelected = new JSONObject();
				try {
					contentSelected.put(JSONKeyLibrary.Subject, SubjectLibrary.Start);
					contentSelected.put(JSONKeyLibrary.Selected, droneStuses[index].getId().toString());
					contentSelected.put(JSONKeyLibrary.Mode, behavior);
				} catch (JSONException e) {

					e.printStackTrace();
				}

				for(DroneStatus status: droneStuses){
					if(!status.isGoalReached()){
						send(ACLMessage.INFORM, status.getId(), ProtocolLibrary.Scout, null, null, buildConversationId(), contentSelected);	
						//Meter mensaje en el log
						addMessageToLog(Log.SENDED, status.getId(), ProtocolLibrary.Scout, SubjectLibrary.Start, "Selected: " + droneStuses[index].getId().name + ", Mode: " + String.valueOf(behavior));		
					}
				}

			}

		}catch(RuntimeException e){
			e.printStackTrace();
		}
	}
	
	/*
	 * @author Ismael
	 * Comprueba si el agente existe entre los que hay
	 * @param sender
	 */
	private boolean search(AgentID sender) {
		boolean find=false;
		for(int i=0;i<maxDrones&&!find;i++){
			if(drones[i].equals(sender)){
				find=true;
			}
		}
		return find;
	}

	
	/*
	 * @author Ismael
	 * método para seleccionar el modo adecuado de salida
	 */
	private int selectMode() {
		return 0;
	}
	/*
	 * @author Ismael
	 * método para seleccionar un drone de entre los posibles a dar salida
	 * @param listOfDrones
	 */
	private AgentID getIdSelectedDrone(ArrayList<AgentID> listOfDrones) {
		return null;
	}
	
	/**
	 * Se obtiene la lista de drones que no han llegado a la meta.
	 * 
	 * @author Jahiel 
	 * @return
	 */
	private ArrayList<AgentID> getDronesNoGoal() {
		ArrayList<AgentID> list = new ArrayList<AgentID>();
		
		for(DroneStatus status: droneStuses){
			if(!status.isGoalReached())
				list.add(status.getId());				
		}
		
		return list;
	}

	/**
	 * TODO Implementation
	 * @author Ismael
	 * @see onStatusQueried para cuando te pidan el status. Si el que implementa esto lo usa que no sea perro y me ponga como autor >_<
	 * @param msg
	 */
	
	public void onInformation (ACLMessage msg) throws JSONException{
		JSONObject content = new JSONObject(msg.getContent());
		String subject = content.getString(JSONKeyLibrary.Subject);
		JSONObject res = new JSONObject();
		/**
		if(msg.getPerformative().equals(ACLMessage.QUERY_REF)){
			throw new RuntimeException("Error de perfomativa");
		}
		**/
		
		try{
			switch(subject){
			    case SubjectLibrary.Status:
			    	try{
			    		AgentID id= msg.getSender();
			    		DroneStatus nm= findStatus(id);
			    		
			    		res.put(JSONKeyLibrary.Subject,SubjectLibrary.Status);
			    		//JSONObject convert = StatusToJSON(nm);
			    		res.put(SubjectLibrary.Values,createJSONStatus(nm));
			    		send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);				    		
			    		
			    	}catch(JSONException e){
			    		throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
					}
			    	break;
				case SubjectLibrary.MapOriginal:	    		
		    		//Meter mensaje en el log
		    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
					try{
						res.put(JSONKeyLibrary.Subject, "MapOriginal");
						res.put("Height", mapOriginal.getHeigh());
						res.put("Width",mapOriginal.getWidth());
						JSONArray aux = new JSONArray();
						if(mapOriginal.getHeigh()==0||mapOriginal.getWidth()==0){
							throw new RuntimeException("Fallo: Mapa no existe");
						}
						for(int i=0;i<mapOriginal.getHeigh();i++){
							for(int j=0;i<mapOriginal.getWidth();j++){
								if(mapOriginal.getValue(i,j)<-1||mapOriginal.getValue(i, j)>5){
									throw new RuntimeException("Fallo: valor erroneo en mapa");
								}
								aux.put(mapOriginal.getValue(i, j));
							}
						}
						res.put("Values", aux);
						send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);		
					}catch(JSONException e){
						throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
					}
					
						
						
					
					break;
				case SubjectLibrary.MapGlobal:	    		
		    		//Meter mensaje en el log
		    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
					try{
						res.put(JSONKeyLibrary.Subject, "MapGlobal");
						res.put("Height", mapSeguimiento.getHeigh());
						res.put("Width",mapSeguimiento.getWidth());
						JSONArray aux = new JSONArray();
						if(mapSeguimiento.getHeigh()==0||mapSeguimiento.getWidth()==0){
							throw new RuntimeException("Fallo: Mapa no existente");
						}
						for(int i=0;i<mapSeguimiento.getHeigh();i++){
							for(int j=0;j<mapSeguimiento.getWidth();j++){
								if(mapSeguimiento.getValue(i,j)<-1||mapSeguimiento.getValue(i, j)>5){
									throw new RuntimeException("Fallo: valor erroneo en mapa");
								}
								aux.put(mapSeguimiento.getValue(i,j));
							}
						}
						res.put("Values", aux);
						send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);   		
			    		//Meter mensaje en el log
			    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
					}catch(JSONException e){
						throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
					}
					
					break;
				case SubjectLibrary.IdAgent:	    		
		    		//Meter mensaje en el log
		    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
					System.out.println("Satelite recibe mensaje ");
					try{
						
						res.put(JSONKeyLibrary.Subject,"IdAgent");
						String names= content.getString("Name");
						
						DroneStatus status =  searchByName(names);
						if(status==null){
							throw new RuntimeException("Fallo: Status agente no existe");
						}
						else{
							System.out.println("SATELITE MUESTRA MENSAAAJEEEE " +status.getId());
							res.put("name",status.getName());
							res.put("ID",status.getId());
							
							send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);   		
				    		//Meter mensaje en el log
				    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, searchByName(names).toString());	
						}
					}catch(JSONException e){
						throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
					}
					
					break;
				case SubjectLibrary.Position:	    		
		    		//Meter mensaje en el log
		    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
					try{
						System.out.println("ESTOY EN POSITION");
						res.put(JSONKeyLibrary.Subject,SubjectLibrary.Position);
						JSONObject aux = new JSONObject();
						AgentID id = msg.getSender();
						if(id==null){//buscar en el satelite 
							throw new RuntimeException("Fallo: ID agente no existe");
						}
						DroneStatus status = findStatus(id);
						GPSLocation n= status.getLocation();
						aux.put("x",status.getLocation().getPositionX());
						aux.put("y", status.getLocation().getPositionY());
						res.put("Posi", aux);
						System.out.println("Envio informacion");
						send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,null,null,buildConversationId(), res);   		
			    		//Meter mensaje en el log
			    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, n.toString());	
					}catch(JSONException e){
						throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
					}
					
					break;
				case SubjectLibrary.GoalDistance:	    		
		    		//Meter mensaje en el log
		    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
						try{
							res.put(JSONKeyLibrary.Subject,"GoalDistance");
							
							AgentID id= new AgentID(content.getString("ID"));
							DroneStatus status= findStatus(id);
							GPSLocation n = status.getLocation();
							double x=n.getPositionX();
							double y=n.getPositionY();
							double dist= Math.sqrt(Math.pow((goalPosX-x),2)+Math.pow((goalPosY-y),2));
							res.put("Distance", dist);
							
							send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);	
				    		//Meter mensaje en el log
				    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, String.valueOf(dist));	
						}catch(JSONException e){
							throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
						}
						
					break;
				case SubjectLibrary.DroneBattery:	    		
		    		//Meter mensaje en el log
		    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, "");	
					
					try{
						res.put(JSONKeyLibrary.Subject,"DroneBattery");
						AgentID id =new AgentID(content.getString("AgentID"));
						System.out.println(id);
						DroneStatus status = findStatus(id);
						int bat=status.getBattery();
						if(bat<0||bat>75){
							throw new RuntimeException("Fallo: valor de bateria erroneo");
						}
						res.put("Battery",bat);
						send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);		
			    		//Meter mensaje en el log
			    		addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), subject, String.valueOf(bat));	
					}catch(JSONException e){
						throw new RuntimeException("Fallo en la obtencion respuesta mensaje");
					}
						
					break;
				default: 
					sendError(new NotUnderstoodException("Subject no encontrado"), msg);
					break;
					
					
			}
		
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			sendError(new FIPAException("Error"),msg);
			e.printStackTrace();
		}	
		
	}
	
	/**
	 * Recoge las acciones que debe realizar el satélite cuando recibe mensajes del protocolo
	 * de notificaciones.
	 * @author Jonay
	 * @param msg El mensaje recibido
	 * @throws JSONException
	 */
	private void onNotification(ACLMessage msg) throws JSONException {
		JSONObject content = new JSONObject(msg.getContent());
		String subject = content.getString(JSONKeyLibrary.Subject);
		//JSONObject res = new JSONObject();
		
		switch(subject){
			case SubjectLibrary.ConflictInform:
				String confJSON = content.getString(JSONKeyLibrary.ConflictBox);
				Gson gson = new Gson();
				ConflictiveBox cb = gson.fromJson(confJSON, ConflictiveBox.class);
				System.out.println("ZZZ RECIBIENDO CASILLA CONFLICTIVA pos: " + cb.getPosInicial().getPositionX() + ", " + cb.getPosInicial().getPositionY());
				mapSeguimiento.addConflictiveBox(cb);
				System.out.println("ZZZ AÑADIENDO CASILLA CONFLICTIVA pos: " + cb.getPosInicial().getPositionX() + ", " + cb.getPosInicial().getPositionY());//Meter mensaje en el log
				//addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), subject, cb.toString());	
				break;
			default: 
				sendError(new NotUnderstoodException("Subject no encontrado"), msg);
				break;
		}
			
	}

	
	/**
     * Pregunta al cargador la cantidad de bateria que le queda.
     * @author Alberto
     * @return Bateria total restante.
     */
	private int askBattery(){
		JSONObject requestContent = new JSONObject();
		ACLMessage answer=null;
		int resultado = -1; 
	
		try {
			requestContent.put(JSONKeyLibrary.Subject, SubjectLibrary.ChargerBattery);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		send(ACLMessage.QUERY_REF, cargador, ProtocolLibrary.Information, "Get-RemainingBattery", null, buildConversationId(), requestContent);
		//Meter mensaje en el log
		addMessageToLog(Log.SENDED, cargador, ProtocolLibrary.Information, SubjectLibrary.ChargerBattery , "");	
		
		try {
			answer = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	
		
		if(answer.getPerformativeInt() == ACLMessage.INFORM){
			try {
				resultado = new JSONObject(answer.getContent()).getInt("ChargerBattery");
				//Meter mensaje en el log
				addMessageToLog(Log.RECEIVED, answer.getSender(), answer.getProtocol(), SubjectLibrary.ChargerBattery , String.valueOf(resultado));	
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
		}else{
			try {
				throw new RuntimeException(new JSONObject(answer.getContent()).getString("error"));
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		return resultado;
	}
	
	/**
     * Pregunta a un drone por su traza
     * @author Jonay
     * @return la traza del drone
     */
    private Trace askForDroneTrace(AgentID DroneID){
    	Trace trazaDelDrone = null;	
    	JSONObject requestContent = new JSONObject();
		ACLMessage answer=null;
		
		try {
			requestContent.put(JSONKeyLibrary.Subject, SubjectLibrary.Trace);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		send(ACLMessage.QUERY_REF, DroneID, ProtocolLibrary.Information, "default", null, buildConversationId(), requestContent);
		//Meter mensaje en el log
		addMessageToLog(Log.SENDED, DroneID, ProtocolLibrary.Information, SubjectLibrary.ChargerBattery , SubjectLibrary.Trace);	
		
		try {
			answer = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	
		if(answer.getPerformativeInt() == ACLMessage.INFORM){
			try {
				String trazaJSON = new JSONObject(answer.getContent()).getString("trace");
				Gson gson = new Gson();
//				Type tipoTraza = new TypeToken<Trace>(){}.getType();
				trazaDelDrone = gson.fromJson(trazaJSON, Trace.class);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
		}else{
			try {
				throw new RuntimeException(new JSONObject(answer.getContent()).getString("error"));
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
    	return trazaDelDrone;
    }
    
	/**
	 * TODO Implementación
	 * 
	 * @author Jahiel Me pongo para que el que haga este mensaje me informe cuando este para poner la linea en su sitio
	 * @author Ismael 
	 * Protocolo de finalizacion
	 * @param msg
	 */
	public void onFinalize (ACLMessage msg){
		//Meter mensaje en el log
		addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), SubjectLibrary.End , "");	
		try{
			JSONObject res = new JSONObject();

			if(!msg.getPerformative().equals(ACLMessage.REQUEST)){
				throw new RuntimeException("Performativa erronea al finalizar");
			}
			else{
			
				finalize++;
				/* Lo dejo por si acaso a mi me parece correcto pero si dices que el drone se pone solico en standby no es necesario (pa Dani)
				if(finalize!=maxDrones){
					res.put(JSONKeyLibrary.Subject,ProtocolLibrary.Finalize);
					res.put("Content", "wait");
					send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Finalize,"default",null,buildConversationId(), res);
				}
				else
					*/
				
				
					if(finalize==maxDrones){
					//System.out.println("Finalizando");
					
						res.put(JSONKeyLibrary.Subject, SubjectLibrary.End);
						res.put("Content","End" );
						for(int i=0;i<maxDrones;i++){
							send(ACLMessage.INFORM,droneStuses[i].getId(),ProtocolLibrary.Finalize,"default",null,buildConversationId(), res);
							//Meter mensaje en el log
							addMessageToLog(Log.SENDED, droneStuses[i].getId(), ProtocolLibrary.Finalize, SubjectLibrary.End , "End");	
						}
						send(ACLMessage.INFORM,cargador,ProtocolLibrary.Finalize,"default",null,buildConversationId(), res);
						//Meter mensaje en el log
						addMessageToLog(Log.SENDED, cargador, ProtocolLibrary.Finalize, SubjectLibrary.End , "End");	
				}					
				
			}
		/**
		 * Realizar esta llamada solo cuando un drone finalize porque a llegado a la meta
		 * 
		 * @author Jahiel
		 */
		
			
			}catch(JSONException e){
				e.printStackTrace();
			}
		
	}
}
