package practica.agent;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
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

import practica.lib.ErrorLibrary;
import practica.lib.ProtocolLibrary;
import practica.lib.SubjectLibrary;
import practica.map.Map;
import practica.map.SharedMap;
import practica.util.GPSLocation;
import practica.util.ImgMapConverter;
import practica.util.MessageQueue;
import practica.util.Visualizer;
import practica.util.DroneStatus;

public class Satellite extends BaseAgent {
	private SharedMap mapOriginal;						//Mapa original a partir del cual transcurre todo.
	private SharedMap mapSeguimiento;						//Mapa que se va actualizando a medida que los drones se muevan.
	private double goalPosX;						//Coordenada X del objetivo.
	private double goalPosY;						//Cordenada Y del objetivo.
	private AgentID [] drones;						//Array que contiene las IDs de los drones.
	private DroneStatus [] droneStuses;				//Array que contiene los estados de los drones.
	private int maxDrones;							//Número máximo de drones que acepta el satélite.
	private int connectedDrones;					//Número de drones conectados.
	private LinkedBlockingQueue messageQueue;		//Cola de mensajes
	private Visualizer visualizer;					//Visualizador.
	private boolean usingVisualizer;				//Variable para controlar si se está usando el visualizador.
	private boolean exit;							//Variable para controlar la terminación de la ejecución del satélite.
	private static List<Integer> posXIniciales;
	private int conversationCounter=0;
	private HashMap<String, HashMap<String, String>> subscriptions;  // <tipoSubcripcion, < IDAgent, ID-combersation>>
	
	
	/**
	 * Constructor
	 * @author Jahiel
	 * @author Dani
	 * FIXME otros autores añadiros.
	 * @param sat ID del satélite.
	 * @param mapa mapa que se usará.
	 * @param maxDrones número máximo de drones que aceptará el satélite.
	 * @throws Exception
	 */
	public Satellite(AgentID sat, Map map, int maxDrones) throws Exception{
		//Inicialización de atributos.
		super(sat);
		exit = false;
		mapOriginal = new SharedMap(map);
		mapSeguimiento = new SharedMap(map);
		drones = new AgentID [maxDrones];
		droneStuses = new DroneStatus [maxDrones];
		this.maxDrones = maxDrones;
		connectedDrones = 0;	
		//TODO cambiar a PriorityBlockingQueue.
		messageQueue = new LinkedBlockingQueue();
		subscriptions =new HashMap<String, HashMap<String, String>>();
		subscriptions.put("DroneReachedGoal", new HashMap<String, String>());
		subscriptions.put("AllMovements", new HashMap<String, String>());
		subscriptions.put("ConflictiveSections", new HashMap<String, String>());
		
		
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
	 * @param sat 			ID del satélite.
	 * @param mapa 			mapa que se usará.
	 * @param maxDrones 	número máximo de drones que aceptará el satélite.
	 * @param v 			visualizador.
	 * @throws Exception
	 */
	public Satellite(AgentID sat, Map mapa, int maxDrones, Visualizer v) throws Exception{
		this (sat, mapa, maxDrones);		
		visualizer = v;
		usingVisualizer = true;
	}
	
	/**
	 * Hebra de recepción de mensajes
	 * @author Dani
	 * @param msg mensaje recibido.
	 */
	public void onMessage (ACLMessage msg){	
		try {
			System.out.println(msg.getPerformative() + " " + msg.getContent());
			messageQueue.put(msg);
			System.out.println("mensaje recibido!");	
		} catch (InterruptedException e) {
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
	 * “radar”:[0,0,0,0,0,0,0,1,1]}
	 * @author Jahiel
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
			decision = ob.getInt("decision");
		} catch (JSONException e) {
			//Cambio de P3: si el JSON no está creado el satélite devuelve NOT_UNDERSTOOD en lugar de FAILURE, ya que no es culpa del satélite.
			send(ACLMessage.NOT_UNDERSTOOD, msg.getSender(), msg.getProtocol(), null, msg.getInReplyTo(), msg.getConversationId(), null);
			return true;
		}

		switch (decision) {
			case Drone.ESTE: // Este
				x = gps.getPositionX() + 1;
				y = gps.getPositionY();
				break;
	
			case Drone.SUR: // Sur
				x = gps.getPositionX();
				y = gps.getPositionY() + 1;
				break;
	
			case Drone.OESTE: // Oeste
				x = gps.getPositionX() - 1;
				y = gps.getPositionY();
				break;
	
			case Drone.NORTE: // Norte
				x = gps.getPositionX();
				y = gps.getPositionY() - 1;
				break;
			case Drone.END_SUCCESS:
				return true;
			case Drone.END_FAIL:
				return true;
			default: // Fin, No me gusta, prefiero un case para el fin y en el default sea un caso de error pero no me deja poner -1 en el case.
				/**
				 * @TODOauthor Dani
				 * TODO mandar error.
				 */
				//sendError("IMoved", droneID, "Error al actualizar el mapa");
				break;
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
		
		while (!exit) {
			//Si la cola de mensajes no está vacía, saca un elemento y lo procesa.
			if (!messageQueue.isEmpty()){				
				try {
					proccesingMessage = (ACLMessage) messageQueue.take();
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
					case ProtocolLibrary.Subscribe : onSubscribe(proccesingMessage); break;
					case ProtocolLibrary.Finalize : onFinalize(proccesingMessage); break;
					case ProtocolLibrary.Reload : onReload(proccesingMessage); break;
					default:
						throw new NotUnderstoodException("");
					}		
			
				}catch(FIPAException fe){
					sendError(fe, proccesingMessage);
				}	
			}
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
	public Map getMapSeguimiento() {
		return mapSeguimiento;
	}
	
	//TODO Implementation
	//Esto es un placeholder y el código siguiente deberá de ser borrado/comentado por quien implemente el protocolo de comunicación inicial
	public void onRegister (ACLMessage msg){
		try{
			System.out.println("REcibido registro de: " + msg.getSender().toString());
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
					send(ACLMessage.INFORM, drones[i], "Register", null, msg.getReplyWith(), msg.getConversationId(), content);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
			ACLMessage error = new RefuseException("Error en el registro").getACLMessage();
			for(int i=0; i<connectedDrones; i++)
				error.addReceiver(drones[i]);
			this.send(error);
			throw new RuntimeException("Error en el registro (Satelite)");
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
	public void onStatusQueried(ACLMessage msg) {
		/**
		 * @TODOauthor Dani
		 * TODO Esto es completamente temporal y tendrá que ser eliminado cuando se implemente el protocolo inicial.
		 */
		if (connectedDrones == 0){
			onRegister(msg);
		}
		//Si hay visualizador, manda actualizar su mapa.
		if (usingVisualizer){
			visualizer.updateMap();
			//Si no está pulsado "Find Target" y está pulsado "Think Once" hay que habilitar "Think Once". Si "Find Target" está pulsado, no se debe de hacer nada.
			if (visualizer.isBtnFindTargetEnabled() && !visualizer.isBtnThinkOnceEnabled())
				visualizer.enableThinkOnce();
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
				if (visualizer.isBtnThinkOnceEnabled())
					while (visualizer.isBtnThinkOnceEnabled()){
						System.out.print("");//Necesario para volver a comprobar la condición del while.
					}

			JSONObject content = null;
			try {
				content = new JSONObject(msg.getContent());
			} catch (JSONException e) {
				e.printStackTrace();
				//TODO enviar error
				//sendError("IMoved", msg.getSender(),"Error al crear objeto JSON con la decision");
			}
			
			//@author Jahiel
			AgentID droneID = msg.getSender();  //obtenemos la posicion actual
			DroneStatus droneStatus = findStatus(droneID);
			GPSLocation currentPosition = droneStatus.getLocation();
			
			exit = evalueDecision(msg);
			send(ACLMessage.INFORM, msg.getSender(), "IMoved", null, msg.getReplyWith(), msg.getConversationId(), null);				
			
			GPSLocation newPosition = droneStatus.getLocation();
			
			sendInformSubscribeAllMovement(msg, currentPosition, newPosition);
			
			
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
			throw new NotUnderstoodException("");
		}
		
		try {
			if(subscriptions.containsKey(content.get("Subject"))){
				if(subscriptions.get(content.get("Subject")).containsKey(msg.getSender().toString())){
					throw new RefuseException(ErrorLibrary.AlreadySubscribed);
				}else if(drones.length != 6){
					throw new RefuseException(ErrorLibrary.MissingAgents);
				}else{
					subscriptions.get(content.get("Subject")).put(msg.getSender().toString(), msg.getConversationId());
					send(ACLMessage.ACCEPT_PROPOSAL, msg.getSender(), "Subcribe", null, "confirmation", msg.getConversationId(), content);	
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
												  GPSLocation newPosition){
		JSONObject contentSub = new JSONObject();
		
		try {
			contentSub.put("Subject", "AllMovement");
			contentSub.put("ID-Drone", msg.getSender().toString());
			int[] posPr = {currentPosition.getPositionX(), currentPosition.getPositionY()};
			contentSub.put("PreviousPosition", new JSONArray(posPr));
			int[] posNew = {newPosition.getPositionX(), newPosition.getPositionY()};
			contentSub.put("CurrentPosition", new JSONArray(posNew));
			JSONObject ob = new JSONObject(msg.getContent());
			contentSub.put("Decision", ob.getInt("decision"));
			
		} catch (JSONException e) {
			// no sudece nunca
			e.printStackTrace();
		}
		
		for(String name: this.subscriptions.get("AllMovement").keySet()){
			send(ACLMessage.INFORM, new AgentID(name), "Subcribe", null, null,  this.subscriptions.get("AllMovement").get(name), contentSub);
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
		
		try {
			contentSub.put("Subject", "DroneReachedGoal");
			contentSub.put("ID-Drone", msg.getSender().toString());
			
		} catch (JSONException e) {
			// no sudece nunca
			e.printStackTrace();
		}
		
		for(String name: this.subscriptions.get("DroneReachedGoal").keySet()){
			send(ACLMessage.INFORM, new AgentID(name), "Subcribe", null, null,  this.subscriptions.get("DroneReachedGoal").get(name), contentSub);
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
				AgentID rechargedDrone = new AgentID (content.getString("DroneID"));
				DroneStatus rechargedDroneStatus = findStatus(rechargedDrone);
				//Lo actualizo
				int rechargedAmmount = content.getInt("AmmountGiven");
				rechargedDroneStatus.setBattery(rechargedDroneStatus.getBattery() + rechargedAmmount);
			} catch (JSONException e) {
				System.out.println("onReload - Error en JSON");
				e.printStackTrace();
			}
		}
		else throw new RuntimeException("onReload - Performativa no inform.");
	}

	/**
	 * TODO Implementation
	 * 
	 * @see onStatusQueried para cuando te pidan el status. Si el que implementa esto lo usa que no sea perro y me ponga como autor >_<
	 * @param msg
	 */
	
	public void onInformation (ACLMessage msg) throws JSONException{
		JSONObject content = new JSONObject(msg.getContent());
		String subject = content.getString("Subject");
		
		JSONObject res = new JSONObject();
		
		
		try{
			switch(subject){
				case SubjectLibrary.MapOriginal:
					try{
						res.put("Subject", "MapOriginal");
						res.put("Height", mapOriginal.getHeigh());
						res.put("Width",mapOriginal.getWidth());
						JSONArray aux = new JSONArray();
						if(mapOriginal.getHeigh()==0||mapOriginal.getWidth()==0){
							throw new FIPAException(msg);
						}
						for(int i=0;i<mapOriginal.getHeigh();i++){
							for(int j=0;i<mapOriginal.getWidth();j++){
								if(mapOriginal.getValue(i,j)<-1||mapOriginal.getValue(i, j)>5){
									throw new FIPAException(msg);
								}
								aux.put(mapOriginal.getValue(i, j));
							}
						}
						res.put("Values", aux);
					}catch(RuntimeException e){
						//es = treatRuntimeError(msg, e);
					}
					send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);
						
						
					
					break;
				case SubjectLibrary.MapGlobal:
					try{
						res.put("Subject", "MapSiguimiento");
						res.put("Height", mapSeguimiento.getHeigh());
						res.put("Width",mapSeguimiento.getWidth());
						JSONArray aux = new JSONArray();
						if(mapSeguimiento.getHeigh()==0||mapSeguimiento.getWidth()==0){
							throw new FIPAException(msg);
						}
						for(int i=0;i<mapSeguimiento.getHeigh();i++){
							for(int j=0;i<mapSeguimiento.getWidth();j++){
								if(mapSeguimiento.getValue(i,j)<-1||mapSeguimiento.getValue(i, j)>5){
									throw new FIPAException(msg);
								}
								aux.put(mapSeguimiento.getValue(i,j));
							}
						}
						res.put("Values", aux);
					}catch(RuntimeException e){
						//es = treatRuntimeError(msg, e);
					}
					send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);
					break;
				case SubjectLibrary.IdAgent:
					try{
						
						res.put("Subject","IdAgent");
						AgentID id = msg.getSender();
						DroneStatus status = findStatus(id);
						if(status==null){
							throw new FIPAException(msg);
						}
						else{
							res.put("name",status.getName());
							res.put("ID",id);
						}
					}catch(RuntimeException e){
						//es = treatRuntimeError(msg, e);
					}
					send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);
					break;
				case SubjectLibrary.Position:
					try{
						
						res.put("Subject","Position");
						JSONObject aux = new JSONObject();
						AgentID id = msg.getSender();
						if(id==null){
							throw new FIPAException(msg);
						}
						DroneStatus status = findStatus(id);
						GPSLocation n= status.getLocation();
						aux.put("x",n.getPositionX());
						aux.put("y", n.getPositionY());
						res.put("Position", aux);
					}catch(RuntimeException e){
						//es = treatRuntimeError(msg, e);
					}
					send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);
					break;
				case SubjectLibrary.GoalDistance:
						try{
							res.put("Subject","GoalDistance");
							
							AgentID id= msg.getSender();
							if(id==null){
								throw new FIPAException(msg);
							}
							DroneStatus status= findStatus(id);
							GPSLocation n = status.getLocation();
							double x=n.getPositionX();
							double y=n.getPositionY();
							double dist= Math.sqrt(Math.pow((goalPosX-x),2)+Math.pow((goalPosY-y),2));
							res.put("Distance", dist);
						}catch(RuntimeException e){
							//es = treatRuntimeError(msg, e);
						}
						send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);
					break;
				case SubjectLibrary.DroneBattery:
					try{
						res.put("Subject","DroneBattery");
						AgentID id = msg.getSender();
						if(id==null){
							throw new FIPAException(msg);
						}
						DroneStatus status = findStatus(id);
						int bat=status.getBattery();
						if(bat<0||bat>75){
							throw new FIPAException(msg);
						}
						res.put("Battery",bat);
					}catch(RuntimeException e){
						//es = treatRuntimeError(msg, e);
					}
					send(ACLMessage.INFORM,msg.getSender(),ProtocolLibrary.Information,"default",null,buildConversationId(), res);	
						
					break;
				default: 
					sendError(new NotUnderstoodException("Subject no encontrado"), msg);
					break;
					
					
			}
		}catch(FIPAException fe){
			sendError(fe, msg);
		}catch(IllegalArgumentException e){
			//res = treatMessageError(msg, e);
		}catch(RuntimeException e){
			//res = treatRuntimeError(msg, e);
		}
		
	}
	
	//TODO Implementation
	
	/**
	 * TODO Implementación
	 * 
	 * @author Jahiel Me pongo para que el que haga este mensaje me informe cuando este para poner la linea en su sitio
	 * @param msg
	 */
	public void onFinalize (ACLMessage msg){
		
		
		/**
		 * Realizar esta llamada solo cuando un drone finalize porque a llegado a la meta
		 * 
		 * @author Jahiel
		 */
		
		sendInformSubscribeFinalize(msg);
	}
}
