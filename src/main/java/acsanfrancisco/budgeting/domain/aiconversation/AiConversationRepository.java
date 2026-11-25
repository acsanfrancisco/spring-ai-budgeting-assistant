package acsanfrancisco.budgeting.domain.aiconversation;

import acsanfrancisco.budgeting.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

public interface AiConversationRepository {

    AiConversation save(AiConversation conversation);

    Optional<AiConversation> findActiveByUserId(UserId userId);

    void delete(AiConversationId id);

    /**
     * Deletes closed conversations and those whose inactivity deadline passed at {@code now}.
     *
     * @return the number of deleted conversations
     */
    int deleteInactive(Instant now);

}
