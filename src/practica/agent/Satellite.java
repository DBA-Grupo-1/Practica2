package practica.agent;


import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import edu.emory.mathcs.backport.java.util.concurrent.PriorityBlockingQueue;
import es.upv.dsic.gti_ia.architecture.RefuseException;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import practica.util.GPSLocation;
import practica.util.ImgMapConverter;
import practica.util.Map;
import practica.util.MessageQueue;
import practica.util.ProtocolLibrary;
import practica.util.SharedMap;
import practica.util.Visualizer;
import practica.util.DroneStatus;

public class Satellite extends SingleAgent {
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
	
	/**
	 * Constructor
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
	 * Manda un mensaje.
	 * @author Dani
	 * @author Jahiel
	 * @param typeMessage 		performativa del mensaje.
	 * @param id				destinatario del mensaje.
	 * @param protocol			protocolo de comunicación del mensaje.
	 * @param replyWith			reply-with del mensaje. Será null si se usa in-reply-to.
	 * @param inReplyTo			in-reply-to del mensaje. Será null si se usa reply-with.
	 * @param conversationId	id de la conversación del mensaje,
	 * @param datas				content del mensaje.
	 */
	private void send(int typeMessage, AgentID id, String protocol, String replyWith, String inReplyTo, String conversationId, JSONObject datas) {

		ACLMessage msg = new ACLMessage(typeMessage);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		
		if (/*replyWith.isEmpty() ||*/ replyWith == null) //Doble comprobación, nunca está de más.
			msg.setReplyWith("");
		else
			msg.setProtocol(protocol);
		msg.setInReplyTo(replyWith);
		
		if (/*inReplyTo.isEmpty() ||*/ inReplyTo == null) //Doble comprobación, nunca está de más.
			msg.setInReplyTo("");
		else
			msg.setProtocol(protocol);
		msg.setInReplyTo(inReplyTo);
		
		if (/*conversationId.isEmpty() ||*/ conversationId == null) //Doble comprobación, nunca está de más.
			msg.setConversationId("");
		else
			msg.setProtocol(protocol);
		msg.setInReplyTo(conversationId);
		
		if (datas != null)
			msg.setContent(datas.toString());
		else
			msg.setContent("");
		this.send(msg);
	}

	/**
	 * Se crea un mensaje del tipo FAIL para informar de algun fallo al agente dron.
	 * FIXME otros autores añadiros.
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
	 * FIXME autores añadiros
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
	 * FIXME autores añadiros.
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
	 * @author Dani
	 * FIXME: otros autores añadiros.
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
			mapSeguimiento.setValue(x, y, Map.VISITADO, droneID);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}


	/**
	 * Hebra de ejecución del satélite. 
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
				switch (proccesingMessage.getProtocol()){
				
				case ProtocolLibrary.Registration : onRegister(proccesingMessage); break;
				case ProtocolLibrary.Information : onInformation (proccesingMessage); break;
				case ProtocolLibrary.DroneMove : onDroneMoved(proccesingMessage); break;
				case ProtocolLibrary.Subscribe : onSubscribe(proccesingMessage); break;
				case ProtocolLibrary.Finalize : onFinalize(proccesingMessage); break;
				case ProtocolLibrary.Reload : onReload(proccesingMessage); break;
				}		
			}
		}
	}

	

	@Override
	/**
	 * FIXME autores añdiros.
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

			exit = evalueDecision(msg);
			send(ACLMessage.INFORM, msg.getSender(), "IMoved", null, msg.getReplyWith(), msg.getConversationId(), null);				
			
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
	//TODO Implementation
	public void onSubscribe (ACLMessage msg){
	}
	
	//TODO Implementation
	public void onReload (ACLMessage msg){
		
	}
	
	
	/**
	 * TODO Implementation
	 * @see onStatusQueried para cuando te pidan el status. Si el que implementa esto lo usa que no sea perro y me ponga como autor >_<
	 * @param msg
	 */
	
	public void onInformation (ACLMessage msg){
		
	}
	
	//TODO Implementation
	
	public void onFinalize (ACLMessage msg){
		
	}
}
