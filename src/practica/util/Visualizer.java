package practica.util;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import practica.Launcher;
import practica.agent.Drone;
import practica.agent.Satelite;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.AgentsConnection;

public class Visualizer extends JFrame {
	private JPanel droneMap;
	private JPanel satelliteMap;
	private JComboBox mapSelector;
	private JButton btnLoadMap;
	private JButton btnThinkOnce;
	private JButton btnFindTarget;
	private JLabel miniMap;
	private JLabel droneMapIcon;
	private JLabel satelliteMapIcon;
	
	private Drone drone;
	private Satelite satelite;
	
	public Visualizer() {		
		initialize();
		setVisible(true);		
	}
	private void initialize() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 275, 275);
		
		//Meter los nombres de los mapas
		File f = new File ("src/Maps");
		String [] mapNames = f.list();
		mapSelector = new JComboBox(mapNames);
		mapSelector.addActionListener(new MapSelectorActionListener());
		mapSelector.setBounds(10, 1, 112, 20);
		getContentPane().setLayout(null);
		
		btnLoadMap = new JButton("Load map");
		btnLoadMap.addActionListener(new BtnLoadMapActionListener());
		btnLoadMap.setEnabled(false);
		btnLoadMap.setBounds(128, 0, 96, 23);
		getContentPane().add(btnLoadMap);
		
		droneMap = new JPanel();
		droneMap.setBounds(10, 29, 350, 350);
		getContentPane().add(droneMap);
		droneMap.setLayout(null);
		
		miniMap = new JLabel("");
		miniMap.setBounds(0, 0, 200, 200);
		droneMap.add(miniMap);
		
		droneMapIcon = new JLabel("");
		droneMapIcon.setBounds(0, 0, 350, 350);
		droneMap.add(droneMapIcon);
		getContentPane().add(mapSelector);
		satelliteMap = new JPanel();
		satelliteMap.setBounds(370, 29, 350, 350);
		getContentPane().add(satelliteMap);
		satelliteMap.setLayout(null);
		
		satelliteMapIcon = new JLabel("");
		satelliteMapIcon.setBounds(0, 0, 350, 350);
		satelliteMap.add(satelliteMapIcon);
		
		btnThinkOnce = new JButton("Think once");
		btnThinkOnce.setBounds(10, 390, 190, 23);
		getContentPane().add(btnThinkOnce);
		
		btnFindTarget = new JButton("Find target");
		btnFindTarget.setBounds(10, 424, 190, 23);
		getContentPane().add(btnFindTarget);
	}
	private class MapSelectorActionListener implements ActionListener {
		public void actionPerformed(ActionEvent arg0) {
			btnLoadMap.setEnabled(true);
			ImageIcon mapIcon = new ImageIcon(Visualizer.class.getResource("/Maps/" + mapSelector.getSelectedItem().toString()));
			//Me creo una imagen a partir de la del icono
			Image img = mapIcon.getImage();
			//Me creo otra reescalándola.
			Image scalatedImg = img.getScaledInstance(200, 200, Image.SCALE_SMOOTH);
			//Se la asigno al icono
			mapIcon.setImage(scalatedImg);
			//Asigno el icon al label
			miniMap.setIcon(mapIcon);
		}
	}
	private class BtnLoadMapActionListener implements ActionListener {
		public void actionPerformed(ActionEvent arg0) {
			setBounds(100, 100, 800, 600);
			mapSelector.setVisible(false);
			miniMap.setVisible(false);
			btnLoadMap.setVisible(false);
			
			DOMConfigurator.configure("src/Configuration/loggin.xml"); // ERR
	        Logger logger = Logger.getLogger(Launcher.class);
	        
	        // QPID
	        AgentsConnection.connect("localhost",5672, "test", "guest", "guest", false);
	        
	        Map map = ImgMapConverter.imgToMap("src/Maps/" + mapSelector.getSelectedItem().toString());
	        AgentID id_satelite = new AgentID("Satelite");
	        
	        try{
	        	satelite = new Satelite(id_satelite, map);
	        	drone = new Drone(new AgentID("Drone"), map.getWidth(), map.getHeigh(), id_satelite);
	            satelite.start();
	            drone.start();
	            droneMapIcon.setIcon(new ImageIcon(ImgMapConverter.mapToScalatedImg(drone.getDroneMap(), 350, 350)));
	            satelliteMapIcon.setIcon(new ImageIcon(ImgMapConverter.mapToScalatedImg(satelite.getMapOriginal(), 350, 350)));
	        }catch(Exception e){
	        	System.err.println("Main: Error al crear los agentes");
	            System.exit(-1);
	        }
			
		}
	}
}
