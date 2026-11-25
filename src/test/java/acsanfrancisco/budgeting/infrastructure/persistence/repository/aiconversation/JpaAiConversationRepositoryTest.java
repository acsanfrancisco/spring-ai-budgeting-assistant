package acsanfrancisco.budgeting.infrastructure.persistence.repository.aiconversation;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.usecase.aiconversation.GetOrCreateAiConversationUseCase;
import acsanfrancisco.budgeting.application.usecase.aiconversation.CloseByIdAndUserIDUseCase;
import acsanfrancisco.budgeting.application.usecase.aiconversation.PurgeInactiveAiConversationsUseCase;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversation;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.aiconversation.AiConversationStatus;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.infrastructure.persistence.repository.user.JpaUserRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.AiConversationEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUserRepository.class, JpaAiConversationRepository.class, JdbcChatHistoryRepository.class,
        GetOrCreateAiConversationUseCase.class, CloseByIdAndUserIDUseCase.class, PurgeInactiveAiConversationsUseCase.class})
@Sql("/sql/seeding-users.sql")
class JpaAiConversationRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired JpaAiConversationRepository repository;

    @Autowired AiConversationEntityRepository entities;

    @Autowired GetOrCreateAiConversationUseCase getOrCreate;

    @Autowired CloseByIdAndUserIDUseCase closeConversation;

    @Autowired PurgeInactiveAiConversationsUseCase purge;

    @Autowired JdbcTemplate jdbc;

    @Autowired EntityManager entityManager;

    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId OTHER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Should persist and restore conversation fields")
    void shouldPersistAndRestoreConversationFields() {
        AiConversation saved = repository.save(new AiConversation(USER));
        flushAndClear();
        AiConversation restored = repository.findActiveByUserId(USER).orElseThrow();

        assertThat(restored.getId()).isEqualTo(saved.getId());
        assertThat(restored.getUserId()).isEqualTo(USER.id());
        assertThat(restored.getStatus()).isEqualTo(AiConversationStatus.ACTIVE);
        assertThat(restored.getExpiresAt()).isCloseTo(saved.getExpiresAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("Should reject creation for missing user without persisting conversation")
    void shouldRejectCreationForMissingUserWithoutPersistingConversation() {
        assertThatThrownBy(() -> repository.save(new AiConversation(new UserId()))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(entities.count()).isZero();
    }

    @Test
    @DisplayName("Should filter active conversation by user and status")
    void shouldFilterActiveConversationByUserAndStatus() {
        repository.save(new AiConversation(new AiConversationId(), USER, AiConversationStatus.CLOSED,
                Instant.now().plus(1, ChronoUnit.HOURS)));
        AiConversation other = repository.save(new AiConversation(OTHER));
        flushAndClear();

        assertThat(repository.findActiveByUserId(USER)).isEmpty();
        assertThat(repository.findActiveByUserId(OTHER).orElseThrow().getId()).isEqualTo(other.getId());
    }

    @Test
    @DisplayName("Should delete only the requested user's conversation and messages on close")
    void shouldDeleteOnlyRequestedUsersConversationAndMessagesOnClose() {
        AiConversation first = repository.save(new AiConversation(USER));
        AiConversation other = repository.save(new AiConversation(OTHER));
        addMessage(first.getId().id());
        addMessage(other.getId().id());

        closeConversation.execute(USER);
        flushAndClear();

        assertThat(entities.findById(first.getId().id())).isEmpty();
        assertThat(messageCount(first.getId().id())).isZero();
        assertThat(repository.findActiveByUserId(OTHER).orElseThrow().getId()).isEqualTo(other.getId());
        assertThat(messageCount(other.getId().id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject closing when user has no active conversation")
    void shouldRejectClosingWhenUserHasNoActiveConversation() {
        assertThatThrownBy(() -> closeConversation.execute(USER))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("No active conversation found");
    }

    @Test
    @DisplayName("Should reuse a valid conversation and persist its renewed expiration")
    void shouldReuseConversationAndPersistRenewedExpiration() {
        UUID first = UUID.randomUUID();
        Instant originalExpiration = Instant.now().plus(1, ChronoUnit.HOURS);
        entities.save(new AiConversationEntity(first, USER.id(), AiConversationStatus.ACTIVE, originalExpiration));
        flushAndClear();

        Instant beforeRenewal = Instant.now();
        assertThat(getOrCreate.execute(USER)).isEqualTo(first);
        Instant afterRenewal = Instant.now();
        flushAndClear();

        AiConversation restored = repository.findActiveByUserId(USER).orElseThrow();
        assertThat(restored.getId().id()).isEqualTo(first);
        assertThat(restored.getExpiresAt()).isAfter(originalExpiration)
                .isBetween(beforeRenewal.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MICROS),
                        afterRenewal.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.MICROS));
        assertThat(entities.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should delete expired conversation and its messages before creating replacement")
    void shouldDeleteExpiredConversationAndMessagesBeforeCreatingReplacement() {
        UUID expiredId = UUID.randomUUID();
        entities.save(new AiConversationEntity(expiredId, USER.id(), AiConversationStatus.ACTIVE, Instant.EPOCH));
        addMessage(expiredId);
        flushAndClear();

        UUID replacementId = getOrCreate.execute(USER);
        flushAndClear();

        assertThat(replacementId).isNotEqualTo(expiredId);
        assertThat(entities.findById(expiredId)).isEmpty();
        assertThat(messageCount(expiredId)).isZero();
        assertThat(repository.findActiveByUserId(USER).orElseThrow().getId().id()).isEqualTo(replacementId);
        assertThat(entities.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create new conversation after explicit close")
    void shouldCreateNewConversationAfterExplicitClose() {
        UUID previous = getOrCreate.execute(USER);
        closeConversation.execute(USER);
        flushAndClear();
        assertThat(getOrCreate.execute(USER)).isNotEqualTo(previous);
    }

    @Test
    @DisplayName("Should purge closed, expired and orphan records and keep the active conversation")
    void shouldPurgeInactiveRecordsAndKeepActiveConversation() {
        Instant now = Instant.now();
        UUID closed = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        entities.save(new AiConversationEntity(closed, USER.id(), AiConversationStatus.CLOSED, now.plus(1, ChronoUnit.HOURS)));
        entities.save(new AiConversationEntity(expired, USER.id(), AiConversationStatus.ACTIVE, now.minusSeconds(1)));
        entities.save(new AiConversationEntity(active, OTHER.id(), AiConversationStatus.ACTIVE, now.plus(1, ChronoUnit.HOURS)));
        flushAndClear();
        addMessage(closed);
        addMessage(expired);
        addMessage(active);
        addMessage(orphan);

        PurgeInactiveAiConversationsUseCase.Result result = purge.execute(now);
        flushAndClear();

        assertThat(result.conversations()).isEqualTo(2);
        assertThat(result.messages()).isEqualTo(3);
        assertThat(entities.findAll()).extracting(AiConversationEntity::toDomain)
                .extracting(conversation -> conversation.getId().id()).containsExactly(active);
        assertThat(messageCount(active)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM SPRING_AI_CHAT_MEMORY", Integer.class)).isEqualTo(1);
    }

    private void addMessage(UUID conversationId) {
        jdbc.update("""
                INSERT INTO SPRING_AI_CHAT_MEMORY (conversation_id, content, type, "timestamp", sequence_id)
                VALUES (?, 'Gastei 40 no mercado', 'USER', now(), 1)""", conversationId.toString());
    }

    private int messageCount(UUID conversationId) {
        return jdbc.queryForObject("SELECT count(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                Integer.class, conversationId.toString());
    }
}
