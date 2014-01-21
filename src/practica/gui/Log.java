package practica.gui;

import java.awt.Font;

import javax.swing.JTextPane;

import practica.lib.ErrorLibrary;

/**
 * Clase que almacena un log de mensajes en formato HTML.
 * @author Daniel
 *
 */
public class Log extends JTextPane{
	private static final long serialVersionUID = 1L;
	private String log;								//Página HTML con la lista de mensajes.
	private String body;							//Cuerpo de la página.
	private String htmlStart;						//Etiquetas de principio de la página (<html> y <body>).
	private String htmlEnd;							//Etiquetas de fin de la página (</html> y </body>).
	private int messageCount;						//Contador de mensajes, para reiniciar el log al llegar al máximo.
	public static final int RECEIVED = 0;
	public static final int SENDED = 1;
	private static final int MAX_MESSAGES = 200;
	
	/**
	 * Constructor vacío
	 * @author Daniel
	 */
	public Log(){
		initialize();
	}
	private void initialize() {
		setFont(new Font("Arial", Font.PLAIN, 11));
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
			//message += "<b>&lt;---</b>";
			message += "<b>IN </b>";
		else if (type == SENDED)
			//message += "<b>---</b>>";
			message += "<b>OUT </b>";
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
