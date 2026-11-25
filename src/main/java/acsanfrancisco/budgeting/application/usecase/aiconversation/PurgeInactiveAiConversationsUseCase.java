package acsanfrancisco.budgeting.application.usecase.aiconversation;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/**
 * Deletes what closing and renewal cannot reach: conversations that expired because the user
 * never came back, closed rows left by older versions, and messages saved after their
 * conversation was deleted.
 */
@Service
@RequiredArgsConstructor
public class PurgeInactiveAiConversationsUseCase {

    private final AiConversationRepository aiConversationRepository;
    private final ChatHistoryRepository chatHistoryRepository;

    public record Result(int conversations, int messages) {
    }

    @Transactional
    public Result execute(Instant now) {
        int conversations = aiConversationRepository.deleteInactive(now);
        int messages = chatHistoryRepository.deleteWithoutConversation();
        return new Result(conversations, messages);
    }
}
