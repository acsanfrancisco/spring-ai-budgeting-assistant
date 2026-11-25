package acsanfrancisco.budgeting.application.usecase.aiconversation;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetOrCreateAiConversationUseCase {

    private final AiConversationRepository aiConversationRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UUID execute(UserId userId) {
        if (!userRepository.lockById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        Optional<AiConversation> current = aiConversationRepository.findActiveByUserId(userId);

        if(current.isPresent()) {
            AiConversation conversation = current.get();
            if (conversation.renewIfActive()) {
                aiConversationRepository.save(conversation);
                return conversation.getId().id();
            }
            // Expired: delete it with its messages before creating the replacement.
            chatHistoryRepository.deleteByConversationId(conversation.getId());
            aiConversationRepository.delete(conversation.getId());
        }

        AiConversation conversation = new AiConversation(userId);
        return aiConversationRepository.save(conversation).getId().id();
    }
}
