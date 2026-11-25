package acsanfrancisco.budgeting.domain.aiconversation;

import java.util.UUID;

public record AiConversationId(UUID id) {
    public AiConversationId {
        if (id == null) {
            throw new IllegalArgumentException();
        }
    }

    public AiConversationId(){
        this(UUID.randomUUID());
    }
}
