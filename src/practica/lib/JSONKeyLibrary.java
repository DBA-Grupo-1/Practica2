package practica.lib;

/**
 * Biblioteca de Strings con las keys que usamos en los objetos JSON.
 * @author Daniel
 * @author Jahiel
 */
public class JSONKeyLibrary {

	/***************************************************************
	 **********************GENERAL********************************** 
	 **************************************************************/
	public static final String Subject = "Subject";
	public static final String DroneID = "DroneID";
	
	

	/***************************************************************
	 ****************ESPECÍFICOS DE PROTOCOLOS********************** 
	 **************************************************************/
	
	//Recharge
	public static final String RequestAmount = "Requested battery amount";
	public static final String AmountGiven = "Given battery amount";
	
	//MOVE DRONE
	public static final String Decision = "Decision";
	
	//INFORMACION
	public static final String ConflictiveBox = "Conflictive box";
}
