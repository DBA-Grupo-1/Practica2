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
	
	
	public Satelite(AgentID sat, Map mapa)throws Exception{
		super(sat);
		mapOriginal= new Map(mapa);
		mapSeguimiento= new Map(mapa);
        state=SolicitudStatus;
		gps = new GPSLocation();
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
		 float grado = 0, distancia = 0;
		 
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
	}
	
	/**
	 * Se actualiza el mapa interno del satelite con la posición del drone.
	 * @param ob Objeto JSon con los valores de la posición del drone:
	 *  - PosX : posición x del drone en el mapa
	 *  - PosY : posición y del drone en el mapa
	 * @return Se devuelve "true" si la operación falla y "false" en caso contrario.
	 */
	private boolean updateMap(JSONObject ob){
		int x, y;
		
		try{
			x = ob.getInt("PosX");
			y = ob.getInt("PosY");
			gps.setPositionX(x);
			gps.setPositionY(y);
			mapSeguimiento.setvalue(x, y, Map.VISITADO);
		}catch (Exception e){
			System.err.println("Agente "+this.getName()+" Error de parametros Posición");
			return true;
		}
			
		return false;
	}
	
	/**
	 * Método con la secuencia de acciones del satelite. Ver diagrama de secuencia
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
					System.err.println("Agente "+this.getName()+" Error de comuncicación");
					send(ACLMessage.FAILURE, dron, null);
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
							System.err.println("Agente "+this.getName()+"Error al crear Status");
							send(ACLMessage.FAILURE, dron, null);
							exit = true;
						}
						if(status != null){
							send(ACLMessage.INFORM, dron, status);
							state = EsperarInform;
						}
					}else{
						//El mensaje recibido es de tipo distinto a Request por tanto error
						
						System.err.println("Agente "+this.getName()+" Error de secuencia en la comunicacion");
						exit = true;
					}
				}
				
				break;
				
			case EsperarInform:
				// Aqui esperamos el Inform
				
				try{
					message = receiveACLMessage();
				} catch(InterruptedException e){
					System.err.println("Agente "+this.getName()+" Error de comuncicación");
					send(ACLMessage.FAILURE, dron, null);
					exit = true;
				}
				if(message.getPerformative().equals("INFORM")){
					
					JSONObject aux=null;
					try {
						aux = new JSONObject(message.getContent());
					} catch (JSONException e) {
						e.printStackTrace();
					}
					if(aux.has("fin")){
						exit = true;
						//TODO Combertir mapa a imagen
					}else{
						exit = updateMap(aux);
						
						//Si ha habido algún fallo al actualizar el mapa se le informa al drone y se finaliza 
						if(exit)
							send(ACLMessage.FAILURE, dron, null);
						else
							state = SolicitudStatus;
					}
					send(ACLMessage.INFORM, dron, null);
				}else{
					//El mensaje recibido es de tipo distinto a Inform por tanto error
					
					System.err.println("Agente "+this.getName()+" Error de secuencia en la comunicacion");
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
