package practica.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import practica.util.ErrorLibrary;
import practica.util.Map;
import practica.util.Pair;
import practica.util.ProtocolLibrary;
import practica.util.SubjectLibrary;
import practica.util.Trace;
import es.upv.dsic.gti_ia.architecture.FIPAException;
import es.upv.dsic.gti_ia.architecture.NotUnderstoodException;
import es.upv.dsic.gti_ia.architecture.RefuseException;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;
import es.upv.dsic.gti_ia.core.ACLMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jaxen.function.StartsWithFunction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Funcionamiento del marco de trabajo drone.
 * 
 * Comportamiento ante mensajes (no respuestas):
 *   La idea principal es usar las funciones on... para modelar el comportamiento del drone ante la llegada de uno de esos mensaje.
 *   Para la contestacion basta con devolver el valor pedido (si existe).
 *   En caso de querer mandar un mensaje de error devolver una excepcion.
 *   (Para los implementadores del protocolo)Debe haber una relacionuno-a-uno entre tipo de mensaje de error y tipo de excepcion.
 * 
 *   Para cambiar el comportamiento del drone solo tenemos que sobreescribir alguna de las siguientes funciones:
 *     onDroneChargedInform
 *     onDroneReachedGoalInform
 *     onTraceQueried
 *     onBatteryQueried
 *     
 *   No es necesario comprobar la cola de mensajes, ni cojer el mensaje, ni construir el mensaje de respuesta, ni mandarlo. Solo el comportamiento.
 *   
 * 
 * Think:
 *   Ahora think tiene un comportamiento mas modular. Esta compuesto por cinco comportamientos y una comprobacion de final fallido.
 *   Podemos sobreescribir cualquiera de los comportamientos para cambiar el modo de pensar de nuestro drone.
 *   Para mas informacion sobre los comportamientos y el orden de ejecuion ver la funcion {@link #checkBehaviours() checkBehaviours}.
 *   
 * Modo StandBy:
 *   Ahora podemos detener la ejecucion (la parte del think) de nuestro drone sin complicaciones debidas a la sincronizacion. Es util para evitar que el drone no avance hasta que se procese un mensaje importante
 *    o cuando queremos que espere a un evento determinado. 
 *
 */
public class Drone extends SingleAgent {
	private final int ESTADOREQUEST = 0, ESTADOINFORM = 1;
	private final int LIMIT_MOVEMENTS;
	private boolean exit;
	protected boolean goal;
	private int estado;
	protected int posX;
	protected int posY;
	protected float angle;
	protected float distance;
	protected int[] surroundings;
	protected Map droneMap;
	protected float distanceMin;
	protected int counterStop;
	protected int battery;
	/** Decisión de pedir batería */
	public static final int ASK_FOR_BATTERY = 4;
	/** Decision de mover al norte */
	public static final int NORTE = 3;
	/** Decision de mover al oeste */
	public static final int OESTE = 2;
	/** Decision de mover al sur */
	public static final int SUR = 1;
	/** Decision de mover al este */
	public static final int ESTE = 0;
	/** Decision de terminar la ejecucion.
	 *	@deprecated Usar END_SUCCESS o END_FAIL
	 */
	public static final int END = -1;
	/** Decision de terminar la ejecución habiendo conseguido el objetivo */
	public static final int END_SUCCESS = -1;
	/** Decision de terminar la ejecución sin haber conseguido el objetivo */
	public static final int END_FAIL = -2;
	/** No se ha tomado decision. Usada por los comportamientos para pasar la toma de decisiones al siguiente comportamiento. */
	public static final int NO_DEC = -3;
	/** Decision de reiniciar el proceso de toma de decision. */
	public static final int RETHINK = -4;
	
	private AgentID sateliteID;
	private AgentID chargerID;
	
	protected boolean dodging = false;
	protected int betterMoveBeforeDodging = -1;
	private int standBy;
	protected BlockingQueue<ACLMessage> answerQueue;
	protected BlockingQueue<ACLMessage> requestQueue;
	protected Thread dispatcher;
	private AgentID[] teammates;
	private int conversationCounter = 0;
	
	private HashMap<String, String> idsCombersationSubscribe;   // {nombreSubscripcion, id-combersation}
	private HashMap<String, String> subscribers;				// {ID_Agente, id-combersation}

	/**
	 * Constructor del Drone.
	 * @author Jahiel
	 * @param aid Identificador del drone.
	 * @param mapWidth Ancho del mapa a explorar.
	 * @param mapHeight Alto del mapa a explorar.
	 * @param sateliteID Identificador del agente satelite.
	 * @param charger Identificador del agente cargador.
	 * @throws Exception
	 */
	public Drone(AgentID aid, int mapWidth, int mapHeight, AgentID sateliteID, AgentID charger) throws Exception {
		super(aid);
		surroundings = new int[9];
		droneMap = new Map(mapWidth, mapHeight);
		//Ahora el limite depende del tamaño del mapa
		LIMIT_MOVEMENTS = mapWidth + mapHeight;
		
		this.sateliteID = sateliteID;
		chargerID = charger;
		
		posX = 0;
		posY = 0;
		distanceMin = 999999;
		counterStop = 0;
		battery=75;
		
		standBy = 0;
		answerQueue = new LinkedBlockingQueue<ACLMessage>();
		requestQueue = new LinkedBlockingQueue<ACLMessage>();
		
		idsCombersationSubscribe = new HashMap<String, String>();
		subscribers = new HashMap<String, String>();
	}
	
	/**
	 * Activa el modo StandBy.
	 * 
	 * @author Alberto
	 * 
	 * @see Drone#leaveStandBy()
	 */
	protected final void enterStandBy(){
		synchronized(this){
			standBy++;
		}
	}
	
	/**
	 * Desactiva el modo StandBy. Si la hebra pensador estaba esperando la libera.
	 * 
	 * @author Alberto
	 * 
	 * @see Drone#enterStandBy()
	 */
	protected final void leaveStandBy(){
		synchronized(this){
			if(standBy > 0)
				standBy--;
			if(standBy == 0)
				notify();
		}
	}
	
	/**
	 * Si el modo StandBy esta activado la hebra espera hasta que se desactive. En caso contrario continua su ejecución.
	 * 
	 * @throws InterruptedException Si la hebra es interrumpida mientras espera.
	 * @author Alberto
	 * 
	 * @see Drone#enterStandBy()
	 * @see Drone#leaveStandBy()
	 */
	protected final void waitIfStandBy() throws InterruptedException{
		synchronized(this){
			if(standBy > 0)
				wait();
		}
	}
	
	
	@Override
	protected void execute(){
		startDispatcher();
	
		register();
		
		subscribe();
		
		int decision;
		
		do{
			getStatus();
			
			decision = think();
			System.out.println(""+decision);
			//Por si las moscas
			if(decision != NO_DEC){
				sendDecision(decision);
				updateTrace(decision);
				postUpdateTrace();
			}
		}while(decision != END_FAIL && decision != END_SUCCESS);
	}
	
	/**
	 * Recibe el mensaje y lo encola. La decision de a que cola mandarlo depende enteramente del protocolo del mensaje (campo protocol).<br>
	 * Estado actual:<br>
	 * answerQueue -> SendMeMyStatus, IMoved<br>
	 * requestQueue -> BatteryQuery, TraceQuery, DroneReachedGoal, DroneRecharged, BatteryRequested
	 * @author Jahiel
	 * @param msg Mensaje recibido
	 */
	@Override
	public void onMessage(ACLMessage msg){
		JSONObject content;
		String subject = null;
		
		try {
			content = new JSONObject(msg.getContent());
			subject = content.getString("Subject");
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		BlockingQueue<ACLMessage> queue = null;
		
		switch(subject){
		case SubjectLibrary.Status:
		case SubjectLibrary.IMoved:
		case SubjectLibrary.Register:
			queue = answerQueue;
			break;
		case SubjectLibrary.BatteryLeft:
		case SubjectLibrary.Trace:
		case SubjectLibrary.Steps:
			if(msg.getPerformativeInt() == ACLMessage.QUERY_REF){
				queue = requestQueue;
			}else{
				queue = answerQueue;
			}
			break;
		case SubjectLibrary.DroneRecharged:
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.YourMovements:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
		case SubjectLibrary.BatteryRequest:
			
			// falta meter la confirmacion en la answerQueue
			queue = requestQueue;
			break;
			
		default:
			sendError(new NotUnderstoodException("Subject no encontrado"), msg);
			break;
		}
		
		if(queue != null){
			try {
				queue.put(msg);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Lanza el despachador.
	 */
	protected void startDispatcher() {
		dispatcher = new Thread(){
			@Override
			public void run(){
				ACLMessage msg;
				try{
					do{
						msg = requestQueue.take();
					}while(dispatch(msg));
				}catch(InterruptedException e){
					
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		
		dispatcher.start();
	}

	/**
	 * Envia al satelite la decision tomada en think()
	 * @param decision Decision tomada
	 */
	protected void sendDecision(int decision) {
		JSONObject data = new JSONObject();
		
		try {
			data.put("decision", decision);
			data.put("Subject", SubjectLibrary.IMoved);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.DroneMove, "default", null, buildConversationId(), data);
		
		try {
			answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Construye un nuevo campo conversationID a partir del id del agente y el contador de conversacion
	 * 
	 * @author Alberto
	 * @return Conversation id formado segun el patron acordado
	 */
	private String buildConversationId() {
		String res;
		synchronized(this){
			res = this.getAid().toString()+"#"+conversationCounter;
			conversationCounter++;
		}
		return res;
	}

	/**
	 * Se comunica con el satelite para recibir su status y actualizarlo.
	 * 
	 * @author Alberto
	 * @author Jahiel
	 */
	protected void getStatus() {
		ACLMessage msg=null;
		JSONObject contenido = null;
		int X = posX, Y = posY;  // posicion previa 
		
		try {
			contenido = new JSONObject();
			contenido.put("Subject", SubjectLibrary.Status);
		} catch (JSONException ex) {
			ex.printStackTrace();
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		
		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), contenido);
		
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		updateStatus(msg);
		
		JSONObject content = new JSONObject();
		
		try {
			content.put("type", SubjectLibrary.YourMovements);
			int[] previousPosition = {X, Y};
			content.put("PreviousPosition", new JSONArray(previousPosition));
			int[] position = {posX, posY};
			content.put("CurrentPosition", new JSONArray(position));
		} catch (JSONException e) {
			// nunca sucede porque las claves no son vacias
			e.printStackTrace();
		}
		
		for(String name: subscribers.keySet()){
	
			send(ACLMessage.INFORM, new AgentID(name), "Subscribe", null, null,
					this.subscribers.get(name), content); 			// Con el nombre del agente sacamos la ID-combersation
		}
		
		
	}

	/**
	 * Actualiza el status del agente en base al mensaje recibido.
	 * @param msg Mensaje recibido del satelite
	 * 
	 * @author Ismael
	 */
	protected void updateStatus(ACLMessage msg) {
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
			droneMap.setValue(posX,posY,Map.VISITADO);
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Metodo llamado tras la actualizacion de la traza. Ideal para comprobaciones de la traza y del rendimiento del drone.
	 */
	protected void postUpdateTrace() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Actualiza la traza del drone con la nueva decision.
	 * @param decision Decision tomada en think
	 */
	protected void updateTrace(int decision) {
		// TODO Auto-generated method stub
		
	}
	
	
	/**
	 * Pide batería al cargador.
	 * @author Dani.
	 * @param amount cantidad que se le pide al cargador.
	 */
	protected void askForBattery (int amount){
		if (battery > 0 && amount > 0 && amount <= 75){ //Comprobación extra
			JSONObject content = new JSONObject();
			try {
				//Construcción del JSON
				content.put("Subject", SubjectLibrary.BatteryRequest);
				content.put("RequestAmmount", amount);
				
				//Envío del mensaje
				send(ACLMessage.REQUEST, chargerID, ProtocolLibrary.Reload, "default", null, buildConversationId(), content);
			} catch (JSONException e){
				e.printStackTrace();
			}
		}
	}

	/**
	 * Realiza las subscripciones del drone. El drone se subscribe a:
	 * 
	 * - DroneReachedGoal: un drone a llegado al Goal. 
	 * - DroneRecharged: La recarga de otro drone. 
	 * - YourMovements: los movimientos de un drone específico. 
	 * - AllMovements: a los movimientos de todos los drones. 
	 * - ConflictiveSections: a las casillas conflictivas. 
	 * 
	 * @author Jahiel
	 */
	protected void subscribe() {
		
		subscribeDroneReachedGoal();
		subscribeDroneRecharged();
		subscribeYourMovements();
		subscribeAllMovements();
		subscribeConflictiveSections();
		
	}
	
	/**
	 * Se subscribe a la llegada de un drone al objetivo.
	 * Se te informa del drone que ha finalizado exitosamente.
	 * 
	 * @author Jahiel
	 */
	private void subscribeDroneReachedGoal(){
		JSONObject content = new JSONObject();
		
		try {
			content.put("Subject", SubjectLibrary.DroneReachedGoal);
		} catch (JSONException e) {
			// esto nunca pasa porque la clave nunca esta vacía
			e.printStackTrace();
		}
		
		idsCombersationSubscribe.put("DroneReachedGoal", this.getAid().toString()+"#"+conversationCounter);
		
		send(ACLMessage.SUBSCRIBE, sateliteID, "Subscribe", "confirmation", null,
				buildConversationId(), content);
		
		WaitResponseSubscriber();
	}

	/**
	 * Se subscribe a cuando un drone recargue. 
	 * Se informa cuanto a recargado.
	 * 
	 * @author Jahiel
	 */
	private void subscribeDroneRecharged(){
			JSONObject content = new JSONObject();
			
			try {
				content.put("Subject", SubjectLibrary.DroneRecharged);
			} catch (JSONException e) {
				// esto nunca pasa porque la clave nunca esta vacía
				e.printStackTrace();
			}
			
			idsCombersationSubscribe.put("DroneRecharged", this.getAid().toString()+"#"+conversationCounter);
			
			send(ACLMessage.SUBSCRIBE, chargerID, "Subscribe", "confirmation", null,
					buildConversationId(), content);
			
			WaitResponseSubscriber();
	}
	
	/**
	 * Se subscribe a los movimientos que realiza un drone específico.
	 * Si el drone se mueve se te informa de su movimiento.
	 * 
	 * De momento se subscribe a TODOS los drones registrados.
	 * 
	 * @author Jahiel
	 */
	private void subscribeYourMovements(){
		JSONObject content = new JSONObject();
		
		try {
			content.put("Subject", SubjectLibrary.YourMovements);
		} catch (JSONException e) {
			// esto nunca pasa porque la clave nunca esta vacía
			e.printStackTrace();
		}

		for(int i=0; i<this.teammates.length; i++){
			idsCombersationSubscribe.put("YourMovements"+teammates[i].getLocalName(), this.getAid().toString()+"#"+conversationCounter);
			send(ACLMessage.SUBSCRIBE, teammates[i], "Subscribe", "confirmation", null,
					buildConversationId(), content);
			
			WaitResponseSubscriber();
		}
		
	}
	
	/**
	 * Se subscribe a los movimientos que realize cualquier drone. 
	 * Si cualquier drone se mueve se te informa de su movimiento.
	 * 
	 * @author Jahiel
	 */
	private void subscribeAllMovements(){
		JSONObject content = new JSONObject();
		
		try {
			content.put("Subject", SubjectLibrary.AllMovements);
		} catch (JSONException e) {
			// esto nunca pasa porque la clave nunca esta vacía
			e.printStackTrace();
		}
		
		idsCombersationSubscribe.put("AllMovements", this.getAid().toString()+"#"+conversationCounter);
		
		send(ACLMessage.SUBSCRIBE, sateliteID, "Subscribe", "confirmation", null,
				buildConversationId(), content);
		
		WaitResponseSubscriber();
	}
	
	/**
	 * Se subscribe a las casillas conflictivas. 
	 * Si se anota una nueva se te informa.
	 * 
	 * @author Jahiel
	 */
	private void subscribeConflictiveSections(){
		JSONObject content = new JSONObject();
		
		try {
			content.put("Subject", SubjectLibrary.ConflictiveSections);
		} catch (JSONException e) {
			// esto nunca pasa porque la clave nunca esta vacía
			e.printStackTrace();
		}
		
		idsCombersationSubscribe.put("ConflictiveSections", this.getAid().toString()+"#"+conversationCounter);
		
		send(ACLMessage.SUBSCRIBE, sateliteID, "Subscribe", "confirmation", null,
				buildConversationId(), content);
		
		WaitResponseSubscriber();
		
	}
	
	/**
	 * Se trata la respuesta a la petición de subscripción.
	 * 
	 * @author Jahiel
	 */
	public void WaitResponseSubscriber(){
		ACLMessage msg = new ACLMessage();
		
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException("Fallo en la respuesta de subscripcion: error al cojer la respuesta");
		}
		
		switch(msg.getPerformativeInt()){
		case ACLMessage.ACCEPT_PROPOSAL:
			break;
		case ACLMessage.REFUSE:
			JSONObject content;
			try {
				content = new JSONObject(msg.getContent());
				if(! (content.get("reason").equals("AlreadySubscribe") || content.get("reason").equals("AlreadyInGoal")
						|| content.get("reason").equals("IWontReachGoal")) )
					throw new RuntimeException("Fallo en la respuesta de subscripcion: petición denegada");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			break;
		case ACLMessage.FAILURE:
			throw new RuntimeException("Fallo en la respuesta de subscripcion: fallo en la petición de subscripción");
		
		default: 
			throw new RuntimeException("Fallo en la respuesta de subscripcion: error en la performativa");
		}
	}
	
	/**
	 * Cancela la subscripción.
	 * 
	 * @author Jahiel
	 * @param name Nombre de la subscripción a cancelar.
	 * @param IDDrone Identificador del drone al que está subscrito, si la subscripción es del tipo YourMovements
	 */
	
	public void cancelSubscribe(String name, AgentID IDDrone){
		JSONObject content = new JSONObject();
		AgentID destino;
		
		try {
			content.put("type", name);
		} catch (JSONException e) {
			// nunca se ejecuta porque la clave no es vacía
			e.printStackTrace();
		}
		
		switch(name){
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
			destino = sateliteID;
			break;
		case SubjectLibrary.DroneRecharged:
			destino = chargerID;
			break;
		case SubjectLibrary.YourMovements:
			destino = IDDrone;
			break;
		default: 
			throw new RuntimeException("Fallo en la cancelación: el nombre de la subscripción no existe");
		}
		
		send(ACLMessage.CANCEL, destino, "Subscribe", null, null,
				this.idsCombersationSubscribe.get(name), content);
	}
	
	/**
	 * Se recibe una subscripción de un agente a un drone. La subscripción se cancela si ocurre los siguientes casos:
	 * - AlreadySubscribe: ya se encuentra subscrito a este tipo de subscripción.
	 * - MissingAgent: aun no está todos los agentes registrados en el satélite.
	 * 
	 * @author Jahiel
	 * @param msg Mesaje de subscripción recibido
	 */
	public void newSubscription(ACLMessage msg)throws RefuseException{
		JSONObject content = new JSONObject();
	
		if(subscribers.containsKey(msg.getSender().toString()))
			throw new RefuseException(ErrorLibrary.AlreadySubscribed);
		else if(teammates.length != 6)      // Esta comprobación pienso que tambien hay que hacerla al mandar la petición.
			throw new RefuseException(ErrorLibrary.MissingAgents);
		else{
			subscribers.put(msg.getSender().toString(), msg.getConversationId().toString());
			send(ACLMessage.ACCEPT_PROPOSAL, msg.getSender(), "Subcribe", null, "confirmation", msg.getConversationId(), content);	
		}
		
	}
	
	/**
	 * Realiza el registro en el satelite.
	 * 
	 * @author Alberto
	 */
	protected void register() {
		ACLMessage msg=null;
		JSONObject data = new JSONObject();
		
		try {
			data.put("ID", this.getAid().toString());
			data.put("nombre_alumno", this.getAid().name);
			data.put("Subject", SubjectLibrary.Register);
		} catch (JSONException e) {
			e.printStackTrace();
			throw new RuntimeException("Fallo en el registro: error al crear content del mensaje de envio");
		}
		
		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Registration, "default", null, buildConversationId(), data);
		
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException("Fallo en el registro: error al cojer mensaje de la cola");
		}
		
		switch(msg.getPerformativeInt()){
		case ACLMessage.INFORM:
			try{
				JSONObject content = new JSONObject(msg.getContent());
				JSONArray idsArray = content.getJSONArray("ids");
				int n = idsArray.length();
				teammates = new AgentID[n];
				for(int i=0; i<n; i++){
					teammates[i] = new AgentID(idsArray.getString(i));
					System.out.println(teammates[i]);
				}
			}catch(JSONException e){
				e.printStackTrace();
				throw new RuntimeException("Fallo en el content de la respuesta de registro");
			}
			break;
		case ACLMessage.REFUSE:
		case ACLMessage.FAILURE:
			throw new RuntimeException("Fallo en el registro");
			
		}
	}

	/**
	 * Toma la decision de la accion a realizar.
	 * Primero comprueba si se ha alcanzado la meta, en cuyo caso se devuelve END_SUCCESS.
	 * En caso contrario realiza una actualizacion del estado llamando a preBehavioursSetUp y despues evalua los comportamientos. Si los comportamientos
	 * devuelven un valor RETHINK se ejecuta de nuevo el ciclo actualizacion-evaluacion. Se puede detener al pensador al principio del ciclo si se entra en el modo StandBy.
	 * 
	 *  En resumen
	 *  do
	 *    waitIfStandBy
	 *    preBehavioursSetUp
	 *    checkBehaviours
	 *  until !RETHINK
	 * @return Decision tomada.
	 */
	protected int think(){
		int tempDecision;
		
		if(goal)
			return END_SUCCESS;

		do{
			try {
				waitIfStandBy();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			preBehavioursSetUp();
			tempDecision = checkBehaviours();
		}while(tempDecision == RETHINK);
		
		return tempDecision;
	}
	
	/**
	 * Realiza cualquier tipo de actualizacion del estado del drone antes de comprobar los comportamientos. Si la comprobacion 
	 * de los comportamientos se ejecuta de nuevo debido a un RETHINK esta funcion se evalua de nuevo.
	 */
	protected void preBehavioursSetUp() {
		
	}

	/**
	 * Recorre todos los comportamientos del drone. Si un comportamiento devuelve una decision (!= NO_DEC) la devuelve como resultado.
	 * En caso contrario comprueba el siguiente comportamiento.
	 * El orden de comprobacion es:
	 * - checkEndCondition
	 * - criticalBehaviour
	 * - firstBehaviour
	 * - secondBehaviour
	 * - thirdBehaviour
	 * - basicBehaviour
	 * @return Decision tomada
	 */
	protected int checkBehaviours(){
		List<Pair> listaMovimientos;
		int tempDecision;
		
		listaMovimientos = getMovementList();

		tempDecision = checkEndCondition(listaMovimientos, null);
		if(tempDecision != NO_DEC)
			return tempDecision;
		
		tempDecision = criticalBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DEC)
			return tempDecision;
		
		tempDecision = firstBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DEC)
			return tempDecision;
		
		tempDecision = secondBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DEC)
			return tempDecision;
		
		tempDecision = thirdBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DEC)
			return tempDecision;
		
		return basicBehaviour(listaMovimientos, null);
	}
	
	/**
	 * Comprueba si el drone debe terminar su ejecucion. Es llamado antes de criticalBehaviour.
	 * Nota: la comprobacion de que se ha alcanzado el objetivo se comprueba en el think, separado de los comportamientos. En esta funcion se deben comprobar
	 * las condiciones de parada en otros casos.
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada. Debe ser END_FAIL o NO_DEC
	 */
	private int checkEndCondition(List<Pair> listaMovimientos, Object object) {
		// TODO Auto-generated method stub
		return NO_DEC;
	}

	/**
	 * Primer comportamiento intermedio del drone. Es el segundo en ejecutarse al recorrer los comportamientos.
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int firstBehaviour(List<Pair> listaMovimientos, Object[] args) {
		// TODO Auto-generated method stub
		return NO_DEC;
	}

	/**
	 * Segundo comportamiento intermedio del drone. Es el tercero en ejecutarse al recorrer los comportamientos.
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int secondBehaviour(List<Pair> listaMovimientos, Object[] args) {
		if(dodging){
			//Buscamos el mejor movimiento en la lista y comprobamos si es posible
			boolean betterIsPosible = false;
			for(int i=0; i<4; i++)
				if(listaMovimientos.get(i).getSecond() == betterMoveBeforeDodging)
					betterIsPosible = listaMovimientos.get(i).getThird(); 

			//Si es posible lo realizamos y salimos del modo esquivando
			if(dodging && betterIsPosible){
				dodging=false;
				System.out.println("Saliendo dodging: " + betterMoveBeforeDodging);
				return betterMoveBeforeDodging;
			}


			//Comprobamos si estamos esquivando y podemos hacer un movimiento que nos deje cerca de un obstaculo

			//Al lado de un obstaculo (en un movimiento)
			if(dodging)
				for(Pair pair: listaMovimientos){
					int move = pair.getSecond();
					if(pair.getThird() && (getCorner(move, (move+1)%4) == Map.OBSTACULO || getCorner(move, (move+3)%4) == Map.OBSTACULO))
						return move;
				}

			//Al lado de un obstaculo (en dos movimientos)
			if(dodging){
				int [] validMovs=getValidMovements();
				for(Pair pair: listaMovimientos){
					int move = pair.getSecond();
					if(pair.getThird() && (validMovs[(move+1)%4] == Map.OBSTACULO || validMovs[(move+3)%4] == Map.OBSTACULO))
						return move;
				}
			}

			return NO_DEC;
		}else{
			//Comprobamos si no podemos hacer el mejor movimiento debido a un obstaculo
			//En ese caso pasamos al modo esquivar
			int [] validMov=getValidMovements();
			if(!listaMovimientos.get(0).getThird() && validMov[listaMovimientos.get(0).getSecond()]==Map.OBSTACULO && !dodging){
				dodging=true;
				betterMoveBeforeDodging=listaMovimientos.get(0).getSecond();
				System.out.println("Entrando dodging: "+betterMoveBeforeDodging);
			}

			return NO_DEC;
		}
	}
	
	/**
	 * Tercer comportamiento intermedio del drone. Es el cuarto en ejecutarse al recorrer los comportamientos.
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int thirdBehaviour(List<Pair> listaMovimientos, Object[] args) {
		// TODO Auto-generated method stub
		return NO_DEC;
	}
	
	/**
	 * Comportamiento critico del drone. Es el primero en ejecutarse al recorrer los comportamientos.
	 * @author Dani
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int criticalBehaviour(List<Pair> listaMovimientos, Object[] args) {
		//Si no le queda batería el drone la pide y se queda en standby.
		if (battery == 0){
			askForBattery(75);
			enterStandBy();
			return ASK_FOR_BATTERY;
		}
		else 
			return NO_DEC;
	}

	/**
	 * Comportamiento básico del agente. Es el ultimo en ejecutarse. Debe devolver una decision distinta de NO_DEC.
	 * 
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada.
	 */
	protected int basicBehaviour(List<Pair> listaMovimientos, Object[] args) {
		//Si podemos hacer el mejor movimiento lo hacemos
		if(listaMovimientos.get(0).getThird()){
			return listaMovimientos.get(0).getSecond();
		}

		int second=-1, third=-1;
		//Para hallar los dos mejores movimientos posibles (si existen) recorremos el array de peor a mejor
		//Si un movimiento es posible entonces hemos encontrado uno mejor que los que encontrasemos antes
		//Desplazamos los valores encontrados antes (siempre se queda en second el mejor posible y en third el segundo mejor posible)
		for(int i=3; i>=0; i--){
			if(listaMovimientos.get(i).getThird()){
				third = second;
				second = listaMovimientos.get(i).getSecond();
			}
		}

		//Si third no existe nuestra unica posibilidad es second
		if(third==-1)
			return second;

		//Si second no existe (y por lo tanto third tampoco) entonces no tenemos movimientos
		if(second==-1)
			return END_FAIL;
		
		float distSecond=0, distThird=0;
		for(int i=0; i<4; i++){
			if(listaMovimientos.get(i).getSecond() == second)
				distSecond = listaMovimientos.get(i).getFirst();
			if(listaMovimientos.get(i).getSecond() == third)
				distThird = listaMovimientos.get(i).getFirst(); 
		}
		
		//Ahora comprobamos si existe empate entre ambos (distancias parecidas).
		//Si no hay empate nos quedamos con el segundo
		//El valor de margen de error debe ser ajustado "a mano" en caso de usar distancias.
		//En caso de usar el angulo se puede poneer un valor mejor pero los calculos son mas coñazo
		float error=1.0f;
		int decision;
		if(Math.abs(distSecond-distThird)<error && dodging && third==(second+2)%4){
			Object[] tieargs = new Integer[2];

			tieargs[0] = new Integer(second);
			tieargs[1] = new Integer(third);

			decision = tieResolution(listaMovimientos, tieargs);

			//Por si el metodo de desempate no ha dado resultados
			if(decision == NO_DEC)
				decision = second;
		}else{
			decision = second;
		}

		return decision;
	}


	/**
	 * Resuelve empates entre movimientos
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param tieargs Argumentos adicionales
	 * @return Decision tomada o NO_DEC si no ha podido resolver el empate
	 */
	protected int tieResolution(List<Pair> listaMovimientos, Object[] tieargs) {
		// TODO Auto-generated method stub
		return NO_DEC;
	}

	/**
	 * Construye una lista ordenada con los movimientos. Primero llama a freeSquaresConditions para tener las condiciones de movimiento libre.
	 * A continuacion calcula las distancias de cada movimiento y construye una lista de movimientos. por ultimo llama a sortMovements para ordenar y devuelve el resultado.
	 * 
	 * Notas para desarrollo drone:
	 * - Si se desea cambiar la condicion de movimiento libre sobreescribir la funcion freeSquaresConditions.
	 * - Si se desea cambiar el proceso de ordenacion sobreescribir la funcion sortMovements.
	 *  
	 * @return List de Movement ordenado.
	 * 
	 * @see Drone#freeSquaresConditions()
	 * @see Drone#sortMovements(List<Pair>)
	 */
	protected List<Pair> getMovementList() {

		ArrayList<Pair> mispares=new ArrayList<Pair>();
		boolean[] basicond;

		double posiOX=0,posiOY=0;
		float calculoDist=0;
		
		posiOX= (posX + (Math.cos(angle) * distance));
		posiOY= (posY + (Math.sin(angle)*distance));

		basicond = freeSquaresConditions();
		
		
		//Creamos el array con todos los movimientos, incluyendo la distancia al objetivo, el movimiento en si, y si es valido o no
		calculoDist= (float) Math.sqrt(Math.pow((posiOX-(posX+1)),2)+Math.pow((posiOY-posY), 2));
		mispares.add(new Pair(calculoDist,ESTE,basicond[ESTE]));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY+1)), 2));
		mispares.add(new Pair(calculoDist,SUR,basicond[SUR]));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-(posX-1)),2)+Math.pow((posiOY-posY), 2));
		mispares.add(new Pair(calculoDist,OESTE,basicond[OESTE]));
		
		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY-1)), 2));
		mispares.add(new Pair(calculoDist,NORTE,basicond[NORTE]));
	
		return sortMovements(mispares);
	}
	
	/**
	 * Evalua las condiciones de movimiento libre para los cuatro movimientos.
	 * @return Array con cuatro booleanos. Los campos corresponden a la condicion de movimiento libre de ESTE, SUR, OESTE y NORTE (en ese orden.
	 */
	protected boolean[] freeSquaresConditions(){
		int[] validSqr = getValidSquares();
		boolean[] basicond=new boolean[4];
		

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
		
		return basicond;
	}
	
	/**
	 * Ordena una lista de movimientos
	 * 
	 * @param lista List de Movement a ordenar.
	 * @return List de Movement ordenado.
	 */
	protected List<Pair> sortMovements(List<Pair> lista){
		List<Pair> ordenados=new ArrayList<Pair>(lista);
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
		
		return ordenados;
	}

	/**
	 * Comprueba el campo "subject" dentro del Content y llama a la funcion correspondiente de ese protocolo.
	 * Si el "subject" no esta entre los aceptados envia un mensaje NOT_UNDERSTOOD
	 * 
	 * @author Jahiel
	 * @author Dani
	 * @param msg Mensaje a analizar
	 * @return True si el dispatcher debe continuar su ejecucion. False en caso contrario.
	 * @throws JSONException 
	 */
	protected boolean dispatch(ACLMessage msg) throws JSONException{
		JSONObject content = new JSONObject(msg.getContent());
		String subject = content.getString("Subject");
		boolean res = true;
		JSONObject resp= new JSONObject();
		
		
		try{
			switch(subject){
			case SubjectLibrary.BatteryLeft:
				int battery2 = onBatteryQueried(msg);
				if(battery2 < 0|| battery2 > 75){
					resp.put("error","error en la peticion de bateria");
					send(ACLMessage.INFORM, protocol, msg.getSender(), resp);
				}
				else{
					resp.put("battery",battery2);
					send(ACLMessage.INFORM, protocol, msg.getSender(), resp);
				}
				//TODO enviar bateria
				break;
			case SubjectLibrary.Trace:
				Trace trace = onTraceQueried(msg);
				resp.put("trace", trace);
				send(ACLMessage.INFORM, protocol, msg.getSender(), resp);
				//TODO enviar traza
				break;
			case SubjectLibrary.Steps:
				Trace trc = onTraceQueried(msg);
				int valor= trc.size();
				if(valor < 0){
					resp.put("error", "numero de valor erroneo");
					send(ACLMessage.REFUSE, protocol, msg.getSender(),resp);
				}
				else{
					resp.put("steps",valor);
					send(ACLMessage.INFORM, protocol, msg.getSender(), resp);
				}
				break;
			case SubjectLibrary.DroneReachedGoal:
				onDroneReachedGoalInform(msg);
				break;
			case SubjectLibrary.DroneRecharged:
				onDroneChargedInform(msg);
				break;
			case SubjectLibrary.YourMovements:
				if(msg.getPerformativeInt() == ACLMessage.SUBSCRIBE)
					newSubscription(msg);
				else
					onYourMovementsInform(msg);
				break;
			case SubjectLibrary.AllMovements:
				onEveryOneMovementsInform(msg);
				break;
			case SubjectLibrary.ConflictiveSections:
				onConflictiveSectionsInform(msg);
				break;
			case SubjectLibrary.BatteryRequest:
				onBatteryReceived(msg);
				break;
				
			default: 
				sendError(new NotUnderstoodException("Subject no encontrado"), msg);
				break;
			}
		}catch(FIPAException fe){
			sendError(fe, msg);
		}catch(IllegalArgumentException e){
			res = treatMessageError(msg, e);
		}catch(RuntimeException e){
			res = treatRuntimeError(msg, e);
		}
		
		return res;
	}
	
	/**
	 * Recepción de la respuesta del cargador a la petición de una recarga.
	 * @author Dani
	 * @param msg mensaje a analizar.
	 * 
	 */
	private void onBatteryReceived(ACLMessage msg) {
		if (msg.getPerformativeInt() == ACLMessage.INFORM){
			//Se ha producido una recarga
			try {
				JSONObject content = new JSONObject(msg.getContent());
				battery += content.getInt("AmountGiven");
			} catch (JSONException e) {
				System.out.println("Error JSON al recibir batería");
				e.printStackTrace();
			}
		}	
		//not-understood
		else if (msg.getPerformativeInt() == ACLMessage.NOT_UNDERSTOOD){
			System.out.println("onBatteryReceived: recibido not-understood.");
		}
		//refuse
		else if (msg.getPerformativeInt() == ACLMessage.REFUSE){
			System.out.println("onBatteryReceiver: recibido refuse.");
			try {
				JSONObject content = new JSONObject(msg.getContent());
				System.out.println("Batería no recibida. Motivo: " + content.getDouble("Error"));
				//TODO: gestionar algunos errores, como el de no más batería.
			} catch (JSONException e) {
				System.out.println("Error JSON al gestionar el refuse en la batería");
				e.printStackTrace();
			}
		}
	}

	/**
	 * @author Alberto
	 * @param fe
	 * @param msgOrig
	 */
	private void sendError(FIPAException fe, ACLMessage msgOrig) {
		ACLMessage msgError = fe.getACLMessage();
		JSONObject content = new JSONObject();
		
		try {
			content.put("error",fe.getMessage());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		msgError.addReceiver(msgOrig.getSender());
		msgError.setContent(content.toString());
		msgError.setProtocol(msgOrig.getProtocol());
		msgError.setConversationId(msgOrig.getConversationId());
		msgError.setInReplyTo(msgOrig.getReplyWith());
		
		this.send(msgError);
	}

	/**
	 * Comportamiento ante un error en un mensaje recibido por el dispatcher.
	 * @param msg Mensaje recibido
	 * @param e Excepcion lanzada
	 * @return True si el dispatcher debe continuar, false en caso contrario
	 */
	protected boolean treatRuntimeError(ACLMessage msg, RuntimeException e) {
		JSONObject content;
		String subject = null;
		try {
			content = new JSONObject(msg.getContent());
			subject = content.getString("Subject");
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		
		boolean res = true;
		
		switch(subject){
		case "BatteryQuery":
		case "TraceQuery":
			//TODO
			break;
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.DroneRecharged:
		case SubjectLibrary.YourMovements:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
			//TODO
			break;
		default:
			break;
		}
		return res;
	}

	/**
	 * Comportamiento ante un error producido durante el procesamiento de un mensaje recibido por el dispatcher.
	 * @param msg Mensaje recibido
	 * @param e Excepcion lanzada
	 * @return True si el dispatcher debe continuar, false en caso contrario
	 */
	protected boolean treatMessageError(ACLMessage msg, IllegalArgumentException e) {
		JSONObject content;
		String subject = null;
		try {
			content = new JSONObject(msg.getContent());
			subject = content.getString("Subject");
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		boolean res = true;
		
		switch(subject){ 
		case "BatteryQuery":
		case "TraceQuery":
			//TODO
			break;
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.DroneRecharged:
		case SubjectLibrary.YourMovements:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
			//TODO
			break;
		default:
			break;
		}
		return res;
	}

	/**
	 * Metodo llamado por el dispatcher para tratar el informe de que el cargador le ha concedido bateria a otro drone.
	 * @param msg Mensaje original
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected void onDroneChargedInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Metodo llamado por el dispatcher para tratar el informe de que otro drone a llegado a la meta.
	 * @param msg Mensaje original
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected void onDroneReachedGoalInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
		// TODO Auto-generated method stub
	}
	
	protected void onYourMovementsInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException{
		// TODO
	}

	protected void onEveryOneMovementsInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException{
		// TODO
	}
	
	protected void onConflictiveSectionsInform(ACLMessage msg)throws IllegalArgumentException, RuntimeException, FIPAException{
		// TODO
	}
	
	/**
	 * Metodo llamado por el dispatcher para tratar la consulta de la traza del drone.
	 * @param msg Mensaje original
	 * @return Traza a enviar.
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected Trace onTraceQueried(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Metodo llamado por el dispatcher para tratar la consulta de la batería del drone.
	 * @param msg Mensaje original
	 * @return Valor de bateria a enviar.
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected int onBatteryQueried(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException{
		return battery;
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
	
	
	
	
	
	
	
	/************************************************************************************************************************/
	/************************************************************************************************************************/
	/************************************************************************************************************************/
	

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
	 * 
	 * Método donde el dron decide a qué dirección mover.
	 * @return dirección a la que se moverá.
	 */
	public int thinkv1(){
		/*La estructura del agente esta formada por task accomplishing behaviours (TAB).
		 *Para que se vean mejor cuales son las comprobaciones de estos TAB pondre en los comentarios TABi donde i
		 *es el orden del TAB empezando por el más crítico (i=1) al menos crítico.
		 */
		
		//Comprobacion de que no hemos alcanzado el limite de movimientos sin mejorar la distancia
		
		if(stop(distance))
			return END;
		
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
		
		//TAB3 Si estamos esquivando y podemos hacer un movimiento que nos deje cerda de un obstaculo lo hacemos
		
		//Al lado de un obstaculo (en un movimiento)
		if(dodging)
			for(Pair pair: ordenados){
				int move = pair.getSecond();
				if(pair.getThird() && (getCorner(move, (move+1)%4) == Map.OBSTACULO || getCorner(move, (move+3)%4) == Map.OBSTACULO))
					return move;
			}
		
		//Al lado de un obstaculo (en dos movimientos)
		if(dodging){
			int [] validMovs=getValidMovements();
			for(Pair pair: ordenados){
				int move = pair.getSecond();
				if(pair.getThird() && (validMovs[(move+1)%4] == Map.OBSTACULO || validMovs[(move+3)%4] == Map.OBSTACULO))
					return move;
			}
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
				System.out.println("Entrando dodging: "+betterMoveBeforeDodging);
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
	 * author Ismael (Parte del codigo es mio pero lo de basicon creo que lo añadio alberto)
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
	 * @author Ismael
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
	 * @author Ismael
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
	 * @author Ismael
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
	 * @author Ismael
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
				droneMap.setValue(posX,posY,Map.VISITADO);
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
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			exit = true;
		}

	}

	/**
	 * @author Ismael
	 */
	@Override
	public void finalize() {
		System.out.println("Agente " + this.getName() + " ha finalizado");
		practica.util.ImgMapConverter.mapToImg("src/maps/miresultado"+this.getAid().name+".png", droneMap);
		super.finalize();
	}

	/*
	 * @author Ismael
	 * Método para las acciones del drone.
	 */
	protected void executev1() {
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
