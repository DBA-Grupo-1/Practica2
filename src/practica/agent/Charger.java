package practica.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;

import org.json.JSONException;
import org.json.JSONObject;

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
	
	
	public Charger(AgentID aid, int Levelbattery, AgentID satellite) throws Exception {
		super(aid);
		
		requestQueue = new LinkedTransferQueue<ACLMessage>();
		battery = Levelbattery;
		IDSatellite = satellite;
	}
	
	/**
	 * Manda un mensaje.
	 * @author Dani
	 * @author Jahiel
	 * @param typeMessage 		performativa del mensaje.
	 * @param id				destinatario del mensaje.
	 * @param protocol			protocolo de comunicación del mensaje.
	 * @param replyWith			reply-with o reply-to del mensaje,
	 * @param conversationId	id de la conversación del mensaje,
	 * @param datas				content del mensaje.
	 */
	private void send(int typeMessage, AgentID id, String protocol, String replyWith, String conversationId, JSONObject datas) {

		ACLMessage msg = new ACLMessage(typeMessage);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		
		if (replyWith.isEmpty() || replyWith == null) //Doble comprobación, nunca está de más.
			msg.setReplyWith("");
		else
			msg.setProtocol(protocol);
		msg.setInReplyTo(replyWith);
		
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
	
	@Override
	public void onMessage(ACLMessage msg){
		
		try {
			requestQueue.put(msg);
		} catch (InterruptedException e) {
			
			onMessage(msg);  //Nose si esto es una ida de olla pero se supone que la iterrupcion se genera para 
			                 //despertar al agente porque la cola ya tiene espacio.
		}
	}
	
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
						send(ACLMessage.REFUSE, msg.getSender(), error, msg.getReplyWith());
					}
					break;
				
				default: 
					JSONObject error = new JSONObject();
					try {
						error.put("error", "Error Id-mensaje desconocido");
					} catch (JSONException e1) {
						// ni caso esta excepcion jamas se lanzará
					}
					send(ACLMessage.REFUSE, msg.getSender(), error, msg.getReplyWith());
					break;
				} // FIN SWICHT
				
			}//FIN IF
			
		}//FIN WHILE
	}
	
	protected void onBatteryRequest(ACLMessage msg) throws JSONException{
		if (msg.getPerformative() == ACLMessage.REQUEST){
			
		}
		/*JSONObject contentReceive = new JSONObject(msgReceive.getContent());
		JSONObject contentSend = new JSONObject();
		int level;
		
		if(battery > 0 ){
			try{
				level = contentReceive.getInt("LevelBattery");
			
			}catch(JSONException e){
				level = 75;
			}
		
			if(battery >= level){
				contentSend.put("Recharge", level);
				battery -= level;
			}else{
				contentSend.put("Recharge", battery);
				battery = 0;
			}
			
			send(ACLMessage.INFORM, msgReceive.getSender(), contentSend, msgReceive.getReplyWith());

		}else{
			JSONObject rejection = new JSONObject();
			rejection.put("error", "No quedan más cargas de batería");
			send(ACLMessage.REFUSE, msgReceive.getSender(), rejection, msgReceive.getReplyWith());
		}*/
		else{
		}
	}
}
