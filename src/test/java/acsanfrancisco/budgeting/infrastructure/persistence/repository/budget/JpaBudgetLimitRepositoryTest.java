package acsanfrancisco.budgeting.infrastructure.persistence.repository.budget;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@DataJpaTest
@Testcontainers
@Import(JpaBudgetLimitRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/seeding-budget-limits.sql")
public class JpaBudgetLimitRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UserId USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final BudgetLimitId BUDGET_LIMIT_ID = new BudgetLimitId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);

    @Autowired
    private JpaBudgetLimitRepository budgetLimitRepository;

    @Autowired
    private EntityManager entityManager;

    private BudgetLimit budgetLimit;

    @BeforeEach
    void setUp() {
        User user = new User(USER_ID, "First Test User", "first@example.com", "encoded-password");
        budgetLimit = new BudgetLimit(user, Category.HOUSING, PERIOD, new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("Should save and return budget limit with persisted user")
    void shouldSaveAndReturnBudgetLimitWithPersistedUser() {
        budgetLimit.getUser().setFullName("Outdated User Name");

        BudgetLimit saved = budgetLimitRepository.save(budgetLimit);
        entityManager.flush();
        entityManager.clear();

        BudgetLimit found = budgetLimitRepository.findBudgetLimitByIdAndUserId(saved.getId(), USER_ID).orElseThrow();
        Assertions.assertEquals(budgetLimit.getId(), saved.getId());
        Assertions.assertEquals("First Test User", saved.getUser().getFullName());
        Assertions.assertEquals(saved.getId(), found.getId());
        Assertions.assertEquals(Category.HOUSING, found.getCategory());
        Assertions.assertEquals(PERIOD, found.getPeriod());
        Assertions.assertEquals(new BigDecimal("750.00"), found.getLimitAmount());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
        Assertions.assertEquals("First Test User", found.getUser().getFullName());
        Assertions.assertEquals("first@example.com", found.getUser().getEmail());
    }

    @Test
    @DisplayName("Should reject save for nonexistent user")
    void shouldRejectSaveForNonexistentUser() {
        User missingUser = new User(new UserId(), "Missing User", "missing@example.com", "encoded-password");
        budgetLimit = new BudgetLimit(budgetLimit.getId(), missingUser, budgetLimit.getCategory(),
                budgetLimit.getPeriod(), budgetLimit.getLimitAmount());

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> budgetLimitRepository.save(budgetLimit));

        Assertions.assertEquals("User not found", thrown.getMessage());
        entityManager.flush();
        entityManager.clear();
        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimit.getId(), USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should delegate existence query using domain user ID")
    void shouldDelegateExistenceQueryUsingDomainUserId() {
        Assertions.assertTrue(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.GROCERIES, PERIOD));
        Assertions.assertFalse(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.HOUSING, PERIOD));
    }

    @Test
    @DisplayName("Should find budget limit as domain object")
    void shouldFindBudgetLimitAsDomainObject() {
        BudgetLimit found = budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, USER_ID).orElseThrow();

        Assertions.assertEquals(BUDGET_LIMIT_ID, found.getId());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
        Assertions.assertEquals("First Test User", found.getUser().getFullName());
        Assertions.assertEquals("first@example.com", found.getUser().getEmail());
        Assertions.assertEquals(Category.GROCERIES, found.getCategory());
        Assertions.assertEquals(PERIOD, found.getPeriod());
        Assertions.assertEquals(new BigDecimal("500.00"), found.getLimitAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-0000-0000-000000000002", "00000000-0000-0000-0000-000000000099"})
    @DisplayName("Should return empty when provided user ID does not match budget owner")
    void shouldReturnEmptyWhenProvidedUserIdDoesNotMatchBudgetOwner(String userId) {
        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(
                BUDGET_LIMIT_ID, new UserId(UUID.fromString(userId))).isEmpty());
    }

    @Test
    @DisplayName("Should return empty for nonexistent budget limit")
    void shouldReturnEmptyForNonexistentBudgetLimit() {
        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(new BudgetLimitId(), USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should map monthly status using requested period")
    void shouldMapMonthlyStatusUsingRequestedPeriod() {
        MonthlyBudgetLimitStatus status = budgetLimitRepository.getBudgetLimitStatus(PERIOD, USER_ID, Category.GROCERIES);

        Assertions.assertEquals(new BigDecimal("500.00"), status.limitAmount());
        Assertions.assertEquals(new BigDecimal("150.30"), status.spent());
    }

    @Test
    @DisplayName("Should convert null spent to zero")
    void shouldConvertNullSpentToZero() {
        MonthlyBudgetLimitStatus status = budgetLimitRepository.getBudgetLimitStatus(PERIOD, USER_ID, Category.PHARMA);

        Assertions.assertEquals(new BigDecimal("200.00"), status.limitAmount());
        Assertions.assertEquals(BigDecimal.ZERO, status.spent());
    }

    @Test
    @DisplayName("Should return unconfigured status when budget does not exist")
    void shouldReturnUnconfiguredStatusWhenBudgetDoesNotExist() {
        MonthlyBudgetLimitStatus status = budgetLimitRepository.getBudgetLimitStatus(PERIOD, USER_ID, Category.AUTO);

        Assertions.assertNull(status.limitAmount());
        Assertions.assertEquals(BigDecimal.ZERO, status.spent());
    }

    @Test
    @DisplayName("Should reject status for nonexistent user")
    void shouldRejectStatusForNonexistentUser() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> budgetLimitRepository.getBudgetLimitStatus(PERIOD, new UserId(), Category.GROCERIES));

        Assertions.assertEquals("User not found", thrown.getMessage());
    }

    @Test
    @DisplayName("Should map absent limit without enforcing category policy in persistence")
    void shouldMapAbsentLimitWithoutEnforcingCategoryPolicy() {
        MonthlyBudgetLimitStatus status = budgetLimitRepository.getBudgetLimitStatus(PERIOD, USER_ID, Category.SALARY);

        Assertions.assertNull(status.limitAmount());
        Assertions.assertEquals(BigDecimal.ZERO, status.spent());
    }

    @Test
    @DisplayName("Should update only limit amount and return domain object")
    void shouldUpdateOnlyLimitAmountAndReturnDomainObject() {
        BudgetLimit updated = budgetLimitRepository.updateLimitAmount(new BigDecimal("650.00"), BUDGET_LIMIT_ID, USER_ID);
        entityManager.flush();
        entityManager.clear();

        BudgetLimit found = budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, USER_ID).orElseThrow();
        Assertions.assertEquals(BUDGET_LIMIT_ID, updated.getId());
        Assertions.assertEquals(new BigDecimal("650.00"), updated.getLimitAmount());
        Assertions.assertEquals(new BigDecimal("650.00"), found.getLimitAmount());
        Assertions.assertEquals(Category.GROCERIES, found.getCategory());
        Assertions.assertEquals(PERIOD, found.getPeriod());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
    }

    @Test
    @DisplayName("Should delete budget limit")
    void shouldDeleteBudgetLimit() {
        budgetLimitRepository.delete(BUDGET_LIMIT_ID, USER_ID);
        entityManager.flush();
        entityManager.clear();

        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, USER_ID).isEmpty());
        Assertions.assertFalse(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.GROCERIES, PERIOD));
        Assertions.assertTrue(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.PHARMA, PERIOD));
    }

    @Test
    @DisplayName("Should reject update of nonexistent budget limit")
    void shouldRejectUpdateOfNonexistentBudgetLimit() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> budgetLimitRepository.updateLimitAmount(new BigDecimal("650.00"), new BudgetLimitId(), USER_ID));

        Assertions.assertEquals("Budget limit not found", thrown.getMessage());
    }

    @Test
    @DisplayName("Should reject delete of nonexistent budget limit")
    void shouldRejectDeleteOfNonexistentBudgetLimit() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> budgetLimitRepository.delete(new BudgetLimitId(), USER_ID));

        Assertions.assertEquals("Budget limit not found", thrown.getMessage());
    }

    @Test
    @DisplayName("Should throw not found on update when provided user ID does not match budget owner")
    void shouldThrowNotFoundOnUpdateWhenProvidedUserIdDoesNotMatchBudgetOwner() {
        UserId otherUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> budgetLimitRepository.updateLimitAmount(new BigDecimal("650.00"), BUDGET_LIMIT_ID, otherUserId));

        Assertions.assertEquals("Budget limit not found", thrown.getMessage());
        entityManager.flush();
        entityManager.clear();
        BudgetLimit found = budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, USER_ID).orElseThrow();
        Assertions.assertEquals(new BigDecimal("500.00"), found.getLimitAmount());
    }

    @Test
    @DisplayName("Should throw not found on delete when provided user ID does not match budget owner")
    void shouldThrowNotFoundOnDeleteWhenProvidedUserIdDoesNotMatchBudgetOwner() {
        UserId otherUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> budgetLimitRepository.delete(BUDGET_LIMIT_ID, otherUserId));

        Assertions.assertEquals("Budget limit not found", thrown.getMessage());
        entityManager.flush();
        entityManager.clear();
        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, USER_ID).isPresent());
    }
}
