package acsanfrancisco.budgeting.infrastructure.persistence.repository.transaction;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.TransactionEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlyExpensesProjection;
import acsanfrancisco.budgeting.infrastructure.persistence.projection.MonthlySummaryProjection;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/seeding-transactions.sql")
public class TransactionEntityRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TRANSACTION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final LocalDate START_DATE = LocalDate.of(2026, 11, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 12, 1);

    @Autowired
    private TransactionEntityRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionEntity transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionEntity(UUID.randomUUID(), "Housing payment", new BigDecimal("750.00"),
                Category.HOUSING, START_DATE, TransactionType.EXPENSE,
                entityManager.getReference(UserEntity.class, USER_ID), null);
    }

    @Test
    @DisplayName("Should find transaction by matching ID and provided user ID")
    void shouldFindTransactionByIdAndUserId() {
        TransactionEntity found = transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, USER_ID).orElseThrow();

        Assertions.assertEquals(TRANSACTION_ID, found.getId());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
        Assertions.assertEquals(Category.GROCERIES, found.getCategory());
        Assertions.assertEquals(new BigDecimal("100.10"), found.getAmount());
    }

    @Test
    @DisplayName("Should return empty when provided user ID does not match transaction owner")
    void shouldReturnEmptyWhenProvidedUserIdDoesNotMatchOwner() {
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, OTHER_USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should return empty for nonexistent transaction ID")
    void shouldReturnEmptyForNonexistentTransaction() {
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(UUID.randomUUID(), USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should filter category and provided user ID with inclusive start and end dates")
    void shouldFilterCategoryUserAndDateBoundaries() {
        Page<TransactionEntity> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, START_DATE, END_DATE.minusDays(1), PageRequest.of(0, 10));

        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertEquals(List.of(END_DATE.minusDays(1), START_DATE),
                result.getContent().stream().map(TransactionEntity::getDate).toList());
        Assertions.assertTrue(result.stream().allMatch(t ->
                t.getUser().getId().equals(USER_ID) && t.getCategory() == Category.GROCERIES));
    }

    @Test
    @DisplayName("Should return category history for provided user ID when both dates are null")
    void shouldFindHistoryWithoutDates() {
        Page<TransactionEntity> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, null, null, PageRequest.of(0, 10));

        Assertions.assertEquals(4, result.getTotalElements());
        Assertions.assertEquals(List.of(END_DATE, END_DATE.minusDays(1), START_DATE, START_DATE.minusDays(1)),
                result.stream().map(TransactionEntity::getDate).toList());
    }

    @Test
    @DisplayName("Should apply only the end date when start date is null")
    void shouldFindTransactionsWithoutStartDate() {
        Page<TransactionEntity> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, null, END_DATE.minusDays(1), PageRequest.of(0, 10));

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.stream().allMatch(t -> t.getDate().isBefore(END_DATE)));
    }

    @Test
    @DisplayName("Should apply only the start date when end date is null")
    void shouldFindTransactionsWithoutEndDate() {
        Page<TransactionEntity> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, START_DATE, null, PageRequest.of(0, 10));

        Assertions.assertEquals(3, result.getTotalElements());
        Assertions.assertTrue(result.stream().noneMatch(t -> t.getDate().isBefore(START_DATE)));
    }

    @Test
    @DisplayName("Should paginate matching transactions with explicit ordering and correct total")
    void shouldPaginateMatchingTransactions() {
        UUID lastDay = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID previousMonth = UUID.fromString("20000000-0000-0000-0000-000000000003");
        UUID nextMonth = UUID.fromString("20000000-0000-0000-0000-000000000004");
        jdbcTemplate.update("UPDATE tb_transaction SET date = ? WHERE id = ?", START_DATE, lastDay);

        Page<TransactionEntity> first = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, null, null, PageRequest.of(0, 2));
        Page<TransactionEntity> second = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, null, null, PageRequest.of(1, 2));

        Assertions.assertEquals(List.of(nextMonth, lastDay), first.map(TransactionEntity::getId).getContent());
        Assertions.assertEquals(List.of(TRANSACTION_ID, previousMonth), second.map(TransactionEntity::getId).getContent());
        Assertions.assertEquals(4, first.getTotalElements());
        Assertions.assertEquals(4, second.getTotalElements());
    }

    @Test
    @DisplayName("Should return empty page beyond results while preserving total")
    void shouldReturnEmptyPageBeyondResults() {
        Page<TransactionEntity> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, START_DATE, END_DATE.minusDays(1), PageRequest.of(2, 1));

        Assertions.assertTrue(result.isEmpty());
        Assertions.assertEquals(2, result.getTotalElements());
    }

    @Test
    @DisplayName("Should sum expenses matching category, provided user ID and month boundaries")
    void shouldSumMatchingMonthlyExpenses() {
        MonthlyExpensesProjection result = transactionRepository.findMonthlyExpensesByCategoryUseCase(
                START_DATE, END_DATE, USER_ID, Category.GROCERIES);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(new BigDecimal("150.30"), result.getTotal());
    }

    @Test
    @DisplayName("Should return no expense total when no transactions match")
    void shouldReturnNoTotalWhenExpensesDoNotExist() {
        MonthlyExpensesProjection result = transactionRepository.findMonthlyExpensesByCategoryUseCase(
                START_DATE, END_DATE, USER_ID, Category.PHARMA);

        Assertions.assertTrue(result == null || result.getTotal() == null);
    }

    @Test
    @DisplayName("Should sum income and expenses for provided user ID and month boundaries")
    void shouldSumMonthlyIncomeAndExpenses() {
        MonthlySummaryProjection result = transactionRepository.getMonthlySummary(
                START_DATE, END_DATE, USER_ID, TransactionType.EXPENSE, TransactionType.INCOME);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(new BigDecimal("240.30"), result.getExpenses());
        Assertions.assertEquals(new BigDecimal("1000.00"), result.getIncome());
    }

    @Test
    @DisplayName("Should return only totals belonging to provided user ID")
    void shouldSumMonthlySummaryForProvidedUserId() {
        MonthlySummaryProjection result = transactionRepository.getMonthlySummary(
                START_DATE, END_DATE, OTHER_USER_ID, TransactionType.EXPENSE, TransactionType.INCOME);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(new BigDecimal("250.00"), result.getExpenses());
        Assertions.assertEquals(0, result.getIncome().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should return null aggregate values when month has no transactions")
    void shouldReturnNullAggregatesForEmptyMonth() {
        MonthlySummaryProjection result = transactionRepository.getMonthlySummary(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), USER_ID,
                TransactionType.EXPENSE, TransactionType.INCOME);

        Assertions.assertNotNull(result);
        Assertions.assertNull(result.getExpenses());
        Assertions.assertNull(result.getIncome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should reject zero or negative transaction amounts")
    void shouldNotSaveNonPositiveAmount(String amount) {
        transaction.setAmount(new BigDecimal(amount));

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(transaction);
            entityManager.flush();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"description", "amount", "category", "date", "type", "user_id"})
    @DisplayName("Should enforce NOT NULL constraints on required transaction columns")
    void shouldRejectNullRequiredColumn(String column) {
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE tb_transaction SET " + column + " = NULL WHERE id = ?", TRANSACTION_ID));
    }

    @Test
    @DisplayName("Should reject transaction referencing a nonexistent user")
    void shouldNotSaveForNonexistentUser() {
        transaction.setUser(entityManager.getReference(UserEntity.class, UUID.randomUUID()));

        Assertions.assertThrows(PersistenceException.class, () -> {
            entityManager.persist(transaction);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should reject transaction type outside INCOME and EXPENSE at database level")
    void shouldRejectInvalidTransactionType() {
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE tb_transaction SET type = ? WHERE id = ?", "INVALID", TRANSACTION_ID));
    }
}
