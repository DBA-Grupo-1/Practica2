package Utilities;


//Clase para que probemos las cosas. No es necesario ponerla en el diagrama.

public class TestWithMainAlberto {
	public static void main (String args []){
		Map map = ImgMapConverter.imgToMap("src/Maps/Challenge.png");
		//printMap (map);
		crearRutaFicticia(map);  // Creo una ruta ficticia sólo para ver cómo se vería, puede atravesar obstáculos
		ImgMapConverter.mapToImg("src/Maps/Prueba.png", map);
	}
	
	public static void printMap (Map m){
		for (int i = 0; i < m.getHeigh(); i++){
			System.out.println("");
			for (int j = 0; j < m.getWidth(); j++)
				System.out.print(m.getValue(i, j));
		}
	}
	
	public static void crearRutaFicticia(Map map){
		int j =0;
		for(int i=0; i<300;i++){
			map.setvalue(i, j, Map.VISITADO);
			j++;
			map.setvalue(i, j, Map.VISITADO);
		}
	}
}
