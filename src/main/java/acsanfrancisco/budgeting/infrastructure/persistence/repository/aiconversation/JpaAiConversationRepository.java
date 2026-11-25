package acsanfrancisco.budgeting.infrastructure.persistence.repository.aiconversation;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationStatus;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.AiConversationEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.repository.user.UserEntityRepository;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaAiConversationRepository implements AiConversationRepository {

    private final AiConversationEntityRepository aiConversationEntityRepository;

    private final UserEntityRepository userEntityRepository;

    public JpaAiConversationRepository(AiConversationEntityRepository aiConversationEntityRepository,  UserEntityRepository userEntityRepository) {
        this.aiConversationEntityRepository = aiConversationEntityRepository;
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    public AiConversation save(AiConversation conversation) {
        userEntityRepository.findById(conversation.getUserId())
                .orElseThrow(()-> new ResourceNotFoundException("User not found"));

        AiConversationEntity entity = AiConversationEntity.from(conversation);
        return aiConversationEntityRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AiConversation> findActiveByUserId(UserId userId) {
        return aiConversationEntityRepository.findActiveByUserId(userId.id(), AiConversationStatus.ACTIVE)
                .map(AiConversationEntity::toDomain);
    }

    @Override
    public void delete(AiConversationId id) {
        aiConversationEntityRepository.deleteNow(id.id());
    }

    @Override
    public int deleteInactive(Instant now) {
        return aiConversationEntityRepository.deleteInactive(AiConversationStatus.CLOSED, now);
    }
}
