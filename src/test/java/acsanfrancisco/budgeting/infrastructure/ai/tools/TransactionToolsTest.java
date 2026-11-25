package acsanfrancisco.budgeting.infrastructure.ai.tools;

import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.application.usecase.transaction.FindMonthlyExpensesByCategoryUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.FindTransactionsByCategoryAndPeriodUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.GetMonthlySummaryUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.PersistTransactionUseCase;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import acsanfrancisco.budgeting.domain.report.MonthlySummary;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionToolsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionTools tools = new TransactionTools(
            new PersistTransactionUseCase(transactions, users),
            new FindTransactionsByCategoryAndPeriodUseCase(transactions),
            new FindMonthlyExpensesByCategoryUseCase(transactions, users),
            new GetMonthlySummaryUseCase(transactions, users));

    private final Category category = Category.GROCERIES;
    private final YearMonth period = YearMonth.of(2026, 11);
    private final LocalDate date = LocalDate.of(2026, 11, 9);
    private User user;
    private UserId userId;
    private ToolContext context;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        context = new ToolContext(Map.of(ToolContexts.USER_ID, userId));
        transaction = new Transaction("Weekly groceries", new BigDecimal("75.50"), category, user, date);
    }

    // persist-transaction

    @Test
    @DisplayName("Should register a transaction for the authenticated user")
    void shouldRegisterTransactionForAuthenticatedUser() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transactions.save(any(Transaction.class), isNull())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = tools.persistTransaction("Groceries", new BigDecimal("50.00"), category,
                date.toString(), context);

        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(saved.capture(), isNull());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(output.id()).isEqualTo(saved.getValue().getId().id());
        assertThat(output.description()).isEqualTo("Groceries");
        assertThat(output.amount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(output.date()).isEqualTo(date);
        assertThat(output.type()).isEqualTo("EXPENSE");
    }

    @Test
    @DisplayName("Should pass the idempotency key from the tool context to the repository")
    void shouldPassIdempotencyKey() {
        TransactionRequestKey key = new TransactionRequestKey(UUID.randomUUID(), "message-hash");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transactions.save(any(Transaction.class), eq(key))).thenAnswer(invocation -> invocation.getArgument(0));

        tools.persistTransaction("Groceries", BigDecimal.TEN, category, date.toString(),
                new ToolContext(Map.of(ToolContexts.USER_ID, userId, ToolContexts.TRANSACTION_REQUEST_KEY, key)));

        verify(transactions).save(any(Transaction.class), eq(key));
    }

    @Test
    @DisplayName("Should use the current date when the date is omitted")
    void shouldUseCurrentDateWhenDateIsOmitted() {
        LocalDate before = LocalDate.now();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transactions.save(any(Transaction.class), isNull())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = tools.persistTransaction("Groceries", BigDecimal.TEN, category, null, context);

        assertThat(output.date()).isBetween(before, LocalDate.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {"15/11/2026", "2026-02-30", "2026-11", ""})
    @DisplayName("Should reject an invalid date without saving")
    void shouldRejectInvalidDateWithoutSaving(String invalidDate) {
        assertThatThrownBy(() -> tools.persistTransaction("Groceries", BigDecimal.TEN, category, invalidDate, context))
                .isInstanceOf(DateTimeParseException.class);
        verifyNoInteractions(users, transactions);
    }

    // list-transaction-by-category-and-period

    @Test
    @DisplayName("Should return only the count when page and size are omitted")
    void shouldReturnOnlyCountWithoutPageAndSize() {
        when(transactions.findByCategoryAndPeriod(category, userId, null, null, new PageQuery(0, 1)))
                .thenReturn(new PageResult<>(List.of(transaction), 0, 1, 7));

        PageResult<TransactionOutput> output = tools.listTransactions(category, null, null, null, null, context);

        assertThat(output.content()).isEmpty();
        assertThat(output.totalElements()).isEqualTo(7);
    }

    @Test
    @DisplayName("Should list the requested page with the default size")
    void shouldListRequestedPageWithDefaultSize() {
        PageQuery pageQuery = new PageQuery(2, 20);
        when(transactions.findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery))
                .thenReturn(new PageResult<>(List.of(transaction), 2, 20, 41));

        PageResult<TransactionOutput> output = tools.listTransactions(category, period.atDay(1), period.atEndOfMonth(),
                2, null, context);

        assertThat(output.content()).containsExactly(new TransactionOutput(transaction.getId().id(), "Weekly groceries",
                new BigDecimal("75.50"), "GROCERIES", date, "EXPENSE"));
        assertThat(output.page()).isEqualTo(2);
        assertThat(output.size()).isEqualTo(20);
        assertThat(output.totalElements()).isEqualTo(41);
    }

    @Test
    @DisplayName("Should use the first page when only the size is informed")
    void shouldUseFirstPageWhenOnlySizeIsInformed() {
        PageQuery pageQuery = new PageQuery(0, 5);
        when(transactions.findByCategoryAndPeriod(category, userId, null, null, pageQuery))
                .thenReturn(new PageResult<>(List.of(), 0, 5, 0));

        PageResult<TransactionOutput> output = tools.listTransactions(category, null, null, null, 5, context);

        assertThat(output.page()).isZero();
        assertThat(output.size()).isEqualTo(5);
        verify(transactions).findByCategoryAndPeriod(category, userId, null, null, pageQuery);
    }

    @Test
    @DisplayName("Should filter by each date alone")
    void shouldFilterByEachDateAlone() {
        PageQuery pageQuery = new PageQuery(0, 20);
        when(transactions.findByCategoryAndPeriod(any(), any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(transaction), 0, 20, 1));

        tools.listTransactions(category, period.atDay(1), null, 0, null, context);
        tools.listTransactions(category, null, period.atEndOfMonth(), 0, null, context);

        verify(transactions).findByCategoryAndPeriod(category, userId, period.atDay(1), null, pageQuery);
        verify(transactions).findByCategoryAndPeriod(category, userId, null, period.atEndOfMonth(), pageQuery);
    }

    @Test
    @DisplayName("Should reject a start date after the end date and a negative page")
    void shouldRejectInvalidPeriodAndPage() {
        assertThatThrownBy(() -> tools.listTransactions(category, period.atEndOfMonth(), period.atDay(1), 0, null, context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.listTransactions(category, period.atDay(1), period.atEndOfMonth(), -1, null, context))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions);
    }

    // list-monthly-expenses-by-category

    @Test
    @DisplayName("Should return the monthly expenses of the category for the authenticated user")
    void shouldReturnMonthlyExpenses() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transactions.findMonthlyExpensesByCategory(period, userId, category))
                .thenReturn(new MonthlyExpenses(category, new BigDecimal("500.75")));

        MonthlyExpensesOutput output = tools.monthlyExpenses("2026-11", category, context);

        assertThat(output.category()).isEqualTo(category);
        assertThat(output.total()).isEqualTo(new BigDecimal("500.75"));
    }

    @Test
    @DisplayName("Should reject a missing category when querying monthly expenses")
    void shouldRejectMissingCategory() {
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> tools.monthlyExpenses("2026-11", null, context))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions);
    }

    // get-monthly-summary

    @Test
    @DisplayName("Should return the monthly summary for the authenticated user")
    void shouldReturnMonthlySummary() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(transactions.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(new MonthlySummary(new BigDecimal("500.00"), new BigDecimal("1500.00")));

        MonthlySummaryOutput output = tools.monthlySummary("2026-11", context);

        assertThat(output.expenses()).isEqualTo(new BigDecimal("500.00"));
        assertThat(output.income()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(output.net()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Should reject an invalid month in the monthly tools before querying")
    void shouldRejectInvalidMonth() {
        assertThatThrownBy(() -> tools.monthlyExpenses("2026-13", category, context))
                .isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> tools.monthlySummary("2026-13", context))
                .isInstanceOf(DateTimeParseException.class);
        verifyNoInteractions(users, transactions);
    }

    @Test
    @DisplayName("Should require the authenticated user in every tool")
    void shouldRequireAuthenticatedUser() {
        ToolContext anonymous = new ToolContext(Map.of());

        assertThatThrownBy(() -> tools.persistTransaction("Groceries", BigDecimal.TEN, category, null, anonymous))
                .isInstanceOf(IllegalStateException.class).hasMessage("Authenticated user is required");
        assertThatThrownBy(() -> tools.listTransactions(category, null, null, 0, 20, anonymous))
                .isInstanceOf(IllegalStateException.class).hasMessage("Authenticated user is required");
        assertThatThrownBy(() -> tools.monthlyExpenses("2026-11", category, anonymous))
                .isInstanceOf(IllegalStateException.class).hasMessage("Authenticated user is required");
        assertThatThrownBy(() -> tools.monthlySummary("2026-11", anonymous))
                .isInstanceOf(IllegalStateException.class).hasMessage("Authenticated user is required");
        verifyNoInteractions(users, transactions);
    }
}
