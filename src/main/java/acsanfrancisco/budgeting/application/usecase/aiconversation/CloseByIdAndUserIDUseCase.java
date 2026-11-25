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

@Service
@RequiredArgsConstructor
public class CloseByIdAndUserIDUseCase {

    private final AiConversationRepository aiConversationRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;

    /**
     * Closing deletes the conversation and its messages: nothing reads a closed conversation.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void execute(UserId userId) {
        if (!userRepository.lockById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        AiConversation conversation = aiConversationRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No active conversation found"));

        chatHistoryRepository.deleteByConversationId(conversation.getId());
        aiConversationRepository.delete(conversation.getId());
    }

}
