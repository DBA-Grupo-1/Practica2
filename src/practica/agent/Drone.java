package practica.agent;

import java.util.ArrayList;

import java.util.Random;

//NOTA_INTEGRACION (Ismael) ni de coña va esto en la version final
import java.util.*;
import practica.util.Map;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;
import es.upv.dsic.gti_ia.core.ACLMessage;

import java.util.logging.Level;
import java.util.logging.Logger;


//NOTA_INTEGRACION (Ismael) Esto no se usa
import javax.json.Json;
import javax.json.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;
//NOTA_INTEGRACION (Ismael) ¿¿Esto que es??
//Usamos Magentix y no Jason asi que no se por que esta aqui (ademas no se usa)
import jason.functions.Random;

public class Drone extends SingleAgent {
    private final int ESTADOREQUEST=0,ESTADOINFORM=1;
    //NOTA_INTEGRACION (Ismael) exit no tiene sentido fuera del execute.
    private boolean exit;
    private int estado;
	private int posX;
	private int posY;
	private float angle;
	private int [] surroundings;
	private Map droneMap;
	public final int NORTE = 3;
	public final int OESTE = 2;
	public final int SUR = 1;
	public final int ESTE = 0;
	public final int END = -1;

	//NOTA_INTEGRACION (Ismael) no entiendo por que esto esta declarado como atributo si despues
	//se declara como variable local del execute. De hecho el Eclipse avisa de que no se usa.
    private int decision=0;
	
	//CAMBIO_INTEGRACION (Ismael) Esto es fallo mio. Se me olvido añadir esta variable al
	//diagrama de clases y Jonay la ha llamado de una forma e Ismael de otra.
	//Me quedo con la de Jonay asi que cambia el nombre en tu codigo (satelite por sateliteID).
	private AgentID sateliteID;

	public Drone(AgentID aid, int mapWidth, int mapHeight, AgentID sateliteID) throws Exception{     
	    super(aid);
	    //NOTA_INTEGRACION (Jonay) deberia ser int[9] (en la que se encuentra el agente tambien cuenta)
	    surroundings = new int[8];
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
		java.util.Random r = new Random();
		float direccion;
		boolean rango;
		int ran;
		ArrayList <Integer> movimientos = new ArrayList <Integer> (4);
		int movLibres [] = new int [4];
		
		direccion = angle / 90;
		rango = (angle % 90) != 0;
		ran = r.nextInt(2); //Generar un número entre 0 y 1.
		
		System.out.println(direccion + " " + rango);
		
		
		if (rango){
			movimientos.add(0, (int) ((ran + direccion) % 4));
			movimientos.add(1, (int) ((1 - ran + direccion) % 4));
			movimientos.add(2, (int) ((ran + direccion + 2) % 4));
			movimientos.add(3, (int) ((3 - ran + direccion) % 4));
		}
		else{
			movimientos.add(0, (int) direccion);
			movimientos.add(1, (int) ((direccion + 2 - ran) % 4)); //En lugar de que lo aleatorio sea donde pongo cada cosa, hago
			movimientos.add(2, (int) ((direccion + 1 + ran) % 4)); //que lo aleatorio sea que pongo en donde. Culpa del ArrayList y sus indices.
			movimientos.add(3, (int) ((direccion + 2) % 4));
		}		
		
		movLibres = getValidMovements();
		
		for (int i = 0; i < 4; i ++)
			if (movLibres[movimientos.get(i)] != 0) //Si no es 0 la casilla o no está libre o se ha pasado por ella
				movimientos.remove(i);				
		
		if (movimientos.isEmpty())
			return -1; //El agente no se puede mover.
		else
			return movimientos.get(0);
	}
	
	/**
	 * Método para obtener un array con los movimientos libres del drone usando la memoria del mismo.
	 * @return Un array con lo que hay en las posiciones Este, Sur, Oeste y Norte a las que se podría mover, en ese orden.
	 */
	//POST DIAGRAMA DE CLASES
	private int [] getValidMovements(){
		int movimientosLibres [] = new int [4];
		
		movimientosLibres [NORTE] = surroundings [1] + droneMap.getValue(posX, posY + 1);
		movimientosLibres [OESTE] = surroundings [3] + droneMap.getValue(posX - 1, posY);
		movimientosLibres [SUR] = surroundings [7] + droneMap.getValue(posX, posY - 1);
		movimientosLibres [ESTE] = surroundings [5] + droneMap.getValue(posX + 1, posY);
		
		return movimientosLibres;		
	}
	
	/**
     * createStatus: Crea estado para un objeto JSON de tipo drone
     * @return estado
     * @throws JSONException
     */
    private JSONObject createStatus() throws JSONException{
      int movimiento=0;


         JSONObject estado = new JSONObject();
         estado.put("connected","Yes");
         estado.put("ready","Yes");
         estado.put("movimiento",movimiento);

        return estado;

    }
	
	/**
	 * sendInform se envia señal de confrimación al agente junto con su acción.
	 * @param id
	 * @param dec
	 */
	 private void sendInform(AgentID id, JSONObject dec){
	       ACLMessage msg= new ACLMessage(ACLMessage.REQUEST);
	       msg.setSender(this.getAid());
	       msg.addReceiver(id);
	       //jsonobject
	       msg.setContent(dec.toString());


	        try {
	            msg = receiveACLMessage();
	        } catch (InterruptedException ex) {
	            System.err.println("Agente "+this.getName()+" Error de comuncicación");
	            Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
	        }
	      if(msg.getPerformative().equals("INFORM")){
	            System.out.println("Confirmo continuacion");

	       }
	        else{
	        	//NOTA_INTEGRACION (Ismael) exit aqui no tiene sentido
	           exit=true;
	        	//NOTA_INTEGRACION (Ismael) no se debe llamar al metodo finalize().
	        	//El sistema lo llama automaticamente al salirse del execute()
	           finalize();
	        }
	   }

	   /**
	    * receiveStatus metodo para comunicar al satélite que le envie información.
	    * @param id
	    * @param dec
	    */
	   private void receiveStatus(AgentID id,JSONObject dec){
	       ACLMessage msg= new ACLMessage(ACLMessage.REQUEST);

	       msg.setSender(this.getAid());
	       msg.addReceiver(id);
	       msg.setContent(null);
	        try {
	            msg = receiveACLMessage();


	        } catch (InterruptedException ex) {
	            System.err.println("Agente "+this.getName()+" Error de comuncicación");
	            Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
	        }
	       if(msg.getPerformative().equals("INFORM")){
	           
	           JSONObject contenido=null;
	            try {
	                contenido = new JSONObject(msg.getContent());
	            } catch (JSONException ex) {
	                ex.printStackTrace();
	                Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
	            }
	            try {
	                JSONObject aux= new JSONObject();
	                aux=contenido.getJSONObject("gps");
	                posX=aux.getInt("x");
	                posY=aux.getInt("y");

	                aux=contenido.getJSONObject("gonio");
	                angle=(float)contenido.getInt("alpha");

	                //CAMBIO_INTEGRACION (Ismael) cambia de ArrayList<Integer> a int[]
	                //surroundings=(int[]) contenido.get("radar");
	                surroundings=(ArrayList<Integer>) contenido.get("radar");

	                
	            } catch (JSONException ex) {
	                ex.printStackTrace();
	                Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
	            }


	       }
	        else{
	        	//NOTA_INTEGRACION (Ismael) exit aqui no tiene sentido
		       exit=true;
		       	//NOTA_INTEGRACION (Ismael) no se debe llamar al metodo finalize().
		       	//El sistema lo llama automaticamente al salirse del execute()
	           finalize();
	        }

	   }
	   

	   //CAMBIO_INTEGRACION (Ismael) es public
	   @Override
	   private void finalize(){
	       System.out.println("Agente "+this.getName()+" ha finalizado");
	       super.finalize();
	}
	   
	   /*
	    * Método para las acciones del drone.
	    *
	    */
	 //CAMBIO_INTEGRACION (Ismael) es protected
	   @Override
	   private void execute(){
	        ACLMessage message = new ACLMessage();
	        
	        JSONObject status= null;
		
	        int decision=0;

	        try {
	            status = createStatus();
	        } catch (JSONException ex) {
	            ex.printStackTrace();
	            Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
	        }
	        while(!exit){
	            switch(estado){
	                case ESTADOREQUEST:
	                          receiveStatus(satelite,null);
	                          estado=ESTADOINFORM;
	                         break;
	                case ESTADOINFORM:
	                    decision=think();
	                    if(decision<0||decision>4){
	                        ACLMessage fallo = new ACLMessage(ACLMessage.FAILURE);
	                        fallo.setSender(this.getAid());
	                        fallo.addReceiver(satelite);
	                        fallo.setContent(null);
	                    }
	                    else{
	                            try {
	                                status.remove("movimiento");
	                                status.put("movimiento", String.valueOf(decision));
	                            } catch (JSONException ex) {
	                                 ex.printStackTrace();
	                                 Logger.getLogger(Drone.class.getName()).log(Level.SEVERE, null, ex);
	                            }
	                        sendInform(satelite,status);
	                        estado=ESTADOREQUEST;
	                    
	                    }
	                    break;
	            }
	            
	       }
	    }
}
