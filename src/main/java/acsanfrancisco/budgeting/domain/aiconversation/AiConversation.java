package acsanfrancisco.budgeting.domain.aiconversation;

import acsanfrancisco.budgeting.domain.user.UserId;
import lombok.Getter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
public class AiConversation {

    private static final Duration INACTIVITY_TIMEOUT = Duration.ofHours(24);

    private final AiConversationId id;

    private final UserId userId;

    private AiConversationStatus status;

    private Instant expiresAt;

    public AiConversation (UserId userId) {
        this(new AiConversationId(), userId, AiConversationStatus.ACTIVE,
                Instant.now().plus(INACTIVITY_TIMEOUT));
    }

    public AiConversation(AiConversationId id, UserId userId, AiConversationStatus status, Instant expiresAt) {
        if(id == null) {
            throw new IllegalArgumentException("Id must be informed");
        }
        if(userId == null) {
            throw new IllegalArgumentException("User must be informed");
        }
        if(status == null) {
            throw new IllegalArgumentException("Status must be informed");
        }
        if(expiresAt == null) {
            throw new IllegalArgumentException("Expiration must be informed");
        }
        this.id = id;
        this.status = status;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    public boolean renewIfActive() {
        if (status == AiConversationStatus.CLOSED) {
            return false;
        }
        Instant now = Instant.now();
        if (!now.isBefore(expiresAt)) {
            this.status = AiConversationStatus.CLOSED;
            return false;
        }
        expiresAt = now.plus(INACTIVITY_TIMEOUT);
        return true;
    }

    public void close(){
        this.status = AiConversationStatus.CLOSED;
    }

    public UUID getUserId() {
        return userId.id();
    }
}
