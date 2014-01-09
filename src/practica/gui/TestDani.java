package practica.gui;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;






import practica.map.SharedMap;
import practica.util.ImgMapConverter;
import es.upv.dsic.gti_ia.core.AgentID;

public class TestDani{


private static SharedMap map;
private static AgentID[] droneIDs = new AgentID[6];

	public static void main (String args []) throws Exception{
		/*map = new SharedMap(ImgMapConverter.imgToMap("src/maps/MeetingPoint2.png"));
		ImgMapConverter.mapToImg("src/maps/pruebaoriginal.png", map);
		droneIDs[0] = new AgentID("Drone1");
		droneIDs[1] = new AgentID("Drone2");
		droneIDs[2] = new AgentID("Drone2");
		droneIDs[3] = new AgentID("Drone2");
		droneIDs[4] = new AgentID("Drone2");
		droneIDs[5] = new AgentID("Drone2");
		System.out.println(droneIDs[0].name);
		
		for (int k = 0; k < 6; k++)
			for (int i = 0; i < 100; i++)
					map.setValue(i, k, map.VISITADO, droneIDs[k]);
		

		for (int i = 50; i < 150; i ++)
			for (int j = 100; j < 150; j++)
				map.setValue(i, j, map.VISITADO, droneIDs[1]);
		
		for (int i = 50; i < 100; i ++)
			for (int j = 100; j < 150; j++)
				map.setValue(i, j, map.VISITADO, droneIDs[2]);
		
		ImgMapConverter.sharedMapToImg("src/maps/pruebaSharedMap.png", map);*/
		TestDani t = new TestDani();
		Visualizer v = new Visualizer(t);	
	}
	
	public static AgentID[] getDroneIDs(){
		return droneIDs;
	}

}
