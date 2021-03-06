package practica;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import practica.agent.*;
import practica.util.ImgMapConverter;
import practica.util.Map;
import practica.util.Visualizer;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.AgentsConnection;

/**
 * 
 * @author jahiel
 *
 */
public class Launcher {
	
	AgentID id_satelite;
	Satelite satelite;
	Drone drone;
	Visualizer visualizer;
	Map map;

	/**
	 * @author jahiel
	 * @param args
	 */
	public static void main(String[] args) {
		DOMConfigurator.configure("src/Configuration/loggin.xml"); // ERR
        Logger logger = Logger.getLogger(Launcher.class);
        
        // QPID
        AgentsConnection.connect("localhost",5672, "test", "guest", "guest", false);
        
        Launcher launcher = new Launcher();
        
        launcher.id_satelite = new AgentID("Satelite");  
		launcher.visualizer = new Visualizer(launcher);
		
		//Comentar la línea anterior y descomentar esta para lanzar sin visualizador.
		//launcher.launchWithoutVisualizer();
	}
	
	/**
	 * Crea y lanza los agentes cuando el visualizador se lo diga.
	 */
	public void launch(){
        try{
            System.out.println("Main: Creando agentes");
        	map = visualizer.getMapToLoad();
        	satelite = new Satelite(id_satelite, map, visualizer);
        	drone = new Drone(new AgentID("Drone"), map.getWidth(), map.getHeigh(), id_satelite);
        	System.out.println("MAIN : Iniciando agentes...");
        	visualizer.setSatelite(satelite);
            satelite.start();
            drone.start();
        }catch(Exception e){
        	System.err.println("Main: Error al crear los agentes");
            System.exit(-1);
        }
	}
	
	public void launchWithoutVisualizer(){
        try{
            System.out.println("Main: Creando agentes");
        	map = ImgMapConverter.imgToMap("src/maps/dead end.png");
        	satelite = new Satelite(id_satelite, map);
        	drone = new Drone(new AgentID("Drone"), map.getWidth(), map.getHeigh(), id_satelite);
        	System.out.println("MAIN : Iniciando agentes...");
            satelite.start();
            drone.start();
        }catch(Exception e){
        	System.err.println("Main: Error al crear los agentes");
			System.err.println(e.getMessage());
            System.exit(-1);
        }
	}
		

}
