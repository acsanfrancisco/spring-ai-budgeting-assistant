package acsanfrancisco.budgeting.infrastructure.persistence.repository.budget;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.BudgetLimitEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlyBudgetLimitStatusProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/seeding-budget-limits.sql")
public class BudgetLimitEntityRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BUDGET_LIMIT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);

    @Autowired
    private BudgetLimitEntityRepository budgetLimitRepository;

    @Autowired
    private EntityManager entityManager;

    private BudgetLimitEntity budgetLimit;

    @BeforeEach
    void setUp() {
        budgetLimit = new BudgetLimitEntity(
                UUID.randomUUID(), Category.HOUSING, PERIOD, new BigDecimal("750.00"));
        budgetLimit.setUser(entityManager.getReference(UserEntity.class, USER_ID));
    }

    @Test
    @DisplayName("Should find budget limit by ID and user ID")
    void shouldFindBudgetLimitByIdAndUserId() {
        Optional<BudgetLimitEntity> found = budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, USER_ID);

        Assertions.assertTrue(found.isPresent());
        Assertions.assertEquals(BUDGET_LIMIT_ID, found.get().getId());
        Assertions.assertEquals(USER_ID, found.get().getUser().getId());
        Assertions.assertEquals(Category.GROCERIES, found.get().getCategory());
        Assertions.assertEquals(PERIOD, found.get().getPeriod());
        Assertions.assertEquals(new BigDecimal("500.00"), found.get().getLimitAmount());
    }

    @Test
    @DisplayName("Should return empty when provided user ID does not match budget owner")
    void shouldReturnEmptyWhenProvidedUserIdDoesNotMatchBudgetOwner() {
        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(BUDGET_LIMIT_ID, OTHER_USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should not find nonexistent budget limit")
    void shouldNotFindNonexistentBudgetLimit() {
        Assertions.assertTrue(budgetLimitRepository.findBudgetLimitByIdAndUserId(UUID.randomUUID(), USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should return true when user category and period match")
    void shouldReturnTrueWhenUserCategoryAndPeriodMatch() {
        Assertions.assertTrue(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.GROCERIES, PERIOD));
    }

    @Test
    @DisplayName("Should return false when provided user ID has no budget for category and period")
    void shouldReturnFalseWhenProvidedUserIdHasNoBudgetForCategoryAndPeriod() {
        Assertions.assertFalse(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(OTHER_USER_ID, Category.PHARMA, PERIOD));
    }

    @Test
    @DisplayName("Should return false when category does not match")
    void shouldReturnFalseWhenCategoryDoesNotMatch() {
        Assertions.assertFalse(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.AUTO, PERIOD));
    }

    @Test
    @DisplayName("Should return false when period does not match")
    void shouldReturnFalseWhenPeriodDoesNotMatch() {
        Assertions.assertFalse(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(USER_ID, Category.GROCERIES, PERIOD.plusMonths(1)));
    }

    @Test
    @DisplayName("Should sum only matching expenses including first and last day of month")
    void shouldSumOnlyMatchingExpensesIncludingFirstAndLastDayOfMonth() {
        MonthlyBudgetLimitStatusProjection status = budgetLimitRepository.getBudgetLimitStatus(
                PERIOD, PERIOD.atDay(1), PERIOD.plusMonths(1).atDay(1),
                USER_ID, Category.GROCERIES);

        Assertions.assertNotNull(status);
        Assertions.assertEquals(new BigDecimal("500.00"), status.getLimit());
        Assertions.assertEquals(new BigDecimal("150.30"), status.getSpent());
    }

    @Test
    @DisplayName("Should return budget and expenses for provided user ID")
    void shouldReturnBudgetAndExpensesForProvidedUserId() {
        MonthlyBudgetLimitStatusProjection status = budgetLimitRepository.getBudgetLimitStatus(
                PERIOD, PERIOD.atDay(1), PERIOD.plusMonths(1).atDay(1),
                OTHER_USER_ID, Category.GROCERIES);

        Assertions.assertNotNull(status);
        Assertions.assertEquals(new BigDecimal("900.00"), status.getLimit());
        Assertions.assertEquals(new BigDecimal("250.00"), status.getSpent());
    }

    @Test
    @DisplayName("Should return budget for requested period")
    void shouldReturnBudgetForRequestedPeriod() {
        YearMonth previousPeriod = PERIOD.minusMonths(1);
        MonthlyBudgetLimitStatusProjection status = budgetLimitRepository.getBudgetLimitStatus(
                previousPeriod, previousPeriod.atDay(1), PERIOD.atDay(1),
                USER_ID, Category.GROCERIES);

        Assertions.assertNotNull(status);
        Assertions.assertEquals(new BigDecimal("300.00"), status.getLimit());
        Assertions.assertEquals(new BigDecimal("70.00"), status.getSpent());
    }

    @Test
    @DisplayName("Should return null spent when budget has no expenses")
    void shouldReturnNullSpentWhenBudgetHasNoExpenses() {
        MonthlyBudgetLimitStatusProjection status = budgetLimitRepository.getBudgetLimitStatus(
                PERIOD, PERIOD.atDay(1), PERIOD.plusMonths(1).atDay(1),
                USER_ID, Category.PHARMA);

        Assertions.assertNotNull(status);
        Assertions.assertEquals(new BigDecimal("200.00"), status.getLimit());
        Assertions.assertNull(status.getSpent());
    }

    @Test
    @DisplayName("Should return null status when category has no budget even with expenses")
    void shouldReturnNullStatusWhenCategoryHasNoBudgetEvenWithExpenses() {
        Assertions.assertNull(budgetLimitRepository.getBudgetLimitStatus(
                PERIOD, PERIOD.atDay(1), PERIOD.plusMonths(1).atDay(1),
                USER_ID, Category.AUTO));
    }

    @Test
    @DisplayName("Should return null status when user has no matching budget")
    void shouldReturnNullStatusWhenUserHasNoMatchingBudget() {
        Assertions.assertNull(budgetLimitRepository.getBudgetLimitStatus(
                PERIOD, PERIOD.atDay(1), PERIOD.plusMonths(1).atDay(1),
                OTHER_USER_ID, Category.PHARMA));
    }

    @Test
    @DisplayName("Should return null status when period has no budget")
    void shouldReturnNullStatusWhenPeriodHasNoBudget() {
        YearMonth nextPeriod = PERIOD.plusMonths(1);
        Assertions.assertNull(budgetLimitRepository.getBudgetLimitStatus(
                nextPeriod, nextPeriod.atDay(1), nextPeriod.plusMonths(1).atDay(1),
                USER_ID, Category.GROCERIES));
    }

    @Test
    @DisplayName("Should not save duplicate user category and period")
    void shouldNotSaveDuplicateUserCategoryAndPeriod() {
        budgetLimit.setCategory(Category.GROCERIES);

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should not save non positive limit amount")
    void shouldNotSaveNonPositiveLimitAmount(String amount) {
        budgetLimit.setLimitAmount(new BigDecimal(amount));

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should not save null limit amount")
    void shouldNotSaveNullLimitAmount() {
        budgetLimit.setLimitAmount(null);

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should not save null category")
    void shouldNotSaveNullCategory() {
        budgetLimit.setCategory(null);

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should not save null period")
    void shouldNotSaveNullPeriod() {
        budgetLimit.setPeriod(null);

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should not save without user")
    void shouldNotSaveWithoutUser() {
        budgetLimit.setUser(null);

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should not save for nonexistent user")
    void shouldNotSaveForNonexistentUser() {
        budgetLimit.setUser(entityManager.getReference(UserEntity.class, UUID.randomUUID()));

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(budgetLimit);
            entityManager.flush();
        });
    }

}
