package practica.agent;

import java.util.ArrayList;

import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;


//NOTA_INTEGRACION (Jahiel) Esto no se usa. Como en el codigo de Ismael.
import javax.json.Json;
import javax.json.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import practica.util.GPSLocation;
import practica.util.Map;

public class Satelite extends SingleAgent {
	private final int SolicitudStatus = 0, EsperarInform = 1; // Estos nombres no me gustan
	private int state;
	private Map mapOriginal, mapSeguimiento;
	private GPSLocation gps;
	private double goalPosX, goalPosY;
	//(Andres) Faltan los atributos goalPosX y goalPosY (ambos private float)
	
	
	public Satelite(AgentID sat, Map mapa)throws Exception{
		super(sat);
		mapOriginal= new Map(mapa);
		mapSeguimiento= new Map(mapa);
        state=SolicitudStatus;
		gps = new GPSLocation();
		//(Andres) Inicializar goalPosX y goalPosY a la posicion media de las casillas rojas del mapa
		
		mapSeguimiento.setvalue(0, 0, Map.VISITADO); //añadido esto que faltaba
	}
	
	/**
	 * Creamos el objeto JSON status:
	 * 
	 * Status: {“connected”:”YES”, “ready”:”YES”, “gps”:{“x”:10,”y”:5}, 
	 * “goal”:”No”, “gonio”:{“alpha”:0, “dist”:4.0}, “battery”:100, 
	 * “radar”:[0,0,0,0,0,0,0,1,1]}
	 * 
	 * @return Objeto JSon con el contenido de Status
	 * @throws JSONException Si la clave es null
	 */
	private JSONObject createStatus() throws JSONException{
		 ArrayList<Integer> casillas = new ArrayList<Integer>();
		 int posXDrone = gps.getPositionX(), posYDrone = gps.getPositionY();
		 double distancia = Math.sqrt(Math.pow(posXDrone, goalPosX) + Math.pow(posYDrone, goalPosY)),
				grado = Math.atan((posYDrone - goalPosY) / (posXDrone - goalPosX)) ; //distancia = raiz( pow(x0-x1) + pow(y0-y1)) angulo = atan (y0-y1 / x0-x1 )
		 
		 /*
		  Version 2:
		 
		 JsonObject status = Json.createObjectBuilder()
				 .add("connected", "Yes")
				 .add("ready", "Yes")
				 .add("gps", Json.createObjectBuilder()
						 .add("x", 10)
						 .add("y", 5))
				 .add("goal", "No")
				 .add("gonio", Json.createObjectBuilder()
						 .add("alpha", grado)
						 .add("dist", distancia))
				 .add("battery", 100)
				 //.add("radar", casillas) no se puede asociar un Collection al
				 // value
				 .build();
		 
		 */
		
		 JSONObject status2 = new JSONObject();
		 status2.put("connected", "Yes");
		 status2.put("ready", "Yes");
		 
		 JSONObject aux = new JSONObject();
		 aux.put("x", gps.getPositionX());
		 aux.put("y", gps.getPositionY());
		 status2.put("gps", aux);
		 
		 status2.put("goal", "No");
		 
		 /*
		  *  No se puede vaciar el objeto Aux, tendria que
		  *  eliminar uno a uno sus elementos ... sin comentarios Java 
		  */
		 JSONObject aux2 = new JSONObject();
		 aux2.put("alpha", grado);
		 aux2.put("dist", distancia);
		 status2.put("gonio", aux2);
		 
		 status2.put("battery", 100);
		 status2.put("radar", casillas);

		 return status2;
	}

	/**
	 * Se envia un mensaje del tipo "typeMessag" al agente "id" con el contenido "datas".
	 * @param typeMessage Tipo del mensaje: REQUEST, INFORM, FAIL
	 * @param id Identificador del destinatario
	 * @param datas Contenido del mensaje
	 */
	private void send(int typeMessage, AgentID id, JSONObject datas){
		
		ACLMessage msg = new ACLMessage(typeMessage);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		msg.setContent(datas.toString());
		this.send(msg);
	}
	
	/**
	 * En función del valor recibido por el dron se actualiza el mapa interno del satelite con la nueva posición
	 *  del drone (x, y en funcion de la dirección elegida) o se da por finalizada la comunicación.
	 *  
	 * @param dron Identificador del agente dron. 
	 * @param ob Objeto JSon con los valores de la decision del drone:
	 *  - 0 : El dron decide ir al Este.
	 *  - 1 : El dorn decide ir al Sur.
	 *  - 2 : El dorn decide ir al Oeste.
	 *  - 3 : El dorn decide ir al Norte.
	 *  - -1: Fin de la comunicación
	 * @return Se devuelve "true" si se debe finalizar la comunicación y "false" en caso contrario.
	 */
	private boolean evalueDecision(AgentID dron, JSONObject ob){
		int decision, x, y;
		
	
		try{
			decision = ob.getInt("decision");
		}catch(JSONException e){
			
			sendError(dron, "Error de parametros en la decisión");
			return true;
		}
		
		switch (decision){
		
		case '0':  //Este
			x = gps.getPositionX() + 1;
			y = gps.getPositionY();
			break;
		
		case '1':  //Sur
			x = gps.getPositionX();
			y = gps.getPositionY() + 1;
			break;
		
		case '2':  //Oeste
			x = gps.getPositionX() - 1;
			y = gps.getPositionY();
			break;
		
		case '3':  //Norte
			x = gps.getPositionX();
			y = gps.getPositionY() - 1;
			break;
		
		default:  //Fin, No me guta, prefiero un case para el fin 
				  //y en el default sea un caso de error pero no me deja poner -1 en el case.
			return true;
		}
		
		gps.setPositionX(x);
		gps.setPositionY(y);
		mapSeguimiento.setvalue(x, y, Map.VISITADO);
			
		return false;
	}
	
	/**
	 * Se crea un mensaje del tipo FAIL para informar de algun fallo al agente dron.
	 * @param dron Identificador del agente dron.
	 * @param cad_error Cadena descriptiva del error producido.
	 */
	private void sendError(AgentID dron, String cad_error){
		JSONObject error = new JSONObject();
		
		try {
			error.put("fail", cad_error);
		} catch (JSONException e) {
			e.printStackTrace();  // esta excepcion nunca va a suceder porque la clave siempre es fail
								  // aun asi hay que capturarla y por eso no se lo comunico al dron
		}
		
		System.err.println("Agente "+this.getName()+cad_error);
		
		send(ACLMessage.FAILURE, dron, error);
		
	}
	
	/**
	 * Secuencia de acciones del satelite. Ver diagrama de secuencia
	 * para ver la secuencia de acciones.
	 */
	@Override
	protected void execute(){
		ACLMessage message = new ACLMessage();
		AgentID dron = null;
		boolean exit = false;
		
		System.out.println("Agente "+this.getName()+" en ejecución");
		while(!exit){
			switch(state){
			
			case SolicitudStatus:
				// Aqui esperamos el primer Request vacio
				
				try{
					message = receiveACLMessage();
				} catch(InterruptedException e){
					sendError(dron, "Error en la comunicación");
					exit = true;
				}
				
				if(exit!=true){
					dron=message.getSender();
					// Una vez recibido el mensaje comprobamos el tipo y si es del tipo Request
					// respondemos con Inform(status)
					
					if(message.getPerformative().equals("REQUEST")){
						JSONObject status = null;
						try{
							status = createStatus();
						}catch(JSONException e){
							sendError(dron, "Error al crear Status");
							exit = true;
						}
						if(status != null){
							send(ACLMessage.INFORM, dron, status);
							state = EsperarInform;
						}
					}else{
						//El mensaje recibido es de tipo distinto a Request por tanto error
						
						sendError(dron, "Error de secuencia en la comunicación. El mensaje debe ser de tipo REQUEST");
						exit = true;
					}
				}
				
				break;
				
			case EsperarInform:
				// Aqui esperamos el Inform
				
				try{
					message = receiveACLMessage();
				} catch(InterruptedException e){
					sendError(dron, "Error de comunicación");
					exit = true;
				}
				if(message.getPerformative().equals("REQUEST")){
					
					JSONObject aux=null;
					try {
						aux = new JSONObject(message.getContent());
					} catch (JSONException e) {
						sendError(dron, "Error al crear objeto JSON con la decision");
					}
					
					exit = evalueDecision(dron, aux);
					
					//Si ha habido algún fallo al actualizar el mapa se le informa al drone y se finaliza 
					if(exit)
						sendError(dron, "Error al actualizar el mapa");
					else
						state = SolicitudStatus;
				
					send(ACLMessage.INFORM, dron, null);
				}else{
					//El mensaje recibido es de tipo distinto a Request por tanto error
					
					sendError(dron, "Error de secuencia en la comunicación. El mensaje debe ser de tipo REQUEST");
					
					exit = true;
				}
				break;
			}
				
		}
	}
	
	@Override
	public void finalize(){
		
		System.out.println("Agente "+this.getName()+" ha finalizado");
	}
	
}
