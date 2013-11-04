package Agents;

import Utilities.Map;
import es.upv.dsic.gti_ia.core.AgentID;
import es.upv.dsic.gti_ia.core.SingleAgent;

public class Drone extends SingleAgent {
	
	private int posX;
	private int posY;
	private float angle;
	private int [] surroundings;
	private Map droneMap;
	//POST DIAGRAMA DE CLASES
	private final int NORTE = 3;
	private final int OESTE = 2;
	private final int SUR = 1;
	private final int ESTE = 0;
	private final int END = -1;
	private final int patata = 0;

	public Drone(AgentID aid) throws Exception {
		super(aid);
		// TODO Auto-generated constructor stub
	}

}
