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

import practica.lib.ErrorLibrary;
import practica.lib.JSONKeyLibrary;
import practica.lib.ProtocolLibrary;
import practica.lib.SubjectLibrary;
import practica.map.Map;
import practica.trace.Trace;
import practica.util.ConflictiveBox;
import practica.util.GPSLocation;
import practica.util.Pair;

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
 */
public class Drone extends SuperAgent {
		private static final int SLEEPING = 0,   //Estados del drone
								 GO_TO_POINT_TRACE = 1,
								 EXPLORE_MAP = 2,
								 FOLLOW_TRACE = 3,
								 FORCE_EXPLORATION = 4,
								 LAGGING = 5, 			// Rezagado
								 UNDO_TRACE = 6,
								 FINISH_GOAL = 7;
								// END = 6;
		
		public static final int SCOUT = 0, SCOUT_IMPROVER = 1, FOLLOWER = 2, FREE = 3; //Comportamientos del drone
		
        private final int ESTADOREQUEST = 0, ESTADOINFORM = 1;
        private final int LIMIT_MOVEMENTS;
        private boolean exit;
        protected boolean goal;
        private int decision;
        private int state;
        private int behavior;
        protected int posX;
        protected int posY;
        protected float angle;
        protected double distance;
        protected int[] surroundings;
        protected Map droneMap;
        protected float distanceMin;
        protected int counterStop;
        protected int battery;
        protected double posiOX, posiOY;
        protected int currentPositionTracking;
        protected int movingBlock;  // movimiento a bloquear para el modo explorar otras decisiones.
        
        /** Decision de mover al norte */
        public static final int NORTE = 3;
        /** Decision de mover al oeste */
        public static final int OESTE = 2;
        /** Decision de mover al sur */
        public static final int SUR = 1;
        /** Decision de mover al este */
        public static final int ESTE = 0;
        /** Decision de terminar la ejecucion.
         *        @deprecated Usar END_SUCCESS o END_FAIL
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
        
        /** Número de casillas que debe pasar separado de un obstáculo para considerar otra zona obstáculo*/
		private static final int N_TO_OTHER_OBSTACLE = 10;
        
        private AgentID sateliteID;
        private AgentID chargerID;
        
        protected boolean dodging = false;
        protected int betterMoveBeforeDodging = -1;
        private int standBy;
        protected BlockingQueue<ACLMessage> answerQueue;
        protected BlockingQueue<ACLMessage> requestQueue;
        protected Thread dispatcher;
        private AgentID[] teammates;
        private Trace trace, optimalTrace;
        
        private ConflictiveBox conflictiveBox;
        private ArrayList<ConflictiveBox> currentConflictiveBox;
        private boolean conflictiveBoxReached;
        private int contSalida = 0;
        private boolean preEsq = false;
        private boolean postEsq = false;
        private boolean zonaObstaculo = false;
        private GPSLocation posSalidaTemporal;
        
        private HashMap<String, String> idsCombersationSubscribe;   // {nombreSubscripcion, id-combersation}
        private HashMap<String, String> subscribers;                                // {ID_Agente, id-combersation}

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
                //Ahora el limite depende del tamaño del mapa
                LIMIT_MOVEMENTS = mapWidth + mapHeight;
                
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
                
                idsCombersationSubscribe = new HashMap<String, String>();
                subscribers = new HashMap<String, String>();
                
                state = SLEEPING;
                optimalTrace = null;
                currentPositionTracking = -1;
                
                conflictiveBoxReached = false;
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
            startDispatcher();
    
            register();
           
            //subscribe(); Se anula hasta que no se sepa donde se usará
            
            do{   	
            		preEsq = dodging;// Guarda si estaba esquivando desde antes de calcular su nueva decisión
                    decision = think();
                    postEsq = dodging;
            		//sendInformYourMovement(posX, posY, decision); // activar si hay Subs. tipo YourMovements
                    
                    System.out.println("Decisión tomada: "+decision);
                    //Por si las moscas
                    if(decision != NO_DEC){
                            sendDecision(decision);
                            updateTrace(decision);
                            postUpdateTrace();
                    }
            }while(decision != END_FAIL && decision != END_SUCCESS);
    }
    
    /**
     * Metodo llamado tras la actualizacion de la traza. Ideal para comprobaciones de la traza y del rendimiento del drone.
     */
    protected void postUpdateTrace() {
            // TODO Todavía no está terminado, sólo casillas conflictivas
            
    }

    /**
     * Actualiza la traza del drone con la nueva decision.
     * @author Jonay
     * @param decision Decision tomada en think
     */
    protected void updateTrace(int decision) {
    	// TODO Todavía no está terminado, solo actualiza casillas conflictivas
            
    	
    	// Casillas conflictivas
    	boolean entrandoEsq = !preEsq && postEsq; //Antes no estaba esquivando y ahora sí
    	boolean saliendoEsq = preEsq && !postEsq; //Antes estaba esquivando y ya no
    	
    	if(state == SLEEPING){ //TODO: Revisar, "si se ha decidido quedar rezagado"
    		conflictiveBox.setDangerous(true);
    		Trace subtraza = trace.getSubtrace(conflictiveBox.getPosInicial(), new GPSLocation(posX, posY));
    		conflictiveBox.setLength(subtraza.size());
    		sendConflictiveBox();
    	}
    	
    	if(!zonaObstaculo && entrandoEsq){
    		zonaObstaculo=true;
    		conflictiveBox.setPosInicial(new GPSLocation(posX, posY));
    		conflictiveBox.setDecision(this.decision); // Se le asigna la decisión actual a la casilla
    	}
    	
    	if(zonaObstaculo){
    		if(!dodging){
    			contSalida++;
    			if(contSalida >= N_TO_OTHER_OBSTACLE){
    				zonaObstaculo = false;
    				conflictiveBox.setPosFinal(posSalidaTemporal);
    				conflictiveBox.setDangerous(false);
    				Trace subTrace = trace.getSubtrace(conflictiveBox.getPosInicial());
    				conflictiveBox.setLength(subTrace.size());
    				sendConflictiveBox();
    			}
    		}
    	}
    	
    	if(zonaObstaculo && saliendoEsq){
    		contSalida = 0;
    		posSalidaTemporal = new GPSLocation(posX, posY);
    	}
    }
    




	/**
     * Lanza el despachador.
     * @author Alberto
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
                    data.put("decision", decision);
                    data.put(JSONKeyLibrary.Subject, SubjectLibrary.IMoved);
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
        
        /*   Quitar comentarios si se pone una respuesta de posibles errores por parte del satélite
        try {
                answerQueue.take();
        } catch (InterruptedException e) {
                e.printStackTrace();
        }
        */
	}

    /************************************************************************************************************************************
     ******** Comunicación Explorador ********************************************************************************************
     ************************************************************************************************************************************/   
  /*
   * @author Ismael
   * comunica que esta STRAGGLER
   */
   public void iStraggler(){
	   JSONObject ask = new JSONObject();
	   try{
		   ask.put(JSONKeyLibrary.Subject, SubjectLibrary.Straggler);
		   send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Scout, "default", null, buildConversationId(), ask);
	   }catch(JSONException e){
		   e.printStackTrace();
	   }
   }
   /**
    * @author Ismael
    * recepcion del STRAGGLER de quien envió el mensage.
    * @return String
    */
   public String iStragglerReceive(){
	   String receive=null;
	   	ACLMessage msg=null;
	   
	   try {
			msg= answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	   
	   switch(msg.getPerformativeInt()){
	   case ACLMessage.INFORM:
		   try{
		   JSONObject content= new JSONObject(msg.getContent());
		  receive = content.getString(JSONKeyLibrary.Subject);		   
		   
		   }catch(JSONException e){
			   e.printStackTrace();
		   }
		   break;
	   case ACLMessage.FAILURE:
		   
		   break;
	   }
	   return receive;
   }
   /**
    * @author Ismael 
    * rececpcion de compañeros al mensaje STRAGGLER
    * @param who
    * @param what
    */
   public void heStragglerReceive(String who,String what){
	 	ACLMessage msg=null;
		   
		   try {
				msg= answerQueue.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		   
		   switch(msg.getPerformativeInt()){
		   case ACLMessage.INFORM:
			   try{
			   JSONObject content= new JSONObject(msg.getContent());
			  what = content.getString(JSONKeyLibrary.Subject);		   
			   who = content.getString(SubjectLibrary.StragglerNotification);
			   }catch(JSONException e){
				   e.printStackTrace();
			   }
			   break;
		   case ACLMessage.FAILURE:
			   
			   break;
		   }
   }
   
   public void askOut(){
	   JSONObject ask = new JSONObject();
	   try{
		   ask.put(JSONKeyLibrary.Subject, SubjectLibrary.Start);
		   send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Scout, "default", null, buildConversationId(), ask);
	   }catch(JSONException e){
		   e.printStackTrace();
	   }
   }

   public void askOutReceive(AgentID resId, int Mod){
	   ACLMessage msg=null;
	   
	   try {
			msg= answerQueue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	   
	   switch(msg.getPerformativeInt()){
	   case ACLMessage.INFORM:
		   try{
		   JSONObject content= new JSONObject(msg.getContent());
		   //AgentID id = new AgentID(content.getString(JSONKeyLibrary.Selected));
		   int Mode = content.getInt("Mode");
		   resId=null;
		   Mod=Mode;
		   }catch(JSONException e){
			   e.printStackTrace();
		   }
		   break;
	   case ACLMessage.FAILURE:
		   
		   break;
		   
		   default:
			   
			   break;
	   }
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
     *  @author jahiel
     * @return Decision tomada.
     */
    protected int think(){
            int tempDecision;
            
            if(goal){
            		state = FINISH_GOAL;
                    return END_SUCCESS;
            } 
            do{
                    try {
                            waitIfStandBy();
                    } catch (InterruptedException e) {
                            e.printStackTrace();
                    }
                    
                    if(state == SLEEPING){	
                    	sendRequestOutput();
                    	tempDecision = RETHINK;
                    }else{
                    	getStatus();
                        
                    	preBehavioursSetUp();
                    	tempDecision = checkBehaviours();
                    }
            }while(tempDecision == RETHINK);
            
            return tempDecision;
    }
    
    /**
     * Se realiza el tratamiento de la petición de salida:
     * - Si no soy el drone seleccionado no hacer nada y bloquearte.
     * - Si soy el seleccionado: cojer el MODO y en funcion del modo que me haya asignado el satelite comportarme de una forma u otra:
     *   TODO MODOS
     *   
     *   SLEEPING = 0,   //Estados del drone
								 GO_TO_POINT_TRACE = 1,
								 EXPLORE_MAP = 2,
								 FOLLOW_TRACE = 3,
								 FORCE_EXPLORATION = 4,
								 LAGGING = 5,
								 UNDO_TRACE = 6,
								 FINISH_GOAL = 7;
     *   
     * @author Jahiel
     */
   public void sendRequestOutput(){
    	int mode = -1;
    	AgentID drone_selected = new AgentID();
    	
    	//Ismael llamar o mandar la peticion de salida al satelite. Y espera a recibir mensaje (actualizar mode)
    	askOut();
    	if(drone_selected.equals(this.getAid())){
    		behavior = mode;
    	}else{
    		this.enterStandBy();
    	}
    	
    }
   
   /**
    * Se devuelve la de la traza óptima desde la que partirán los drones para seguir la traza. Esta posición es el comienzo de cuando el drone
    * inicia la bajada por primera vez. En caso de no existir tal punto (el goal se encuantra en un punto (x, 0) de devuelve el punto final de la traza.
    * @Jahiel 
    * @return Punto de partida.
    */
   	public int getInitialPosition(){
   		int i, size = optimalTrace.size(); 
   		
   		for(i=0; i<size; ++i){
   			if(optimalTrace.getLocation(i).getPositionY()>0)
   				return i-1;
   		}
   		
   		return i-1; 
   	}
   
    /**
     * Realiza cualquier tipo de actualizacion del estado del drone antes de comprobar los comportamientos. Si la comprobacion 
     * de los comportamientos se ejecuta de nuevo debido a un RETHINK esta funcion se evalua de nuevo.
     * 
     *  posiOX= (posX + (Math.cos(angle) * distance));
    	     	   posiOY= (posY + (Math.sin(angle)*distance));
     */
   protected void preBehavioursSetUp() {  
    	
    		switch(state){
    		case SLEEPING:
    			if(behavior == SCOUT){
    				state = EXPLORE_MAP;
    			}else if(behavior == FOLLOWER || behavior == SCOUT_IMPROVER){
    				state = GO_TO_POINT_TRACE;
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
    			int indexPosInic = getInitialPosition();
    			
    			posiOX = optimalTrace.getLocation(indexPosInic).getPositionX();
        		posiOY = optimalTrace.getLocation(indexPosInic).getPositionY();
        		
    			if(posX == posiOX && posY == posiOY)
    				state = FOLLOW_TRACE;
    			
    			break;
    		case FOLLOW_TRACE:
    			
    			currentPositionTracking++;
    			break;
    		case FORCE_EXPLORATION:
    			
    			break;
    		/*case LAGGING:
    			if(behavior == FOLLOWER || behavior == SCOUT_IMPROVER){
    				state = FOLLOW_TRACE;
    			}else if(behavior == FREE)
    				state = LOST;
    			break;
    		case ROAD_FORCE:
    			break;*/
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
    		
            return NO_DEC;
    }

    /**
     * Primer comportamiento intermedio del drone. Es el segundo en ejecutarse al recorrer los comportamientos.
     * @param listaMovimientos Lista de movimientos a analizar
     * @param args Argumentos adicionales
     * @return Decision tomada
     */
    protected int firstBehaviour(List<Pair> listaMovimientos, Object[] args) {
    	
    	   return NO_DEC;
    }

    /**
     * Segundo comportamiento intermedio del drone. Es el tercero en ejecutarse al recorrer los comportamientos.
     * @author Alberto
     * @author Jahiel
     * @param listaMovimientos Lista de movimientos a analizar
     * @param args Argumentos adicionales
     * @return Decision tomada
     */
    protected int secondBehaviour(List<Pair> listaMovimientos, Object[] args) {

    	if(state == this.FOLLOWER){
    		return optimalTrace.get(currentPositionTracking).getMove();
    	}else
            return NO_DEC;
           
    }
    
    /**
     * Tercer comportamiento intermedio del drone. Es el cuarto en ejecutarse al recorrer los comportamientos.
     * @param listaMovimientos Lista de movimientos a analizar
     * @param args Argumentos adicionales
     * @return Decision tomada
     */
    protected int thirdBehaviour(List<Pair> listaMovimientos, Object[] args) {
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
                     /**
                     @author Jahiel
                     */
                     if(listaMovimientos.get(0).getSecond() == movingBlock){
                     	state = EXPLORE_MAP;
                     }
                     
                     betterMoveBeforeDodging=listaMovimientos.get(0).getSecond();
                     System.out.println("Entrando dodging: "+betterMoveBeforeDodging);
             }
             
             return NO_DEC;
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
    protected int criticalBehaviour(List<Pair> listaMovimientos, Object[] args) {
            //Si no le queda batería el drone la pide y se queda en standby.
    	int amount=75;
            if (battery == 0){
                    askForBattery(amount);
                    enterStandBy();
                  
                   return RETHINK;
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

    
/*********Funciones auxiliares *****************************************************************************************************************/
    
    /**
     * Resuelve empates entre movimientos
     * @param listaMovimientos Lista de movimientos a analizar
     * @param tieargs Argumentos adicionales
     * @return Decision tomada o NO_DEC si no ha podido resolver el empate
     */
    protected int tieResolution(List<Pair> listaMovimientos, Object[] tieargs) {
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

          
            float calculoDist=0;         

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
            

            basicond[ESTE]=         validSqr[5]==Map.LIBRE        && !(validSqr[2]==Map.VISITADO || validSqr[8]==Map.VISITADO);
            basicond[SUR]=                 validSqr[7]==Map.LIBRE        && !(validSqr[6]==Map.VISITADO || validSqr[8]==Map.VISITADO);
            basicond[OESTE]=         validSqr[3]==Map.LIBRE        && !(validSqr[0]==Map.VISITADO || validSqr[6]==Map.VISITADO);
            basicond[NORTE]=         validSqr[1]==Map.LIBRE        && !(validSqr[0]==Map.VISITADO || validSqr[2]==Map.VISITADO);

            if(!(basicond[ESTE] || basicond[SUR] || basicond[OESTE] || basicond[NORTE])){
                    basicond[ESTE]=         validSqr[5]==Map.LIBRE        && !(validSqr[2]==Map.VISITADO && validSqr[8]==Map.VISITADO);
                    basicond[SUR]=                 validSqr[7]==Map.LIBRE        && !(validSqr[6]==Map.VISITADO && validSqr[8]==Map.VISITADO);
                    basicond[OESTE]=         validSqr[3]==Map.LIBRE        && !(validSqr[0]==Map.VISITADO && validSqr[6]==Map.VISITADO);
                    basicond[NORTE]=         validSqr[1]==Map.LIBRE        && !(validSqr[0]==Map.VISITADO && validSqr[2]==Map.VISITADO);
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
    
    
    
    
    
    
    
    
    
    
    
    /************************************************************************************************************************************
     ******** Recepcion de mensajes *****************************************************************************************************
     ************************************************************************************************************************************/
    
    /**
     * Recibe el mensaje y lo encola. La decision de a que cola mandarlo depende enteramente del protocolo del mensaje (campo protocol).<br>
     * Estado actual:<br>
     * answerQueue -> SendMeMyStatus, IMoved<br>
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
        System.out.println("RECIBO DRONE: "+subject);
        switch(subject){
       
        	
        case SubjectLibrary.StragglerNotification:
        	queue=answerQueue;
        	break;
        case SubjectLibrary.Start:
        	queue=answerQueue;
        	break;
        case SubjectLibrary.Straggler:
        	queue=answerQueue;
        	break;
        case SubjectLibrary.Status:
        	queue=answerQueue;
        	break;
        //case SubjectLibrary.IMoved:
        case SubjectLibrary.End:
        	queue=answerQueue;
        	break;
        case SubjectLibrary.MapGlobal:
        	queue=answerQueue;
        	break;
        case SubjectLibrary.DroneBattery:
        	queue = answerQueue;
        	break;
        case SubjectLibrary.IdAgent:
        	queue = answerQueue;
        	break;
        case SubjectLibrary.Position:
        	
        	queue = answerQueue;
        	            	
        	break;
        case SubjectLibrary.GoalDistance:
        	queue = answerQueue;
        	break;
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
        case SubjectLibrary.YourMovements:
        case SubjectLibrary.DroneRecharged:
        case SubjectLibrary.DroneReachedGoal:
        case SubjectLibrary.AllMovements:
        case SubjectLibrary.ConflictiveSections:
        case SubjectLibrary.BatteryRequest:
        
        	if(msg.getPerformativeInt() == ACLMessage.ACCEPT_PROPOSAL){
                queue = answerQueue;
        	}else
                queue = requestQueue;
                
                break;        
        default:
        		System.out.println("Drone: "+subject);
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
     ******** Protocolo de finalización 
     * @throws JSONException *************************************************************************************************
     ************************************************************************************************************************************/
    /* función que llama al finalizador del satélite.
     * Si la respuesta es wait espera, si es otra finaliza.
     * @author Ismael
     * @param value valor de decision
     */
    protected void onFinalize() {
    		
    	ACLMessage msg=null;	
    		
    		try {
				msg = answerQueue.take();
				
				if(!msg.getPerformative().equals(ACLMessage.INFORM)){
					throw new RuntimeException("Error en la recepcion del tipo de mensaje");
				}
				try{
					JSONObject content = new JSONObject(msg.getContent());
					String stat = content.getString("content");
					/*
					if(stat.equals("wait")){
						//enterStandBy(); lo dejo aquí por si acaso.
					}
					else*/
						if(stat.equals("End")){
						finalize(); 
					}
				}catch(JSONException e){
					e.printStackTrace();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
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
     * Pregunta a un drone por el número de pasos que ha dado
     * @author Jonay
     * @return El número de pasos
     */
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
	private int askBattery(){
		JSONObject requestContent = new JSONObject();
		ACLMessage answer=null;
		int resultado = -1;
		
		try {
			requestContent.put(JSONKeyLibrary.Subject, SubjectLibrary.ChargerBattery);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		send(ACLMessage.QUERY_REF, chargerID, ProtocolLibrary.Information, "Get-RemainingBattery", null, buildConversationId(), requestContent);
		
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
                    ask.put(JSONKeyLibrary.Subject, "MapGlobal");
                    
                    send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, null, null, buildConversationId(), ask);
            } catch (JSONException e){
                    e.printStackTrace();
            }
    }
    /**
     * @author Ismael
     * Recive mapa común.
     * @param msg
     * @return int[][]
     */
    protected void askForMapReceive(Map m){
    	JSONObject content;
    	int H,W,matriz[][] = null;
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
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }
            
            case ACLMessage.INFORM:
                    try{
                            content = new JSONObject(msg.getContent());
                            H= content.getInt("Height");
                            W= content.getInt("Width");
                            matriz = new int[H][W];
                            JSONArray data = (JSONArray) content.get("Values");
                    
                            for(int i=0,z=0;i<H;i++){
                                    for(int j=0;j<W;j++,z++){
                                            try {
												m.setValue(i, j, data.getInt(z));
											} catch (Exception e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
                                    }
                            }
                            
                    } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }
                    break;
            default: 
                    throw new RuntimeException("Fallo en cojer respuesta del satelite");
            }
            
           
    }
    
    /**
     * Pide el nombre ID de un nombre.
     * @author Ismael
     * @param name
     */
    protected void askForID(String name){
            
            JSONObject ask = new JSONObject();
           
            try{
                    ask.put(JSONKeyLibrary.Subject, SubjectLibrary.IdAgent);
                    ask.put(SubjectLibrary.Name, name);
                    send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), ask);
            } catch (JSONException e){
                    e.printStackTrace();
            }
    }
    /**
     * @author Ismael
     * Recive ID para un nombre
     * @param msg
     * @return AgentID
     */
     protected AgentID askForIDReceive(){ 
    	 System.out.println("MECAGO EN MI PUTA VIDAAAAAA");
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
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }
            
            case ACLMessage.INFORM:
                    try{
                            content = new JSONObject(msg.getContent());
                            id = new AgentID(content.getString("ID"));
                    } catch (JSONException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }
                    break;
            default: 
                    throw new RuntimeException("Fallo en cojer respuesta del satelite");
            }
            System.out.println("Estoy en el receptor " + id);
            return id;
    }
    /**
     * Pide distancia de un drone especifico
     * @author Ismael
     * @param ID
     */
    protected void askForGoal(AgentID id){
            JSONObject ask = new JSONObject();
           
           
           
            try{
                    ask.put(JSONKeyLibrary.Subject, "GoalDistance");
                    ask.put("ID", id);
                    send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), ask);
            } catch (JSONException e){
                    e.printStackTrace();
            }
    }
    /**
     * @author Ismael
     * Recibe distancia meta para un agente
     * @param msg
     * @return
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
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }
            
            case ACLMessage.INFORM:
                    try{
                            content = new JSONObject(msg.getContent());
                            Ggoal = content.getDouble("Distance");
                    } catch (JSONException e) {
                            // TODO Auto-generated catch block
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
            } catch (JSONException e){
                    e.printStackTrace();
            }
    }
    /**
     * @author Ismael
     * Recoge posicion solicitada
     * @param msg
     * @return int[2]
     */
    protected int[] askPositionReceive(){
    		JSONObject content;
    		int pos[] =new int[2];
            ACLMessage msg=null;
            System.out.println("llego al receive");
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
     * @param identidad agente.
     */
    protected void askForMyBatterySatellite(AgentID id){
            JSONObject ask = new JSONObject();
          
            try{
                    ask.put(JSONKeyLibrary.Subject, "DroneBattery");
                    ask.put("AgentID", id);
                    //Envio mensaje
                    send(ACLMessage.QUERY_REF, sateliteID, ProtocolLibrary.Information, null, null, buildConversationId(), ask);
            } catch (JSONException e){
                    e.printStackTrace();
            }
    }
    /**
     * @author Ismael
     * Recoge bateria del satelite solicitada para un drone
     * @param msg
     * @return int
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
            ACLMessage msg=null;
            JSONObject contenido = null;
            int X = posX, Y = posY;  // posicion previa 
            
            try {
                    contenido = new JSONObject();
                    contenido.put(JSONKeyLibrary.Subject, SubjectLibrary.Status);
            } catch (JSONException ex) {
                    ex.printStackTrace();
                    Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            send(ACLMessage.REQUEST, sateliteID, ProtocolLibrary.Information, "default", null, buildConversationId(), contenido);
           /** 
            try {
                    msg = answerQueue.take();
            } catch (InterruptedException e) {
                    e.printStackTrace();
            }
           **/ 
            
           
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
    		//hola
    		try {
				msg=answerQueue.take();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
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
                    
                    String campo=null;
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
                    if(content.has(JSONKeyLibrary.ConflictBox)){
                    	//Extraigo la información
                    	conflictiveBoxReached = true;
                    	String conflictiveBoxData = content.getString(JSONKeyLibrary.ConflictiveBox);
                    	//Defino el tipo de datos que voy a extraer del JSON
                    	Type collectionType = new TypeToken<ArrayList<ConflictiveBox>>(){}.getType();	
                    	//Extraigo los datos
                    	currentConflictiveBox = new Gson().fromJson(conflictiveBoxData, collectionType); 
                    	
                    }else
                    	conflictiveBoxReached = false;
                    
                    
                    /**
                    aux = contenido.getJSONObject("gonio");
                    angle = (float) aux.getDouble("alpha");
                    //Recoger distancia.
                    distance= (float) aux.getDouble("dist");                                
                    **/
                    /*Recogida y comprobación del campo goal.
                    campo= contenido.getString("goal");
                    if(campo.equals("Si")){
                            goal=true;
                    }
                    else if(campo.equals("No")){
                            goal=false;
                    }
                    */
                    // Corregido, alpha estaba en aux y no en contenido

                    // surroundings=(int[]) contenido.get("radar"); // No se puede hacer así
                    // Una opción sería usando JSONArray, se tendría que mirar como pasarlo a un array normal tras sacarlo
                    //JSONArray jsArray = contenido.getJSONArray("radar");
                    
                    /* TODO: recupera bien lo que tiene al rededor (lo muestro por consola bien) 
                     * Pero parece que si lo pongo no termina en el mapa1 y si no lo pongo sí.
                     */
    	 
                    /*
                    for (int i=0; i < jsArray.length(); i++){
                            surroundings[i] = jsArray.getInt(i);
                    }
                    // Compruebo si se reciben bien los alrededores:
                    System.out.println("Alrededores del Dron: ");
                    System.out.println("|"+surroundings[0]+", "+surroundings[1]+", "+surroundings[2]+"|");
                    System.out.println("|"+surroundings[3]+", "+surroundings[4]+", "+surroundings[5]+"|");
                    System.out.println("|"+surroundings[6]+", "+surroundings[7]+", "+surroundings[8]+"|");
                     */               
            } catch (JSONException ex) {
                    System.out.println("numeritos");
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
            if (battery == 0 && amount > 0 && amount <= 75){ //Comprobación extra
                    JSONObject content = new JSONObject();
                    try {
                            //Construcción del JSON
                            content.put(JSONKeyLibrary.Subject, SubjectLibrary.BatteryRequest);
                            content.put(JSONKeyLibrary.RequestAmount, amount);
                            
                            //Envío del mensaje
                            send(ACLMessage.REQUEST, chargerID, ProtocolLibrary.Reload, "default", null, buildConversationId(), content);
                    } catch (JSONException e){
                            e.printStackTrace();
                            //Se recoge la RefuseExcetion y se envia el END_FAIL
                            if(e.equals(ErrorLibrary.NotBatteryAnymore)){
                            	//sendDecision(END_FAIL);
                            	amount= END_FAIL;
                            }
                    }
            }
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
                            battery += content.getInt(JSONKeyLibrary.AmountGiven);
                            System.out.println("Drone con ID = " + this.getAid() + " - Batería recibida, ahora tengo " + battery);
                            leaveStandBy();
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
                            decision=END_FAIL;
                    } catch (JSONException e) {
                            System.out.println("Error JSON al gestionar el refuse en la batería");
                            e.printStackTrace();
                    }
            }
    }
    
    
    
    
    
    
    
    
    
    
    /************************************************************************************************************************************
     ******** Protocolos de subscripcion ************************************************************************************************
     ************************************************************************************************************************************/

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
            /*
            subscribeDroneRecharged();
            subscribeAllMovements();
            subscribeConflictiveSections();
            
            for(int i=0; i<teammates.length; i++)
                    subscribeYourMovements(teammates[i]);
            */
    }
    
    /**
     * Se subscribe a la llegada de un drone al objetivo.
     * Se te informa del drone que ha finalizado exitosamente.
     * 
     * @author Jahiel
     */
    private void subscribeDroneReachedGoal(){
            JSONObject content = new JSONObject();
            String combersationID;
            
            try {
                    content.put(JSONKeyLibrary.Subject, SubjectLibrary.DroneReachedGoal);
            } catch (JSONException e) {
                    // esto nunca pasa porque la clave nunca esta vacía
                    e.printStackTrace();
            }
            
            combersationID = buildConversationId();
            idsCombersationSubscribe.put(SubjectLibrary.DroneReachedGoal, this.getAid().toString()+"#"+combersationID);
            
            send(ACLMessage.SUBSCRIBE, sateliteID, ProtocolLibrary.Subscribe, "confirmation", null,
                            combersationID, content);
            
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
                    String combersationID;
                    
                    try {
                            content.put(JSONKeyLibrary.Subject, SubjectLibrary.DroneRecharged);
                    } catch (JSONException e) {
                            // esto nunca pasa porque la clave nunca esta vacía
                            e.printStackTrace();
                    }
                    

                    combersationID = buildConversationId();
                    
                    idsCombersationSubscribe.put(SubjectLibrary.DroneRecharged, this.getAid().toString()+"#"+combersationID);
                    
                    send(ACLMessage.SUBSCRIBE, chargerID, ProtocolLibrary.Subscribe, "confirmation", null,
                                    combersationID, content);
                    
                    WaitResponseSubscriber();
    }
    
    /**
     * Se subscribe a los movimientos que realiza un drone específico.
     * Si el drone se mueve se te informa de su movimiento.
     * 
     * @author Jahiel
     * @param id Identificador del Drone a subscriberse.
     */
    private void subscribeYourMovements(AgentID id){
            JSONObject content = new JSONObject();
            String combersationID;
            
            try {
                    content.put(JSONKeyLibrary.Subject, SubjectLibrary.YourMovements);
            } catch (JSONException e) {
                    // esto nunca pasa porque la clave nunca esta vacía
                    e.printStackTrace();
            }

            combersationID = buildConversationId();
            
            idsCombersationSubscribe.put(SubjectLibrary.YourMovements+id.getLocalName(), this.getAid().toString()+"#"+combersationID);
            send(ACLMessage.SUBSCRIBE, id, ProtocolLibrary.Subscribe, "confirmation", null,
                            combersationID, content);
                    
            WaitResponseSubscriber();
            
            
    }
    
    /**
     * Se subscribe a los movimientos que realize cualquier drone. 
     * Si cualquier drone se mueve se te informa de su movimiento.
     * 
     * @author Jahiel
     */
    private void subscribeAllMovements(){
            JSONObject content = new JSONObject();
            String combersationID;
            
            try {
                    content.put(JSONKeyLibrary.Subject, SubjectLibrary.AllMovements);
            } catch (JSONException e) {
                    // esto nunca pasa porque la clave nunca esta vacía
                    e.printStackTrace();
            }
            
            combersationID = buildConversationId();
            
            idsCombersationSubscribe.put(SubjectLibrary.AllMovements, this.getAid().toString()+"#"+combersationID);
            
            send(ACLMessage.SUBSCRIBE, sateliteID, ProtocolLibrary.Subscribe, "confirmation", null,
                            combersationID, content);
            
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
            String combersationID;
            
            try {
                    content.put(JSONKeyLibrary.Subject, SubjectLibrary.ConflictiveSections);
            } catch (JSONException e) {
                    // esto nunca pasa porque la clave nunca esta vacía
                    e.printStackTrace();
            }
            
            combersationID = buildConversationId();
            
            idsCombersationSubscribe.put(SubjectLibrary.ConflictiveSections, this.getAid().toString()+"#"+combersationID);
            
            send(ACLMessage.SUBSCRIBE, sateliteID, ProtocolLibrary.Subscribe, "confirmation", null,
                            combersationID, content);
            
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
                    content.put(JSONKeyLibrary.Subject, name);
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
            
            send(ACLMessage.CANCEL, destino, ProtocolLibrary.Subscribe, null, null,
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
            JSONObject content = null;
			try {
				content = new JSONObject(msg.getContent());
			} catch (JSONException e) {
				e.printStackTrace();
			}
    
            if(subscribers.containsKey(msg.getSender().toString())){
                        throw new RefuseException(ErrorLibrary.AlreadySubscribed);
            }else{ /*if(teammates.length != 2){      // Esta comprobación pienso que tambien hay que hacerla al mandar la petición.
            	System.out.println("REFUSE 2  !!!!!!");
            	
                    throw new RefuseException(ErrorLibrary.MissingAgents);
            }else{*/
                    subscribers.put(msg.getSender().toString(), msg.getConversationId().toString());
                    send(ACLMessage.ACCEPT_PROPOSAL, msg.getSender(), ProtocolLibrary.Subscribe, null, "confirmation", msg.getConversationId(), content);        
            }
            
    }
    
    private void sendInformYourMovement(int X, int Y, int decision){
        JSONObject content = new JSONObject();
        
        if(! subscribers.isEmpty()){
            try {
                content.put(JSONKeyLibrary.Subject, SubjectLibrary.YourMovements);
                int[] previousPosition = {X, Y};
                content.put("PreviousPosition", new JSONArray(previousPosition));
                content.put("Decision", decision);
            } catch (JSONException e) {
                // nunca sucede porque las claves no son vacias
                e.printStackTrace();
            }
        
            for(String name: subscribers.keySet()){
                send(ACLMessage.INFORM, new AgentID(name), ProtocolLibrary.Subscribe, null, null,
                                this.subscribers.get(name), content);                         // Con el nombre del agente sacamos la ID-combersation
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
    	System.out.println("Informado de CHARGER");
            
    }

    /**
     * Metodo llamado por el dispatcher para tratar el informe de que otro drone a llegado a la meta.
     * 
     * @author jahiel
     * @param msg Mensaje original
     * @throws IllegalArgumentException En caso de error en el mensaje original (performativa equivocada, content erroneo...).
     * @throws RuntimeException En caso de error en el procesamiento del mensaje (comportamiento del drone ante el mensaje).
     */
    protected void onDroneReachedGoalInform(ACLMessage msg) throws IllegalArgumentException, RuntimeException, FIPAException {
    	JSONObject content = null;
    	AgentID id = null;
    	
    	try {
			content = new JSONObject(msg.getContent());
		} catch (JSONException e) {
			throw new NotUnderstoodException(ErrorLibrary.NotUnderstood);
		}
         
        try {
			id = new AgentID(content.getString(JSONKeyLibrary.Decision));
		} catch (JSONException e) {
			throw new FailureException(ErrorLibrary.FailureInformationAccess);
		}      
        
        Trace trace =  askForDroneTrace(id); // pregunto la traza del drone que ha finalizado para ver si es mejor que la optima 
        									 // hasta el momento.
        
        if(optimalTrace == null)
        	optimalTrace = trace;
        else{
        	if(optimalTrace.size() > trace.size())
        		optimalTrace = trace;
        }
        
        this.leaveStandBy(); //Despertamos al drone
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

    
    
    
    
    
    
    
    
    /************************************************************************************************************************************
     ******** Dispatcher ****************************************************************************************************************
     ************************************************************************************************************************************/
    
    /**
     * Comprueba el campo JSONKeyLibrary.Subject dentro del Content y llama a la funcion correspondiente de ese protocolo.
     * Si el JSONKeyLibrary.Subject no esta entre los aceptados envia un mensaje NOT_UNDERSTOOD
     * 
     * @author Jahiel
     * @author Dani
     * @author Jonay
     * @author Ismael
     * @param msg Mensaje a analizar
     * @return True si el dispatcher debe continuar su ejecucion. False en caso contrario.
     * @throws JSONException 
     */
    protected boolean dispatch(ACLMessage msg) throws JSONException{
            JSONObject content = new JSONObject(msg.getContent());
            String subject = content.getString(JSONKeyLibrary.Subject);
            boolean res = true;
            JSONObject resp= new JSONObject();
            
            try{
                    switch(subject){
                    case SubjectLibrary.BatteryLeft:
                            int batteryLeft = onBatteryQueried(msg);
                            if(batteryLeft < 0|| batteryLeft > 75){
                                    throw new RefuseException(ErrorLibrary.UnespectedAmount);
                            }
                            else{
                                    resp.put(JSONKeyLibrary.Subject, SubjectLibrary.BatteryLeft);
                                    resp.put("EnergyLeft",batteryLeft);
                                    send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), resp);
                            }
                            break;
                    case SubjectLibrary.Trace:
                            Trace trc = onTraceQueried(msg);
                            String traceJSON = traceToStringJSON(trc);
                            resp.put(JSONKeyLibrary.Subject, SubjectLibrary.Trace);
                            resp.put("trace", traceJSON);
                            send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), resp);
                            break;
                    case SubjectLibrary.Steps:
                            int nSteps = onStepsQueried(msg);
                            resp.put(JSONKeyLibrary.Subject, SubjectLibrary.Steps);
                            resp.put("steps", nSteps);
                            send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), resp);
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
                    case SubjectLibrary.BatteryRequest:
                            onBatteryReceived(msg);
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
                    res = treatMessageError(msg, e);
            }catch(RuntimeException e){
            		System.err.println("Error en ejecución");
                    res = treatRuntimeError(msg, e);
            }
            
            return res;
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
                    subject = content.getString(JSONKeyLibrary.Subject);
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
     * Método para obtener un array con los movimientos libres del drone usando la memoria del mismo.
     * @author Alberto
     * @author Daniel
     * @author Ismael
     * @return Un array con lo que hay en las posiciones Este, Sur, Oeste y Norte a las que se podría mover, en ese orden.
     */
    // POST DIAGRAMA DE CLASES
    private int[] getValidMovements() {
            int movimientosLibres[] = new int[4];
            
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
            	movimientosLibres = blockMovement(movimientosLibres, movingBlock);
            }
            
            return movimientosLibres;
    }

    /**
     * Se comprueba cual es el movimiento a bloquear y dicha casilla se pone como Visitada para forzar al drone a no dirigirse
     * hacia esa casilla.
     * @author Jahiel
     */
    public int[] blockMovement(int mov[], int movement){
    	int index;
    	
    	switch(movement){
    	case NORTE:
    		index = 1;
    		break;
    	case OESTE:
    		index = 3;
    		break;
    	case ESTE:
    		index = 5;
    		break;
    	case SUR:
    		index = 7;
    		break;
    	default:
    		index = -1;
    		break;
    	}
    	
    	mov[index] = Map.VISITADO;
    	
    	return mov;
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
     */
    @Override
    public void finalize() {
            System.out.println("Agente " + this.getName() + " ha finalizado");
            practica.util.ImgMapConverter.mapToImg("src/maps/miresultado"+this.getAid().name+".png", droneMap);
            super.finalize();
    }



/******************************************************************************/
/* Métodos usados para testear la pedida y respuesta de información del drone. 
 * TODO: Borrar
*/
	public void testPedirInformacion(AgentID agentID) {
		System.out.println("Batería recibida en" + this.getAid().name+" del " +agentID.name+": "+ askForDroneBattery(agentID));
		System.out.println("El número de pasos que ha dado ha sido: "+askForDroneSteps(agentID));
		Trace t = askForDroneTrace(agentID);
		System.out.println("Su traza: "+t.toString(t.DECISION_AND_POSITION));
	}
	
	public void setTrace(Trace t){
		this.trace = t;
	}
/**************************************************************************/
}
