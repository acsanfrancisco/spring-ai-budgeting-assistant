package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.transaction.*;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import acsanfrancisco.budgeting.infrastructure.ai.tools.BudgetLimitTools;
import acsanfrancisco.budgeting.infrastructure.ai.tools.TransactionTools;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceToolContractTest {

    @Mock
    UserRepository users;

    @Mock
    TransactionRepository transactions;

    @Mock
    BudgetLimitRepository budgets;

    private final User user = new User("Voice user", "voice@example.com", "encoded");
    private final ObjectMapper json = new ObjectMapper();
    private ToolCallback[] callbacks;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        context = new ToolContext(Map.of("userId", user.getId()));
        callbacks = ToolCallbacks.from(
                new TransactionTools(new PersistTransactionUseCase(transactions, users),
                        new FindTransactionsByCategoryAndPeriodUseCase(transactions),
                        new FindMonthlyExpensesByCategoryUseCase(transactions, users),
                        new GetMonthlySummaryUseCase(transactions, users)),
                new BudgetLimitTools(new PersistBudgetLimitUseCase(budgets, users),
                        new GetBudgetLimitStatusUseCase(budgets, users)));
    }

    private ToolCallback tool(String name) {
        return Arrays.stream(callbacks).filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Should expose temporal arguments as strings without exposing user or context")
    void shouldExposeTemporalArgumentsAsStringsWithoutExposingUserOrContext() {
        Map<String, String> temporalFields = Map.of(
                "persist-transaction", "date", "persist-budget-limit", "period",
                "get-budget-limit-status", "period", "get-monthly-summary", "yearMonth",
                "list-monthly-expenses-by-category", "yearMonth",
                "list-transaction-by-category-and-period", "from");
        temporalFields.forEach((name, field) -> {
            JsonNode properties = json.readTree(tool(name).getToolDefinition().inputSchema()).get("properties");
            assertThat(properties.get(field).get("type").asText()).as(name + "." + field).isEqualTo("string");
            assertThat(properties.has("context")).isFalse();
            assertThat(properties.has("userId")).isFalse();
        });
    }

    @Test
    @DisplayName("Should create budget from JSON with ISO period and return JSON object")
    void shouldCreateBudgetFromJsonWithIsoPeriodAndReturnJsonObject() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(budgets.save(any(BudgetLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String result = tool("persist-budget-limit").call(
                "{\"category\":\"GROCERIES\",\"period\":\"2026-11\",\"limitAmount\":500}", context);

        var saved = ArgumentCaptor.forClass(BudgetLimit.class);
        verify(budgets).save(saved.capture());
        assertThat(saved.getValue().getPeriod()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(saved.getValue().getUser().getId()).isEqualTo(user.getId());
        JsonNode response = json.readTree(result);
        assertThat(response.isObject()).isTrue();
        assertThat(response.get("period").asText()).isEqualTo("2026-11");
        assertThat(response.get("limitAmount").decimalValue()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Should parse transaction JSON and use user from context")
    void shouldParseTransactionJsonAndUseUserFromContext() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(transactions.save(any(Transaction.class), isNull())).thenAnswer(invocation -> invocation.getArgument(0));

        String result = tool("persist-transaction").call("""
                {"description":"Groceries","amount":40,"category":"GROCERIES","date":"2026-11-15"}
                """, context);

        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(saved.capture(), isNull());
        assertThat(saved.getValue().getDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(saved.getValue().getUser().getId()).isEqualTo(user.getId());
        assertThat(json.readTree(result).get("amount").decimalValue()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("Should allow transaction JSON without date")
    void shouldAllowTransactionJsonWithoutDate() {
        LocalDate before = LocalDate.now();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(transactions.save(any(Transaction.class), isNull())).thenAnswer(invocation -> invocation.getArgument(0));
        tool("persist-transaction").call("""
                {"description":"Salary","amount":3000,"category":"SALARY"}
                """, context);

        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(saved.capture(), isNull());
        assertThat(saved.getValue().getDate()).isBetween(before, LocalDate.now());
        JsonNode required = json.readTree(tool("persist-transaction").getToolDefinition().inputSchema()).get("required");
        assertThat(required.toString()).doesNotContain("date");
    }

    @Test
    @DisplayName("Should allow history query without period or page in schema and execution")
    void shouldAllowHistoryQueryWithoutPeriodOrPageInSchemaAndExecution() {
        when(transactions.findByCategoryAndPeriod(any(), eq(user.getId()), isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 10, 137));
        String result = tool("list-transaction-by-category-and-period").call("{\"category\":\"GROCERIES\"}", context);

        assertThat(json.readTree(result).get("totalElements").asLong()).isEqualTo(137);
        verify(transactions).findByCategoryAndPeriod(any(), eq(user.getId()), isNull(), isNull(),
                eq(new acsanfrancisco.budgeting.domain.pagination.PageQuery(0, 10)));
        tool("list-transaction-by-category-and-period").call("{\"category\":\"GROCERIES\",\"page\":1,\"size\":100}", context);
        verify(transactions).findByCategoryAndPeriod(any(), eq(user.getId()), isNull(), isNull(),
                eq(new acsanfrancisco.budgeting.domain.pagination.PageQuery(1, 100)));
        JsonNode required = json.readTree(tool("list-transaction-by-category-and-period")
                .getToolDefinition().inputSchema()).get("required");
        assertThat(required.toString()).doesNotContain("from", "to", "page", "size");
    }

    @Test
    @DisplayName("Should reject tool call without authenticated context before saving")
    void shouldRejectToolCallWithoutAuthenticatedContextBeforeSaving() {
        assertThatThrownBy(() -> tool("persist-budget-limit").call(
                "{\"category\":\"GROCERIES\",\"period\":\"2026-11\",\"limitAmount\":500}",
                new ToolContext(Map.of())))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(users, budgets, transactions);
    }
}
