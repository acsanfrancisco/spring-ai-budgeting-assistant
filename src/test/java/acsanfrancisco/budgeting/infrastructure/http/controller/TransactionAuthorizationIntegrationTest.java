package acsanfrancisco.budgeting.infrastructure.http.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionAuthorizationIntegrationTest extends AuthorizationIntegrationTestSupport {

    private static final String URL = "/api/v1/transactions";
    private static final String TRANSACTION_B = "20000000-0000-0000-0000-000000000006";

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "'', 4", "'&from=30/11/2026', 2", "'&to=01/11/2026', 2",
            "'&from=01/11/2026&to=01/11/2026', 1"
    })
    void filtersAuthenticatedCategoryWithIndependentDates(String dates, int expected) throws Exception {
        mockMvc.perform(get(URL + "?category=GROCERIES" + dates).header("Authorization", tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(expected));
    }

    @Test
    void rejectsReversedListDates() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", tokenA).param("category", "GROCERIES")
                        .param("from", "30/11/2026").param("to", "01/11/2026"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE"})
    @DisplayName("Should reject changes to another user's transaction and preserve it")
    void shouldRejectOtherUsersTransactionAndPreserveIt(String method) throws Exception {
        var request = method.equals("PATCH")
                ? patch(URL + "/" + TRANSACTION_B).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}")
                : delete(URL + "/" + TRANSACTION_B);
        mockMvc.perform(request.header("Authorization", tokenA).param("userId", USER_B))
                .andExpect(status().isNotFound());
        entityManager.flush();
        entityManager.clear();
        assertEquals(new BigDecimal("250.00"), jdbc.queryForObject(
                "SELECT amount FROM tb_transaction WHERE id = ? AND user_id = ?", BigDecimal.class,
                UUID.fromString(TRANSACTION_B), UUID.fromString(USER_B)));
        assertEquals("Other user groceries", jdbc.queryForObject(
                "SELECT description FROM tb_transaction WHERE id = ?", String.class, UUID.fromString(TRANSACTION_B)));
    }

    @Test
    @DisplayName("Should create transaction for authenticated user despite supplied user ID")
    void shouldCreateTransactionOnlyForAuthenticatedUser() throws Exception {
        var result = mockMvc.perform(post(URL).header("Authorization", tokenA).param("userId", USER_B)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "description" : "New groceries",
                                    "amount" : 12,
                                    "category" : "GROCERIES",
                                    "date" : "15/11/2026",
                                    "userId" : "00000000-0000-0000-0000-000000000002"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn();
        entityManager.flush();
        String id = new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertEquals(UUID.fromString(USER_A), jdbc.queryForObject(
                "SELECT user_id FROM tb_transaction WHERE id = ?", UUID.class, UUID.fromString(id)));
    }

    @Test
    @DisplayName("Should list only authenticated user's transactions")
    void shouldListOnlyAuthenticatedUsersTransactions() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", tokenA).param("userId", USER_B)
                        .param("from", "01/11/2026").param("to", "30/11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].description", org.hamcrest.Matchers.containsInAnyOrder(
                        "First day groceries", "Last day groceries")));
        mockMvc.perform(get(URL).header("Authorization", tokenB)
                        .param("from", "01/11/2026").param("to", "30/11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(TRANSACTION_B));
    }

    @Test
    @DisplayName("Should calculate expenses only for authenticated user")
    void shouldCalculateExpensesOnlyForAuthenticatedUser() throws Exception {
        mockMvc.perform(get(URL + "/monthly/expenses").header("Authorization", tokenA)
                        .param("userId", USER_B).param("yearMonth", "11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(150.30));
        mockMvc.perform(get(URL + "/monthly/expenses").header("Authorization", tokenB)
                        .param("yearMonth", "11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(250));
    }

    @Test
    @DisplayName("Should calculate income, expenses and net balance only for authenticated user")
    void shouldCalculateSummaryOnlyForAuthenticatedUser() throws Exception {
        jdbc.update("INSERT INTO tb_transaction (id, description, amount, category, date, type, user_id) VALUES (?, 'Salary A', 1000, 'SALARY', '2026-11-15', 'INCOME', ?), (?, 'Salary B', 5000, 'SALARY', '2026-11-15', 'INCOME', ?)",
                UUID.randomUUID(), UUID.fromString(USER_A), UUID.randomUUID(), UUID.fromString(USER_B));
        mockMvc.perform(get(URL + "/monthly/summary").header("Authorization", tokenA)
                        .param("userId", USER_B).param("yearMonth", "11/2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expenses").value(240.30))
                .andExpect(jsonPath("$.income").value(1000)).andExpect(jsonPath("$.net").value(759.70));
        mockMvc.perform(get(URL + "/monthly/summary").header("Authorization", tokenB).param("yearMonth", "11/2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expenses").value(250))
                .andExpect(jsonPath("$.income").value(5000)).andExpect(jsonPath("$.net").value(4750));
    }

    @Test
    @DisplayName("Should allow owner to update transaction")
    void shouldAllowOwnerToUpdateTransaction() throws Exception {
        mockMvc.perform(patch(URL + "/" + TRANSACTION_B).header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":200}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(200));
        entityManager.flush();
        assertEquals(new BigDecimal("200.00"), jdbc.queryForObject(
                "SELECT amount FROM tb_transaction WHERE id = ?", BigDecimal.class, UUID.fromString(TRANSACTION_B)));
    }

    @Test
    @DisplayName("Should allow owner to delete transaction")
    void shouldAllowOwnerToDeleteTransaction() throws Exception {
        mockMvc.perform(delete(URL + "/" + TRANSACTION_B).header("Authorization", tokenB))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tb_transaction WHERE id = ?", Integer.class, UUID.fromString(TRANSACTION_B)));
    }
}
