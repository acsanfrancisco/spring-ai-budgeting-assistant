package acsanfrancisco.budgeting.application.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key already used with a different message.");
    }
}
