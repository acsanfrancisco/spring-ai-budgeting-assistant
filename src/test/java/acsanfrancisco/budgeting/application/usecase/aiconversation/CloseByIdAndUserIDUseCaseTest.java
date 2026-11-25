package acsanfrancisco.budgeting.application.usecase.aiconversation;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloseByIdAndUserIDUseCaseTest {

    @Mock
    AiConversationRepository repository;

    @Mock
    ChatHistoryRepository history;

    @Mock
    UserRepository users;

    @InjectMocks
    CloseByIdAndUserIDUseCase useCase;

    @Test
    @DisplayName("Should reject missing user before finding or closing a conversation")
    void shouldRejectMissingUser() {
        UserId userId = new UserId();
        when(users.lockById(userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(userId));
        verifyNoInteractions(repository, history);
    }

    @Test
    @DisplayName("Should delete the conversation and its messages instead of keeping a closed record")
    void shouldDeleteConversationAndMessages() {
        UserId userId = new UserId();
        AiConversation conversation = new AiConversation(userId);
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.of(conversation));

        useCase.execute(userId);

        InOrder order = inOrder(users, repository, history);
        order.verify(users).lockById(userId);
        order.verify(repository).findActiveByUserId(userId);
        order.verify(history).deleteByConversationId(conversation.getId());
        order.verify(repository).delete(conversation.getId());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should propagate missing conversation")
    void shouldPropagateMissingConversation() {
        UserId userId = new UserId();
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.empty());
        ResourceNotFoundException failure = assertThrows(ResourceNotFoundException.class,
                () -> useCase.execute(userId));
        assertEquals("No active conversation found", failure.getMessage());
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
        verifyNoInteractions(history);
    }
}
