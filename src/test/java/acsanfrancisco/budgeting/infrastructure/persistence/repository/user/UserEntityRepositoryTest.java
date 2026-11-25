package acsanfrancisco.budgeting.infrastructure.persistence.repository.user;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.Set;
import java.util.UUID;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/seeding-users.sql")
public class UserEntityRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private UserEntityRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity(UUID.randomUUID(), "New Test User", "new@example.com", "encoded-password");
    }

    @Test
    @DisplayName("Should find user matching provided email and load persisted roles")
    void shouldFindUserByEmail() {
        UserEntity found = userRepository.findByEmail("second@example.com").orElseThrow();

        Assertions.assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), found.getId());
        Assertions.assertEquals("Second Test User", found.getFullName());
        Assertions.assertEquals("second@example.com", found.getEmail());
        Assertions.assertEquals("encoded-second-password", found.getPassword());
        Assertions.assertEquals(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN), found.getRoles());
    }

    @Test
    @DisplayName("Should return empty when provided email does not exist")
    void shouldReturnEmptyForNonexistentEmail() {
        Assertions.assertTrue(userRepository.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    @DisplayName("Should return true when provided email exists")
    void shouldReturnTrueForExistingEmail() {
        Assertions.assertTrue(userRepository.existsByEmail("first@example.com"));
    }

    @Test
    @DisplayName("Should return false when provided email does not exist")
    void shouldReturnFalseForNonexistentEmail() {
        Assertions.assertFalse(userRepository.existsByEmail("missing@example.com"));
    }

    @Test
    @DisplayName("Should assign default user role through the persist lifecycle callback")
    void shouldPersistDefaultUserRole() {
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        Assertions.assertEquals(Set.of(Role.ROLE_USER), userRepository.findById(user.getId()).orElseThrow().getRoles());
    }

    @Test
    @DisplayName("Should reject insertion with duplicate email")
    void shouldRejectDuplicateEmailOnInsert() {
        user.setEmail("first@example.com");

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(user);
            entityManager.flush();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"full_name", "email", "password"})
    @DisplayName("Should enforce NOT NULL constraints on required user columns")
    void shouldRejectNullRequiredColumn(String column) {
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE tb_user SET " + column + " = NULL WHERE id = ?", USER_ID));
    }
}
