package practica;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import practica.agent.*;
import practica.gui.Visualizer;
import practica.map.Map;
import practica.util.ImgMapConverter;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.AgentsConnection;

/**
 * @author Jonay
 * @author Daniel
 * @author Alberto
 * @author Ismael
 *
 */
public class Launcher {
	
	private AgentID id_satelite, id_charger;
	private Satellite satellite;
	private Drone drone1, drone2, drone3;
	private static Drone[] drones;
	private Visualizer visualizer;
	private Map map;
	private Charger charger;
	private static int droneAmount;
	private static AgentID[] droneIDs;
	
	/**
	 * @author Jahiel
	 * @author Daniel
	 * @param args
	 */
	public static void main(String[] args) {
		droneAmount = 5;
		droneIDs = new AgentID [droneAmount];
		drones = new Drone[droneAmount];
		DOMConfigurator.configure("src/Configuration/loggin.xml"); // ERR
        Logger logger = Logger.getLogger(Launcher.class);
        
        // QPID
        AgentsConnection.connect("localhost",5672, "test", "guest", "guest", false);
        
        Launcher launcher = new Launcher();
        
        launcher.id_satelite = new AgentID("Satelite");  
        launcher.id_charger = new AgentID("Charger");
		launcher.visualizer = new Visualizer(launcher);
		
		//Comentar la línea anterior y descomentar esta para lanzar sin visualizador.
		//launcher.launchWithoutVisualizer();
	}
	
	/**
	 * Crea y lanza los agentes cuando el visualizador se lo diga.
	 * @author Daniel
	 * @author Alberto
	 * @author Ismaels
	 */
	public void launch(){
        try{
            System.out.println("Main: Creando agentes");
            //Cargar el mapa del visualizador
        	map = visualizer.getMapToLoad();
        	
            //LLamar a los constructores de los agentes y asignar logs
        	satellite = new Satellite(id_satelite,id_charger, map, droneAmount, visualizer);
        	satellite.setLog(visualizer.getLogs()[0]);
        	charger = new Charger(id_charger, 500*droneAmount, id_satelite, visualizer);
        	charger.setLog(visualizer.getLogs()[1]);
        	for(int i=0; i<droneAmount; i++){
            	drones[i] = new Drone(new AgentID("Drone" + i), map.getWidth(), map.getHeight(), id_satelite, id_charger);
            	droneIDs[i] = drones[i].getAid();	
            	drones[i].setLog(visualizer.getLogs()[i+2]);
        	}
        	
        	//Conectar visualizador con el satélite
        	visualizer.setSatelite(satellite);
        	
        	//Lanzar agentes
        	System.out.println("MAIN : Iniciando agentes...");
            satellite.start();
            for(int i=0; i<droneAmount; i++){
            	drones[i].start();	
        	}
            charger.start();
        }catch(Exception e){
        	System.err.println("Main: Error al crear los agentes");
            System.exit(-1);
        }
	}
	
	/**
	 * @author Daniel
	 * @author Alberto
	 * @author Ismael
	 */
	public void launchWithoutVisualizer(){
        try{
            System.out.println("Main: Creando agentes");
            //PARTE CONFLICTIVA
        	map = ImgMapConverter.imgToMap("src/maps/MeetingPoint2.png");
    		ImgMapConverter.mapToImg("src/maps/pruebaoriginal.png", map);
    		//(Ismael) modificada la función Satellite para que acepte una identida de cargador
        	satellite = new Satellite(id_satelite,id_charger, map, droneAmount);
        	charger = new Charger(id_charger, 500*droneAmount, id_satelite);
        	for(int i=0; i<droneAmount; i++){
            	drones[i] = new Drone(new AgentID("Drone" + i), map.getWidth(), map.getHeight(), id_satelite, id_charger);
            	droneIDs[i] = drones[i].getAid();	
        	}
        	System.out.println("MAIN : Iniciando agentes...");
            satellite.start();
            for(int i=0; i<droneAmount; i++){
            	drones[i].start();	
        	}
            charger.start();
        }catch(Exception e){
        	System.err.println("Main: Error al crear los agentes");
			System.err.println(e.getMessage());
            System.exit(-1);
        }
	}
		
	/**
	 * Getter de droneIDs.
	 * @return la lista con todos los IDs de los drones.
	 */
	public static AgentID[] getDroneIDs (){
		return droneIDs;
	}

}
