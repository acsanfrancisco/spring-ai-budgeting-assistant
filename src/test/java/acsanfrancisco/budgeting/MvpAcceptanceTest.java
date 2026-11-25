package acsanfrancisco.budgeting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roteiro de aceite do MVP descrito em "Entrega e aceite do MVP": uma conta nova,
 * criada e autenticada pelos endpoints, registra um limite e uma despesa de R$ 40
 * na mesma categoria e mês e confere listagem, total, situação do limite e resumo.
 */
class MvpAcceptanceTest extends HttpIntegrationTestSupport {

    @Test
    @DisplayName("New account registers a limit and a R$ 40 expense and sees the monthly totals")
    void newAccountTracksBudgetAndMonthlySummary() {
        String token = registerAndLogin("maria@example.com").token();

        ResponseEntity<JsonNode> budget = post(token, "/budgets",
                Map.of("category", "GROCERIES", "period", "11/2026", "limitAmount", "500.00"));
        assertThat(budget.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(budget.getBody().get("period").asString()).isEqualTo("11/2026");
        assertMoney(budget.getBody(), "limitAmount", "500.00");

        ResponseEntity<JsonNode> expense = post(token, "/transactions",
                Map.of("description", "Feira", "amount", "40.00", "category", "GROCERIES", "date", "10/11/2026"));
        assertThat(expense.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(expense.getBody().get("type").asString()).isEqualTo("EXPENSE");
        String expenseId = expense.getBody().get("id").asString();

        JsonNode list = get(token, "/transactions?category=GROCERIES");
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);
        JsonNode listed = list.get("content").get(0);
        assertThat(listed.get("id").asString()).isEqualTo(expenseId);
        assertThat(listed.get("description").asString()).isEqualTo("Feira");
        assertThat(listed.get("date").asString()).isEqualTo("10/11/2026");
        assertMoney(listed, "amount", "40.00");

        assertMoney(get(token, "/transactions/monthly/expenses?yearMonth=11/2026&category=GROCERIES"), "total", "40.00");

        JsonNode status = get(token, "/budgets?period=11/2026&category=GROCERIES");
        assertThat(status.get("status").asString()).isEqualTo("WITHIN_LIMIT");
        assertMoney(status, "limit", "500.00");
        assertMoney(status, "spent", "40.00");
        assertMoney(status, "remaining", "460.00");
        assertMoney(status, "percentage", "8.00");

        JsonNode summary = get(token, "/transactions/monthly/summary?yearMonth=11/2026");
        assertThat(summary.get("period").asString()).isEqualTo("11/2026");
        assertMoney(summary, "income", "0");
        assertMoney(summary, "expenses", "40.00");
        assertMoney(summary, "net", "-40.00");
    }

    @Test
    @DisplayName("Reaching the limit changes its status without blocking new expenses")
    void reachingLimitDoesNotBlockExpenses() {
        String token = registerAndLogin("joao@example.com").token();
        String budgetId = post(token, "/budgets",
                Map.of("category", "TRANSPORT", "period", "11/2026", "limitAmount", "40.00"))
                .getBody().get("id").asString();

        post(token, "/transactions",
                Map.of("description", "Ônibus", "amount", "40.00", "category", "TRANSPORT", "date", "05/11/2026"));
        assertThat(get(token, "/budgets?period=11/2026&category=TRANSPORT").get("status").asString())
                .isEqualTo("AT_LIMIT");

        ResponseEntity<JsonNode> overLimit = post(token, "/transactions",
                Map.of("description", "Táxi", "amount", "10.00", "category", "TRANSPORT", "date", "06/11/2026"));
        assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode exceeded = get(token, "/budgets?period=11/2026&category=TRANSPORT");
        assertThat(exceeded.get("status").asString()).isEqualTo("EXCEEDED");
        assertMoney(exceeded, "spent", "50.00");
        assertMoney(exceeded, "remaining", "-10.00");

        ResponseEntity<JsonNode> raised = http.patch()
                .uri("/api/v1/budgets/{id}?limitAmount={amount}", budgetId, "100.00")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve().toEntity(JsonNode.class);
        assertThat(raised.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode afterRaise = get(token, "/budgets?period=11/2026&category=TRANSPORT");
        assertThat(afterRaise.get("status").asString()).isEqualTo("WITHIN_LIMIT");
        assertMoney(afterRaise, "spent", "50.00");
    }

    @Test
    @DisplayName("Expense without a date is registered with today's date")
    void expenseWithoutDateUsesToday() {
        String token = registerAndLogin("ana@example.com").token();

        ResponseEntity<JsonNode> expense = post(token, "/transactions",
                Map.of("description", "Padaria", "amount", "12.50", "category", "GROCERIES"));

        assertThat(expense.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(expense.getBody().get("date").asString())
                .isEqualTo(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }
}
