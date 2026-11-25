package acsanfrancisco.budgeting.infrastructure.config;

import acsanfrancisco.budgeting.application.usecase.aiconversation.PurgeInactiveAiConversationsUseCase;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationCleanupJobTest {

    private final AiConversationRepository conversations = mock(AiConversationRepository.class);
    private final ChatHistoryRepository history = mock(ChatHistoryRepository.class);
    private final AiConversationCleanupJob job = new AiConversationCleanupJob(
            new PurgeInactiveAiConversationsUseCase(conversations, history));

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 0", "0, 3"})
    @DisplayName("Should purge inactive conversations and orphan messages using the current time")
    void shouldPurgeUsingCurrentTime(int deletedConversations, int deletedMessages) {
        when(conversations.deleteInactive(any())).thenReturn(deletedConversations);
        when(history.deleteWithoutConversation()).thenReturn(deletedMessages);
        Instant before = Instant.now();

        job.run();

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(conversations).deleteInactive(now.capture());
        assertThat(now.getValue()).isBetween(before, Instant.now());
        verify(history).deleteWithoutConversation();
    }
}
