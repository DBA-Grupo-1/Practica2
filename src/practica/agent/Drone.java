package practica.agent;

import java.util.ArrayList;
import java.util.Random;

import practica.util.Map;
import practica.util.Pair;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;
import es.upv.dsic.gti_ia.core.ACLMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Drone extends SingleAgent {
	private final int ESTADOREQUEST = 0, ESTADOINFORM = 1;
	// NOTA_INTEGRACION (Ismael) exit no tiene sentido fuera del execute
	private boolean exit;
	private int estado;
	private int posX;
	private int posY;
	private float angle;
	private float distancia;
	private int[] surroundings;
	private Map droneMap;
	public static final int NORTE = 3;
	public static final int OESTE = 2;
	public static final int SUR = 1;
	public static final int ESTE = 0;
	public static final int END = -1;

	private AgentID sateliteID;

	public Drone(AgentID aid, int mapWidth, int mapHeight, AgentID sateliteID) throws Exception {
		super(aid);
		surroundings = new int[9];
		droneMap = new Map(mapWidth, mapHeight);
		this.sateliteID = sateliteID;
		posX = 0;
		posY = 0;
	}
	
	/**
	 * Método donde el dron decide a qué dirección mover.
	 * @return dirección a la que se moverá.
	 */
	public int think(){
		ArrayList<Pair> mispares = new ArrayList<Pair>();
		ArrayList<Pair> misCandi= new ArrayList<Pair>();

		int posiOX=0,posiOY=0;

		/* FIXME
		 * Los angulos son de 0 a 360 (mirate la practica) y angle%90 no tiene sentido. 
		 * Ademas el coseno y el seno estan al reves. 
		 * Lo correcto:
		 * posiOX= (int) (posX + (Math.cos(angle) * distancia));
		 * posiOY= (int) (posY + (Math.sin(angle) * distancia));
		 */
		//Supongo que los ángulos son entre 0 a 90 grados
		posiOX= (int) (posX + (Math.sin(angle%90) * distancia));
		posiOY= (int) (posY + (Math.cos(angle%90)*distancia));


		/* FIXME
		 * Estan todas las direcciones mal menos OESTE (mirate la practica).
		 * Hay demasiados calculos ya que como minimo una de las direcciones no vale para nada. Eso es poco eficiente.
		 * Ademas fijate que el tercer componente (por el que juzgas si es un movimiento valido) solo esta formado
		 * por la componente del surroundings. No se comprueba si la posicion ya fue visitada.
		 * Los componentes del surroundings se cojen mal (mirate la practica). 
		 * Lo correcto:
		 * 	Norte:	posX, posY-1		surroundings[1]
		 * 	Este:	posX+1, posY		surroundings[5]
		 * 	Sur:	posX, posY+1		surroundings[7]
		 * 	Oeste:	posX-1, posY		surroundings[3]
		 */
		mispares.add(new Pair((float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY+1)), 2)),NORTE,surroundings[NORTE]));
		mispares.add(new Pair((float) Math.sqrt(Math.pow((posiOX-(posX+1)),2)+Math.pow((posiOY-posY), 2)),SUR,surroundings[SUR]));
		mispares.add(new Pair((float) Math.sqrt(Math.pow((posiOX-posX),2)+Math.pow((posiOY-(posY-1)), 2)),ESTE,surroundings[ESTE]));
		mispares.add(new Pair((float) Math.sqrt(Math.pow((posiOX-(posX-1)),2)+Math.pow((posiOY-posY), 2)),OESTE,surroundings[OESTE]));

		//Aquí se obtienen los candidatos a comprobar
		misCandi=obtenerCandidatos(mispares);

		//Aquí se toma una decisión.
		int dec=decision(misCandi);

		return dec;
	}

	/**
	 * decision funcion para tomar la decisión de movimiento
	 * se busca la distancia mas pequeña al objetivo en caso de empate se coge siempre la primera.
	 * @param datos
	 * @return -1(error) o decision
	 */
	public int decision(ArrayList<Pair> datos){
		float min=9999999;
		int i=0;
		int po=-1;
		while(i<datos.size()){

			if(datos.get(i).getFirst()<min){
				po=i;
				min=datos.get(i).getFirst();
			}
			i++;
		}
		if(min==9999999){
			return -1;
		}
		else{
			return datos.get(po).getSecond();
		}
	}

	/**
	 * ObtenerCandidatos devuelve un ArrayList con los candidatos de movimiento.
	 * @return n
	 */
	public ArrayList obtenerCandidatos(ArrayList<Pair> mios){
		ArrayList<Pair> n = new ArrayList<Pair>();
		for(int i=0;i<4;i++){
			if(mios.get(i).getT()==0){
				n.add(mios.get(i));
			}
			else{
				System.out.println("mala\n");
			}
		}
		return n;
	}
	
	/**
	 * Método para obtener un array con los movimientos libres del drone usando la memoria del mismo.
	 * @return Un array con lo que hay en las posiciones Este, Sur, Oeste y Norte a las que se podría mover, en ese orden.
	 */
	// POST DIAGRAMA DE CLASES
	private int[] getValidMovements() {
		int movimientosLibres[] = new int[4];
		/* TODO: Revisar la suma de valores. ¿Qué pasa si el drone ya ha guardado que es una posición
		 * ocupada (un 1) y el satélite le envía otro 1 de que está ocupada? ¿Da un 2 de visitado?
		 * Estos errores ocurrirán cuando el dron guarde en su mapa lo que hay en las posiciones.
		 */
		/* TODO (Alberto)
		 * El drone no guarda los obstaculos en el mapa, solo si los ha visitado o no.
		 * Los posible valores de la suma serian:
		 * Vacio y no visitado = 0
		 * Vacio y visitado = 2
		 * Obstaculo = 1
		 * Para mi estan bien. No veo el fallo.
		 */
		// CAMBIO REALIZADO: El norte puesto como posY-1 y sur posY+1 (estaba al revés)
		movimientosLibres[NORTE] = surroundings[1] + droneMap.getValue(posX, posY - 1);
		// La siguiente línea de código ¡PETA! porque intenta acceder a la posición X = -1 (arreglado)
		movimientosLibres[OESTE] = surroundings[3] + droneMap.getValue(posX - 1, posY);
		movimientosLibres[SUR] = surroundings[7] + droneMap.getValue(posX, posY + 1);
		movimientosLibres[ESTE] = surroundings[5] + droneMap.getValue(posX + 1, posY);
		return movimientosLibres;
	}

	/**
	 * createStatus: Crea estado para un objeto JSON de tipo drone
	 * @return estado
	 * @throws JSONException
	 */
	private JSONObject createStatus() throws JSONException {
		int movimiento = 0;

		JSONObject estado = new JSONObject();
		estado.put("connected", "Yes");
		estado.put("ready", "Yes");
		estado.put("movimiento", movimiento);

		return estado;

	}

	/**
	 * Getter del mapa, usado para el visualizador.
	 * @return el mapa del drone.
	 */
	public Map getDroneMap() {
		return droneMap;
	}

	/**
	 * sendInform se envia señal de confrimación al agente junto con su acción.
	 * @param id
	 * @param dec
	 */

	private void sendInform(AgentID id, JSONObject dec) {
		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
		msg.setSender(this.getAid());
		msg.addReceiver(id);
		// jsonobject
		msg.setContent(dec.toString());
		this.send(msg);

		try {
			msg = receiveACLMessage();
		} catch (InterruptedException ex) {
			System.err.println("Agente " + this.getName() + " Error de comuncicación");
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		if (msg.getPerformative().equals("INFORM")) {
			System.out.println("Confirmo continuacion");

		} else {
			exit = true;

		}
	}

	/**
	 * receiveStatus metodo para comunicar al satélite que le envie información.
	 * @param id
	 * @param dec
	 */
	private void receiveStatus(AgentID id, JSONObject dec) {
		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);

		msg.setSender(this.getAid());
		msg.addReceiver(id);
		msg.setContent(null);
		this.send(msg);
		try {
			msg = receiveACLMessage();

		} catch (InterruptedException ex) {
			System.err.println("Agente " + this.getName() + " Error de comuncicación");
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		if (msg.getPerformative().equals("INFORM")) {

			JSONObject contenido = null;
			try {
				contenido = new JSONObject(msg.getContent());
			} catch (JSONException ex) {
				ex.printStackTrace();
				Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
			}
			try {
				JSONObject aux = new JSONObject();
				aux = contenido.getJSONObject("gps");
				posX = aux.getInt("x");
				posY = aux.getInt("y");

				aux = contenido.getJSONObject("gonio");
				angle = (float) aux.getDouble("alpha");
				// Corregido, alpha estaba en aux y no en contenido

				// surroundings=(int[]) contenido.get("radar"); // No se puede hacer así
				// Una opción sería usando JSONArray, se tendría que mirar como pasarlo a un array normal tras sacarlo
				JSONArray jsArray = contenido.getJSONArray("radar");
				
				/* TODO: recupera bien lo que tiene al rededor (lo muestro por consola bien) 
				 * Pero parece que si lo pongo no termina en el mapa1 y si no lo pongo sí.
				 */
				
				for (int i=0; i < jsArray.length(); i++){
					surroundings[i] = jsArray.getInt(i);
				}
				// Compruebo si se reciben bien los alrededores:
				System.out.println("Alrededores del Dron: ");
				System.out.println("|"+surroundings[0]+", "+surroundings[1]+", "+surroundings[2]+"|");
				System.out.println("|"+surroundings[3]+", "+surroundings[4]+", "+surroundings[5]+"|");
				System.out.println("|"+surroundings[6]+", "+surroundings[7]+", "+surroundings[8]+"|");
						
			} catch (JSONException ex) {
				System.out.println("numeritos");
				ex.printStackTrace();
				Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
			}

		} else {
			exit = true;
		}

	}


	@Override
	public void finalize() {
		System.out.println("Agente " + this.getName() + " ha finalizado");
		super.finalize();
	}

	/*
	 * Método para las acciones del drone.
	 */
	@Override
	protected void execute() {
		ACLMessage message = new ACLMessage();
		JSONObject status = null;
		System.out.println("Agente " + this.getName() + " en ejecución");

		int decision = 0;

		try {
			status = createStatus();
		} catch (JSONException ex) {
			ex.printStackTrace();
			Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
		}
		while (!exit) {
			switch (estado) {
			case ESTADOREQUEST:
				receiveStatus(sateliteID, null);
				estado = ESTADOINFORM;
				break;
			case ESTADOINFORM:
				decision = think();
				if (decision < 0 || decision > 4) {
					ACLMessage fallo = new ACLMessage(ACLMessage.FAILURE);
					fallo.setSender(this.getAid());
					fallo.addReceiver(sateliteID);
					fallo.setContent(null);
				} else {
					try {
						status.remove("decision");
						System.out.println("decision " + decision);
						status.put("decision", decision);
					} catch (JSONException ex) {
						ex.printStackTrace();
						Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
					}
					sendInform(sateliteID, status);
					estado = ESTADOREQUEST;

				}
				break;
			}

		}
	}
}
