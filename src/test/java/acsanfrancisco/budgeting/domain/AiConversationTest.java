package acsanfrancisco.budgeting.domain;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationStatus;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AiConversationTest {

    static Stream<Arguments> missingReconstructionFields() {
        AiConversationId id = new AiConversationId();
        UserId userId = new UserId();
        return Stream.of(
                Arguments.of(null, userId, AiConversationStatus.ACTIVE, Instant.EPOCH),
                Arguments.of(id, null, AiConversationStatus.ACTIVE, Instant.EPOCH),
                Arguments.of(id, userId, null, Instant.EPOCH),
                Arguments.of(id, userId, AiConversationStatus.ACTIVE, null)
        );
    }

    @ParameterizedTest
    @MethodSource("missingReconstructionFields")
    @DisplayName("Should reject missing fields when reconstructing conversation")
    void shouldRejectMissingReconstructionFields(AiConversationId id, UserId owner,
            AiConversationStatus status, Instant expiration) {
        assertThrows(IllegalArgumentException.class, () -> new AiConversation(id, owner, status, expiration));
    }

    @ParameterizedTest
    @EnumSource(AiConversationStatus.class)
    @DisplayName("Should restore expired or closed conversation without replacing persisted fields")
    void shouldReconstructPastConversation(AiConversationStatus status) {
        AiConversationId id = new AiConversationId();
        AiConversation conversation = new AiConversation(id, userId, status, Instant.EPOCH);

        assertEquals(id, conversation.getId());
        assertEquals(userId.id(), conversation.getUserId());
        assertEquals(status, conversation.getStatus());
        assertEquals(Instant.EPOCH, conversation.getExpiresAt());
        assertFalse(conversation.renewIfActive());
        assertEquals(AiConversationStatus.CLOSED, conversation.getStatus());
    }

    @Test
    @DisplayName("Should restore valid active conversation with supplied expiration")
    void shouldReconstructValidActiveConversation() {
        AiConversationId id = new AiConversationId();
        Instant expiration = Instant.now().plusSeconds(3600);
        AiConversation conversation = new AiConversation(id, userId, AiConversationStatus.ACTIVE, expiration);

        assertEquals(id, conversation.getId());
        assertEquals(userId.id(), conversation.getUserId());
        assertEquals(AiConversationStatus.ACTIVE, conversation.getStatus());
        assertEquals(expiration, conversation.getExpiresAt());
    }

    @Test
    @DisplayName("Should keep explicit close idempotent and preserve identity, owner and expiration")
    void shouldPreserveFieldsWhenClosingTwice() {
        AiConversation conversation = new AiConversation(userId);
        AiConversationId id = conversation.getId();
        Instant expiration = conversation.getExpiresAt();

        conversation.close();
        conversation.close();

        assertEquals(AiConversationStatus.CLOSED, conversation.getStatus());
        assertFalse(conversation.renewIfActive());
        assertEquals(id, conversation.getId());
        assertEquals(userId.id(), conversation.getUserId());
        assertEquals(expiration, conversation.getExpiresAt());
    }

    private final UserId userId = new UserId();

    @Test
    @DisplayName("Should create active conversation with one day expiration")
    void shouldCreateActiveConversationWithOneDayExpiration() {
        Instant before = Instant.now();
        AiConversation conversation = new AiConversation(userId);

        assertNotNull(conversation.getId());
        assertEquals(userId.id(), conversation.getUserId());
        assertEquals(AiConversationStatus.ACTIVE, conversation.getStatus());
        assertFalse(conversation.getExpiresAt().isBefore(before.plusMillis(86_400_000L)));
        assertFalse(conversation.getExpiresAt().isAfter(Instant.now().plusMillis(86_400_000L)));
    }

    @Test
    @DisplayName("Should reject missing user")
    void shouldRejectMissingUser() {
        assertThrows(IllegalArgumentException.class, () -> new AiConversation(null));
    }

    @Test
    @DisplayName("Should keep closed conversation invalid even before expiration")
    void shouldKeepClosedConversationInvalidEvenBeforeExpiration() {
        AiConversation conversation = new AiConversation(userId);
        conversation.close();

        assertFalse(conversation.renewIfActive());
        assertEquals(AiConversationStatus.CLOSED, conversation.getStatus());
    }

    @Test
    @DisplayName("Should close expired conversation")
    void shouldCloseExpiredConversation() {
        AiConversation conversation = new AiConversation(new AiConversationId(), userId,
                AiConversationStatus.ACTIVE, Instant.EPOCH);

        assertFalse(conversation.renewIfActive());
        assertEquals(Instant.EPOCH, conversation.getExpiresAt());
        assertEquals(AiConversationStatus.CLOSED, conversation.getStatus());
    }
}
