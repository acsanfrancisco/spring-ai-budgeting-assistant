package acsanfrancisco.budgeting.application.exception;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
public class AiProviderException extends RuntimeException {
    public enum Reason {
        INVALID_KEY("Check the configured API key."),
        ACCESS_DENIED("Check your account permissions for the selected model."),
        MODEL_NOT_FOUND("Check the name and availability of the configured model."),
        RATE_LIMITED("Usage limit reached. Check your account limits."),
        QUOTA_EXCEEDED("Quota exhausted. Check your available quota."),
        TIMEOUT("The request timed out."),
        UNAVAILABLE("The service is temporarily unavailable.");

        private final String guidance;
        Reason(String guidance) {
            this.guidance = guidance;
        }
    }

    private final Reason reason;
    private final String retryAfter;
    @Setter private UUID transactionId;

    public AiProviderException(Reason reason, String operation, Throwable cause, String retryAfter) {
        super("AI provider error (" + operation + "): " + reason.guidance, cause);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }
}
