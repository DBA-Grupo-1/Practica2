package practica;

import org.json.JSONArray;
import org.json.JSONException;

import practica.trace.Choice;
import practica.trace.Trace;
import practica.util.GPSLocation;

import com.google.gson.Gson;

public class TestGson {

	public static void main (String args []) {
		//Crear traza
		Trace t1 = new Trace();
		//Crear locations
		GPSLocation g1 = new GPSLocation(0, 0);
		GPSLocation g2 = new GPSLocation(50, 50);
		GPSLocation g3 = new GPSLocation(100, 100);
		
		//Crear choices
		Choice c1 = new Choice(0, g1);
		Choice c2 = new Choice(1, g2);
		Choice c3 = new Choice(2, g3);				
		
		//Meter choices en traza
		t1.add(c1);
		t1.add(c2);
		t1.add(c3);
		
		Gson gson = new Gson (); //Crear Gson
		String json = gson.toJson(t1); //Crear String Json (serializar)
		System.out.println(json);	
		//JSONArray ja = new JSONArray(json); //Crear objeto JSONArray a partir de la cadena		
		Trace t2 = gson.fromJson(json, Trace.class); //Crar traza a partir de Json (deserializar)
		//Sacar todo por pantalla para comprobar que todo ha ido bien.
		System.out.println(t2.size());
		System.out.println(t2.toString(Trace.POSITION_ONLY));
		
	}

}
