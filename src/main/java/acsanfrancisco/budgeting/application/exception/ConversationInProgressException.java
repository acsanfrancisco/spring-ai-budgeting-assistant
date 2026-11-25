package acsanfrancisco.budgeting.application.exception;

public class ConversationInProgressException extends RuntimeException {
    public ConversationInProgressException() {
        super("There is already a message being processed");
    }
}
