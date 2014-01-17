package practica.agent;

import org.json.JSONException;
import org.json.JSONObject;

import practica.gui.Log;
import practica.lib.SubjectLibrary;
import es.upv.dsic.gti_ia.architecture.FIPAException;
import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

/**
 * Clase para agrupar todas las funcionalidades comunes a nuestros agentes
 * @author Daniel
 *
 */
public class SuperAgent extends SingleAgent {
	private int conversationCounter;
	private Log log;

	/**
	 * Constructor
	 * @param aid ID del agente.
	 * @throws Exception si no se pudo asignar la ID.
	 */
	public SuperAgent(AgentID aid) throws Exception {
		super(aid);
		conversationCounter = 0;
		log = null;
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
		
		if(replyWith != null && !replyWith.isEmpty())
			msg.setReplyWith(replyWith);
		
		if(inReplyTo != null && !inReplyTo.isEmpty())
			msg.setInReplyTo(inReplyTo);
		
		if(conversationId != null && !conversationId.isEmpty())
			msg.setConversationId(conversationId);
		
		if(protocol != null && !protocol.isEmpty())
			msg.setProtocol(protocol);
		
		if (datas != null)
			msg.setContent(datas.toString());
		else
			msg.setContent("");
		
		this.send(msg);
	}	
	
	/**
	 * Manda un mensaje de error en base a una excepción y a un mensaje
	 * @author Alberto
	 * @param fe excepción a tratar
	 * @param msgOrig mensaje que originó la excepción.
	 */
	protected void sendError(FIPAException fe, ACLMessage msgOrig) {
		ACLMessage msgError = fe.getACLMessage();
		JSONObject content = new JSONObject();
		String subject = "";
		
		try {
			content.put("error",fe.getMessage());
			subject = new JSONObject(msgOrig.getContent()).getString("Subject");
			content.put("Subject", subject);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		msgError.addReceiver(msgOrig.getSender());
		msgError.setContent(content.toString());
		msgError.setProtocol(msgOrig.getProtocol());
		msgError.setConversationId(msgOrig.getConversationId());
		msgError.setInReplyTo(msgOrig.getReplyWith());
		
		this.send(msgError);
		//Meter mensaje en el log
    	addMessageToLog(Log.SENDED, msgError.getReceiver(), msgError.getProtocol(), subject, "ERROR:" + fe.getMessage());   
	}
	
	/**
	 * Construye un nuevo campo conversationID a partir del id del agente y el contador de conversacion
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
	
	/**
	 * Asigna un log para mostrarlo por pantalla.
	 * @author Daniel
	 * @param log log a asignar.
	 */
	public void setLog(Log log){
		this.log = log;
	}
	
	/**
	 * Encapsulación del método addMessage del log para controlar que no sea nulo, para poder realizar una ejecución sin la interfaz.
	 * @param type RECEIVED si el mensaje se ha recibido, SENDED si se ha enviado.
	 * @param name nombre del receptor o remitente del mensaje.
	 * @param protocol protocolo del mensaje.
	 * @param subject campo subject del mensaje.
	 * @param content campo content del mensaje, sin subject.
	 */
	protected void addMessageToLog (int type, String name, String protocol, String subject, String content){
		if (log != null)
			log.addMessage(type, name, protocol, subject, content);
	}
	
	/**
	 * Encapsulación del método addMessage del log para controlar que no sea nulo, para poder realizar una ejecución sin la interfaz.
	 * @param type RECEIVED si el mensaje se ha recibido, SENDED si se ha enviado.
	 * @param aid ID del agente receptor o remitente del mensaje.
	 * @param protocol protocolo del mensaje.
	 * @param subject campo subject del mensaje.
	 * @param content campo content del mensaje, sin subject.
	 */
	protected void addMessageToLog (int type, AgentID aid, String protocol, String subject, String content){
		if (log != null){
			String name = aid.name;
			log.addMessage(type, name, protocol, subject, content);
		}
	}
}
