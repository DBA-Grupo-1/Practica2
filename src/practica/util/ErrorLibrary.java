package practica.util;

/**
 * Librería de Strings para los Errores de comunicación o rechazos.
 * 
 * @author Jonay
 */
public class ErrorLibrary {
	public static final String FailureCommunication = "An error occurred during communication.";
	public static final String EmptyContent = "The content field is empty.";
	public static final String BadlyStructuredContent = "The content’s structure doesn’t meet the JSON standard.";
	public static final String SenderDrone = "The sender can't be a drone.";
	public static final String FailureAgentID = "The Agent ID is not valid";
	public static final String FailureInformationAccess = "Failure when trying to access the asked Information.";
	public static final String UnespectedAmount = "The given amount is not between the limits.";
	public static final String NotChargeDecided = "Charger has decided not charge drone.";
	public static final String NotBatteryAnymore = "Charger is empty or has decided not to charge drone ever again.";
	public static final String FailureAcessPosition = "Failure when trying to access vector positions.";
	public static final String AlreadySubscribed = "The agent is already subscribed to this.";
	public static final String TooManyRequests = "There are too many petitions.";
	public static final String AlreadyInGoal = "Agent is already in a goal position.";
	public static final String IWontReachGoal = "Agent is not going to reach the goal.";
	public static final String MissingAgents = "There are agents not connected to platform";
	public static final String NotUnderstood = "The message has not been understood.";
}
