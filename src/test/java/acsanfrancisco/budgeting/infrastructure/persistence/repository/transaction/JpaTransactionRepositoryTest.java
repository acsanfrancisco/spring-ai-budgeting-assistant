package acsanfrancisco.budgeting.infrastructure.persistence.repository.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.exception.IdempotencyConflictException;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import acsanfrancisco.budgeting.domain.report.MonthlySummary;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@DataJpaTest
@Testcontainers
@Import(JpaTransactionRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("/sql/seeding-transactions.sql")
public class JpaTransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final UserId USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId OTHER_USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final TransactionId TRANSACTION_ID = new TransactionId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);

    @Autowired
    private JpaTransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        User user = new User(USER_ID, "First Test User", "first@example.com", "encoded-password");
        transaction = new Transaction("Housing payment", new BigDecimal("750.00"),
                Category.HOUSING, user, LocalDate.of(2026, 11, 15));
    }

    @Test
    void shouldReuseKeyOnlyWithTheSameMessageAndOwner() {
        var key = new TransactionRequestKey(UUID.randomUUID(), "a".repeat(64));
        var differentMessage = new TransactionRequestKey(key.value(), "b".repeat(64));
        Transaction retry = new Transaction("Housing retry", transaction.getAmount(),
                transaction.getCategory(), transaction.getUser(), transaction.getDate());

        Transaction saved = transactionRepository.save(transaction, key);
        entityManager.clear();
        Assertions.assertEquals(saved.getId(), transactionRepository.save(retry, key).getId());
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(retry.getId(), USER_ID).isEmpty());
        Assertions.assertTrue(transactionRepository.findByIdempotencyKey(OTHER_USER_ID, key).isEmpty());
        Assertions.assertThrows(IdempotencyConflictException.class,
                () -> transactionRepository.save(retry, differentMessage));
    }

    @Test
    @DisplayName("Should save and map transaction with persisted user")
    void shouldSaveAndMapTransactionWithPersistedUser() {
        transaction.getUser().setFullName("Outdated User Name");
        Transaction saved = transactionRepository.save(transaction, null);
        entityManager.flush();
        entityManager.clear();

        Transaction found = transactionRepository.findTransactionByIdAndUserId(saved.getId(), USER_ID).orElseThrow();
        Assertions.assertEquals(transaction.getId(), saved.getId());
        Assertions.assertEquals("First Test User", saved.getUser().getFullName());
        Assertions.assertEquals(transaction.getId(), found.getId());
        Assertions.assertEquals("Housing payment", found.getDescription());
        Assertions.assertEquals(new BigDecimal("750.00"), found.getAmount());
        Assertions.assertEquals(Category.HOUSING, found.getCategory());
        Assertions.assertEquals(TransactionType.EXPENSE, found.getType());
        Assertions.assertEquals(transaction.getDate(), found.getDate());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
        Assertions.assertEquals("first@example.com", found.getUser().getEmail());
    }

    @Test
    @DisplayName("Should reject save for nonexistent user")
    void shouldRejectSaveForNonexistentUser() {
        User missingUser = new User(new UserId(), "Missing User", "missing@example.com", "encoded-password");
        transaction = new Transaction(transaction.getId(), transaction.getDescription(), transaction.getAmount(),
                transaction.getCategory(), transaction.getDate(), transaction.getType(), missingUser);

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> transactionRepository.save(transaction, null));

        Assertions.assertEquals("User not found", thrown.getMessage());
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(transaction.getId(), USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should find transaction as domain object")
    void shouldFindTransactionAsDomainObject() {
        Transaction found = transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, USER_ID).orElseThrow();

        Assertions.assertEquals(TRANSACTION_ID, found.getId());
        Assertions.assertEquals("First day groceries", found.getDescription());
        Assertions.assertEquals(new BigDecimal("100.10"), found.getAmount());
        Assertions.assertEquals(Category.GROCERIES, found.getCategory());
        Assertions.assertEquals(TransactionType.EXPENSE, found.getType());
        Assertions.assertEquals(PERIOD.atDay(1), found.getDate());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
    }

    @Test
    @DisplayName("Should return empty when provided user ID does not match transaction owner")
    void shouldReturnEmptyWhenProvidedUserIdDoesNotMatchTransactionOwner() {
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, OTHER_USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should return empty for nonexistent transaction")
    void shouldReturnEmptyForNonexistentTransaction() {
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(new TransactionId(), USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Should update description, amount, expense category and date while preserving ID, type and owner")
    void shouldUpdateAllowedFieldsPreservingIdTypeAndOwner() {
        transaction = new Transaction(TRANSACTION_ID, transaction.getDescription(), transaction.getAmount(),
                transaction.getCategory(), transaction.getDate(), transaction.getType(), transaction.getUser());
        transaction.setDescription("Updated expense");
        transaction.setAmount(new BigDecimal("1200.00"));
        transaction.setCategory(Category.PHARMA);
        transaction.setDate(LocalDate.of(2026, 12, 10));

        Transaction updated = transactionRepository.update(transaction, TRANSACTION_ID, USER_ID);
        entityManager.flush();
        entityManager.clear();

        Transaction found = transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, USER_ID).orElseThrow();
        Assertions.assertEquals(TRANSACTION_ID, updated.getId());
        Assertions.assertEquals(USER_ID, updated.getUser().getId());
        Assertions.assertEquals("Updated expense", found.getDescription());
        Assertions.assertEquals(new BigDecimal("1200.00"), found.getAmount());
        Assertions.assertEquals(Category.PHARMA, found.getCategory());
        Assertions.assertEquals(TransactionType.EXPENSE, found.getType());
        Assertions.assertEquals(transaction.getDate(), found.getDate());
        Assertions.assertEquals(USER_ID, found.getUser().getId());
    }

    @Test
    @DisplayName("Should throw not found on update when provided user ID does not match owner")
    void shouldThrowNotFoundOnUpdateWhenProvidedUserIdDoesNotMatchOwner() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> transactionRepository.update(transaction, TRANSACTION_ID, OTHER_USER_ID));

        Assertions.assertEquals("Transaction not found", thrown.getMessage());
        entityManager.flush();
        entityManager.clear();
        Transaction found = transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, USER_ID).orElseThrow();
        Assertions.assertEquals(new BigDecimal("100.10"), found.getAmount());
        Assertions.assertEquals("First day groceries", found.getDescription());
    }

    @Test
    @DisplayName("Should throw not found on update for nonexistent transaction")
    void shouldThrowNotFoundOnUpdateForNonexistentTransaction() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> transactionRepository.update(transaction, new TransactionId(), USER_ID));

        Assertions.assertEquals("Transaction not found", thrown.getMessage());
    }

    @Test
    @DisplayName("Should delete transaction")
    void shouldDeleteTransaction() {
        transactionRepository.delete(TRANSACTION_ID, USER_ID);
        entityManager.flush();
        entityManager.clear();

        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, USER_ID).isEmpty());
        Assertions.assertEquals(1, transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(0, 10)).totalElements());
    }

    @Test
    @DisplayName("Should throw not found on delete when provided user ID does not match owner")
    void shouldThrowNotFoundOnDeleteWhenProvidedUserIdDoesNotMatchOwner() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> transactionRepository.delete(TRANSACTION_ID, OTHER_USER_ID));

        Assertions.assertEquals("Transaction not found", thrown.getMessage());
        entityManager.flush();
        entityManager.clear();
        Assertions.assertTrue(transactionRepository.findTransactionByIdAndUserId(TRANSACTION_ID, USER_ID).isPresent());
    }

    @Test
    @DisplayName("Should throw not found on delete for nonexistent transaction")
    void shouldThrowNotFoundOnDeleteForNonexistentTransaction() {
        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class,
                () -> transactionRepository.delete(new TransactionId(), USER_ID));

        Assertions.assertEquals("Transaction not found", thrown.getMessage());
    }

    @Test
    @DisplayName("Should filter by category provided user ID and month boundaries")
    void shouldFilterByCategoryProvidedUserIdAndMonthBoundaries() {
        PageResult<Transaction> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(0, 10));

        Assertions.assertEquals(2, result.totalElements());
        Assertions.assertEquals(Set.of(PERIOD.atDay(1), PERIOD.atEndOfMonth()),
                result.content().stream().map(Transaction::getDate).collect(Collectors.toSet()));
        Assertions.assertTrue(result.content().stream().allMatch(t ->
                t.getUser().getId().equals(USER_ID) && t.getCategory() == Category.GROCERIES));
    }

    @Test
    @DisplayName("Should return history when period is null")
    void shouldReturnHistoryWhenPeriodIsNull() {
        PageResult<Transaction> result = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, null, null, new PageQuery(0, 10));

        Assertions.assertEquals(4, result.totalElements());
        Assertions.assertEquals(4, result.content().size());
    }

    @Test
    @DisplayName("Should map page metadata and return empty page beyond results")
    void shouldMapPageMetadataAndReturnEmptyPageBeyondResults() {
        PageResult<Transaction> secondPage = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(1, 1));
        Assertions.assertEquals(1, secondPage.page());
        Assertions.assertEquals(1, secondPage.size());
        Assertions.assertEquals(2, secondPage.totalElements());
        Assertions.assertEquals(1, secondPage.content().size());

        PageResult<Transaction> beyondResults = transactionRepository.findByCategoryAndPeriod(
                Category.GROCERIES, USER_ID, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(2, 1));
        Assertions.assertEquals(2, beyondResults.page());
        Assertions.assertEquals(2, beyondResults.totalElements());
        Assertions.assertTrue(beyondResults.content().isEmpty());
    }

    @Test
    @DisplayName("Should return empty page when no transactions match")
    void shouldReturnEmptyPageWhenNoTransactionsMatch() {
        PageResult<Transaction> result = transactionRepository.findByCategoryAndPeriod(
                Category.PHARMA, USER_ID, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(0, 10));

        Assertions.assertEquals(0, result.totalElements());
        Assertions.assertTrue(result.content().isEmpty());
    }

    @Test
    @DisplayName("Should map monthly expenses for requested category and user")
    void shouldMapMonthlyExpensesForRequestedCategoryAndUser() {
        MonthlyExpenses expenses = transactionRepository.findMonthlyExpensesByCategory(PERIOD, USER_ID, Category.GROCERIES);

        Assertions.assertEquals(Category.GROCERIES, expenses.category());
        Assertions.assertEquals(new BigDecimal("150.30"), expenses.total());
    }

    @Test
    @DisplayName("Should return zero when monthly expenses are absent")
    void shouldReturnZeroWhenMonthlyExpensesAreAbsent() {
        MonthlyExpenses expenses = transactionRepository.findMonthlyExpensesByCategory(PERIOD, USER_ID, Category.PHARMA);

        Assertions.assertEquals(Category.PHARMA, expenses.category());
        Assertions.assertEquals(BigDecimal.ZERO, expenses.total());
    }

    @Test
    @DisplayName("Should map category total without enforcing expense policy in persistence")
    void shouldMapCategoryTotalWithoutEnforcingExpensePolicy() {
        MonthlyExpenses result = transactionRepository.findMonthlyExpensesByCategory(PERIOD, USER_ID, Category.SALARY);

        Assertions.assertEquals(Category.SALARY, result.category());
        Assertions.assertEquals(new BigDecimal("1000.00"), result.total());
    }

    @Test
    @DisplayName("Should map income and expenses to correct summary fields")
    void shouldMapIncomeAndExpensesToCorrectSummaryFields() {
        MonthlySummary summary = transactionRepository.getMonthlySummary(
                PERIOD, USER_ID, TransactionType.INCOME, TransactionType.EXPENSE);

        Assertions.assertEquals(new BigDecimal("240.30"), summary.expenses());
        Assertions.assertEquals(new BigDecimal("1000.00"), summary.income());
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 10})
    @DisplayName("Should handle empty month and month with only expenses")
    void shouldHandleEmptyMonthAndMonthWithOnlyExpenses(int month) {
        MonthlySummary summary = transactionRepository.getMonthlySummary(
                YearMonth.of(2026, month), USER_ID, TransactionType.INCOME, TransactionType.EXPENSE);

        Assertions.assertEquals(0, summary.income().compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, summary.expenses().compareTo(month == 9 ? BigDecimal.ZERO : new BigDecimal("70.00")));
    }
}
