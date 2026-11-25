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

class BudgetLimitAuthorizationIntegrationTest extends AuthorizationIntegrationTestSupport {

    private static final String URL = "/api/v1/budgets";
    private static final String BUDGET_B = "10000000-0000-0000-0000-000000000004";

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE"})
    @DisplayName("Should reject changes to another user's budget and preserve it")
    void shouldRejectOtherUsersBudgetAndPreserveIt(String method) throws Exception {
        var request = method.equals("PATCH") ? patch(URL + "/" + BUDGET_B).param("limitAmount", "1.00")
                : delete(URL + "/" + BUDGET_B);
        mockMvc.perform(request.header("Authorization", tokenA).param("userId", USER_B))
                .andExpect(status().isNotFound());
        entityManager.flush();
        entityManager.clear();
        assertEquals(new BigDecimal("900.00"), jdbc.queryForObject(
                "SELECT limit_amount FROM tb_budget_limit WHERE id = ? AND user_id = ?",
                BigDecimal.class, UUID.fromString(BUDGET_B), UUID.fromString(USER_B)));
    }

    @Test
    @DisplayName("Should create budget for authenticated user despite supplied user ID")
    void shouldCreateBudgetOnlyForAuthenticatedUser() throws Exception {
        var result = mockMvc.perform(post(URL).header("Authorization", tokenA)
                        .param("userId", USER_B).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "category" : "EDUCATION",
                                    "period" : "11/2026",
                                    "limitAmount" : 300,
                                    "userId" : "00000000-0000-0000-0000-000000000002"
                                }
                                """))
                .andExpect(status().isCreated()).andReturn();
        entityManager.flush();
        String id = new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertEquals(UUID.fromString(USER_A), jdbc.queryForObject(
                "SELECT user_id FROM tb_budget_limit WHERE id = ?", UUID.class, UUID.fromString(id)));
    }

    @Test
    @DisplayName("Should return only authenticated user's budget and expenses")
    void shouldQueryOnlyAuthenticatedUsersBudgetAndExpenses() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", tokenA).param("userId", USER_B)
                        .param("period", "11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(500))
                .andExpect(jsonPath("$.spent").value(150.30));
        mockMvc.perform(get(URL).header("Authorization", tokenB)
                        .param("period", "11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(900))
                .andExpect(jsonPath("$.spent").value(250));
    }

    @Test
    @DisplayName("Should allow owner to update budget")
    void shouldAllowOwnerToUpdateBudget() throws Exception {
        mockMvc.perform(patch(URL + "/" + BUDGET_B).header("Authorization", tokenB).param("limitAmount", "850"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limitAmount").value(850));
        entityManager.flush();
        assertEquals(new BigDecimal("850.00"), jdbc.queryForObject(
                "SELECT limit_amount FROM tb_budget_limit WHERE id = ?", BigDecimal.class, UUID.fromString(BUDGET_B)));
    }

    @Test
    @DisplayName("Should allow owner to delete budget while preserving transactions")
    void shouldAllowOwnerToDeleteBudgetWithoutDeletingTransactions() throws Exception {
        mockMvc.perform(delete(URL + "/" + BUDGET_B).header("Authorization", tokenB))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tb_budget_limit WHERE id = ?", Integer.class, UUID.fromString(BUDGET_B)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tb_transaction WHERE user_id = ?", Integer.class, UUID.fromString(USER_B)));
    }
}

