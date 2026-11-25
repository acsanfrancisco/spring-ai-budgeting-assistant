package acsanfrancisco.budgeting.application.usecase.aiconversation;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurgeInactiveAiConversationsUseCaseTest {

    @Mock
    AiConversationRepository repository;

    @Mock
    ChatHistoryRepository history;

    @InjectMocks
    PurgeInactiveAiConversationsUseCase useCase;

    @Test
    @DisplayName("Should delete inactive conversations before the messages left without a conversation")
    void shouldDeleteConversationsBeforeOrphanMessages() {
        Instant now = Instant.parse("2026-11-24T12:00:00Z");
        when(repository.deleteInactive(now)).thenReturn(2);
        when(history.deleteWithoutConversation()).thenReturn(5);

        var result = useCase.execute(now);

        assertEquals(new PurgeInactiveAiConversationsUseCase.Result(2, 5), result);
        InOrder order = inOrder(repository, history);
        order.verify(repository).deleteInactive(now);
        order.verify(history).deleteWithoutConversation();
    }
}
