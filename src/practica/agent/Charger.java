package practica.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import practica.lib.ErrorLibrary;
import practica.lib.ProtocolLibrary;
import practica.lib.SubjectLibrary;
import es.upv.dsic.gti_ia.architecture.FIPAException;
import es.upv.dsic.gti_ia.architecture.NotUnderstoodException;
import es.upv.dsic.gti_ia.architecture.RefuseException;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

/**
 * 
 * @author jahiel
 * @author Dani
 * No documento la clase porque pronto cambiara todo
 * 
 */
public class Charger extends SuperAgent {
	private BlockingQueue<ACLMessage> requestQueue;  //de momento es una cola sin priridad, primero en llegar primero en ser atendido
	private AgentID IDSatellite;
	private int battery;
	private int conversationCounter;
	private HashMap<String, String> subscribers;
	private BlockingQueue<ACLMessage> answerQueue;
	
	/**
	 * @author Jahiel
	 * @author Dani
	 * @param aid
	 * @param Levelbattery
	 * @param satellite
	 * @throws Exception
	 */
	public Charger(AgentID aid, int Levelbattery, AgentID satellite) throws Exception {
		super(aid);
		
		requestQueue = new LinkedTransferQueue<ACLMessage>();
		answerQueue = new LinkedTransferQueue<ACLMessage>();
		battery = Levelbattery;
		IDSatellite = satellite;
		conversationCounter = 0;
		subscribers = new HashMap<String, String>();
	}

	/**
	 * Hebra encargada del tratamiento de la cola sin prioridad de mensajes.
	 * @author Jahiel
	 * @author Andres
	 * @param msg Mensaje ACL recibido y listo para introducir en la cola.
	 */
	
	@Override
	public void onMessage(ACLMessage msg){
		/*
		try {
			requestQueue.put(msg);
		} catch (InterruptedException e) {
			
			onMessage(msg);  //Nose si esto es una ida de olla pero se supone que la iterrupcion se genera para 
			                 //despertar al agente porque la cola ya tiene espacio.
		}
		*/
		JSONObject content;
		String subject = null;
		
		try{
			content = new JSONObject(msg.getContent());
			subject = content.getString("Subject");
		}
		catch (JSONException e){
			e.printStackTrace();
		}
		BlockingQueue<ACLMessage> q = null;     // Hasta aquí, coger mensaje y su subject.
		
		switch(subject)
		{
			case SubjectLibrary.ChargerBattery:
			case SubjectLibrary.Charge:
			case SubjectLibrary.DetailedCharges:
			case SubjectLibrary.BatteryRequest:
				q=requestQueue;
				break;
			
			case SubjectLibrary.Position:
			case SubjectLibrary.GoalDistance:
			case SubjectLibrary.DroneBattery: //No sé qué mas cosas podría pedir el Cargador.
				q=answerQueue;
				break;
			
			default:
			try {
				throw new NotUnderstoodException("Subject no encontrado.");
			} catch (NotUnderstoodException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
				break;
		}
		
		if(q != null){
			try{
				q.put(msg);
			}
			catch (InterruptedException e){
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Metodo execute del agente. Aquie se añade la lógica de ejecución para el agente Cargador.
	 * @author Jahiel
	 * @author Dani
	 * @author Andres
	 */
	@Override
	public void execute(){ 
		ACLMessage msg = null;
		
		while (true){
			try {
				msg = (ACLMessage) requestQueue.take();
			} catch (InterruptedException e) {
				msg = null;
			}
			
			if(msg != null){
				switch (msg.getProtocol()){
				
				case ProtocolLibrary.Reload:
					try {
						onReload(msg);
	
					} catch (JSONException e) {  
						JSONObject content = new JSONObject();
						try {
							content.put("Error", ErrorLibrary.BadlyStructuredContent);
						} catch (JSONException e1) {
							// ni caso esta excepcion jamas se lanzará
						}
						send(ACLMessage.REFUSE, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), content);
					} catch (RefuseException e) {
						e.printStackTrace();
						JSONObject content = new JSONObject();
						try {
							content.put("Error", e.getMessage());
							send(ACLMessage.REFUSE, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), content);
						} catch (JSONException e1) {
							e1.printStackTrace();
						}
					} catch (NotUnderstoodException e) {
						send(ACLMessage.NOT_UNDERSTOOD, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), null);
						e.printStackTrace();
					}
					break;
				
				case ProtocolLibrary.Subscribe:
					try{
						onSubscription(msg);
					}catch(FIPAException fe){
						sendError(fe, msg);
					}	
					break;
					
				case ProtocolLibrary.Information:
					try{
					JSONObject content = new JSONObject(msg.getContent());
					String subject = content.getString("Subject");
					JSONObject resp= new JSONObject();
					switch(subject)
					{
						case SubjectLibrary.ChargerBattery:
							try{
							int totalBattery = onChargerBattery();
							resp.put("Subject", subject);
							resp.put("ChargerBattery", totalBattery);
							send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), resp);
							}
							catch(JSONException e){ e.printStackTrace();}
							break;
							
						case SubjectLibrary.Charge:
						if(content.has("ID")){
							try{
							String droneid = content.getString("ID");
							int totalDroneCharge = onCharge(droneid);
							resp.put("Subject", subject);
							resp.put("TotalCharge", totalDroneCharge);
							send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), resp);
							}
							catch(JSONException e){ e.printStackTrace();}
						}
						else{
							throw new RefuseException(ErrorLibrary.BadlyStructuredContent);
						}
							break;
							
						case SubjectLibrary.DetailedCharges:
							if(content.has("ID")){
							try{
							String droneid = content.getString("ID");
							ArrayList<Integer> DetailCharge = onDetailedCharges(droneid);
							resp.put("Subject", subject);
							resp.put("Charges", DetailCharge);
							send(ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), resp);
							}
							catch(JSONException e){ e.printStackTrace();}
							}
							else{
							throw new RefuseException(ErrorLibrary.BadlyStructuredContent);
							}
							break;
							
							// Hasta aquí las respuestas Charger -> Otro
							
						case SubjectLibrary.Position:
							if(msg.getPerformativeInt()== ACLMessage.INFORM){
								onPositionInform(msg);
							}
							
							break;
							
						case SubjectLibrary.GoalDistance:
							if(msg.getPerformativeInt()==ACLMessage.INFORM){
								onGoalDistanceInform(msg);
							}
							
							break;
							
						case SubjectLibrary.DroneBattery:
							if(msg.getPerformativeInt()==ACLMessage.INFORM){
								onDroneBatteryInform(msg);
							}
							
							break;
					}
					}
					catch(FIPAException fe){
						sendError(fe, msg);
					} catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				
				default: 
					JSONObject error = new JSONObject();
					try {
						error.put("error", "Error Id-mensaje desconocido");
					} catch (JSONException e1) {
						// ni caso esta excepcion jamas se lanzará
					}
					//send(ACLMessage.REFUSE, msg.getSender(), error, msg.getReplyWith());
					break;
				} // FIN SWICHT
				
			}//FIN IF
			
		}//FIN WHILE
	}
	
	/**
	 * author Ismael
	 * Solicita al satelite el mapa original
	 * @return matriz con el mapa original
	 */
	protected int[][] askForMap(){
		JSONObject ask = new JSONObject();
		ACLMessage msg= new ACLMessage();
		JSONObject content;
		int H,W,matriz[][] = null;
		
		try{
			ask.put("Subject", "MapOriginal");
			
			send(ACLMessage.QUERY_REF, IDSatellite, ProtocolLibrary.Information, "default", null, buildConversationId(), ask);
		} catch (JSONException e){
			e.printStackTrace();
		}
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
				W= content.getInt("Weight");
				matriz = new int[H][W];
				JSONArray data = (JSONArray) content.get("Values");
			
				for(int i=0,z=0;i<H;i++)
					for(int j=0;j<W;j++,z++)
						matriz[i][j] = data.getInt(z);
				
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		default: 
			throw new RuntimeException("Fallo en cojer respuesta del satelite");
		}
		
		return matriz;
	}
	
	/**
	 * Método encargado del tratamiento de la petición de bateria. 
	 *   Heuristicas Base inicial: se otorga el nivel de batería que se a solicitado el Drone, con una carga máxima de 75U y
	 *   una carga mínima de 1U.
	 * @author Jahiel   
	 * @author Dani
	 * @param msg
	 * @throws JSONException
	 */
	private void onReload(ACLMessage msg) throws RefuseException, NotUnderstoodException, JSONException{
		//Primera comprobación: performativa correcta.
		if (msg.getPerformativeInt() == ACLMessage.REQUEST){
			JSONObject content = new JSONObject(msg.getContent());
			int requestedBattery = content.getInt("RequestAmmount");
			int givenBattery;
			
			//Segunda comprobación: content vacío
			if (content.length() == 0)
				throw new RefuseException(ErrorLibrary.EmptyContent);
			//Tercera comprobación: content incorrecto
			if (!content.has("Subject") || !content.has("RequestAmount"))
				throw new RefuseException(ErrorLibrary.BadlyStructuredContent);
			
			
			//Cuarta comprobación : batería pedida fuera de los límites.
			if (requestedBattery < 0 || requestedBattery > 75)
				/*JSONObject reason = new JSONObject();
				reason.put("Error", ErrorLibrary.UnespectedAmountReason);
				send (ACLMessage.REFUSE, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), reason);
				 */
				throw new RefuseException(ErrorLibrary.UnespectedAmount);
			
			//Quinta comprobación : No le queda batería al cargador.
			if (battery <= 0)
				/*JSONObject reason = new JSONObject();
				reason.put("Error", ErrorLibrary.NotBatteryAnymoreReason);
				send (ACLMessage.REFUSE, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), reason);
			*/
				throw new RefuseException(ErrorLibrary.NotBatteryAnymore);
			

			/**
			 * @TODOauthor Dani
			 * TODO heurística temporal, hay que implementar la que decidamos.
			 */
			if (battery > requestedBattery)
				givenBattery = requestedBattery;
			else 
				givenBattery = battery;
			battery -= givenBattery; 
			
			JSONObject sendContent = new JSONObject();
			sendContent.put ("AmountGiven", givenBattery);
			sendContent.put ("Subject", SubjectLibrary.BatteryRequest);
			send (ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), sendContent);
			//Le mando la información al satélite
			sendContent.put ("DroneID", msg.getSender().toString());
			send (ACLMessage.INFORM, IDSatellite, msg.getProtocol(), null, null, buildConversationId(), sendContent);
			
			sendInformSubscribeReached( msg.getSender(), givenBattery); // Informa a los drones subscritos
		}
		else{
			send(ACLMessage.NOT_UNDERSTOOD, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), null);
			throw new NotUnderstoodException("");
		}
	}
	
	/**
	 * Se recibe trata la petición de subscripción. Se rechaza si ocurre lo siguiente:
	 *  - AlreadySubscribe: ya se encuentra subscrito a este tipo de subscripción.
	 * 
	 * @param msg Mensaje recibido de petición de subscripción
	 * @throws RefuseException 
	 * @throws NotUnderstoodException
	 */
	private void onSubscription(ACLMessage msg)throws RefuseException, NotUnderstoodException{
		JSONObject content = null;
		try {
			content = new JSONObject(msg.getContent());
		} catch (JSONException e1) {
			throw new NotUnderstoodException("");
		}
		
		try {
			if(content.get("Subject").equals("DroneRecharger")){
				if(subscribers.containsKey(msg.getSender().toString()))
					throw new RefuseException(ErrorLibrary.AlreadySubscribed);
				else{
					subscribers.put(msg.getSender().toString(), msg.getConversationId());
					send(ACLMessage.ACCEPT_PROPOSAL, msg.getSender(), "Subcribe", null, "confirmation", msg.getConversationId(), content);
				}
			
			}else
				throw new NotUnderstoodException("");
		} catch (JSONException e) {
			// no sucede nunca
			e.printStackTrace();
		}
	}
	
	/**
	 * Se informa a todos los drones subscritos que ha habido una recarga.
	 * 
	 * @author Jahiel
	 */
	private void sendInformSubscribeReached(AgentID droneRecharger, int levelRecharge){
		JSONObject content = new JSONObject();
		
		try {
			content.put("Subject", "DroneRecharger");
			content.put("ID-Drone", droneRecharger.toString());
			content.put("amount", levelRecharge);
		} catch (JSONException e) {
			// no sudece nunca
			e.printStackTrace();
		}
		
		for(String name: subscribers.keySet()){
			send(ACLMessage.INFORM, new AgentID(name), "Subcribe", null, null, subscribers.get(name), content);
		}
	}
	
	/**
	 * Método llamado cuando un mensaje pide el nivel de batería total del cargador.
	 * @author Andres
	 * @return Valor de la batería total del cargador
	 */
	private int onChargerBattery(){
		return battery;
	}

	/**
	 * Metodo llamado cuando un mensaje pide las cantidad de cargas totales que se le ha dado a un agente.
	 * @param agentID
	 * @return Número de cargas totales que ha recibido un agente
	 */
	private int onCharge(String agentID){
		//De momento no hay datos para esto, pongo 75 (la carga inicial).
		return 75;
	}

	/**
	 * Metodo llamado cuando un mensaje pide las cargas de un agente de manera detallada.
	 * @param agentID
	 * @return Lista con las cargas detalladas que ha recibido un agente
	 */
	private ArrayList<Integer> onDetailedCharges(String agentID){
		ArrayList<Integer> listaCargas = new ArrayList<Integer>(); // No hay datos para esto, de momento devuelvo este array sin nada.
		return listaCargas;
	}

	private void onPositionInform(ACLMessage msg){
		// TODO
	}

	private void onGoalDistanceInform(ACLMessage msg){
		// TODO
	}

	private void onDroneBatteryInform(ACLMessage msg){
		// TODO
	}
}
