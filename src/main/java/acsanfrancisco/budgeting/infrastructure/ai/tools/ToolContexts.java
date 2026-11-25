package acsanfrancisco.budgeting.infrastructure.ai.tools;

import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Reads the values that AssistantChatClient puts in the ToolContext. The model never sees
 * them, so a missing user means the server called the tool without authentication.
 */
public final class ToolContexts {

    public static final String USER_ID = "userId";
    public static final String TRANSACTION_REQUEST_KEY = "transactionRequestKey";

    private ToolContexts() {
    }

    static UserId userId(ToolContext context) {
        if (context == null || !(context.getContext().get(USER_ID) instanceof UserId userId)) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return userId;
    }

    /** Present only for text requests sent with an Idempotency-Key. */
    static TransactionRequestKey transactionRequestKey(ToolContext context) {
        return (TransactionRequestKey) context.getContext().get(TRANSACTION_REQUEST_KEY);
    }
}
