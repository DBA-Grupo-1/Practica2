package practica;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import practica.agent.*;
import practica.util.ImgMapConverter;
import practica.util.Map;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.AgentsConnection;

public class Launcher {

	public static void main(String[] args) {
		DOMConfigurator.configure("src/Configuration/loggin.xml"); // ERR
        Logger logger = Logger.getLogger(Launcher.class);
        
        // QPID
        AgentsConnection.connect("localhost",5672, "test", "guest", "guest", false);
        System.out.println("Main: Creando agentes");
        
        Map map = ImgMapConverter.imgToMap("src/maps/map3.png");
        AgentID id_satelite = new AgentID("Satelite");
        
        try{
        	Satelite satelite = new Satelite(id_satelite, map);
        	Drone drone = new Drone(new AgentID("Drone"), map.getWidth(), map.getHeigh(), id_satelite);
        	System.out.println("MAIN : Iniciando agentes...");
            satelite.start();
            drone.start();
        }catch(Exception e){
        	System.err.println("Main: Error al crear los agentes");
            System.exit(-1);
        }

	}

}
