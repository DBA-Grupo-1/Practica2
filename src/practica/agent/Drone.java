package practica.agent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import practica.gui.Log;
import practica.lib.ErrorLibrary;
import practica.lib.JSONKeyLibrary;
import practica.lib.ProtocolLibrary;
import practica.lib.SubjectLibrary;
import practica.map.Map;
import practica.trace.Choice;
import practica.trace.Trace;
import practica.util.ConflictiveBox;
import practica.util.GPSLocation;
import practica.util.Movement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import es.upv.dsic.gti_ia.architecture.FIPAException;
import es.upv.dsic.gti_ia.architecture.FailureException;
import es.upv.dsic.gti_ia.architecture.NotUnderstoodException;
import es.upv.dsic.gti_ia.architecture.RefuseException;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;

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
 * @author Alberto
 * @author Ismael
 * @author Daniel
 * @author Jahiel
 */
public class Drone extends SuperAgent {
	private static final int SLEEPING = 0,  	//Durmiendo.
			GO_TO_POINT_TRACE = 1,				//Ir al punto más cercano de la traza.
			EXPLORE_MAP = 2,					//Explorar el mapa sin forzarla.
			FOLLOW_TRACE = 3,					//Seguir la traza.
			FORCE_EXPLORATION = 4,				//Forzar la exploración.
			LAGGING = 5, 						//Rezagado
			UNDO_TRACE = 6,						//Deshacer la traza.
			FINISH_GOAL = 7,					//Ir directamente al objetivo.
			OBSTACLE_AREA = 8;					//En zona obstáculo.


	public static final int SCOUT = 0, SCOUT_IMPROVER = 1, FOLLOWER = 2, FREE = 3; //Comportamientos del drone

	private static final int MIN_SIZE_FOR_OBSTACLE_SKIRTING = 40; //Distancia a partir de la cual empieza a considerar dos zonas obstáculos consecutivas como una sola. 


	protected boolean goal;						//Si el drone ha llegado o no al objetivo.
	private int decision;						//Decisión tomada por el drone.
	private int state;							//Estado del drone.
	private int behavior;						//Comportamiento actual del drone.
	protected int posX;							//Posición en el eje X.
	protected int posY;							//Posición en el eje Y.
	protected float angle;						//Ángulo con respecto al objetivo.
	protected double distance;					//Distancia al objetivo.
	protected int[] surroundings;				//Casillas que rodean al drone.
	protected Map droneMap;						//Mapa local del drone.
	protected float distanceMin;				//¿?
	protected int counterStop;					//¿?
	protected int battery;						//Batería restante.
	protected double posiOX, posiOY;			//¿?
	protected int currentPositionTracking;		//¿?
	protected int movingBlock;  				// movimiento a bloquear para el modo explorar otras decisiones.
	protected int indexPosition;				//¿?
	private boolean enter_force;				//Para forzar la exploración del drone.
	
	public static final int DECISION_NORTH = 3; 			//Decision de mover al norte. 
	public static final int DECISION_WEST = 2;				//Decision de mover al oeste. 	
	public static final int DECISION_SOUTH = 1;				//Decision de mover al sur. 												
	public static final int DECISION_EAST = 0;				//Decision de mover al este. 	
	public static final int DECISION_END_SUCCESS = -1;		//Decision de terminar la ejecución habiendo conseguido el objetivo.
	public static final int DECISION_END_FAIL = -2;			//Decision de terminar la ejecución sin haber conseguido el objetivo.	
	public static final int NO_DECISION = -3; 				//No se ha tomado decision. Usada por los comportamientos para pasar la toma de decisiones al siguiente comportamiento. 
	public static final int DECISION_RETHINK = -4;			//Decision de reiniciar el proceso de toma de decision.
	public static final int DECISION_ENTER_LAGGING = -5;	//Decisión de entrar en modo rezagado.

	private static final int N_TO_OTHER_OBSTACLE = 40;		//Número de casillas que debe pasar separado de un obstáculo para considerar otra zona obstáculo.

	private AgentID sateliteID;			//ID del satélite.
	private AgentID chargerID;			//ID del cargador.
	
	protected boolean dodging = false;									//Si ha entrado en modo esquivar.
	protected int betterMoveBeforeDodging = -1;							//Mejor movimiento antes de esquivar.
	private int standBy;												//Quedarse parado.
	protected BlockingQueue<ACLMessage> answerQueue;					//Cola de mensajes de respuestas a peticiones que ha hecho el drone..
	protected BlockingQueue<ACLMessage> requestQueue;					//Cola de mensajes de peticiones al drone.
	protected Thread dispatcher;										//Hebra que gestiona los mensajes.
	private AgentID[] teammates;										//Otros drones.
	private Trace trace, optimalTrace;									//Trazas actual y óptima.
	private ConflictiveBox conflictiveBox;								//Zona obstáculo actual.
	private ConflictiveBox otherSideOfTheObstacle;						//Zona obstáculo del otro lado del obstáculo.
	private ArrayList<ConflictiveBox> currentObstacleConflictiveBoxes;	//Zonas obstáculo del obstáculo actual.
	private boolean conflictiveBoxReached;								//Si ha llegado a una zona obstáculo.
	private int contSalida = 0;											//¿?
	private boolean preEsq = false;										//Si estaba esquivando cuando pedió su estatus.
	private boolean postEsq = false;									//Si entró en esquivando la última vez que pidió su estatus.
	private GPSLocation posSalidaTemporal;								//Posición en el mapa de la que se prevee que saldrá de la zona obstáculo.

	private HashMap<String, String> idsConversationSubscribe;   		//Guarda las suscripciones a las que está suscrito el drone {nombreSubscripcion, id-conversation}
	private HashMap<String, String> subscribers;                        //Guarda quienes están suscritos al drone {ID_Agente, id-conversation}
	private HashMap<String, Trace> otherTracesDrones;					//Guarda las trazas de los drones.		


										



	/**
	 * Constructor del Drone.
	 * @author Jahiel
	 * @author Daniel
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

		this.sateliteID = sateliteID;
		chargerID = charger;

		posX = 0;
		posY = 0;
		distanceMin = 999999;
		counterStop = 0;
		battery=75;
		trace = new Trace();
		posiOX = posiOY = 0;
		goal = false;

		standBy = 0;
		answerQueue = new LinkedBlockingQueue<ACLMessage>();
		requestQueue = new LinkedBlockingQueue<ACLMessage>();

		idsConversationSubscribe = new HashMap<String, String>();
		subscribers = new HashMap<String, String>();
		otherTracesDrones = new HashMap<String, Trace>();

		state = SLEEPING;
		currentPositionTracking = -1;

		conflictiveBoxReached = false;
		optimalTrace = null;

		enter_force = true;
	}

	/************************************************************************************************************************************
	 ******** Modo StandBy **************************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Activa el modo StandBy.
	 * 
	 * @author Alberto
	 * 
	 * @see Drone#leaveStandBy()
	 */
	protected synchronized final void enterStandBy(){
		standBy++;
	}

	/**
	 * Desactiva el modo StandBy. Si la hebra pensador estaba esperando la libera.
	 * 
	 * @author Alberto
	 * 
	 * @see Drone#enterStandBy()
	 */
	protected synchronized final void leaveStandBy(){
		if(standBy > 0)
			standBy--;
		if(standBy == 0)
			notify();
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
	protected synchronized final void waitIfStandBy() throws InterruptedException{
		if(standBy > 0)
			wait();
	}   






	/************************************************************************************************************************************
	 ******** Ejecucion *****************************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Ejecucion principal del agente.
	 * @author Alberto
	 * @author Ismael
	 * @see es.upv.dsic.gti_ia.core.BaseAgent#execute()
	 */
	@Override
	protected void execute(){
		try{
			startDispatcher();

			register();

			subscribe();

			do{   	
				preEsq = dodging;// Guarda si estaba esquivando desde antes de calcular su nueva decisión
				decision = think();
				postEsq = dodging;

				if(battery<=0 && decision!=DECISION_END_FAIL)
					throw new RuntimeException("Sin Bateria (Drone)");

				//System.out.println("Decisión tomada: "+decision);
				if(decision != NO_DECISION){
					sendDecision(decision);
					updateTrace(decision);
					postUpdateTrace();
					//System.out.println("Y despues de put es state = " + state);
				}
			}while(decision != DECISION_END_FAIL && decision != DECISION_END_SUCCESS);

			practica.util.ImgMapConverter.mapToImg("src/maps/miresultado"+this.getAid().name+".png", droneMap);
			onFinalize();
		}catch(RuntimeException e){
			e.printStackTrace();
		}
	}

	/**
	 *  Se determina a que punto del mapa se dirigirá el drone una vez que a salido de bordear
	 *  una zona obstáculo:
	 *  - Si el objetivo está mas cerca que ir hacia un punto de la traza optima para seguirla
	 *  entonces el drone se dirigirá hacia el objetivo.
	 *  - En caso contrario se dirige hacia el punto más cercano de la traza optima hasta el momento.
	 * 
	 * @author Jahiel
	 */
	public void getOutPutDecision(){
		Trace subTrace = optimalTrace.getSubtrace(otherSideOfTheObstacle.getPosFinal());
		double dist = 99999.0;
		double distTemp;
		int index = -1;

		for(int i=0; i<subTrace.size(); i++){
			distTemp = Math.sqrt(Math.pow((subTrace.getLocation(i).getPositionX() - posX), 2) + Math.pow((subTrace.getLocation(i).getPositionY() - posY), 2));
			if(distTemp < dist){
				dist = distTemp;
				index = i;
			}
		}

		if(distance <= dist){
			state = EXPLORE_MAP;

		}else{
			state = GO_TO_POINT_TRACE;
			index = optimalTrace.getIndex(subTrace.getLocation(index));
			indexPosition = index+1; 

		}
	}

	/**
	 * Actualiza la traza del drone con la nueva decision.
	 * @author Jonay
	 * @author Jahiel
	 */
	protected void postUpdateTrace() {
		// Casillas conflictivas
		boolean entrandoEsq = !preEsq && postEsq; //Antes no estaba esquivando y ahora sí
		boolean saliendoEsq = preEsq && !postEsq; //Antes estaba esquivando y ya no
		int decision = trace.get(trace.size()-1).getMove(); 

		if(enter_force)
			enter_force = false;

		if(decision == Drone.DECISION_ENTER_LAGGING){ 
			state = LAGGING;
			currentPositionTracking = trace.size()-1;
			conflictiveBox.setDangerous(true);
			Trace subtraza = trace.getSubtrace(conflictiveBox.getPosInicial());
			conflictiveBox.setLength(subtraza.size());
			conflictiveBox.setPosFinal(new GPSLocation(posX,posY));
			sendConflictiveBox();
			iStraggler();
			iStragglerReceive();
		}
		
		if((state == EXPLORE_MAP || state == FORCE_EXPLORATION) && (behavior == SCOUT || behavior == SCOUT_IMPROVER) && entrandoEsq ){
			if(state == EXPLORE_MAP)
				state = OBSTACLE_AREA;

			conflictiveBox = new ConflictiveBox(this.getAid());
			conflictiveBox.setPosInicial(new GPSLocation(posX, posY));
			conflictiveBox.setDecision(this.decision); // Se le asigna la decisión actual a la casilla
		}

		if(state == OBSTACLE_AREA){ 
			if(!dodging){
				contSalida++;
				if(contSalida >= N_TO_OTHER_OBSTACLE || decision == DECISION_END_SUCCESS || decision == DECISION_END_FAIL){
					if(behavior == SCOUT)
						state = EXPLORE_MAP;
					else{
						getOutPutDecision();
					}

					contSalida = 0;

					conflictiveBox.setPosFinal(posSalidaTemporal);
					conflictiveBox.setDangerous(false);
					Trace subTrace = trace.getSubtrace(conflictiveBox.getPosInicial(), posSalidaTemporal);
					conflictiveBox.setLength(subTrace.size());

					sendConflictiveBox();
				}
			}

		}

		if(state == OBSTACLE_AREA && saliendoEsq){
			contSalida = 0;
			posSalidaTemporal = new GPSLocation(posX, posY);
		}

		if(trace.get(trace.size()-1).getMove() == Drone.DECISION_END_SUCCESS){
			if(state == OBSTACLE_AREA){
				conflictiveBox.setPosFinal(posSalidaTemporal);
				conflictiveBox.setDangerous(false);
				Trace subTrace = trace.getSubtrace(conflictiveBox.getPosInicial(), posSalidaTemporal);
				conflictiveBox.setLength(subTrace.size());

				sendConflictiveBox();
			}

			state = FINISH_GOAL;

		}
	}
	
	/**
	 * Actualiza la traza del drone con la nueva decision.
	 * @author Jonay
	 * @param decision Decision tomada en think
	 */
	protected void updateTrace(int decision) {
		trace.add(new Choice(decision, new GPSLocation(posX, posY)));
	}

	/**
	 * Lanza el despachador.
	 * @author Alberto
	 */
	protected void startDispatcher() {
		dispatcher = new Thread(){
			private boolean boolExit=false;
			@Override
			public void interrupt(){
				boolExit=true;
				super.interrupt();
			}
			@Override                    
			public void run(){
				ACLMessage msg;
				try{
					do{
						msg = requestQueue.take();
					}while(dispatch(msg));
				}catch(InterruptedException e){
					if(!boolExit){
						e.printStackTrace();
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};

		dispatcher.start();
	}

	/**
	 * Envia al satelite la decision tomada en think()
	 * 
	 * @author Alberto
	 * @param decision Decision tomada
	 */
	protected void sendDecision(int decision) {
		JSONObject data = new JSONObject();

		try {
			data.put(JSONKeyLibrary.Decision, decision);
			data.put(JSONKeyLibrary.Subject, SubjectLibrary.IMoved);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.DroneMove, "default", null, buildConversationId(), data);	
		//No se añaden mensajes al log ya que hay demasiados.

		try {
			answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Envía la casilla conflictiva al satélite
	 * @author Jonay
	 */
	private void sendConflictiveBox() {
		Gson gson = new Gson();
		String confBox=gson.toJson(conflictiveBox);

		JSONObject data = new JSONObject();

		try {
			data.put(JSONKeyLibrary.ConflictBox, confBox);
			data.put(JSONKeyLibrary.Subject, SubjectLibrary.ConflictInform);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Notification, "default", null, buildConversationId(), data);
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Notification, SubjectLibrary.ConflictInform, conflictiveBox.toString());	
	}

	/************************************************************************************************************************************
	 ******** Comunicación Explorador ********************************************************************************************
	 ************************************************************************************************************************************/   
	
	/**
	 * @author Ismael
	 * Comunica que esta STRAGGLER
	 */
	public void iStraggler(){
		JSONObject ask = new JSONObject();
		try{
			ask.put(JSONKeyLibrary.Subject, SubjectLibrary.Straggler);
			send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Scout, "default", null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Scout, SubjectLibrary.Straggler, "");
		}catch(JSONException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @author Ismael
	 * Recepcion del STRAGGLER de quien envió el mensage.
	 * 
	 */
	public void iStragglerReceive(){
		
		ACLMessage msg=null;

		try {
			msg= answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		switch(msg.getPerformativeInt()){
		case ACLMessage.INFORM:
			//System.out.println("Recibido mensaje correctamente Straggler");
			break;
		case ACLMessage.FAILURE:
			sendError(new FIPAException("Error en recepción del mensaje Straggler"),msg);
			break;
		}
		
	}

	/**
	 * @author Ismael 
	 * @author Jahiel
	 * Rececpcion de compañeros al mensaje STRAGGLER
	 */
	private void heStragglerReceive(ACLMessage msg){
		if(state != FINISH_GOAL){
			JSONObject content=null;
			AgentID id = null;
			switch(msg.getPerformativeInt()){
			case ACLMessage.INFORM:
				try {
					content = new JSONObject(msg.getContent());
					id = new AgentID(content.getString(JSONKeyLibrary.Straggler));
				} catch (JSONException e) {
					e.printStackTrace();
				}      

				Trace t =  askForDroneTrace(id); // pregunto la traza del drone que ha finalizado para ver si es mejor que la optima hasta el momento.				
				otherTracesDrones.put(id.toString(), t);
				
				//Despierto al drone. No incluyo la traza del rezagado puesto que es una traza peligrosa.
				this.leaveStandBy();
				break;
			case ACLMessage.FAILURE:
				break;
			}
		}
	}

	/**
	 * @author Ismael
	 * Función mensaje para petición de salida
	 */
	public Object[] askOut(){
		JSONObject ask = new JSONObject();
		try{
			ask.put(JSONKeyLibrary.Subject, SubjectLibrary.Start);
			send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Scout, "default", null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Scout, SubjectLibrary.Start, "");
		}catch(JSONException e){
			e.printStackTrace();
		}

		return askOutReceive();
	}

	/**
	 * @author Ismael
	 * Funcion de que recibe mensaje de peticion de salida
	 */
	public Object[] askOutReceive(){
		ACLMessage msg=null;
		AgentID resId=null;
		Integer mode=null;
		try {
			msg= answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		switch(msg.getPerformativeInt()){
		case ACLMessage.INFORM:
			try{
				JSONObject content= new JSONObject(msg.getContent());
				resId = new AgentID(content.getString(JSONKeyLibrary.Selected));
				mode = new Integer(content.getInt(JSONKeyLibrary.Mode));
			}catch(JSONException e){
				e.printStackTrace();
			}
			break;
		case ACLMessage.FAILURE:

			break;

		default:

			break;
		}

		return new Object[]{resId, mode};
	}






	/************************************************************************************************************************************
	 ******** Pensamiento y comportamientos ********************************************************************************************
	 ************************************************************************************************************************************/

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
	 *  @author Alberto
	 *  @author Jahiel
	 * @return Decision tomada.
	 */
	protected int think(){
		int tempDecision;

		if(goal){
			return DECISION_END_SUCCESS;
		} 
		do{
			try {
				waitIfStandBy();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if(state == SLEEPING || state == LAGGING){	
				sendRequestOutput();
				tempDecision = DECISION_RETHINK;
			}else{
				getStatus();

				preBehavioursSetUp();
				tempDecision = checkBehaviours();
			}
		}while(tempDecision == DECISION_RETHINK);

		return tempDecision;
	}

	/**
	 * Se realiza el tratamiento de la petición de salida:
	 * - Si no soy el drone seleccionado no hacer nada y me bloquearé.
	 * - Si soy el seleccionado: cojer el MODO y en funcion del modo que me haya asignado el satelite
	 *  comportarme de una forma u otra.
	 *   
	 * @author Jahiel
	 */
	public void sendRequestOutput(){
		int mode = -1;
		Object[] response;
		AgentID drone_selected;

		response = askOut();
		drone_selected = (AgentID)response[0];
		mode = ((Integer) response[1]).intValue();

		if(this.getAid().toString().equals((drone_selected.toString()))){
			behavior = mode;
			switch(behavior){
			case SCOUT:
			case FREE:
				state = EXPLORE_MAP;
				break;
			case SCOUT_IMPROVER:
				indexPosition = getInitialPosition();
				state = GO_TO_POINT_TRACE;
				break;
			case FOLLOWER:
				if(state == LAGGING){
					state = UNDO_TRACE;
				}else{
					state = GO_TO_POINT_TRACE;
				}
				break;
			default: break;
			}
		}else{
			this.enterStandBy();
		}

	}

	/**
	 * Se devuelve el punto inicial desde el cual los drones empezarán a seguir la traza óptima
	 * hasta el momento.
	 * 
	 *  Nota: En caso de no existir tal punto el goal se encuantra en un punto (x, 0) se devuelve el punto final de la traza.
	 * @Jahiel 
	 * @return Punto de partida.
	 */
	public int getInitialPosition(){
		int i, size = optimalTrace.size(); 

		for(i=0; i<size; i++){
			if(optimalTrace.getLocation(i).getPositionY()>0)
				return i-1;
		}

		return i-1; 
	}

	/**
	 * Se realizan dos funciones:
	 * - Comprobar si se debe efectuar algún cambio de estado.
	 * - Actualizar los datos del drones en funcion de su estado actual y su comportamiento. De esta
	 * forma se prepara al drone antes de que inicie algun comportamiento y tome la decisión oportuna.
	 * 
	 * @author jahiel
	 */
	protected void preBehavioursSetUp() {  
		switch(state){
		case SLEEPING:
			if(behavior == SCOUT){
				state = EXPLORE_MAP;
			}else if(behavior == FOLLOWER || behavior == SCOUT_IMPROVER){
				state = GO_TO_POINT_TRACE;
				if(behavior == SCOUT_IMPROVER)
					indexPosition = getInitialPosition();
			}else 
				throw new RuntimeException("Comportamiento incongruente para el estado : "+state);
			break;
		case LAGGING:
			if(behavior == FOLLOWER){
				state = UNDO_TRACE;
			}else if(behavior == FREE)
				state = EXPLORE_MAP;

			break;
		case GO_TO_POINT_TRACE:

			if(behavior == FOLLOWER){
				int indexPosInic = getInitialPosition();

				posiOX = optimalTrace.getLocation(indexPosInic).getPositionX();
				posiOY = optimalTrace.getLocation(indexPosInic).getPositionY();
				if(posX == posiOX && posY == posiOY)
					currentPositionTracking = indexPosInic;
			}else if(behavior == SCOUT_IMPROVER){

				posiOX = optimalTrace.getLocation(indexPosition).getPositionX();
				posiOY = optimalTrace.getLocation(indexPosition).getPositionY();
				if(posX == posiOX && posY == posiOY)
					currentPositionTracking = indexPosition;
			}else
				throw new RuntimeException("Comportamiento incongruente para el estado : "+state);

			if(posX == posiOX && posY == posiOY)
				state = FOLLOW_TRACE;

			break;
		case FOLLOW_TRACE:
			// Se comprueba si estoy en una casilla conflictiva:
			// - Si estoy en ella y solo hay una anotada y su tamaño es grande -> bordear por el otro lado.
			// - Otro caso -> no cambio de estado

			if(conflictiveBoxReached && (behavior == SCOUT_IMPROVER)
					&& currentObstacleConflictiveBoxes.size() == 1
					&& currentObstacleConflictiveBoxes.get(0).getLength() > MIN_SIZE_FOR_OBSTACLE_SKIRTING){
				state = FORCE_EXPLORATION;
				enter_force = true;
				posiOX= (posX + (Math.cos(angle) * distance));
				posiOY= (posY + (Math.sin(angle)*distance));
				movingBlock = currentObstacleConflictiveBoxes.get(0).getDecision();  // Se coje la decisión que tomó el otro drone para bloquear este movimiento
				otherSideOfTheObstacle = currentObstacleConflictiveBoxes.get(0);

			}else{
				currentPositionTracking++;
			}

			break;
		case UNDO_TRACE:

			if(otherSideOfTheObstacle.getPosInicial().equals(new GPSLocation(posX, posY))){
				state = FOLLOW_TRACE;
				currentPositionTracking = optimalTrace.getIndex(otherSideOfTheObstacle.getPosInicial());
			}else{
				currentPositionTracking--;
			}

			break;
		case FORCE_EXPLORATION:
		case EXPLORE_MAP:
			posiOX= (posX + (Math.cos(angle) * distance));
			posiOY= (posY + (Math.sin(angle)*distance));
			break;
		default: break;
		}

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
	 * @author Alberto
	 * @return Decision tomada
	 */
	protected int checkBehaviours(){
		List<Movement> listaMovimientos;
		int tempDecision;

		listaMovimientos = getMovementList();

		tempDecision = checkEndCondition(listaMovimientos, null);
		if(tempDecision != NO_DECISION)
			return tempDecision;

		tempDecision = criticalBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DECISION)
			return tempDecision;

		tempDecision = firstBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DECISION)
			return tempDecision;

		tempDecision = secondBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DECISION)
			return tempDecision;

		tempDecision = thirdBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DECISION)
			return tempDecision;

		tempDecision = fourthBehaviour(listaMovimientos, null);
		if(tempDecision != NO_DECISION)
			return tempDecision;

		return basicBehaviour(listaMovimientos, null);
	}

	/**
	 * Comprueba si el drone debe terminar su ejecucion. Es llamado antes de criticalBehaviour.
	 *
	 * Nota: la comprobacion de que se ha alcanzado el objetivo se comprueba en el think, separado de los comportamientos. En esta funcion se deben comprobar
	 * las condiciones de parada en otros casos.
	 * @author Alberto
	 * @author Jahiel
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada. Debe ser END_FAIL o NO_DEC
	 */
	private int checkEndCondition(List<Movement> listaMovimientos, Object[] object) {
		if(state != UNDO_TRACE && state != FOLLOW_TRACE){
			for(Movement movement : listaMovimientos)
				if(movement.isValid())
					return NO_DECISION;

			return DECISION_END_FAIL;
		}else{
			return NO_DECISION;
		}
	}

	/**
	 * Primer comportamiento intermedio del drone. Es el segundo en ejecutarse:
	 *   Se comprueba si el drone debe entrar en modo rezagado.
	 * 
	 * @author Jahiel
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int firstBehaviour(List<Movement> listaMovimientos, Object[] args) {

		if((state == OBSTACLE_AREA) && (behavior == SCOUT_IMPROVER)){
			Trace subTrace = trace.getSubtrace(conflictiveBox.getPosInicial());
			if(subTrace.size()-this.contSalida >= otherSideOfTheObstacle.getLength())
				return Drone.DECISION_ENTER_LAGGING;
		}

		return NO_DECISION;
	}

	/**
	 * Segundo comportamiento intermedio del drone. Es el tercero en ejecutarse al recorrer los comportamientos:
	 * 
	 *     Deshacer la traza. Exlcusivo para los rezagados que deberán deshacer la traza hasta el inicio de la casilla conflictiva 
	 *   si dicho punto está más cerca del objetivo.
	 *       
	 * @author Jahiel
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int secondBehaviour(List<Movement> listaMovimientos, Object[] args) {
		if(state == UNDO_TRACE){
			return (trace.get(currentPositionTracking).getMove() + 2)%4;	
		}

		return NO_DECISION;
	}

	/**
	 * Tercer comportamiento intermedio del drone. Es el cuarto en ejecutarse al recorrer los comportamientos.
	 * 
	 *     Si el drone está en el estado FOLLOW_TRACE se sigue la traza óptima. Si además se encuentra en una casilla conflictiva
	 *   se cambia a la traza cuyo camino implique bordear el obstáculo por el camino más corto.
	 *   
	 *   Nota: si está en el estado FOLLOW_TRACE y además se encuentra situado sobre una casilla conflictiva no se tiene en cuenta el modo que tenga asignado y deberá
	 *   escojer el lado óptimo. Si su modo es SCOUT_IMPROVER deberá igualmente elegir el lado óptima sin pensar en si debe mejorarlo o no
	 *   puesto que si hubiera tenido que mejorarlo su estado habría cambiado de FOLLOW_TRACE -> FORCE_EXPLORATION.
	 *  
	 * @author Jahiel
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int thirdBehaviour(List<Movement> listaMovimientos, Object[] args) {

		if(state == FOLLOW_TRACE){
			if(conflictiveBoxReached){
				int minSize = 999999, indexMinBox = -1;
				int size = currentObstacleConflictiveBoxes.size();
				AgentID id = null;

				for(int i=0; i<size; i++){
					if(currentObstacleConflictiveBoxes.get(i).getLength() < minSize && !currentObstacleConflictiveBoxes.get(i).isDangerous()){
						minSize = currentObstacleConflictiveBoxes.get(i).getLength();
						id = currentObstacleConflictiveBoxes.get(i).getDroneID();
						indexMinBox = i;
					}
				}
				optimalTrace = otherTracesDrones.get(id.toString()); // Cambio a la traza que me conduce por el camino mas corto para bordear el obstaculo
				currentPositionTracking = optimalTrace.getIndex( currentObstacleConflictiveBoxes.get(indexMinBox).getPosInicial());
			}

			return optimalTrace.get(currentPositionTracking).getMove();
		}else
			return NO_DECISION;

	}

	/**
	 * Cuarto comportamiento intermedio del drone. Es el quinto en ejecutarse al recorrer los comportamientos.
	 * @author Alberto
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int fourthBehaviour(List<Movement> listaMovimientos, Object[] args) {
		if(dodging){
			//Buscamos el mejor movimiento en la lista y comprobamos si es posible
			boolean betterIsPosible = false;
			for(int i=0; i<4; i++)
				if(listaMovimientos.get(i).getMove() == betterMoveBeforeDodging)
					betterIsPosible = listaMovimientos.get(i).isValid(); 


			//Si es posible lo realizamos y salimos del modo esquivando
			if(betterIsPosible){
				if(state == FORCE_EXPLORATION)
					state = OBSTACLE_AREA;
				dodging=false;
				return betterMoveBeforeDodging;
			}


			//Comprobamos si estamos esquivando y podemos hacer un movimiento que nos deje cerca de un obstaculo

			//Al lado de un obstaculo (en un movimiento)
			for(Movement movement: listaMovimientos){
				int move = movement.getMove();
				if(movement.isValid() && (getCorner(move, (move+1)%4) == Map.OBSTACULO || getCorner(move, (move+3)%4) == Map.OBSTACULO))
					return move;
			}

			//Al lado de un obstaculo (en dos movimientos)
			int [] validMovs=getValidMovements();
			for(Movement movement: listaMovimientos){
				int move = movement.getMove();
				if(movement.isValid() && (validMovs[(move+1)%4] == Map.OBSTACULO || validMovs[(move+3)%4] == Map.OBSTACULO))
					return move;
			}


			return NO_DECISION;
		}else{
			//Comprobamos si no podemos hacer el mejor movimiento debido a un obstaculo
			//En ese caso pasamos al modo esquivar
			int [] validMov=getValidMovements();
			if(!listaMovimientos.get(0).isValid() && validMov[listaMovimientos.get(0).getMove()]==Map.OBSTACULO){
				dodging=true;

				if(state == FORCE_EXPLORATION && listaMovimientos.get(0).getMove()==movingBlock)
					state=OBSTACLE_AREA;

				betterMoveBeforeDodging=listaMovimientos.get(0).getMove();
			}

			return NO_DECISION;
		}

	}


	/**
	 * Comportamiento critico del drone. Es el primero en ejecutarse al recorrer los comportamientos.
	 * @author Dani
	 * @author Ismael
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada
	 */
	protected int criticalBehaviour(List<Movement> listaMovimientos, Object[] args) {
		//Si no le queda batería el drone la pide.
		int amount=1;
		if (battery == 0){
			askForBattery(amount);
			ACLMessage msg = null;
			try {
				msg = answerQueue.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			int batteryReceived = onBatteryReceived(msg);

			if(batteryReceived > 0){
				battery += batteryReceived;
			}else{
				return DECISION_END_FAIL;
			}
		}

		return NO_DECISION;
	}

	/**
	 * Comportamiento básico del agente. Es el ultimo en ejecutarse. Debe devolver una decision distinta de NO_DEC.
	 * 
	 * @author Alberto
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param args Argumentos adicionales
	 * @return Decision tomada.
	 */
	protected int basicBehaviour(List<Movement> listaMovimientos, Object[] args) {
		//Si podemos hacer el mejor movimiento lo hacemos
		if(listaMovimientos.get(0).isValid()){
			return listaMovimientos.get(0).getMove();
		}

		int second=-1, third=-1;
		//Para hallar los dos mejores movimientos posibles (si existen) recorremos el array de peor a mejor
		//Si un movimiento es posible entonces hemos encontrado uno mejor que los que encontrasemos antes
		//Desplazamos los valores encontrados antes (siempre se queda en second el mejor posible y en third el segundo mejor posible)
		for(int i=3; i>=0; i--){
			if(listaMovimientos.get(i).isValid()){
				third = second;
				second = listaMovimientos.get(i).getMove();
			}
		}

		//Si third no existe nuestra unica posibilidad es second
		if(third==-1)
			return second;

		//Si second no existe (y por lo tanto third tampoco) entonces no tenemos movimientos
		if(second==-1)
			return DECISION_END_FAIL;

		float distSecond=0, distThird=0;
		for(int i=0; i<4; i++){
			if(listaMovimientos.get(i).getMove() == second)
				distSecond = listaMovimientos.get(i).getDistance();
			if(listaMovimientos.get(i).getMove() == third)
				distThird = listaMovimientos.get(i).getDistance(); 
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
			if(decision == NO_DECISION)
				decision = second;
		}else{
			decision = second;
		}

		return decision;
	}


	/*********Funciones auxiliares *****************************************************************************************************************/

	/**
	 * Resuelve empates entre movimientos
	 * @param listaMovimientos Lista de movimientos a analizar
	 * @param tieargs Argumentos adicionales
	 * @return Decision tomada o NO_DEC si no ha podido resolver el empate
	 */
	protected int tieResolution(List<Movement> listaMovimientos, Object[] tieargs) {
		return NO_DECISION;
	}

	/**
	 * Construye una lista ordenada con los movimientos. Primero llama a freeSquaresConditions para tener las condiciones de movimiento libre.
	 * A continuacion calcula las distancias de cada movimiento y construye una lista de movimientos. por ultimo llama a sortMovements para ordenar y devuelve el resultado.
	 * 
	 * Notas para desarrollo drone:
	 * - Si se desea cambiar la condicion de movimiento libre sobreescribir la funcion freeSquaresConditions.
	 * - Si se desea cambiar el proceso de ordenacion sobreescribir la funcion sortMovements.
	 *  
	 * @author Alberto
	 * @return List de Movement ordenado.
	 * 
	 * @see Drone#freeSquaresConditions()
	 * @see Drone#sortMovements(List<Movement>)
	 */
	protected List<Movement> getMovementList() {

		ArrayList<Movement> mispares=new ArrayList<Movement>();
		boolean[] basicond;


		float calculoDist=0;         

		basicond = freeSquaresConditions();


		//Creamos el array con todos los movimientos, incluyendo la distancia al objetivo, el movimiento en si, y si es valido o no
		calculoDist= (float) Math.sqrt(Math.pow((posiOX-(posX+1)),2)+Math.pow((posiOY-posY), 2));
		mispares.add(new Movement(calculoDist,DECISION_EAST,basicond[DECISION_EAST]));

		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY+1)), 2));
		mispares.add(new Movement(calculoDist,DECISION_SOUTH,basicond[DECISION_SOUTH]));

		calculoDist=(float) Math.sqrt(Math.pow((posiOX-(posX-1)),2)+Math.pow((posiOY-posY), 2));
		mispares.add(new Movement(calculoDist,DECISION_WEST,basicond[DECISION_WEST]));

		calculoDist=(float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY-1)), 2));
		mispares.add(new Movement(calculoDist,DECISION_NORTH,basicond[DECISION_NORTH]));

		return sortMovements(mispares);
	}

	/**
	 * Evalua las condiciones de movimiento libre para los cuatro movimientos.
	 * @author Alberto
	 * @return Array con cuatro booleanos. Los campos corresponden a la condicion de movimiento libre de ESTE, SUR, OESTE y NORTE (en ese orden.
	 */
	protected boolean[] freeSquaresConditions(){
		int[] validSqr = getValidSquares();
		boolean[] basicond=new boolean[4];


		basicond[DECISION_EAST] = validSqr[5] == Map.LIBRE && !(validSqr[2] == Map.VISITADO || validSqr[8] == Map.VISITADO);
		basicond[DECISION_SOUTH] = validSqr[7] == Map.LIBRE && !(validSqr[6] == Map.VISITADO || validSqr[8] == Map.VISITADO);
		basicond[DECISION_WEST] = validSqr[3] == Map.LIBRE && !(validSqr[0] == Map.VISITADO || validSqr[6] == Map.VISITADO);
		basicond[DECISION_NORTH] = validSqr[1] == Map.LIBRE && !(validSqr[0] == Map.VISITADO || validSqr[2] == Map.VISITADO);

		if(!(basicond[DECISION_EAST] || basicond[DECISION_SOUTH] || basicond[DECISION_WEST] || basicond[DECISION_NORTH])){
			basicond[DECISION_EAST] = validSqr[5] == Map.LIBRE && !(validSqr[2] == Map.VISITADO && validSqr[8] == Map.VISITADO);
			basicond[DECISION_SOUTH] = validSqr[7] == Map.LIBRE && !(validSqr[6] == Map.VISITADO && validSqr[8] == Map.VISITADO);
			basicond[DECISION_WEST] = validSqr[3] == Map.LIBRE && !(validSqr[0] == Map.VISITADO && validSqr[6] == Map.VISITADO);
			basicond[DECISION_NORTH] = validSqr[1] == Map.LIBRE && !(validSqr[0] == Map.VISITADO && validSqr[2] == Map.VISITADO);
		}

		if(state == FORCE_EXPLORATION && !(basicond[DECISION_EAST] || basicond[DECISION_SOUTH] || basicond[DECISION_WEST] || basicond[DECISION_NORTH])){
			int lastMove = (trace.get(trace.size()-1).getMove() + 2) % 4;
			basicond[lastMove]=true;
		}

		return basicond;
	}

	/**
	 * Ordena una lista de movimientos
	 * 
	 * @author Alberto
	 * @param lista List de Movement a ordenar.
	 * @return List de Movement ordenado.
	 */
	protected List<Movement> sortMovements(List<Movement> lista){
		List<Movement> ordenados=new ArrayList<Movement>(lista);
		Collections.sort(ordenados, new Comparator<Movement>(){
			public int compare(Movement mov1, Movement mov2){
				if(mov1.getDistance()<mov2.getDistance()){
					return -1;
				}else{
					if(mov1.getDistance()>mov2.getDistance()){
						return 1;
					}else{
						return 0;
					}
				}
			}
		});

		return ordenados;
	}

	/************************************************************************************************************************************
	 ******** Recepcion de mensajes *****************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Recibe el mensaje y lo encola. La decision de a que cola mandarlo depende enteramente del protocolo del mensaje (campo protocol).<br>
	 * Estado actual:
	 * answerQueue -> SendMeMyStatus, IMoved
	 * requestQueue -> BatteryQuery, TraceQuery, DroneReachedGoal, DroneRecharged, BatteryRequested
	 * @author Jahiel
	 * @author Alberto
	 * @param msg Mensaje recibido
	 */
	@Override
	public void onMessage(ACLMessage msg){
		JSONObject content;
		String subject = null;

		try {
			content = new JSONObject(msg.getContent());
			subject = content.getString(JSONKeyLibrary.Subject);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}

		BlockingQueue<ACLMessage> queue = null;

		if (!subject.equals(SubjectLibrary.Status) && !subject.equals(SubjectLibrary.IMoved) && !subject.equals(SubjectLibrary.BatteryRequest))
			addMessageToLog(Log.RECEIVED, msg.getSender(), msg.getProtocol(), subject, "");

		switch(subject){

		case SubjectLibrary.StragglerNotification:
			queue=requestQueue;
			break;
		case SubjectLibrary.Start:
		case SubjectLibrary.Straggler:
		case SubjectLibrary.Status:
		case SubjectLibrary.IMoved:
		case SubjectLibrary.End:
		case SubjectLibrary.MapGlobal:
		case SubjectLibrary.DroneBattery:
		case SubjectLibrary.IdAgent:
		case SubjectLibrary.Position:
		case SubjectLibrary.GoalDistance:
		case SubjectLibrary.Register:
			queue = answerQueue;
			break;
		case SubjectLibrary.BatteryLeft:
		case SubjectLibrary.Trace:
		case SubjectLibrary.Steps:
			if(msg.getPerformativeInt() == ACLMessage.QUERY_REF)
				queue = requestQueue;
			else
				queue = answerQueue;			
			break;
		case SubjectLibrary.YourMovements:
		case SubjectLibrary.DroneRecharged:
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
			if(msg.getPerformativeInt() == ACLMessage.ACCEPT_PROPOSAL)
				queue = answerQueue;
			else
				queue = requestQueue;
			break;
		case SubjectLibrary.BatteryRequest:
			queue = answerQueue;
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

	/************************************************************************************************************************************
	 ******** Protocolo de finalización**************************************************************************************************
	 ************************************************************************************************************************************/
	
	/**
	 * Función que llama al finalizador del satélite.
	 * Si la respuesta es wait espera, si es otra finaliza.
	 * @author Ismael
	 */
	protected void onFinalize() {
		ACLMessage msg=null;	

		try {
			msg = answerQueue.take();

			if(msg.getPerformativeInt()!= ACLMessage.INFORM){
				throw new RuntimeException("Error en la recepcion del tipo de mensaje");
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}


	/************************************************************************************************************************************
	 ******** Protocolos de Informacion *************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Pregunta a un drone por la batería que le queda
	 * @author Jonay
	 * @return Batería restante del drone
	 */
	@SuppressWarnings("unused")
	private int askForDroneBattery(AgentID DroneID){
		int bateriaRestante = -1;   	
		JSONObject requestContent = new JSONObject();
		ACLMessage answer=null;

		try {
			requestContent.put(JSONKeyLibrary.Subject, SubjectLibrary.BatteryLeft);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		send(ACLMessage.QUERY_REF, DroneID, ProtocolLibrary.Information, "default", null, buildConversationId(), requestContent);
		addMessageToLog(Log.SENDED, DroneID, ProtocolLibrary.Information, SubjectLibrary.BatteryLeft, "");

		try {
			answer = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if(answer.getPerformativeInt() == ACLMessage.INFORM){
			try {
				bateriaRestante = new JSONObject(answer.getContent()).getInt("EnergyLeft");
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

		return bateriaRestante;
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
		addMessageToLog(Log.SENDED, DroneID, ProtocolLibrary.Information, SubjectLibrary.Trace, "");

		try {
			answer = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if(answer.getPerformativeInt() == ACLMessage.INFORM){
			try {
				String trazaJSON = new JSONObject(answer.getContent()).getString("trace");
				Gson gson = new Gson();
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
	 * Pregunta a un drone por el número de pasos que ha dado
	 * @author Jonay
	 * @return El número de pasos
	 */
	@SuppressWarnings("unused")
	private int askForDroneSteps(AgentID DroneID){
		int pasos = -1;
		JSONObject requestContent = new JSONObject();
		ACLMessage answer=null;

		try {
			requestContent.put(JSONKeyLibrary.Subject, SubjectLibrary.Steps);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		send(ACLMessage.QUERY_REF, DroneID, ProtocolLibrary.Information, "default", null, buildConversationId(), requestContent);
		addMessageToLog(Log.SENDED, DroneID, ProtocolLibrary.Information, SubjectLibrary.Steps, "");

		try {
			answer = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if(answer.getPerformativeInt() == ACLMessage.INFORM){
			try {
				pasos = new JSONObject(answer.getContent()).getInt("steps");
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

		return pasos;
	}

	/**
	 * Pregunta al cargador la cantidad de bateria que le queda.
	 * @author Alberto
	 * @return Bateria total restante.
	 */
	protected int askBattery(){
		JSONObject requestContent = new JSONObject();
		ACLMessage answer=null;
		int resultado = -1;

		try {
			requestContent.put(JSONKeyLibrary.Subject, SubjectLibrary.ChargerBattery);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		send(ACLMessage.QUERY_REF, chargerID, ProtocolLibrary.Information, "Get-RemainingBattery", null, buildConversationId(), requestContent);
		addMessageToLog(Log.SENDED, chargerID, ProtocolLibrary.Information, SubjectLibrary.ChargerBattery, "");

		try {
			answer = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}


		if(answer.getPerformativeInt() == ACLMessage.INFORM){
			try {
				resultado = new JSONObject(answer.getContent()).getInt("ChargerBattery");
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
	 * Pide mapa común.
	 * @author Ismael.
	 * 
	 */
	protected void askForMap(){
		JSONObject ask = new JSONObject();

		try{
			ask.put(JSONKeyLibrary.Subject, SubjectLibrary.MapGlobal);

			send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, null, null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, chargerID, ProtocolLibrary.Information, SubjectLibrary.MapGlobal, "");
		} catch (JSONException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @author Ismael
	 * Recive mapa común.
	 * @param m
	 */
	protected void askForMapReceive(Map m){
		JSONObject content;
		int mapHeight, mapWidth;
		ACLMessage msg = null;
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		switch(msg.getPerformativeInt()){

			case ACLMessage.NOT_UNDERSTOOD:
				throw new RuntimeException("Fallo: no entendimiento de mensaje");
	
			case ACLMessage.REFUSE:
				try {
					content = new JSONObject(msg.getContent());
					if(! (content.get("reason").equals("FailureCommunication") || content.get("reason").equals("EmptyContent")
							|| content.get("reason").equals("BadlyStructuredContent")) || content.get("reason").equals("FailureAccess") || content.get("reason").equals("SenderDrone") )
						throw new RuntimeException("Fallo en la respuesta del satelite");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
	
			case ACLMessage.INFORM:
				try{
					content = new JSONObject(msg.getContent());
					mapHeight= content.getInt("Height");
					mapWidth= content.getInt("Width");
					JSONArray data = (JSONArray) content.get("Values");
	
					for(int i=0,z=0;i<mapHeight;i++){
						for(int j=0;j<mapWidth;j++,z++){
							try {
								m.setValue(i, j, data.getInt(z));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
	
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			default: 
				throw new RuntimeException("Fallo en cojer respuesta del satelite");
			}
	}

	/**
	 * Pide el ID de un drone.
	 * @author Ismael
	 * @param name nombre del drone cuyo ID se pretende saber.
	 */
	protected void askForID(String name){
		JSONObject ask = new JSONObject();

		try{
			ask.put(JSONKeyLibrary.Subject, SubjectLibrary.IdAgent);
			ask.put(SubjectLibrary.Name, name);
			send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Information, SubjectLibrary.IdAgent, name);
		} catch (JSONException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Recive ID para un nombre
	 */
	protected AgentID askForIDReceive(){ 
		JSONObject content= null;
		AgentID id=null;
		ACLMessage msg=null;
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		switch(msg.getPerformativeInt()){

		case ACLMessage.NOT_UNDERSTOOD:
			throw new RuntimeException("Fallo: no entendimiento de mensaje");

		case ACLMessage.REFUSE:
			try {
				content = new JSONObject(msg.getContent());
				if(! (content.get("reason").equals("FailureCommunication") || content.get("reason").equals("EmptyContent")
						|| content.get("reason").equals("BadlyStructuredContent")) || content.get("reason").equals("FailureAgentID") )
					throw new RuntimeException("Fallo en la respuesta del satelite");
			} catch (JSONException e) {
				e.printStackTrace();
			}

		case ACLMessage.INFORM:
			try{
				content = new JSONObject(msg.getContent());
				id = new AgentID(content.getString("ID"));
			} catch (JSONException e) {
				e.printStackTrace();
			}
			break;
		default: 
			throw new RuntimeException("Fallo en cojer respuesta del satelite");
		}
		return id;
	}
	
	/**
	 * Pide distancia de un drone especifico
	 * @author Ismael
	 * @param ID AgentID del drone
	 */
	protected void askForGoal(AgentID id){
		JSONObject ask = new JSONObject();
		
		try{
			ask.put(JSONKeyLibrary.Subject, SubjectLibrary.GoalDistance);
			ask.put("ID", id);
			send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Information, SubjectLibrary.GoalDistance, id.name);
		} catch (JSONException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @author Ismael
	 * Recibe distancia meta para un agente
	 * @return distancia.
	 */
	protected double askForGoalReceive(){
		JSONObject content;
		double Ggoal=0;
		ACLMessage msg=null;
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		switch(msg.getPerformativeInt()){
	
			case ACLMessage.NOT_UNDERSTOOD:	
				throw new RuntimeException("Fallo: no entendimiento de mensaje");	
	
			case ACLMessage.REFUSE:
				try {
					content = new JSONObject(msg.getContent());
					if(! (content.get("reason").equals("FailureCommunication") || content.get("reason").equals("EmptyContent")
							|| content.get("reason").equals("BadlyStructuredContent")) || content.get("reason").equals("FailureAgentID") ||content.get("reason").equals("FailureAccessDistance") )
						throw new RuntimeException("Fallo en la respuesta del satelite");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
				
			case ACLMessage.INFORM:
				try{
					content = new JSONObject(msg.getContent());
					Ggoal = content.getDouble("Distance");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
				
			default: 
				throw new RuntimeException("Fallo en cojer respuesta del satelite");
		}
		return Ggoal;
	}

	/**
	 * Pide posicion 
	 * @author Ismael
	 */
	protected void askPosition(){
		JSONObject ask = new JSONObject();

		try{
			ask.put(JSONKeyLibrary.Subject, SubjectLibrary.Position);
			send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information,null, null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Information, SubjectLibrary.Position, "");
		} catch (JSONException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @author Ismael
	 * Recoge posicion solicitada
	 * @return int[2] coordenadas en el mapa del drone.
	 */
	protected int[] askPositionReceive(){
		JSONObject content;
		int pos[] = new int[2];
		ACLMessage msg = null;
		
		try {
			msg=answerQueue.take();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		switch(msg.getPerformativeInt()){
	
			case ACLMessage.NOT_UNDERSTOOD:	
				throw new RuntimeException("Fallo: no entendimiento de mensaje");	
	
			case ACLMessage.REFUSE:
				try {
					content = new JSONObject(msg.getContent());
					if(! (content.get("reason").equals("FailureCommunication") || content.get("reason").equals("EmptyContent")
							|| content.get("reason").equals("BadlyStructuredContent")) || content.get("reason").equals("FailureAgentID") ||content.get("reason").equals("FailureAccessPossition") )
						throw new RuntimeException("Fallo en la respuesta del satelite");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
				
			case ACLMessage.INFORM:
				try{
	
					content = new JSONObject(msg.getContent());
					JSONObject aux = new JSONObject();
					aux = content.getJSONObject("Posi");
					pos[0]= aux.getInt("x");
					pos[1]= aux.getInt("y");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			default: 
				throw new RuntimeException("Fallo en cojer respuesta del satelite");
		}

		return pos;
	}
	
	/**
	 * Pide batería al satelite de un drone ID
	 * @author Ismael
	 * @param id AgentID cuya batería queremos saber.
	 */
	protected void askForMyBatterySatellite(AgentID id){
		JSONObject ask = new JSONObject();

		try{
			ask.put(JSONKeyLibrary.Subject, "DroneBattery");
			ask.put("AgentID", id);
			send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, null, null, buildConversationId(), ask);
			addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Information, SubjectLibrary.DroneBattery, "");
		} catch (JSONException e){
			e.printStackTrace();
		}
	}
	
	/**
	 * @author Ismael
	 * Recoge bateria del satelite solicitada para un drone
	 * @return batería del drone por la que hemos preguntado.
	 */
	protected int askForMyBatterySatelliteReceive(){
		JSONObject content;
		int bat=0;
		ACLMessage msg=null;
		try {
			msg = answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}

		switch(msg.getPerformativeInt()){
			case ACLMessage.NOT_UNDERSTOOD:
				throw new RuntimeException("Fallo: no entendimiento de mensaje");
	
			case ACLMessage.REFUSE:
				try {
					content = new JSONObject(msg.getContent());
					if(! (content.get("reason").equals("FailureCommunication") || content.get("reason").equals("EmptyContent")
							|| content.get("reason").equals("BadlyStructuredContent")) || content.get("reason").equals("FailureAgentID") ||content.get("reason").equals("FailureLevelBatteryDrone") )
						throw new RuntimeException("Fallo en la respuesta del satelite");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
	
			case ACLMessage.INFORM:
				try{
					content = new JSONObject(msg.getContent());
					bat = content.getInt("Battery");
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			default: 
				throw new RuntimeException("Fallo en cojer respuesta del satelite");
		}
		return bat;
	}

	/**
	 * Metodo llamado por el dispatcher para tratar la consulta de la traza del drone.
	 * @author Jonay
	 * @param msg Mensaje original
	 * @return Traza a enviar.
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected Trace onTraceQueried(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
		basicErrorsComprobation(msg, ACLMessage.QUERY_REF);

		return trace;
	}

	/**
	 * Metodo llamado por el dispatcher para tratar la consulta de la batería del drone.
	 * @author Jonay
	 * @param msg Mensaje original
	 * @return Valor de bateria a enviar.
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected int onBatteryQueried(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException{
		basicErrorsComprobation(msg, ACLMessage.QUERY_REF);

		return battery;
	}

	/**
	 * Métoro llamado por el dispacher para tratar la consulta del número de pasos que ha dado un drone.
	 * @author Jonay
	 * @param msg Mensaje original
	 * @return El número de pasos que ha dado el drone.
	 * @throws FIPAException En caso de error por algún motivo recogido en el protocolo de comunicación.
	 */
	protected int onStepsQueried(ACLMessage msg) throws FIPAException{
		basicErrorsComprobation(msg, ACLMessage.QUERY_REF);

		return trace.size(); 
	}


	/**
	 * Se comunica con el satelite para recibir su status y actualizarlo. Tambien se notifica a todos los agentes 
	 * subscritos a él que se ha movido.
	 * 
	 * @author Alberto
	 */
	protected void getStatus() {
		JSONObject contenido = null;
		try {
			contenido = new JSONObject();
			contenido.put(JSONKeyLibrary.Subject, SubjectLibrary.Status);
		} catch (JSONException ex) {
			ex.printStackTrace();
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}

		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), contenido);

		updateStatus();
	}

	/**
	 * Actualiza el status del agente en base al mensaje recibido.
	 * @param msg Mensaje recibido del satelite
	 * 
	 * @author Ismael
	 * @author Jahiel
	 * @author Daniel
	 */
	protected void updateStatus() {
		ACLMessage msg=null;
		try {
			msg=answerQueue.take();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		JSONObject content = null;
		try {
			content = new JSONObject(msg.getContent());
		} catch (JSONException ex) {
			ex.printStackTrace();
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		try {
			JSONObject contentValues, contentGPS,aux3 = new JSONObject();

			contentValues = content.getJSONObject(SubjectLibrary.Values);
			contentGPS = contentValues.getJSONObject("gps");
			aux3 = contentValues.getJSONObject("gonio");
			//actualizamos el mapa del drone antes de recoger las nuevas posiciones X e Y.
			droneMap.setValue(posX,posY,Map.VISITADO);
			posX = contentGPS.getInt("x");
			posY = contentGPS.getInt("y");
			distance = aux3.getDouble("dist");
			angle= (float) aux3.getDouble("alpha");
			battery= contentValues.getInt("battery");
			//System.out.println("Mi bateria es: " + battery);

			String valor = contentValues.getString("goal");
			if(valor.equals("No")){
				goal=false;
			}
			else if (valor.equals("Si")){
				goal=true;
			}
			JSONArray jsArray = new JSONArray();
			jsArray= contentValues.getJSONArray("radar");
			for(int i=0;i<9;i++){
				surroundings[i]=jsArray.getInt(i);
			}
			//Compruebo si ha llegado información sobre si la casilla es conflictiva

			if(contentValues.has(JSONKeyLibrary.ConflictiveBox)){
				//Extraigo la información
				conflictiveBoxReached = true;
				String conflictiveBoxData = contentValues.getString(JSONKeyLibrary.ConflictiveBox);
				//Defino el tipo de datos que voy a extraer del JSON
				Type collectionType = new TypeToken<ArrayList<ConflictiveBox>>(){}.getType();	
				//Extraigo los datos
				currentObstacleConflictiveBoxes = new Gson().fromJson(conflictiveBoxData, collectionType); 

			}else
				conflictiveBoxReached = false;                 

		} catch (JSONException ex) {
			ex.printStackTrace();
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/*********Funciones auxiliares *****************************************************************************************************************/

	/**
	 * Comprueba los errores básicos de comunicación y lanza las excepciones necesarias
	 * @author Jonay
	 * @param msg mensaje que vamos a comprobar
	 * @param performative Performativa esperada del mensaje
	 * @throws FIPAException Error en algún aspecto de la comunicación
	 */
	private void basicErrorsComprobation(ACLMessage msg, int performative) throws FIPAException{
		//Primera comprobación: performativa correcta.
		if (msg.getPerformativeInt() != performative)
			throw new NotUnderstoodException(ErrorLibrary.NotUnderstood);

		JSONObject content;
		try {
			content = new JSONObject(msg.getContent());
			if (content.length() == 0)
				throw new RefuseException(ErrorLibrary.EmptyContent);

			if (!content.has(JSONKeyLibrary.Subject) )
				throw new RefuseException(ErrorLibrary.BadlyStructuredContent);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Convierte una traza en un JSONArray
	 * @author Jonay
	 * @author Daniel
	 * @return el array JSONArray
	 * @throws JSONException 
	 */
	private String traceToStringJSON(Trace trc) throws JSONException {
		Gson gson = new Gson();
		String trace = gson.toJson(trc);
		return trace;
	}


	/************************************************************************************************************************************
	 ******** Protocolo de recarga ******************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Pide batería al cargador.
	 * @author Dani.
	 * @author Ismael
	 * @param amount cantidad que se le pide al cargador.
	 */
	protected void askForBattery (int amount){
		if (battery == 0 && amount > 0 && amount <= 75){
			JSONObject content = new JSONObject();
			try {
				//Construcción del JSON
				content.put(JSONKeyLibrary.Subject, SubjectLibrary.BatteryRequest);
				content.put(JSONKeyLibrary.RequestAmount, amount);

				//Envío del mensaje
				send(ACLMessage.REQUEST, chargerID, ProtocolLibrary.Reload, "default", null, buildConversationId(), content);
			} catch (JSONException e){
				e.printStackTrace();
			}
		}
	}

	/**
	 * Recepción de la respuesta del cargador a la petición de una recarga.
	 * @author Dani
	 * @param msg mensaje a analizar.
	 * 
	 */
	private int onBatteryReceived(ACLMessage msg) {
		int batteryReceived = 0;
		if (msg.getPerformativeInt() == ACLMessage.INFORM){
			//Se ha producido una recarga
			try {
				JSONObject content = new JSONObject(msg.getContent());
				batteryReceived = content.getInt(JSONKeyLibrary.AmountGiven);
			} catch (JSONException e) {
				System.err.println("Error JSON al recibir batería");
				e.printStackTrace();
			}
		}        
		//not-understood
		else if (msg.getPerformativeInt() == ACLMessage.NOT_UNDERSTOOD){
			addMessageToLog(Log.RECEIVED, chargerID, msg.getProtocol(), SubjectLibrary.BatteryRequest, "Not-understood");
		}
		//refuse
		else if (msg.getPerformativeInt() == ACLMessage.REFUSE){
			try {
				JSONObject content = new JSONObject(msg.getContent());
				String errorReason = content.getString(JSONKeyLibrary.Error);
				addMessageToLog(Log.RECEIVED, chargerID, msg.getProtocol(), SubjectLibrary.BatteryRequest, errorReason);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		return batteryReceived;
	}

	/************************************************************************************************************************************
	 ******** Protocolos de subscripcion ************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Realiza las subscripciones del drone. El drone se puede subscribir a:
	 * 
	 * - DroneReachedGoal: un drone a llegado al Goal. 
	 * - DroneRecharged: La recarga de otro drone. 
	 * - YourMovements: los movimientos de un drone específico. 
	 * - AllMovements: a los movimientos de todos los drones. 
	 * - ConflictiveSections: a las casillas conflictivas. 
	 * 
	 * Nota: en este caso o por defecto solo se subscribe a droneReachedGoal.
	 * @author Jahiel
	 */
	protected void subscribe() {            
		subscribeDroneReachedGoal();
	}

	/**
	 * Se subscribe a cuando un drone alcanza el objetivo o entra en estado rezagado.
	 * 
	 * @author Jahiel
	 */
	private void subscribeDroneReachedGoal(){
		JSONObject content = new JSONObject();
		String conversationID;

		try {
			content.put(JSONKeyLibrary.Subject, SubjectLibrary.DroneReachedGoal);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		conversationID = buildConversationId();
		idsConversationSubscribe.put(SubjectLibrary.DroneReachedGoal, this.getAid().toString()+"#"+conversationID);

		send(ACLMessage.SUBSCRIBE, sateliteID, ProtocolLibrary.Subscribe, "confirmation", null,
				conversationID, content);
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Subscribe, SubjectLibrary.DroneReachedGoal, "");

		WaitResponseSubscriber();
	}

	/**
	 * Se subscribe a cuando un drone recargue. 
	 * Se informa cuanto a recargado.
	 * 
	 * @author Jahiel
	 */
	@SuppressWarnings("unused")
	private void subscribeDroneRecharged(){
		JSONObject content = new JSONObject();
		String conversationID;

		try {
			content.put(JSONKeyLibrary.Subject, SubjectLibrary.DroneRecharged);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		conversationID = buildConversationId();

		idsConversationSubscribe.put(SubjectLibrary.DroneRecharged, this.getAid().toString()+"#"+conversationID);

		send(ACLMessage.SUBSCRIBE, chargerID, ProtocolLibrary.Subscribe, "confirmation", null, conversationID, content);
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Subscribe, SubjectLibrary.DroneRecharged, "");

		WaitResponseSubscriber();
	}

	/**
	 * Se subscribe a los movimientos que realiza un drone específico.
	 * Si el drone se mueve se te informa de su movimiento.
	 * 
	 * @author Jahiel
	 * @param id Identificador del Drone a subscriberse.
	 */
	@SuppressWarnings("unused")
	private void subscribeYourMovements(AgentID id){
		JSONObject content = new JSONObject();
		String conversationID;

		try {
			content.put(JSONKeyLibrary.Subject, SubjectLibrary.YourMovements);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		conversationID = buildConversationId();

		idsConversationSubscribe.put(SubjectLibrary.YourMovements+id.getLocalName(), this.getAid().toString()+"#"+conversationID);
		send(ACLMessage.SUBSCRIBE, id, ProtocolLibrary.Subscribe, "confirmation", null,	conversationID, content);
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Subscribe, SubjectLibrary.YourMovements, "");

		WaitResponseSubscriber();
	}

	/**
	 * Se subscribe a los movimientos que realize cualquier drone. 
	 * Si cualquier drone se mueve se te informa de su movimiento.
	 * 
	 * @author Jahiel
	 */
	@SuppressWarnings("unused")
	private void subscribeAllMovements(){
		JSONObject content = new JSONObject();
		String conversationID;

		try {
			content.put(JSONKeyLibrary.Subject, SubjectLibrary.AllMovements);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		conversationID = buildConversationId();

		idsConversationSubscribe.put(SubjectLibrary.AllMovements, this.getAid().toString()+"#"+conversationID);

		send(ACLMessage.SUBSCRIBE, sateliteID, ProtocolLibrary.Subscribe, "confirmation", null,	conversationID, content);
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Subscribe, SubjectLibrary.AllMovements, "");

		WaitResponseSubscriber();
	}

	/**
	 * Se subscribe a las casillas conflictivas. 
	 * Si se anota una nueva se te informa.
	 * 
	 * @author Jahiel
	 */
	@SuppressWarnings("unused")
	private void subscribeConflictiveSections(){
		JSONObject content = new JSONObject();
		String conversationID;

		try {
			content.put(JSONKeyLibrary.Subject, SubjectLibrary.ConflictiveSections);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		conversationID = buildConversationId();

		idsConversationSubscribe.put(SubjectLibrary.ConflictiveSections, this.getAid().toString()+"#"+conversationID);

		send(ACLMessage.SUBSCRIBE, sateliteID, ProtocolLibrary.Subscribe, "confirmation", null,	conversationID, content);
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Subscribe, SubjectLibrary.ConflictiveSections, "");

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
			content.put(JSONKeyLibrary.Subject, name);
		} catch (JSONException e) {
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

		send(ACLMessage.CANCEL, destino, ProtocolLibrary.Subscribe, null, null,	this.idsConversationSubscribe.get(name), content);
		addMessageToLog(Log.SENDED, destino, ProtocolLibrary.Subscribe, name, "Cancel subscription");
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
		JSONObject content = null;
		String subject = "";
		try {
			content = new JSONObject(msg.getContent());
			subject = content.getString(JSONKeyLibrary.Subject);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		if(subscribers.containsKey(msg.getSender().toString()))
			throw new RefuseException(ErrorLibrary.AlreadySubscribed);		
		else{
			subscribers.put(msg.getSender().toString(), msg.getConversationId().toString());
			send(ACLMessage.ACCEPT_PROPOSAL, msg.getSender(), ProtocolLibrary.Subscribe, null, "confirmation", msg.getConversationId(), content);   
			addMessageToLog(Log.SENDED, msg.getSender(), ProtocolLibrary.Subscribe, subject, "");     
		}

	}

	/**
	 * Se manda la información de la decisión tomada por el drone a los drones que están subscritos
	 * a él para cuando se mueva.
	 * 
	 * @author jahiel
	 * @param X Posición X actual del drone en el mapa
	 * @param Y Posición Y actual del drone en el mapa
	 * @param decision Decisión tomada por el drone para moverse.
	 */
	private void sendInformYourMovement(int X, int Y, int decision){
		JSONObject content = new JSONObject();

		if(! subscribers.isEmpty()){
			try {
				content.put(JSONKeyLibrary.Subject, SubjectLibrary.YourMovements);
				int[] previousPosition = {X, Y};
				content.put("PreviousPosition", new JSONArray(previousPosition));
				content.put("Decision", decision);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			for(String name: subscribers.keySet()){
				send(ACLMessage.INFORM, new AgentID(name), ProtocolLibrary.Subscribe, null, null, this.subscribers.get(name), content); // Con el nombre del agente sacamos la ID-conversation    
			}
		}
	}

	/**
	 * Metodo llamado por el dispatcher para tratar el informe de que el cargador le ha concedido bateria a otro drone.
	 * @param msg Mensaje original
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected void onDroneChargedInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
		// TODO Auto-generated method stub
		//System.out.println("Informado de CHARGER");
	}

	/**
	 * Metodo llamado por el dispatcher para tratar el informe de la subscripción "ReachedGoal":
	 *  
	 *   El drone pregunta la traza al drone que ha detenido su movimiento y es que ha probocado el envio de este mensaje.
	 * Almacena dicha traza y actualiza su traza óptima hasta el momento. (se queda con la más corta). Por ultimo
	 * sale del modo StandBye para realizar la petición de salida.  
	 * 
	 * @author jahiel
	 * @param msg Mensaje original
	 * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
	 * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
	 */
	protected void onDroneReachedGoalInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
		if(state != FINISH_GOAL){
			JSONObject content = null;
			AgentID id = null;

			try {
				content = new JSONObject(msg.getContent());
			} catch (JSONException e) {
				throw new NotUnderstoodException(ErrorLibrary.NotUnderstood);
			}

			try {
				id = new AgentID(content.getString(JSONKeyLibrary.DroneID));
			} catch (JSONException e) {
				throw new FailureException(ErrorLibrary.FailureInformationAccess);
			}      

			Trace t =  askForDroneTrace(id); // pregunto la traza del drone que ha finalizado para ver si es mejor que la optima hasta el momento.
			otherTracesDrones.put(id.toString(), t);

			if(optimalTrace == null)
				optimalTrace = t;
			else
				for(Trace traceAux: otherTracesDrones.values()) // Me quedo con la traza mas corta de todas
					if(optimalTrace.size() > traceAux.size())
						optimalTrace = traceAux;
			
			this.leaveStandBy(); //Despertamos al drone
		}
	}

	protected void onYourMovementsInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException{
		// TODO
		System.out.println("Informado de YOURMOVEMENTs");
	}

	protected void onAllMovementsInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException{
		// TODO
		System.out.println("Informado de ALLMOVEMENT");
	}

	protected void onConflictiveSectionsInform(ACLMessage msg)throws IllegalArgumentException, RuntimeException, FIPAException{
		// TODO
		System.out.println("Informado de CONFLICTIVE");
	}


	/************************************************************************************************************************************
	 ******** Protocolo registro ********************************************************************************************************
	 ************************************************************************************************************************************/

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
			data.put(JSONKeyLibrary.Subject, SubjectLibrary.Register);
		} catch (JSONException e) {
			e.printStackTrace();
			throw new RuntimeException("Fallo en el registro: error al crear content del mensaje de envio");
		}

		send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Registration, "default", null, buildConversationId(), data);   
		addMessageToLog(Log.SENDED, sateliteID, ProtocolLibrary.Registration, SubjectLibrary.Register, "ID:" + this.getAid().toString() + " Name:" + this.getAid().name);     

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


	/************************************************************************************************************************************
	 ******** Dispatcher ****************************************************************************************************************
	 ************************************************************************************************************************************/

	/**
	 * Comprueba el campo JSONKeyLibrary.Subject dentro del Content y llama a la funcion correspondiente de ese protocolo.
	 * Si el JSONKeyLibrary.Subject no esta entre los aceptados envia un mensaje NOT_UNDERSTOOD
	 * 
	 * @author Jahiel
	 * @author Daniel
	 * @author Jonay
	 * @author Ismael
	 * @param msg Mensaje a analizar
	 * @return True si el dispatcher debe continuar su ejecucion. False en caso contrario.
	 * @throws JSONException 
	 */
	protected boolean dispatch(ACLMessage msg) throws JSONException{
		JSONObject content = new JSONObject(msg.getContent());
		String subject = content.getString(JSONKeyLibrary.Subject);
		boolean continueDispatching = true;
		JSONObject responseContent= new JSONObject();

		try{
			switch(subject){
				case SubjectLibrary.StragglerNotification:
					heStragglerReceive(msg);
					break;
					
				case SubjectLibrary.BatteryLeft:
					int batteryLeft = onBatteryQueried(msg);
					if(batteryLeft < 0|| batteryLeft > 75)
						throw new RefuseException(ErrorLibrary.UnespectedAmount);					
					else{
						responseContent.put(JSONKeyLibrary.Subject, SubjectLibrary.BatteryLeft);
						responseContent.put("EnergyLeft",batteryLeft);
						send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), responseContent);
						addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.BatteryLeft, String.valueOf(batteryLeft));   
					}
					break;
					
				case SubjectLibrary.Trace:
					Trace trc = onTraceQueried(msg);
					String traceJSON = traceToStringJSON(trc);
					responseContent.put(JSONKeyLibrary.Subject, SubjectLibrary.Trace);
					responseContent.put("trace", traceJSON);
					send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), responseContent);
					addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Trace, "");   
					break;
					
				case SubjectLibrary.Steps:
					int nSteps = onStepsQueried(msg);
					responseContent.put(JSONKeyLibrary.Subject, SubjectLibrary.Steps);
					responseContent.put("steps", nSteps);
					send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), responseContent);
					addMessageToLog(Log.SENDED, msg.getSender(), msg.getProtocol(), SubjectLibrary.Steps, String.valueOf(nSteps));   
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
					onAllMovementsInform(msg);
					break;
					
				case SubjectLibrary.ConflictiveSections:
					onConflictiveSectionsInform(msg);
					break;	
	
				default: 
					sendError(new NotUnderstoodException("Subject no encontrado"), msg);
					break;
			}
		}catch(FIPAException fe){
			System.err.println("Error FIPA");
			sendError(fe, msg);
		}catch(IllegalArgumentException e){
			System.err.println("Error de argumento");
			continueDispatching = treatMessageError(msg, e);
		}catch(RuntimeException e){
			e.printStackTrace();
			System.err.println("Error en ejecución");
			continueDispatching = treatRuntimeError(msg, e);
		}

		return continueDispatching;
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
			subject = content.getString(JSONKeyLibrary.Subject);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}

		boolean res = true;

		switch(subject){
		case SubjectLibrary.BatteryRequest:
		case SubjectLibrary.Trace:
			break;
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.DroneRecharged:
		case SubjectLibrary.YourMovements:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
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
			subject = content.getString(JSONKeyLibrary.Subject);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		boolean res = true;

		switch(subject){ 
		case SubjectLibrary.DroneReachedGoal:
		case SubjectLibrary.DroneRecharged:
		case SubjectLibrary.YourMovements:
		case SubjectLibrary.AllMovements:
		case SubjectLibrary.ConflictiveSections:
			break;
		default:
			break;
		}
		return res;
	}










	/************************************************************************************************************************/
	/************************************************************************************************************************/
	/************************************************************************************************************************/     

	/**
	 * Calcula la esquina que rodean dos posiciones.
	 * @author Alberto
	 * @param mov1 Movimiento que nos dejaria en la primera posición 
	 * @param mov2 Movimiento que nos dejaria en la segunda posición
	 * @return Valor del surrounding para esa esquina
	 */
	private int getCorner(int mov1, int mov2) {
		if(mov1 == (mov2 + 2) % 4)
			return surroundings[4];

		switch(mov1){
		case DECISION_EAST:
			return ((mov2==DECISION_SOUTH) ? surroundings[8] : surroundings[2]);
		case DECISION_SOUTH:
			return ((mov2==DECISION_WEST) ? surroundings[6] : surroundings[8]);
		case DECISION_WEST:
			return ((mov2==DECISION_NORTH) ? surroundings[0] : surroundings[6]);
		case DECISION_NORTH:
			return ((mov2==DECISION_EAST) ? surroundings[2] : surroundings[0]);
		default:
			return surroundings[4];
		}
	}

	/**
	 * Método para obtener un array con los movimientos libres del drone usando la memoria del mismo.
	 * @author Alberto
	 * @author Daniel
	 * @author Ismael
	 * @return Un array con lo que hay en las posiciones Este, Sur, Oeste y Norte a las que se podría mover, en ese orden.
	 */
	private int[] getValidMovements() {
		int movimientosLibres[] = new int[4];

		if(surroundings[1]==Map.LIBRE || surroundings[1]==Map.OBJETIVO){
			movimientosLibres[DECISION_NORTH] = droneMap.getValue(posX, posY - 1);
		}else{
			movimientosLibres[DECISION_NORTH] = surroundings[1];
		}
		if(surroundings[3]==Map.LIBRE || surroundings[3]==Map.OBJETIVO){
			movimientosLibres[DECISION_WEST] = droneMap.getValue(posX-1, posY);
		}else{
			movimientosLibres[DECISION_WEST] = surroundings[3];
		}
		if(surroundings[7]==Map.LIBRE || surroundings[7]==Map.OBJETIVO){
			movimientosLibres[DECISION_SOUTH] = droneMap.getValue(posX, posY + 1);
		}else{
			movimientosLibres[DECISION_SOUTH] = surroundings[7];
		}
		if(surroundings[5]==Map.LIBRE || surroundings[5]==Map.OBJETIVO){
			movimientosLibres[DECISION_EAST] = droneMap.getValue(posX + 1, posY);
		}else{
			movimientosLibres[DECISION_EAST] = surroundings[5];
		}

		return movimientosLibres;
	}

	/**
	 * Método para obtener un array con los valores combinados de surroundings y el mapa
	 * @author Alberto
	 * @author Jahiel
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

		if(state == FORCE_EXPLORATION ){
			for(int i=0; i<movimientosLibres.length; i++){
				if(movimientosLibres[i] == Map.VISITADO)
					movimientosLibres[i] = Map.LIBRE; 
			}
			movimientosLibres = blockMovement(movimientosLibres, movingBlock, true);
			int lastMove = trace.get(trace.size()-1).getMove();
			if(!enter_force)
				movimientosLibres = blockMovement(movimientosLibres, (lastMove+2)%4, false);
		}

		int[] movs = {5,7,3,1};
		int libre=0, nobs=0;
		for(int i=0; i<4; i++)
			if(movimientosLibres[movs[i]] == Map.OBSTACULO)
				nobs++;
			else
				libre = i;

		if(nobs == 3)
			movimientosLibres[movs[libre]] = Map.LIBRE;

		return movimientosLibres;
	}

	/**
	 * Se comprueba cual es el movimiento a bloquear y dicha casilla (y las casillas que hacen esquina con ella) se pone como Visitada para forzar al drone a no dirigirse
	 * hacia esa casilla.
	 * @author Jahiel
	 * @author Alberto
	 */
	public int[] blockMovement(int mov[], int movement, boolean wall){
		int index, indexWall1, indexWall2;

		switch(movement){
			case DECISION_NORTH:
				index = 1;
				indexWall1 = 0;
				indexWall2 = 2;
				break;
			case DECISION_WEST:
				index = 3;
				indexWall1 = 0;
				indexWall2 = 6;
				break;
			case DECISION_EAST:
				index = 5;
				indexWall1 = 2;
				indexWall2 = 8;
				break;
			case DECISION_SOUTH:
				index = 7;
				indexWall1 = 6;
				indexWall2 = 8;
				break;
			default:
				index = indexWall1 = indexWall2 = -1;
				break;
		}

		mov[index] = Map.VISITADO;

		if(wall){
			if(mov[indexWall1] == Map.LIBRE)
				mov[indexWall1] = Map.VISITADO;
			if(mov[indexWall2] == Map.LIBRE)
				mov[indexWall2] = Map.VISITADO;
		}

		return mov;
	}
	
	/**
	 * Getter del mapa, usado para el visualizador.
	 * @author Daniel
	 * @return el mapa del drone.
	 */
	public Map getDroneMap() {
		return droneMap;
	}

	/**
	 * @author Ismael
	 */
	@Override
	public void finalize() {
		practica.util.ImgMapConverter.mapToImg("src/maps/miresultado"+this.getAid().name+".png", droneMap);
		dispatcher.interrupt();
		super.finalize();
	}
}
