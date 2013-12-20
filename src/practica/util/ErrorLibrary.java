package practica.util;

/**
 * Librería de Strings para los Errores de comunicación o rechazos.
 * 
 * @author Jonay
 */
public class ErrorLibrary {
	public static final String FailureCommunication = "FailureCommunication";
	public static final String FailureCommunicationReason = "An error occurred during communication.";
	
	public static final String EmptyContent = "EmptyContent";
	public static final String EmptyContentReason = "The content field is empty.";
	
	public static final String BadlyStructuredContent = "BadlyStructuredContent";
	public static final String BadlyStructuredContentReason = "The content’s structure doesn’t meet the JSON standard.";
	
	public static final String SenderDrone = "SenderDrone";
	public static final String SenderDroneReason = "The sender can't be a drone.";
	
	public static final String FailureAgentID = "FailureAgentID";
	public static final String FailureAgentIDReason = "The Agent ID is not valid";
	
	public static final String FailureInformationAccess = "FailureInformationAccess";
	public static final String FailureInformationAccessReason = "Failure when trying to access the asked Information.";
	
	public static final String UnespectedAmount = "UnespectedAmount";
	public static final String UnespectedAmountReason = "The given amount is not between the limits.";
	
	public static final String NotChargeDecided = "NotChargeDecided";
	public static final String NotChargeDecidedReason = "Charger has decided not charge drone.";
	
	public static final String NotBatteryAnymore = "NotBatteryAnymore";
	public static final String NotBatteryAnymoreReason = "Charger is empty or has decided not to charge drone ever again.";
	
	public static final String FailureAcessPosition = "FailureAcessPosition";
	public static final String FailureAcessPositionReason = "Failure when trying to access vector positions.";
	
	public static final String AlreadySubscribed = "AlreadySubscribed";
	public static final String AlreadySubscribedReason = "The agent is already subscribed to this.";
	
	public static final String TooManyRequests = "TooManyRequests";
	public static final String TooManyRequestsReason = "There are too many petitions.";
	
	public static final String AlreadyInGoal = "AlreadyInGoal";
	public static final String AlreadyInGoalReason = "Agent is already in a goal position.";
	
	public static final String IWontReachGoal = "IWontReachGoal";
	public static final String IWontReachGoalReason = "Agent is not going to reach the goal.";
	
	public static final String MissingAgents = "MissingAgents";
	public static final String MissingAgentsReason = "There are agents not connected to platform";
	
	public static final String UnderstoodReason = "The message has not been understood.";
}
