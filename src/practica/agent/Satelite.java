package practica.agent;


import es.upv.dsic.gti_ia.core.ACLMessage;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import practica.util.GPSLocation;
import practica.util.ImgMapConverter;
import practica.util.Map;
import practica.util.Visualizer;

public class Satelite extends SingleAgent {
	private final int SolicitudStatus = 0, EsperarInform = 1; // Estos nombres no me gustan
	private int state;
	private Map mapOriginal, mapSeguimiento;
	private GPSLocation gps;
	private double goalPosX, goalPosY;

	private Visualizer visualizer;
	private boolean usingVisualizer;
	
	
	public Satelite(AgentID sat, Map mapa) throws Exception{
		super(sat);
		mapOriginal = new Map(mapa);
		mapSeguimiento = new Map(mapa);
		state = SolicitudStatus;
		gps = new GPSLocation();
		
		int f=mapOriginal.getHeigh();
		int c=mapOriginal.getWidth();
		int suma_x=0, suma_y=0, cont=0;
		
		for(int i=0; i<f; i++){
		    for(int j=0; j<c; j++){
		        if(mapOriginal.getValue(j,i)==Map.OBJETIVO){
		            suma_x+=j;
		            suma_y+=i;
		            cont++;
		        }
		    }
		}
		goalPosX=suma_x/(float)cont;
		goalPosY=suma_y/(float)cont;

		mapSeguimiento.setvalue(0, 0, Map.VISITADO); // añadido esto que faltaba
		
		usingVisualizer = false;
	}
	
	public Satelite(AgentID sat, Map mapa, Visualizer v) throws Exception{
		this (sat, mapa);		
		visualizer = v;
		usingVisualizer = true;
	}

	//De momento no tengo por qué usarlo.
	/*public void waitForPass(){
		synchronized(lock){
			if(lock.intValue()==0){
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void allowPass(){
		synchronized(lock){
			lock.notify();
		}
	}*/
	
	/**
	 * Se calcula el valor del ángulo que forma la baliza y el EjeX horizontal tomando como centro
	 * a el agente drone.
	 * @param posX Posición relativa de la baliza con respecto al drone.
	 * @param posY Posición relativa de la baliza con respecto al drone.
	 * @return valor del ángulo.
	 */
	private double calculateAngle(double posX, double posY){
		double angle = 0;
		
		if(posX>0 && posY>=0)
			angle = Math.atan(posY / posX);
		else if(posX>0 && posY<0)
			angle = Math.atan(posY / posX) + (2.0*Math.PI);
		else if(posX == 0 && posY>0)
			angle = Math.PI/2.0;
		else if(posX == 0 && posY<0)
			angle = (3*Math.PI) / 2.0;
		else if(posX<0)
			angle = Math.atan(posY / posX) + Math.PI;
		
		return angle;
	}
	
	/**
	 * Creamos el objeto JSON status:
	 * Status: {“connected”:”YES”, “ready”:”YES”, “gps”:{“x”:10,”y”:5},
	 * “goal”:”No”, “gonio”:{“alpha”:0, “dist”:4.0}, “battery”:100,
	 * “radar”:[0,0,0,0,0,0,0,1,1]}
	 * 
	 * @return Objeto JSon con el contenido de Status
	 * @throws JSONException  Si la clave es null
	 */
	private JSONObject createStatus() throws JSONException {
		int posXDrone = gps.getPositionX(), posYDrone = gps.getPositionY();
		double distance = Math.sqrt(Math.pow(goalPosX - posXDrone, 2) + Math.pow(goalPosY - posYDrone, 2));
		double angle = calculateAngle(goalPosX - posXDrone, goalPosY - posYDrone);

		JSONObject status2 = new JSONObject();
		status2.put("connected", "Yes");
		status2.put("ready", "Yes");
		
		JSONObject aux = new JSONObject();
		aux.put("x", gps.getPositionX());
		aux.put("y", gps.getPositionY());

		status2.put("gps", aux);

		if(mapOriginal.getValue(posXDrone, posYDrone) == Map.OBJETIVO)
			status2.put("goal", "Si");
		else
			status2.put("goal", "No");

		JSONObject aux2 = new JSONObject();
		aux2.put("alpha", angle);
		aux2.put("dist", distance);
		status2.put("gonio", aux2);
		status2.put("battery", 100);
		
		int[] surroundings = obtenerAlrededores();
		JSONArray jsArray = new JSONArray(surroundings);
		status2.put("radar", jsArray);

		return status2;
	}

	/**
	 * Este método obtiene los valores de las celdas en las 9 casillas que rodean el drone 
	 * (incluyendo en la que se encuentra el drone)
	 * @return Array de enteros con las 
	 */
	private int[] obtenerAlrededores(){
		int[] surroundings = new int[9];
		int posX = gps.getPositionX();
		int posY = gps.getPositionY();
		
		// Recorre desde la posición dron -1  hasta la del dron + 1, tanto en X como en Y
		for (int i = 0; i< 3; i++){
			for(int j = 0; j < 3; j++){
				/* TODO: ¿poner mapSeguimiento o mapOriginal? Depende del método getValidMoviments del drone
				 * Se puede liar si aquí digo que está visitado, y allí le suma que también.
				 */
				surroundings[i+j*3] = mapOriginal.getValue(posX-1+i, posY-1+j);
			}
		}
		
		return surroundings;
	}
	
	/**
	 * Se envia un mensaje del tipo "typeMessag" al agente "id" con el contenido "datas".
	 * @param typeMessage 	Tipo del mensaje: REQUEST, INFORM, FAIL
	 * @param id   			Identificador del destinatario
	 * @param datas			Contenido del mensaje
	 */
	private void send(int typeMessage, AgentID id, JSONObject datas) {

		ACLMessage msg = new ACLMessage(typeMessage);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		if (datas != null)
			msg.setContent(datas.toString());
		else
			msg.setContent("");
		this.send(msg);
	}

	/**
	 * En función del valor recibido por el dron se actualiza el mapa interno
	 * del satelite con la nueva posición del drone (x, y en funcion de la
	 * dirección elegida) o se da por finalizada la comunicación.
	 * @param dron		Identificador del agente dron.
	 * @param ob		Objeto JSon con los valores de la decision del drone: 
	 * 					-  0 : El dron decide ir al Este. 
	 * 					-  1 : El dorn decide ir al Sur. 
	 * 					-  2 : El dorn decide ir al Oeste. 
	 * 					-  3 : El dorn decide ir al Norte.
	 *            		- -1: Fin de la comunicación
	 * @return Se devuelve "true" si se debe finalizar la comunicación y "false" en caso contrario.
	 */
	private boolean evalueDecision(AgentID dron, JSONObject ob) {
		int decision, x = -1, y = -1;

		try {
			decision = ob.getInt("decision");
		} catch (JSONException e) {

			sendError(dron, "Error de parametros en la decisión");
			return true;
		}

		switch (decision) {

		case Drone.ESTE: // Este
			x = gps.getPositionX() + 1;
			y = gps.getPositionY();
			break;

		case Drone.SUR: // Sur
			x = gps.getPositionX();
			y = gps.getPositionY() + 1;
			break;

		case Drone.OESTE: // Oeste
			x = gps.getPositionX() - 1;
			y = gps.getPositionY();
			break;

		case Drone.NORTE: // Norte
			x = gps.getPositionX();
			y = gps.getPositionY() - 1;
			break;

		case Drone.END:
			return true;
		default: // Fin, No me gusta, prefiero un case para el fin y en el default sea un caso de error pero no me deja poner -1 en el case.
			sendError(dron, "Error al actualizar el mapa");
			break;
		}

		gps.setPositionX(x);
		gps.setPositionY(y);
		mapSeguimiento.setvalue(x, y, Map.VISITADO);

		return false;
	}

	/**
	 * Se crea un mensaje del tipo FAIL para informar de algun fallo al agente dron.
	 * @param dron 			Identificador del agente dron.
	 * @param cad_error 	Cadena descriptiva del error producido.
	 */
	private void sendError(AgentID dron, String cad_error) {
		JSONObject error = new JSONObject();

		try {
			error.put("fail", cad_error);
		} catch (JSONException e) {
			e.printStackTrace(); // esta excepcion nunca va a suceder porque la clave siempre es fail aun asi hay que capturarla y por eso no se lo comunico al dron
		}
		System.err.println("Agente " + this.getName() + " " + cad_error);

		send(ACLMessage.FAILURE, dron, error);

	}

	/**
	 * Secuencia de acciones del satelite. Ver diagrama de secuencia para ver la secuencia de acciones.
	 */
	@Override
	protected void execute() {
		ACLMessage message = new ACLMessage();
		AgentID dron = null;
		boolean exit = false;
		System.out.println("Agente " + this.getName() + " en ejecución");
		while (!exit) {

			switch (state) {

			case SolicitudStatus:
				//Si hay visualizador, manda actualizar sus mapas.
				if (usingVisualizer){
					visualizer.updateMap();
					//Si no está pulsado "Find Target" y está pulsado "Think Once" hay que habilitar "Think Once". Si "Find Target" está pulsado, no se debe de hacer nada.
					if (visualizer.isBtnFindTargetEnabled() && !visualizer.isBtnThinkOnceEnabled())
						visualizer.enableThinkOnce();
				}
				
				// Aqui esperamos el primer Request vacio
				try {
					message = receiveACLMessage();
				} catch (InterruptedException e) {
					sendError(dron, "Error en la comunicación");
					exit = true;
				}

				if (!exit) {
					dron = message.getSender();
					// Una vez recibido el mensaje comprobamos el tipo y si es del tipo Request respondemos con Inform(status)

					if (message.getPerformative().equals("REQUEST")) {
						
						System.out.println("Posicion: " + gps.getPositionX() + ", "+ gps.getPositionY());
						
						JSONObject status = null;
						try {
							status = createStatus();
						} catch (JSONException e) {
							sendError(dron, "Error al crear Status");
							exit = true;
						}
						if (status != null) {
							send(ACLMessage.INFORM, dron, status);
							state = EsperarInform;
						}
					} else {
						// El mensaje recibido es de tipo distinto a Request por tanto error

						sendError(dron,"Error de secuencia en la comunicación. El mensaje debe ser de tipo REQUEST");
						exit = true;
					}
				}

				break;

			case EsperarInform:
				// Aqui esperamos el Inform
				try {
					message = receiveACLMessage();
				} catch (InterruptedException e) {
					sendError(dron, "Error de comunicación");
					exit = true;
				}
				if (message.getPerformative().equals("REQUEST")) {
					if (usingVisualizer)
						if (visualizer.isBtnThinkOnceEnabled())
							while (visualizer.isBtnThinkOnceEnabled()){
								System.out.print("");//Necesario para volver a comprobar la condición del while.
							}

					JSONObject aux = null;
					try {
						aux = new JSONObject(message.getContent());
					} catch (JSONException e) {
						sendError(dron,"Error al crear objeto JSON con la decision");
					}

					exit = evalueDecision(dron, aux);
					// Si ha habido algún fallo al actualizar el mapa se le informa al drone y se finaliza
					if (!exit)
						state = SolicitudStatus;
					send(ACLMessage.INFORM, dron, null);
				} else {
					// El mensaje recibido es de tipo distinto a Request por tanto error

					sendError(dron,"Error de secuencia en la comunicación. El mensaje debe ser de tipo REQUEST");

					exit = true;
				}
				break;
			}

		}
	}

	@Override
	public void finalize() {
		System.out.println("Agente " + this.getName() + " ha finalizado");
		// TODO: he añadido la creación del mapa. Revisar si esto debería ir aquí o en el main de algún modo, u otro lugar
		ImgMapConverter.mapToImg("src/maps/resutado.png", mapSeguimiento);
	}

	/**
	 * Getter del mapa original.
	 * @return el mapa original.
	 */
	public Map getMapOriginal() {
		return mapOriginal;
	}

	/**
	 * Getter del mapa de seguimiento.
	 * @return el mapa de seguimiento.
	 */
	public Map getMapSeguimiento() {
		return mapSeguimiento;
	}
}