package practica.util;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import practica.Launcher;
import practica.map.Map;
import practica.map.SharedMap;
import practica.map.SharedSquare;
import es.upv.dsic.gti_ia.core.AgentID;

/**
 * 
 * @author Daniel
 * @author Jonay
 */
public abstract class ImgMapConverter {
	
	/**
	 * Convierte un objeto del tipo BufferedImage a uno del tipo Map traduciendo los valores RGB a los distintos tipos
	 * de posibles valores.
	 * @author Daniel
	 * @param bf BufferedImage a convertir.
	 * @return Objeto del tipo Map correspondiente a traducir los valores de bf a su correspondiente.
	 */
	
	private static Map bufferedImageToMap (BufferedImage bf){
		int valueRGB; //Para guardar valores RGB
		int value; //Para insertar los valores "traducidos" en el mapa
		
		//Creo el mapa
		Map map = new Map (bf.getHeight(), bf.getWidth());
		
		//Lo Relleno
		for (int i = 0; i < map.getHeigh(); i++)
			for (int j = 0; j < map.getWidth(); j++){
				valueRGB = bf.getRGB(i, j);
				
				switch (valueRGB){
					case -1 : value = Map.LIBRE; break;
					case -16777216 : value = Map.OBSTACULO; break;
					case -1237980 : value = Map.OBJETIVO; break;	
					case -800000 : value = Map.VISITADO; break;
					default : value = -1; break; //Por si acaso.
				}
				
				try {
					map.setValue(i, j, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		return map;	
	}
	
	
	/**
	 * Convierte un objeto del tipo Image a uno del tipo BufferedImage, heredando algunas de sus propiedades importantes 
	 * (ancho, alto, valores RGB de cada píxel...)
	 * @author Daniel
	 * @param image Imagen a convertir.
	 * @return Objeto del tipo BufferedImage para poder trabajar con él.
	 */
	private static BufferedImage imageToBufferedImage(Image image) {
		//Hay que pasarla a ImageIcon para que tenga alto y ancho, si no no me lo coge el constructor de BufferedImage
	    image = new ImageIcon(image).getImage();

	    // Meto los valores de la imagen leída en un búffer de enteros RGB
	    BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null),  BufferedImage.TYPE_INT_ARGB);
	    Graphics g = bufferedImage.createGraphics();
	    g.drawImage(image, 0, 0, null);
	    g.dispose();

	    return bufferedImage;	 
	}
	
	/**
	 * Convierte el mapa de datos a un bÃºffer de imagen
	 * @author Jonay
	 * @param map El mapa de datos a convertir
	 * @return Devuelve un BufferedImage con los datos del mapa ya traducidos e introducidos 
	 */
	private static BufferedImage mapToBufferedImage(Map map){
		int valueRGB; // Para guardar valores "traducidos" a RGB
		int value; // Para leer los valores del mapa
		
		BufferedImage bf = new BufferedImage(map.getWidth(), map.getHeigh(), BufferedImage.TYPE_INT_ARGB);

		// Creo los colores de la imagen según los valores del mapa
		for (int i = 0; i < map.getHeigh(); i++)
			for (int j = 0; j < map.getWidth(); j++){
				value = map.getValue(i, j);
				
				switch (value){
					case Map.LIBRE: valueRGB = -1; break;
					case Map.OBSTACULO : valueRGB = -16777216; break;
					case Map.OBJETIVO : valueRGB = -1237980; break;
					case Map.VISITADO : valueRGB = -800000; break; 
					default : valueRGB = -1; break; //Por si acaso.
				}
				
				bf.setRGB(i, j, valueRGB);
			}
		return bf;	
	}
		
	/**
	 * Convierte una imagen con el formato de las imágenes de prácticas a un mapa.
	 * @author Daniel
	 * @param path Localización de la imagen.
	 * @return Objeto del tipo Map correspondiente a traducir cada píxel de la imagen a un valor determinado.
	 */
	public static Map imgToMap (String path){
		//Me creo objeto del tipo Image con el toolkit de java.
		Image img = Toolkit.getDefaultToolkit().createImage(path);
		//La convierto en BufferedImage
		BufferedImage bf = imageToBufferedImage(img);
		//La convierto en Map
		Map map = bufferedImageToMap (bf);
		
		return map;
	}
	
	/**
	 * Crea una imagen a partir de un mapa de datos
	 * @author Jonay
	 * @param path Ruta en la que se guardará la imagen
	 * @param map Mapa de datos del que se obtendrá la imagen
	 */
	public static void mapToImg (String path, Map map){		
		// Genera el bÃºffer de la imagen con los datos del mapa
		BufferedImage bf = mapToBufferedImage(map);
		
		// Crea el archivo de la imagen
		try {
			   ImageIO.write(bf, "png", new File(path));
		} catch (IOException e) {
			   System.out.println("Error de escritura");
		}
	}
	
	public static BufferedImage sharedMapToBufferedImage (SharedMap map){
		int valueRGB; // Para guardar valores "traducidos" a RGB
		int value; // Para leer los valores del mapa
		int [] droneColors = {Color.RED.getRGB(), Color.BLUE.getRGB(), Color.MAGENTA.getRGB(), Color.YELLOW.getRGB(), Color.GREEN.getRGB(), Color.ORANGE.getRGB()}; //Colores para los drones 
		BufferedImage bf = new BufferedImage(map.getWidth(), map.getHeigh(), BufferedImage.TYPE_INT_ARGB);
		AgentID [] droneIDs = Launcher.getDroneIDs(); //IDs de los drones
		
		// Creo los colores de la imagen según los valores del mapa
		for (int i = 0; i < map.getHeigh(); i++)
			for (int j = 0; j < map.getWidth(); j++){
				value = map.getValue(i, j);
				switch (value){
					case Map.LIBRE: valueRGB = -1; break;
					case Map.OBSTACULO : valueRGB = -16777216; break;
					case Map.OBJETIVO : valueRGB = -1237980; break;
					case Map.VISITADO : 
						SharedSquare square = map.getSharedSquare(i, j);
						//Miro el último agente que visitó la casilla y pinto la casilla de su color.
						AgentID id = square.getLastVisited();
						valueRGB = -1852; //Valor por defecto.
						
						for (int k = 0; k < droneIDs.length; k++){
							if (id.toString().equals(droneIDs[k].toString())){
								valueRGB = droneColors[k];
							}
						}						
						break; 
					default : valueRGB = -1; break; //Por si acaso.
				}
				
				bf.setRGB(i, j, valueRGB);
			}
		return bf;	
		
	}
	
	/**
	 * Crea una imagen a partir de un mapa compartido.
	 * @author Daniel
	 * @param path Ruta en la que se guardará la imagen.
	 * @param map Mapa de datos del que se obtendrá la imagen.
	 */
	public static void sharedMapToImg (String path, SharedMap map){
		BufferedImage bf = sharedMapToBufferedImage(map);
		
		// Crea el archivo de la imagen
		try {
			   ImageIO.write(bf, "png", new File(path));
		} catch (IOException e) {
			   System.out.println("Error de escritura");
		}
	}
	
	/**
	 * Crea una imagen escalada a partir de un mapa de datos
	 * @author Daniel
	 * @param map Mapa de datos del que se obtendrá la imagen.
	 * @param width Ancho de la imagen
	 * @param height Alto de la imagen
	 * @return Imagen correspondiente al mapa.
	 */
	public static Image mapToScalatedImg (Map map, int width, int height){
		BufferedImage bf = mapToBufferedImage(map);		
		return bf.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}

	/**
	 * Crea una imagen escalada a partir de un mapa de datos compartido
	 * @author Daniel
	 * @param map Mapa de datos del que se obtendrá la imagen.
	 * @param width Ancho de la imagen
	 * @param height Alto de la imagen
	 * @return Imagen correspondiente al mapa.
	 */
	public static Image sharedMapToScalatedImg(SharedMap map, int width, int height) {
		BufferedImage bf = sharedMapToBufferedImage(map);		
		return bf.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}
}