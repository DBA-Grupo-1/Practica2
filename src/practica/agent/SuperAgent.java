package practica.agent;

import org.json.JSONException;
import org.json.JSONObject;

import es.upv.dsic.gti_ia.architecture.FIPAException;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

public class SuperAgent extends SingleAgent {
		
	
	private int conversationCounter ;

	public SuperAgent(AgentID aid) throws Exception {
		super(aid);
		conversationCounter = 0;
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
	protected void send(int typeMessage, AgentID id, String protocol, String replyWith, String inReplyTo, String conversationId, JSONObject datas) {

		ACLMessage msg = new ACLMessage(typeMessage);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		
		if(replyWith!=null && !replyWith.isEmpty())
			msg.setReplyWith(replyWith);
		
		if(inReplyTo!=null && !inReplyTo.isEmpty())
			msg.setInReplyTo(inReplyTo);
		
		if(conversationId!=null && !conversationId.isEmpty())
			msg.setConversationId(conversationId);
		
		if(protocol!=null && !protocol.isEmpty())
			msg.setProtocol(protocol);
		
		if (datas != null)
			msg.setContent(datas.toString());
		else
			msg.setContent("");
		
		this.send(msg);
	}	

	
	/**
	 * @author Alberto
	 * @param fe
	 * @param msgOrig
	 */
	protected void sendError(FIPAException fe, ACLMessage msgOrig) {
		ACLMessage msgError = fe.getACLMessage();
		JSONObject content = new JSONObject();
		
		try {
			content.put("error",fe.getMessage());
			content.put("Subject", new JSONObject(msgOrig.getContent()).get("Subject"));
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
	 * Construye un nuevo campo conversationID a partir del id del agente y el contador de conversacion
	 * 
	 * @author Alberto
	 * @return Conversation id formado segun el patron acordado
	 */
	protected String buildConversationId() {
		String res;
		synchronized(this){
			res = this.getAid().toString()+"#"+conversationCounter;
			conversationCounter++;
		}
		return res;
	}


}
