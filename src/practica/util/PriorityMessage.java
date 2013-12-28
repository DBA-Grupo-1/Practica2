package practica.util;

import es.upv.dsic.gti_ia.core.ACLMessage;

public class PriorityMessage extends ACLMessage implements Comparable<PriorityMessage>{
	public static final int CRITICAL = 0,
			HIGH = 1,
			AVERAGE = 2,
			LOW = 3;
	private int priority;

	public PriorityMessage(){
		this(ACLMessage.UNKNOWN, AVERAGE);
	}
	
	public PriorityMessage(int performative){
		this(performative, AVERAGE);
	}
	
	public PriorityMessage(int performative, int priority){
		super(performative);
		this.priority = priority;
	}

	public int getPriority(){
		return priority;
	}
	
	public void setPriority(int priority){
		this.priority = priority;
	}

	@Override
	public int compareTo(PriorityMessage o) {
		return priority - o.getPriority();
	}
}
