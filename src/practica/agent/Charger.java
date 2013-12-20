package practica.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;

import org.json.JSONException;
import org.json.JSONObject;

import practica.util.ErrorLibrary;
import practica.util.SubjectLibrary;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

/**
 * 
 * @author jahiel
 *
 * No documento la clase porque pronto cambiara todo
 * 
 */
public class Charger extends SingleAgent {
	private BlockingQueue<ACLMessage> requestQueue;  //de momento es una cola sin priridad, primero en llegar primero en ser atendido
	private AgentID IDSatellite;
	private int battery;
	
	/**
	 * @author Jahiel
	 * @param aid
	 * @param Levelbattery
	 * @param satellite
	 * @throws Exception
	 */
	public Charger(AgentID aid, int Levelbattery, AgentID satellite) throws Exception {
		super(aid);
		
		requestQueue = new LinkedTransferQueue<ACLMessage>();
		battery = Levelbattery;
		IDSatellite = satellite;
	}
	
	/**
	 * Manda un mensaje.
	 * @author Jahiel
	 * @author Dani
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
		
		if (replyWith.isEmpty() || replyWith == null) //Doble comprobación, nunca está de más.
			msg.setReplyWith("");
		else
			msg.setProtocol(protocol);
		msg.setInReplyTo(replyWith);
		
		if (inReplyTo.isEmpty() || inReplyTo == null) //Doble comprobación, nunca está de más.
			msg.setInReplyTo("");
		else
			msg.setProtocol(protocol);
		msg.setInReplyTo(inReplyTo);
		
		if (conversationId.isEmpty() || conversationId == null) //Doble comprobación, nunca está de más.
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
	 * Hebra encargada del tratamiento de la cola sin prioridad de mensajes.
	 * @author Jahiel
	 * @param msg Mensaje ACL recivido y listo para introducir en la cola.
	 */
	@Override
	public void onMessage(ACLMessage msg){
		
		try {
			requestQueue.put(msg);
		} catch (InterruptedException e) {
			
			onMessage(msg);  //Nose si esto es una ida de olla pero se supone que la iterrupcion se genera para 
			                 //despertar al agente porque la cola ya tiene espacio.
		}
	}
	
	/**
	 * Metodo execute del agente. Aquie se añade la lógica de ejecución para el agente Cargador.
	 * @author Jahiel
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
				
				case "ChargeMe":
					try {
						onBatteryRequest(msg);
					} catch (JSONException e) {  
						JSONObject error = new JSONObject();
						try {
							error.put("error", "Error en la estructura del content");
						} catch (JSONException e1) {
							// ni caso esta excepcion jamas se lanzará
						}
						//send(ACLMessage.REFUSE, msg.getSender(), error, msg.getReplyWith());
					}
					break;
				
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
	 * Método encargado del tratamiento de la petición de bateria. 
	 *   Heuristicas Base inicial: se otorga el nivel de batería que se a solicitado el Drone, con una carga máxima de 75U y
	 *   una carga mínima de 1U.
	 * @author Jahiel   
	 * @param msg
	 * @throws JSONException
	 */
	protected void onBatteryRequest(ACLMessage msg) throws JSONException{
		if (msg.getPerformativeInt() == ACLMessage.REQUEST){
			JSONObject content = new JSONObject(msg.getContent());
			int requestedBattery = content.getInt("RequestAmmount");
			int givenBattery;
			
			if (requestedBattery <= 0 || requestedBattery > 75){
				JSONObject reason = new JSONObject();
				reason.put("reason", ErrorLibrary.UnespectedAmountReason);
				send (ACLMessage.REFUSE, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), reason);
			}
			
			else{
				/**
				 * @TODOauthor Dani
				 * TODO heurística tempora, hay que implementar la que decidamos.
				 */
				if (battery <= 0){
					JSONObject reason = new JSONObject();
					reason.put("reason", ErrorLibrary.NotBatteryAnymoreReason);
					send (ACLMessage.REFUSE, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), reason);
				}
				else{
					if (battery > requestedBattery)
						givenBattery = requestedBattery;
					else 
						givenBattery = battery;
					battery -= requestedBattery;
					
					JSONObject sendContent = new JSONObject();
					sendContent.put ("GivenAmount", requestedBattery);
					send (ACLMessage.INFORM, msg.getSender(), msg.getProtocol(), null, msg.getReplyWith(), msg.getConversationId(), sendContent);
				}
					
			}
		}
		else{
			send(ACLMessage.NOT_UNDERSTOOD, msg.getSender(), msg.getProtocol(), null, null, msg.getConversationId(), null);
		}
	}
}
