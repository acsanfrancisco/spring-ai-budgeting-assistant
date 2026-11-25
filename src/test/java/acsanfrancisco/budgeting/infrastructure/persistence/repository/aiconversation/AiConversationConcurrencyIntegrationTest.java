package acsanfrancisco.budgeting.infrastructure.persistence.repository.aiconversation;

import acsanfrancisco.budgeting.application.usecase.aiconversation.CloseByIdAndUserIDUseCase;
import acsanfrancisco.budgeting.application.usecase.aiconversation.GetOrCreateAiConversationUseCase;
import acsanfrancisco.budgeting.domain.aiconversation.*;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.repository.user.JpaUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUserRepository.class, JpaAiConversationRepository.class,
        JdbcChatHistoryRepository.class, GetOrCreateAiConversationUseCase.class, CloseByIdAndUserIDUseCase.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AiConversationConcurrencyIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired JpaUserRepository users;
    @Autowired JpaAiConversationRepository conversations;
    @Autowired GetOrCreateAiConversationUseCase getOrCreate;
    @Autowired CloseByIdAndUserIDUseCase close;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;

    private TransactionTemplate transaction;
    private UserId userId;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        userId = transaction.execute(status -> users.save(new User(
                "Concurrent User", UUID.randomUUID() + "@example.com", "encoded-password")).getId());
    }

    @Test
    @DisplayName("Should reuse one conversation when creation overlaps for the same user")
    void shouldSerializeInitialCreation() throws Exception {
        assertConcurrentCallsReuseConversation(null);
    }

    @Test
    @DisplayName("Should reject a second active conversation even when saved without the application lock")
    void shouldEnforceUniqueActiveConversationInDatabase() {
        UUID originalId = transaction.execute(status -> conversations.save(new AiConversation(userId)).getId().id());

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                conversations.save(new AiConversation(userId))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_ai_conversation_active_user");

        transaction.executeWithoutResult(status -> {
            assertThat(conversations.findActiveByUserId(userId).orElseThrow().getId().id()).isEqualTo(originalId);
            assertThat(entityManager.createQuery("SELECT count(c) FROM AiConversationEntity c WHERE c.userId = :userId", Long.class)
                    .setParameter("userId", userId.id()).getSingleResult()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Should allow multiple closed conversations alongside one active conversation")
    void shouldAllowMultipleClosedConversations() {
        transaction.executeWithoutResult(status -> {
            AiConversation first = new AiConversation(userId);
            first.close();
            conversations.save(first);
            AiConversation second = new AiConversation(userId);
            second.close();
            conversations.save(second);
            conversations.save(new AiConversation(userId));
        });

        transaction.executeWithoutResult(status -> {
            assertThat(conversations.findActiveByUserId(userId)).isPresent();
            assertThat(entityManager.createQuery("""
                    SELECT count(c) FROM AiConversationEntity c
                    WHERE c.userId = :userId AND c.status = :status
                    """, Long.class).setParameter("userId", userId.id())
                    .setParameter("status", AiConversationStatus.CLOSED).getSingleResult()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Should delete expired conversation once and reuse one replacement under concurrency")
    void shouldSerializeExpiredReplacement() throws Exception {
        AiConversation expired = new AiConversation(new AiConversationId(), userId,
                AiConversationStatus.ACTIVE, Instant.EPOCH);
        transaction.executeWithoutResult(status -> conversations.save(expired));

        assertConcurrentCallsReuseConversation(expired.getId());
    }

    private void assertConcurrentCallsReuseConversation(AiConversationId expiredId) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch started = new CountDownLatch(1);
            var pending = new java.util.concurrent.atomic.AtomicReference<Future<UUID>>();
            UUID firstId = transaction.execute(status -> {
                entityManager.find(UserEntity.class, userId.id(), LockModeType.PESSIMISTIC_WRITE);
                Future<UUID> second = executor.submit(() -> {
                    started.countDown();
                    return getOrCreate.execute(userId);
                });
                pending.set(second);
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return getOrCreate.execute(userId);
            });

            assertThat(pending.get().get(10, TimeUnit.SECONDS)).isEqualTo(firstId);
            transaction.executeWithoutResult(status -> {
                Long active = entityManager.createQuery("""
                        SELECT count(c) FROM AiConversationEntity c
                        WHERE c.userId = :userId AND c.status = :status
                        """, Long.class).setParameter("userId", userId.id())
                        .setParameter("status", AiConversationStatus.ACTIVE).getSingleResult();
                assertThat(active).isEqualTo(1);
                if (expiredId != null) {
                    assertThat(entityManager.find(
                            acsanfrancisco.budgeting.infrastructure.persistence.entity.AiConversationEntity.class,
                            expiredId.id())).isNull();
                    assertThat(firstId).isNotEqualTo(expiredId.id());
                }
            });
        }
    }

    @Test
    @DisplayName("Should allow a different user to create a conversation while first user is locked")
    void shouldNotBlockDifferentUser() throws Exception {
        UserId other = transaction.execute(status -> users.save(new User(
                "Another User", UUID.randomUUID() + "@example.com", "encoded-password")).getId());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            transaction.executeWithoutResult(status -> {
                entityManager.find(UserEntity.class, userId.id(), LockModeType.PESSIMISTIC_WRITE);
                Future<UUID> otherConversation = executor.submit(() -> getOrCreate.execute(other));
                try {
                    assertThat(otherConversation.get(10, TimeUnit.SECONDS)).isNotNull();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Should wait for another transaction holding the same user's lock before explicit close")
    void shouldSerializeExplicitClose(boolean createReplacement) throws Exception {
        UUID originalId = getOrCreate.execute(userId);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = new java.util.concurrent.atomic.AtomicReference<Future<?>>();
            CountDownLatch started = new CountDownLatch(1);
            transaction.executeWithoutResult(status -> {
                entityManager.find(UserEntity.class, userId.id(), LockModeType.PESSIMISTIC_WRITE);
                Future<?> closing = executor.submit(() -> {
                    started.countDown();
                    close.execute(userId);
                });
                pending.set(closing);
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThrows(TimeoutException.class, () -> closing.get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            });
            pending.get().get(10, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status -> assertThat(conversations.findActiveByUserId(userId)).isEmpty());
            if (createReplacement) {
                assertThat(getOrCreate.execute(userId)).isNotEqualTo(originalId);
            }
        }
    }
}
