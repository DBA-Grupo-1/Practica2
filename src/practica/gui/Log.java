package practica.gui;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;

import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentType;

import practica.lib.ErrorLibrary;

/**
 * Clase que almacena un log de mensajes en formato HTML.
 * @author Daniel
 *
 */
public class Log extends JTextPane{
	private static JTextPane label;
	private String log;
	private String body;
	private String htmlStart;
	private String htmlEnd;
	private int messageCount;
	public static final int RECEIVED = 0;
	public static final int SENDED = 1;
	private static final int MAX_MESSAGES = 100;
	
	/**
	 * Constructor vacío
	 * @author Daniel
	 */
	public Log(){
		htmlStart = "<html><body style='width : 250'>";
		htmlEnd = "</body></html>";
		body = "";
		log = htmlStart+body+htmlEnd;
		setText(log);
		setContentType("text/html");
		setEditable(false);
		messageCount = 0;
	}
	
	/**
	 * Añade un mensaje al log.
	 * @param type RECEIVED si el mensaje se ha recibido, SENDED si se ha enviado.
	 * @param name nombre del receptor o remitente del mensaje,
	 * @param protocol protocolo del mensaje.
	 * @param subject subject del mensaje.
	 * @param content content del mensaje, sin subject.
	 */
	public void addMessage (int type, String name, String protocol, String subject, String content){
		//Aumentar contador de mensajes
		messageCount ++;
		
		//Si hay demasiados mensajes, borrarlos
		if (messageCount >= MAX_MESSAGES){
			body = "";
			messageCount = 0;
		}
		
		String message = "";
		if (type != RECEIVED && type != SENDED)
			throw new RuntimeException(ErrorLibrary.LogInvalidFirstArgument);
		//Enviado/recibido
		if (type == RECEIVED)
			message += "<b>&lt;---</b>";
		else if (type == SENDED)
			message += "<b>---</b>>";
		//Destinatario/remitente
		message += ("<font color = 'blue'>" + name + " " +  "</font>");
		//Procotolo
		message += ("<font color = 'green'><b>" + protocol + ":" + "</b>");
		//Asunto
		message += (subject + " " + "</font>");
		//Contenido
		message += content;
		
		//Actualizar body
		body += (message + "<br>");
		
		//Actualizar log
		log = htmlStart + body + htmlEnd;
		
		//Actualizar etiqueta
		setText(log);
		}
}
