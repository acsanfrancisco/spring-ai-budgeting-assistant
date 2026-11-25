package acsanfrancisco.budgeting.infrastructure.persistence.entity;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationStatus;
import acsanfrancisco.budgeting.domain.user.UserId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_ai_conversation")
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private AiConversationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static AiConversationEntity from(AiConversation aiConversation) {
        return new AiConversationEntity(
                aiConversation.getId().id(),
                aiConversation.getUserId(),
                aiConversation.getStatus(),
                aiConversation.getExpiresAt());
    }

    public AiConversation toDomain(){
        return new AiConversation(
                new AiConversationId(this.id),
                new UserId(this.userId),
                this.status,
                this.expiresAt);
    }
}
