package practica.gui;

import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import practica.Launcher;
import practica.agent.Satellite;
import practica.map.Map;
import practica.util.ImgMapConverter;
import es.upv.dsic.gti_ia.core.AgentID;
import java.awt.GridLayout;

/**
 * Interfaz de usuario.
 * @author Daniel
 *
 */
public class Visualizer extends JFrame {
	private static final long serialVersionUID = 1L;
	private boolean paused = true;
	private JComboBox mapSelector;
	private JButton btnLoadMap;
	private JButton btnLaunchExplorer;
	private JLabel miniMap;
	private JLabel satelliteMapIcon;
	private Map mapToLoad;
	private Launcher launcher;
	
	private Satellite satellite;
	private JTabbedPane tabbedPane;
	private JPanel satelliteLogPanel;
	private JPanel chargerLogPanel;
	private JPanel drone1LogPanel;
	private JPanel drone2LogPanel;
	private JPanel drone3LogPanel;
	private JPanel drone4LogPanel;
	private JPanel drone5LogPanel;
	private JPanel drone6LogPanel;
	private Log drone1Log;
	private Log drone2Log;
	private Log drone3Log;
	private Log satelliteLog;
	private Log drone4Log;
	private Log drone5Log;
	private Log drone6Log;
	private Log chargerLog;
	private JScrollPane satelliteScrollPane;
	private JScrollPane chargerScrollPane;
	private JScrollPane drone1ScrollPane;
	private JScrollPane drone2ScrollPane;
	private JScrollPane drone3ScrollPane;
	private JScrollPane drone4ScrollPane;
	private JScrollPane drone5ScrollPane;
	private JScrollPane drone6ScrollPane;
	private AgentID [] droneIDs;
	private Log [] logs;
	private JPanel infoPanel;
	private JLabel [] droneNamesLabels;
	private JLabel [] droneBatteries;
	
	/**
	 * Setter de satelite.
	 * @author Daniel
	 * @param sat satélite para poder comunicarse con él.
	 */
	public void setSatelite(Satellite sat){
		satellite = sat;
	}
	
	/**
	 * Getter del mapa.
	 * @author Daniel
	 * @return el mapa que ha cargado.
	 */
	public Map getMapToLoad(){
		return mapToLoad;
	}
	
	/**
	 * Getter del array con los logs.
	 * @return array con los logs.
	 */
	public Log[] getLogs(){
		return logs;
	}
	
	/**
	 * Constructor del visualizador
	 * @param l lanzador de la aplicación
	 */
	public Visualizer(Launcher l) {		
		try {			
			LookAndFeelInfo [] lookAndFeels = UIManager.getInstalledLookAndFeels();
			UIManager.setLookAndFeel(lookAndFeels[0].getClassName());
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}
		initialize();
		launcher = l;
		setBounds(100, 100, 800, 600);
		buildLogArray();
		setVisible(true);	
	}
	
	/**
	 * Construye un panel en la parte de abajo para mostrar información sobre los drones.
	 * @author Daniel
	 */
	private void buildInfoPanel(){
		//Cambio las columnas en función del número de drones.
		GridLayout infoPanelLayout = (GridLayout) infoPanel.getLayout();
		infoPanelLayout.setColumns(droneIDs.length);
		
		//Creo las etiquetas
		droneNamesLabels = new JLabel [droneIDs.length];
		droneBatteries = new JLabel [droneIDs.length];
		
		for (int i = 0; i < droneIDs.length; i++){
			droneNamesLabels [i] = new JLabel (droneIDs[i].name);
			droneBatteries [i] = new JLabel ("75");
		}
		
		//Las posiciono
		for (int i = 0; i < droneIDs.length; i++){
			infoPanel.add(droneNamesLabels[i]);
		}
		
		for (int i = 0; i < droneIDs.length; i++){
			infoPanel.add(droneBatteries[i]);
		}		
	}
	
	/**
	 * Cambia la información sobre la batería de un drone
	 * @param drone ID del drone cuya batería ha cambiado.
	 * @param newBattery nuevo valor de la batería.
	 */
	public void setDroneBattery (AgentID drone, int newBattery){
		int index = -1;

		//Busco el índice del drone
		for (int i = 0; i < droneIDs.length; i++){
			if (droneIDs[i].toString().equals(drone.toString()))
				index = i;
		}
		
		
		//Cambio su etiqueta
		if (index != -1)
			droneBatteries[index].setText(String.valueOf(newBattery));
	}
	
	/**
	 * Construye un array con los logs para que el Launcher lo pueda consultar y pasárselo a los agentes.
	 * @author Daniel
	 */
	private void buildLogArray(){
		logs = new Log [8];
		logs[0] = satelliteLog;
		logs[1] = chargerLog;
		logs[2] = drone1Log;
		logs[3] = drone2Log;
		logs[4] = drone3Log;
		logs[5] = drone4Log;
		logs[6] = drone5Log;
		logs[7] = drone6Log;
	}
	
	/**
	 * Cambia los nombres de las pestañas.
	 * @author Daniel
	 */
	private void setTabNames(){
		droneIDs = Launcher.getDroneIDs();
		//Copio los nombres
		for (int i = 0; i < droneIDs.length; i++){
			tabbedPane.setTitleAt(i + 2, droneIDs[i].name);
		}
		
	}
	
	/**
	 * Crea todos los componentes, los coloca, y asigna los eventos.
	 * @author Daniel
	 */
	private void initialize() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 250, 300);
		
		//Meter los nombres de los mapas
		File f = new File ("src/maps");
		String [] mapNames = f.list();
		getContentPane().setLayout(null);
		mapSelector = new JComboBox (mapNames);
		mapSelector.setSelectedIndex(-1);
		mapSelector.addActionListener(new MapSelectorActionListener());
		
		miniMap = new JLabel("");
		miniMap.setBounds(10, 40, 500, 500);
		getContentPane().add(miniMap);
		{
			tabbedPane = new JTabbedPane(JTabbedPane.TOP);
			tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
			tabbedPane.setBounds(520, 10, 254, 500);
			getContentPane().add(tabbedPane);
			{
				satelliteLogPanel = new JPanel();
				tabbedPane.addTab("Satellite", null, satelliteLogPanel, null);
				{
					satelliteLog = new Log();
				}
				satelliteLogPanel.setLayout(new BoxLayout(satelliteLogPanel, BoxLayout.X_AXIS));
				{
					satelliteScrollPane = new JScrollPane(satelliteLog);
					satelliteScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
					satelliteLogPanel.add(satelliteScrollPane);
				}
			}
			{
				chargerLogPanel = new JPanel();
				tabbedPane.addTab("Charger", null, chargerLogPanel, null);
				tabbedPane.setEnabledAt(1, true);
				chargerLogPanel.setLayout(new BoxLayout(chargerLogPanel, BoxLayout.X_AXIS));
				{
					chargerLog = new Log();
				}
				{
					chargerScrollPane = new JScrollPane(chargerLog);
					chargerLogPanel.add(chargerScrollPane);
				}
			}
			{
				drone1LogPanel = new JPanel();
				tabbedPane.addTab("Drone1", null, drone1LogPanel, null);
				{
					drone1Log = new Log();
				}
				drone1LogPanel.setLayout(new BoxLayout(drone1LogPanel, BoxLayout.X_AXIS));
				{
					drone1ScrollPane = new JScrollPane(drone1Log);
					drone1LogPanel.add(drone1ScrollPane);
				}
			}
			{
				drone2LogPanel = new JPanel();
				tabbedPane.addTab("Drone2", null, drone2LogPanel, null);
				drone2LogPanel.setLayout(new BoxLayout(drone2LogPanel, BoxLayout.X_AXIS));
				{
					drone2Log = new Log();
				}
				{
					drone2ScrollPane = new JScrollPane(drone2Log);
					drone2LogPanel.add(drone2ScrollPane);
				}
			}
			{
				drone3LogPanel = new JPanel();
				tabbedPane.addTab("Drone3", null, drone3LogPanel, null);
				drone3LogPanel.setLayout(new BoxLayout(drone3LogPanel, BoxLayout.X_AXIS));
				{
					drone3Log = new Log();
				}
				{
					drone3ScrollPane = new JScrollPane(drone3Log);
					drone3LogPanel.add(drone3ScrollPane);
				}
			}
			{
				drone4LogPanel = new JPanel();
				tabbedPane.addTab("Drone4", null, drone4LogPanel, null);
				drone4LogPanel.setLayout(new BoxLayout(drone4LogPanel, BoxLayout.X_AXIS));
				{
					drone4Log = new Log();
				}
				{
					drone4ScrollPane = new JScrollPane(drone4Log);
					drone4LogPanel.add(drone4ScrollPane);
				}
			}
			{
				drone5LogPanel = new JPanel();
				tabbedPane.addTab("Drone5", null, drone5LogPanel, null);
				drone5LogPanel.setLayout(new BoxLayout(drone5LogPanel, BoxLayout.X_AXIS));
				{
					drone5Log = new Log();
				}
				{
					drone5ScrollPane = new JScrollPane(drone5Log);
					drone5LogPanel.add(drone5ScrollPane);
				}
			}
			{
				drone6LogPanel = new JPanel();
				tabbedPane.addTab("Drone6", null, drone6LogPanel, null);
				drone6LogPanel.setLayout(new BoxLayout(drone6LogPanel, BoxLayout.X_AXIS));
				{
					drone6Log = new Log();
				}
				{
					drone6ScrollPane = new JScrollPane(drone6Log);
					drone6LogPanel.add(drone6ScrollPane);
				}
			}
		}
		
		satelliteMapIcon = new JLabel("");
		satelliteMapIcon.setBounds(10, 10, 500, 500);
		getContentPane().add(satelliteMapIcon);
		mapSelector.setBounds(10, 11, 112, 20);
		getContentPane().add(mapSelector);
		
		btnLoadMap = new JButton("Load map");
		btnLoadMap.addActionListener(new BtnLoadMapActionListener());
		btnLoadMap.setEnabled(false);
		btnLoadMap.setBounds(129, 10, 96, 23);
		getContentPane().add(btnLoadMap);
		
		btnLaunchExplorer = new JButton("Launch");
		btnLaunchExplorer.setVisible(false);
		btnLaunchExplorer.addActionListener(new BtnLaunchExplorerActionListener());
		btnLaunchExplorer.setBounds(10, 528, 112, 23);
		getContentPane().add(btnLaunchExplorer);
		{
			infoPanel = new JPanel();
			infoPanel.setBounds(134, 522, 638, 29);
			getContentPane().add(infoPanel);
			infoPanel.setLayout(new GridLayout(2, 6, 0, 0));
		}
	}
	
	/**
	 * Activa el botón "Think Once"
	 * @author Daniel
	 */
	public void enableThinkOnce(){
		btnLaunchExplorer.setEnabled(true);
	}
	
	/**
	 * Mira si el botón "Think Once" está habilitado.
	 * @author Daniel
	 * @return true si está deshabilitado (y por lo tanto se pulsó). False si no.
	 */
	public boolean paused(){
		return paused;
	}
	
	/**
	 * Actualiza el mapa.
	 * @author Daniel
	 */
	public void updateMap(){
        satelliteMapIcon.setIcon(new ImageIcon(ImgMapConverter.sharedMapToScalatedImg(satellite.getMapSeguimiento(), 500, 500)));
	}
	
	
	/**
	 * ActionListener del selector de mapas
	 * @author Daniel
	 *
	 */
	private class MapSelectorActionListener implements ActionListener {
		public void actionPerformed(ActionEvent arg0) {
			btnLoadMap.setEnabled(true);
			ImageIcon mapIcon = new ImageIcon(Visualizer.class.getResource("/maps/" + mapSelector.getSelectedItem().toString()));
			//Me creo una imagen a partir de la del icono
			Image img = mapIcon.getImage();
			//Me creo otra reescalándola.
			Image scalatedImg = img.getScaledInstance(500, 500, Image.SCALE_SMOOTH);
			//Se la asigno al icono
			mapIcon.setImage(scalatedImg);
			//Asigno el icon al label
			miniMap.setIcon(mapIcon);
		}
	}
	
	/**
	 * ActionListener del botón de cargar mapa.
	 * @author Daniel
	 *
	 */
	private class BtnLoadMapActionListener implements ActionListener {
		public void actionPerformed(ActionEvent arg0) {
			setBounds(100, 100, 800, 600);
			mapSelector.setVisible(false);
			miniMap.setVisible(false);
			btnLoadMap.setVisible(false);
	        //btnLaunchAll.setVisible(true);
	        btnLaunchExplorer.setVisible(true);
	        
	        mapToLoad = ImgMapConverter.imgToMap("src/maps/" + mapSelector.getSelectedItem().toString());
	        launcher.launch();			
			setTabNames();
			buildInfoPanel();
			updateMap();
		}
	}
	
	/**
	 * ActionListener del botón que lanza drones de 1 en 1.
	 * @author Daniel
	 *
	 */
	private class BtnLaunchExplorerActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			switch (btnLaunchExplorer.getText()){
			case "Launch" : 
				btnLaunchExplorer.setText("Pause");
				paused = false;
				break;
			case "Pause":
				btnLaunchExplorer.setText("Continue");
				paused = true;
				break;
			case "Continue":
				btnLaunchExplorer.setText("Pause");
				paused = false;
				break;
			}
		}
	}
}
