package practica.gui;

import java.awt.BorderLayout;
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

import es.upv.dsic.gti_ia.core.AgentID;
import practica.agent.Satellite;
import practica.map.Map;
import practica.util.ImgMapConverter;

import java.awt.Font;

/**
 * Interfaz de usuario.
 * @author Daniel
 *
 */
public class Visualizer extends JFrame {
	private static final long serialVersionUID = 1L;
	private JComboBox mapSelector;
	private JButton btnLoadMap;
	private JButton btnLaunchExplorer;
	private JButton btnLaunchAll;
	private JLabel miniMap;
	private JLabel satelliteMapIcon;
	private Map mapToLoad;
	private TestDani launcher;
	
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
	private JButton buttonTest;
	private Log chargerLog;
	private JScrollPane satelliteScrollPane;
	private JScrollPane chargerScrollPane;
	private JScrollPane drone1ScrollPane;
	private JScrollPane drone2ScrollPane;
	private JScrollPane drone3ScrollPane;
	private JScrollPane drone4ScrollPane;
	private JScrollPane drone5ScrollPane;
	private JScrollPane drone6ScrollPane;
	private String [] droneNames;
	private Log [] logs;
	
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
	 * Constructor. Inicializa componentes y se hace visible.
	 * TODO cambiar argumento tras testeo.
	 * @author Daniel
	 */
	public Visualizer(TestDani l) {		
		try {			
			LookAndFeelInfo [] lookAndFeels = UIManager.getInstalledLookAndFeels();
			for (int i = 0; i < lookAndFeels.length; i++)
				System.out.println(lookAndFeels[i].getClassName());
			UIManager.setLookAndFeel(lookAndFeels[0].getClassName());
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | UnsupportedLookAndFeelException e1) {
			e1.printStackTrace();
		}
		initialize();
		launcher = l;
		setBounds(100, 100, 800, 600);
		setTabNames();
		buildLogArray();
		setVisible(true);	
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
		AgentID[] droneIDs = launcher.getDroneIDs();
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
		mapSelector.addActionListener(new MapSelectorActionListener());
		{
			buttonTest = new JButton("Test");
			buttonTest.addActionListener(new ButtonTestActionListener());
			buttonTest.setBounds(404, 528, 89, 23);
			getContentPane().add(buttonTest);
		}
		{
			tabbedPane = new JTabbedPane(JTabbedPane.TOP);
			tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 12));
			tabbedPane.setBounds(520, 10, 254, 541);
			getContentPane().add(tabbedPane);
			{
				satelliteLogPanel = new JPanel();
				tabbedPane.addTab("Satellite", null, satelliteLogPanel, null);
				{
					satelliteLog = new Log();
					//satelliteLogPanel.add(satelliteLog);
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
					//chargerLogPanel.add(chargerLog);
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
					//drone1LogPanel.add(drone1Log);
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
					//drone2LogPanel.add(drone2Log);
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
					//drone3LogPanel.add(drone3Log);
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
					//drone4LogPanel.add(drone4Log);
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
					//drone5LogPanel.add(drone5Log);
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
					//drone6LogPanel.add(drone6Log);
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
		
		miniMap = new JLabel("");
		miniMap.setBounds(10, 44, 210, 210);
		getContentPane().add(miniMap);
		
		btnLaunchExplorer = new JButton("Launch first explorer");
		btnLaunchExplorer.addActionListener(new BtnLaunchExplorerActionListener());
		btnLaunchExplorer.setBounds(10, 528, 190, 23);
		getContentPane().add(btnLaunchExplorer);
		
		btnLaunchAll = new JButton("Launch all");
		btnLaunchAll.addActionListener(new BtnLaunchAllActionListener());
		btnLaunchAll.setBounds(210, 528, 190, 23);
		getContentPane().add(btnLaunchAll);
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
	public boolean isBtnThinkOnceEnabled(){
		return btnLaunchExplorer.isEnabled();
	}
	
	/**
	 * Mira si el botón "Find target" está habilitado.
	 * @author Daniel
	 * @return true si está deshabilitado (y por lo tanto se pulsó). False si no.
	 */
	public boolean isBtnFindTargetEnabled(){
		return btnLaunchAll.isEnabled();
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
			Image scalatedImg = img.getScaledInstance(210, 210, Image.SCALE_SMOOTH);
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
	        
	        mapToLoad = ImgMapConverter.imgToMap("src/maps/" + mapSelector.getSelectedItem().toString());
	        //TODO: descomentar despues de testeo.
	        //launcher.launch();			
		}
	}
	
	/**
	 * ActionListener del botón para lanzar todos los drones.
	 * @author Daniel
	 *
	 */
	private class BtnLaunchAllActionListener implements ActionListener {
		public void actionPerformed(ActionEvent arg0) {
			btnLaunchExplorer.setEnabled(false);
			btnLaunchAll.setEnabled(false);
			//System.out.println("Botones desactivados");
		}
	}
	
	/**
	 * ActionListener del botón que lanza drones de 1 en 1.
	 * @author Daniel
	 *
	 */
	private class BtnLaunchExplorerActionListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			btnLaunchExplorer.setEnabled(false);
			btnLaunchExplorer.setText("Launch next explorer");
		}
	}
	private class ButtonTestActionListener implements ActionListener {
		public void actionPerformed(ActionEvent arg0) {
			satelliteLog.addMessage(Log.SENDED, "Drone4", "Registration", "Register", "Probando el content del mensaje, también pruebo los espacio y que se redimensione bien.");
			chargerLog.addMessage(Log.RECEIVED, "Drone1", "Registration", "Register", "Probando el content del mensaje, también pruebo los espacio y que se redimensione bien.");
			
			//satelliteLog.label.setText("patata");
		}
	}
}
