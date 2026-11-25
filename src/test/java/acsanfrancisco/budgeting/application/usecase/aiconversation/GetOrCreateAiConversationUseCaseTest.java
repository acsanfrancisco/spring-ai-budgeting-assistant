package acsanfrancisco.budgeting.application.usecase.aiconversation;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationRepository;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationStatus;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetOrCreateAiConversationUseCaseTest {

    @Mock
    AiConversationRepository repository;

    @Mock
    ChatHistoryRepository history;

    @Mock
    UserRepository users;

    @InjectMocks
    GetOrCreateAiConversationUseCase useCase;

    private final UserId userId = new UserId();

    @Test
    @DisplayName("Should stop before conversation access when user cannot be found for locking")
    void shouldRejectMissingUser() {
        when(users.lockById(userId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(userId));
        verifyNoInteractions(repository, history);
    }

    @Test
    @DisplayName("Should create conversation when user has no active conversation")
    void shouldCreateConversationWhenUserHasNoActiveConversation() {
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(repository.save(any(AiConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(userId);
        var captor = ArgumentCaptor.forClass(AiConversation.class);
        verify(repository).save(captor.capture());
        AiConversation created = captor.getValue();
        assertNotNull(created.getId());
        assertEquals(created.getId().id(), result);
        assertEquals(userId.id(), created.getUserId());
        assertEquals(AiConversationStatus.ACTIVE, created.getStatus());
    }

    @Test
    @DisplayName("Should reuse active conversation and save a new 24-hour expiration")
    void shouldReuseActiveConversationAndRenewExpiration() {
        Instant before = Instant.now();
        AiConversation existing = new AiConversation(new AiConversationId(), userId,
                AiConversationStatus.ACTIVE, before.plusSeconds(3600));
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.of(existing));

        assertEquals(existing.getId().id(), useCase.execute(userId));
        assertFalse(existing.getExpiresAt().isBefore(before.plusSeconds(86_400)));
        assertFalse(existing.getExpiresAt().isAfter(Instant.now().plusSeconds(86_400)));
        InOrder order = inOrder(users, repository);
        order.verify(users).lockById(userId);
        order.verify(repository).findActiveByUserId(userId);
        order.verify(repository).save(existing);
    }

    @Test
    @DisplayName("Should delete expired conversation and its messages before creating replacement")
    void shouldDeleteExpiredConversationBeforeCreatingReplacement() {
        AiConversation expired = new AiConversation(new AiConversationId(), userId,
                AiConversationStatus.ACTIVE, Instant.EPOCH);
        AiConversationId expiredId = expired.getId();
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.of(expired));
        when(repository.save(any(AiConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(userId);
        InOrder order = inOrder(users, repository, history);
        order.verify(users).lockById(userId);
        order.verify(repository).findActiveByUserId(userId);
        order.verify(history).deleteByConversationId(expiredId);
        order.verify(repository).delete(expiredId);
        var captor = ArgumentCaptor.forClass(AiConversation.class);
        order.verify(repository).save(captor.capture());
        AiConversation replacement = captor.getValue();
        assertNotEquals(expiredId, replacement.getId());
        assertEquals(result, replacement.getId().id());
        assertEquals(userId.id(), replacement.getUserId());
        assertEquals(AiConversationStatus.ACTIVE, replacement.getStatus());
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Should keep conversation IDs separate for different users")
    void shouldKeepConversationIdsSeparateForDifferentUsers() {
        UserId otherUser = new UserId();
        AiConversation first = new AiConversation(userId);
        AiConversation second = new AiConversation(otherUser);
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.of(first));
        when(users.lockById(otherUser)).thenReturn(true);
        when(repository.findActiveByUserId(otherUser)).thenReturn(Optional.of(second));

        assertEquals(first.getId().id(), useCase.execute(userId));
        assertEquals(second.getId().id(), useCase.execute(otherUser));
        assertNotEquals(first.getId(), second.getId());
        verify(repository).save(first);
        verify(repository).save(second);
    }

    @Test
    @DisplayName("Should not create replacement when deleting expired conversation fails")
    void shouldNotCreateReplacementWhenDeletingExpiredConversationFails() {
        AiConversation expired = new AiConversation(new AiConversationId(), userId, AiConversationStatus.ACTIVE, Instant.EPOCH);
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.of(expired));
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        doThrow(failure).when(repository).delete(expired.getId());

        assertSame(failure, assertThrows(IllegalStateException.class, () -> useCase.execute(userId)));
        verify(repository).delete(expired.getId());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should create separate conversations with the correct owner for different users")
    void shouldCreateSeparateConversationsForDifferentUsers() {
        UserId otherUser = new UserId();
        when(users.lockById(userId)).thenReturn(true);
        when(repository.findActiveByUserId(userId)).thenReturn(Optional.empty());
        when(users.lockById(otherUser)).thenReturn(true);
        when(repository.findActiveByUserId(otherUser)).thenReturn(Optional.empty());
        when(repository.save(any(AiConversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var firstId = useCase.execute(userId);
        var secondId = useCase.execute(otherUser);

        var captor = ArgumentCaptor.forClass(AiConversation.class);
        verify(repository, times(2)).save(captor.capture());
        var saved = captor.getAllValues();
        assertEquals(userId.id(), saved.get(0).getUserId());
        assertEquals(otherUser.id(), saved.get(1).getUserId());
        assertEquals(firstId, saved.get(0).getId().id());
        assertEquals(secondId, saved.get(1).getId().id());
        assertNotEquals(firstId, secondId);
        assertEquals(AiConversationStatus.ACTIVE, saved.get(0).getStatus());
        assertEquals(AiConversationStatus.ACTIVE, saved.get(1).getStatus());
    }
}
