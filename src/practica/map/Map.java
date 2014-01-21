package practica.map;

/**
 * Estructura de datos que contiene el mapa por donde se mueve el drone.
 * @author Daniel
 */ 
public class Map {
	private int matrix[][];		//Estructura de datos donde se guardan los datos del mapa.
	private int height;			//Altura del mapa.
	private int width;			//Anchura del mapa.
	public final static int LIBRE = 0;
	public final static int OBSTACULO = 1;
	public final static int VISITADO = 2;
	public final static int OBJETIVO = 3;

	/**
	 * Constructor por defecto. Todas las celdas se rellenan con el valor LIBRE.
	 * @author Daniel
	 * @param heigh 	Altura del mapa
	 * @param width 	Anchura del mapa
	 */
	public Map(int heigh, int width) {
		matrix = new int[heigh][width];
		this.height = heigh;
		this.width = width;

		// Por defecto todo está libre
		for (int i = 0; i < heigh; i++)
			for (int j = 0; j < width; j++)
				matrix[i][j] = LIBRE;
	}

	/**
	 * Constructor por copia.
	 * @author Daniel
	 * @param map 	Mapa original a copiar.
	 */
	public Map(Map map) {
		// Inicialización de componentes
		matrix = new int[map.getHeight()][map.getWidth()];
		height = map.getHeight();
		width = map.getWidth();

		// Copia de valores
		for (int i = 0; i < height; i++)
			for (int j = 0; j < width; j++)
				matrix[i][j] = map.getValue(j, i);
	}

	/**
	 * Getter de la altura
	 * @author Daniel
	 * @return Altura del mapa
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Getter de la anchura.
	 * @author Daniel
	 * @return Anchura del mapa
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Getter del valor de una celda.
	 * @author Daniel
	 * @param x Columna.
	 * @param y Fila.
	 * @return Valor de la celda en la posición x,y.
	 */
	public int getValue(int x, int y) {
		if (x < 0 || y < 0 || x >= this.getWidth() || y >= this.getHeight()) {
			return OBSTACULO;
		} else {
			return matrix[y][x];
		}
	}

	/**
	 * Setter del valor de una celda
	 * @author Daniel
	 * @param x 	Columna.
	 * @param y 	Fila.
	 * @param value Valor nuevo de la celda.
	 * @throws Exception Lanza una excepción si los valores x o y no son correctos
	 */
	public void setValue(int x, int y, int value) throws Exception {
		if(x < 0 || x >= this.width || y < 0 || y >= this.height){
			throw new Exception("La posición ("+x+", "+y+") no se encuentra dentro de los límites del mapa");
		}
		matrix[y][x] = value;
	}
}