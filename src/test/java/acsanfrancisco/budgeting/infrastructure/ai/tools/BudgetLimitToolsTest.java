package acsanfrancisco.budgeting.infrastructure.ai.tools;

import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BudgetLimitToolsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final BudgetLimitRepository budgets = mock(BudgetLimitRepository.class);
    private final BudgetLimitTools tools = new BudgetLimitTools(
            new PersistBudgetLimitUseCase(budgets, users), new GetBudgetLimitStatusUseCase(budgets, users));

    private final Category category = Category.GROCERIES;
    private final YearMonth period = YearMonth.of(2026, 11);
    private final BigDecimal limit = new BigDecimal("500.00");
    private User user;
    private UserId userId;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        context = new ToolContext(Map.of(ToolContexts.USER_ID, userId));
    }

    @Test
    @DisplayName("Should create a budget limit for the authenticated user")
    void shouldCreateBudgetLimitForAuthenticatedUser() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(budgets.save(any(BudgetLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetLimitOutput output = tools.persistBudgetLimit(category, "2026-11", limit, context);

        assertThat(output.category()).isEqualTo("GROCERIES");
        assertThat(output.period()).isEqualTo("2026-11");
        assertThat(output.limitAmount()).isEqualTo(limit);
        verify(users).findById(userId);
    }

    @Test
    @DisplayName("Should reject a duplicate budget limit without saving")
    void shouldRejectDuplicateBudgetLimitWithoutSaving() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(budgets.existsByUserIdAndCategoryAndPeriod(userId, category, period)).thenReturn(true);

        assertThatThrownBy(() -> tools.persistBudgetLimit(category, "2026-11", limit, context))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        verify(budgets, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"11/2026", "2026-13", "2026-11-01", ""})
    @DisplayName("Should reject a malformed period before creating a budget limit")
    void shouldRejectMalformedPeriodBeforeCreating(String invalidPeriod) {
        assertThatThrownBy(() -> tools.persistBudgetLimit(category, invalidPeriod, limit, context))
                .isInstanceOf(DateTimeParseException.class);
        verifyNoInteractions(users, budgets);
    }

    @Test
    @DisplayName("Should query the budget limit status for the authenticated user")
    void shouldQueryBudgetLimitStatusForAuthenticatedUser() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(budgets.getBudgetLimitStatus(period, userId, category))
                .thenReturn(new MonthlyBudgetLimitStatus(limit, new BigDecimal("400.00")));

        var output = tools.budgetLimitStatus("2026-11", category, context);

        assertThat(output.status()).isEqualTo(BudgetStatus.NEAR_LIMIT);
        assertThat(output.remaining()).isEqualTo(new BigDecimal("100.00"));
        verify(budgets).getBudgetLimitStatus(period, userId, category);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "11/2026", "2026-13", ""})
    @DisplayName("Should reject a malformed period before querying the status")
    void shouldRejectMalformedPeriodBeforeQuerying(String invalidPeriod) {
        assertThatThrownBy(() -> tools.budgetLimitStatus(invalidPeriod, category, context))
                .isInstanceOf(DateTimeParseException.class);
        verifyNoInteractions(users, budgets);
    }

    @Test
    @DisplayName("Should require the authenticated user in both tools")
    void shouldRequireAuthenticatedUser() {
        ToolContext anonymous = new ToolContext(Map.of());

        assertThatThrownBy(() -> tools.persistBudgetLimit(category, "2026-11", limit, anonymous))
                .isInstanceOf(IllegalStateException.class).hasMessage("Authenticated user is required");
        assertThatThrownBy(() -> tools.budgetLimitStatus("2026-11", category, anonymous))
                .isInstanceOf(IllegalStateException.class).hasMessage("Authenticated user is required");
        verifyNoInteractions(users, budgets);
    }
}
