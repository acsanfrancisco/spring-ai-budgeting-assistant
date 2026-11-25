package acsanfrancisco.budgeting.infrastructure.persistence.repository.user;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;
import java.util.UUID;

@DataJpaTest
@Testcontainers
@Import(JpaUserRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/seeding-users.sql")
public class JpaUserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("New Test User", "new@example.com", "encoded-password");
    }

    @Test
    @DisplayName("Should save and map user fields and default role without changing password")
    void shouldSaveAndMapUser() {
        User saved = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        User found = userRepository.findById(user.getId()).orElseThrow();
        Assertions.assertEquals(user.getId(), saved.getId());
        Assertions.assertEquals(Set.of(Role.ROLE_USER), saved.getRoles());
        Assertions.assertEquals(user.getId(), found.getId());
        Assertions.assertEquals(user.getFullName(), found.getFullName());
        Assertions.assertEquals(user.getEmail(), found.getEmail());
        Assertions.assertEquals(user.getPassword(), found.getPassword());
        Assertions.assertEquals(Set.of(Role.ROLE_USER), found.getRoles());
    }

    @Test
    @DisplayName("Should map user found by email including all persisted roles")
    void shouldFindUserByEmailAsDomainObject() {
        User found = userRepository.findByEmail("second@example.com").orElseThrow();

        Assertions.assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), found.getId().id());
        Assertions.assertEquals("Second Test User", found.getFullName());
        Assertions.assertEquals("second@example.com", found.getEmail());
        Assertions.assertEquals("encoded-second-password", found.getPassword());
        Assertions.assertEquals(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN), found.getRoles());
    }

    @Test
    @DisplayName("Should return empty when email does not exist")
    void shouldReturnEmptyForNonexistentEmail() {
        Assertions.assertTrue(userRepository.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    @DisplayName("Should map user found by UUID to domain object")
    void shouldFindUserByIdAsDomainObject() {
        User found = userRepository.findById(new UserId(USER_ID)).orElseThrow();

        Assertions.assertEquals(USER_ID, found.getId().id());
        Assertions.assertEquals("First Test User", found.getFullName());
        Assertions.assertEquals("first@example.com", found.getEmail());
        Assertions.assertEquals("encoded-first-password", found.getPassword());
        Assertions.assertEquals(Set.of(Role.ROLE_USER), found.getRoles());
    }

    @Test
    @DisplayName("Should return empty when UUID does not exist")
    void shouldReturnEmptyForNonexistentId() {
        Assertions.assertTrue(userRepository.findById(new UserId()).isEmpty());
    }

    @Test
    @DisplayName("Should find and lock an existing user through JPQL and return false for missing user")
    void shouldLockExistingUserAndRejectMissingUser() {
        Assertions.assertTrue(userRepository.lockById(new UserId(USER_ID)));
        Assertions.assertFalse(userRepository.lockById(new UserId()));
    }

    @Test
    @DisplayName("Should return true for existing email and false for nonexistent email")
    void shouldDelegateEmailExistenceQuery() {
        Assertions.assertTrue(userRepository.existsByEmail("first@example.com"));
        Assertions.assertFalse(userRepository.existsByEmail("missing@example.com"));
    }
}
