package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.application.usecase.transaction.*;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.http.request.TransactionRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.UpdateTransactionRequest;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetails;
import acsanfrancisco.budgeting.infrastructure.security.CustomUserDetailsService;
import acsanfrancisco.budgeting.infrastructure.security.JWTAuthenticationFilter;
import acsanfrancisco.budgeting.infrastructure.security.SecurityConfig;
import acsanfrancisco.budgeting.infrastructure.security.TokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, JWTAuthenticationFilter.class, TransactionControllerTest.MockConfiguration.class})
public class TransactionControllerTest {

    private static final String URL = "/api/v1/transactions";
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PersistTransactionUseCase persistUseCase;
    @Autowired private FindTransactionsByCategoryAndPeriodUseCase findUseCase;
    @Autowired private FindMonthlyExpensesByCategoryUseCase expensesUseCase;
    @Autowired private GetMonthlySummaryUseCase summaryUseCase;
    @Autowired private UpdateTransactionUseCase updateUseCase;
    @Autowired private DeleteTransactionUseCase deleteUseCase;


    private UserId userId;
    private CustomUserDetails principal;
    private TransactionId transactionId;
    private TransactionOutput output;
    private TransactionRequest validRequest;

    @BeforeEach
    void setUp() {
        Mockito.reset(persistUseCase, findUseCase, expensesUseCase, summaryUseCase, updateUseCase, deleteUseCase);
        userId = new UserId();
        principal = new CustomUserDetails(userId, "first@example.com", "encoded-password", Set.of(Role.ROLE_USER));
        transactionId = new TransactionId();
        validRequest = new TransactionRequest("Groceries", new BigDecimal("100.10"), Category.GROCERIES, PERIOD.atDay(1));
        output = new TransactionOutput(transactionId.id(), "Groceries", new BigDecimal("100.10"),
                "GROCERIES", PERIOD.atDay(1), "EXPENSE");
    }

    @Test
    @DisplayName("Should create transaction using request fields and authenticated principal ID")
    void shouldCreateTransaction() throws Exception {
        Mockito.when(persistUseCase.execute(validRequest.toInput(), userId)).thenReturn(output);

        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(transactionId.id().toString()))
                .andExpect(jsonPath("$.description").value("Groceries"))
                .andExpect(jsonPath("$.amount").value(100.10))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.date").value("01/11/2026"))
                .andExpect(jsonPath("$.type").value("EXPENSE"));

        Mockito.verify(persistUseCase).execute(validRequest.toInput(), userId);
    }

    @Test
    @DisplayName("Should forward omitted transaction date to use case")
    void shouldAcceptOmittedDate() throws Exception {
        TransactionRequest withoutDate = new TransactionRequest("Groceries", new BigDecimal("100.10"), Category.GROCERIES, null);
        Mockito.when(persistUseCase.execute(withoutDate.toInput(), userId)).thenReturn(output);

        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutDate)))
                .andExpect(status().isCreated());

        Mockito.verify(persistUseCase).execute(withoutDate.toInput(), userId);
    }

    @Test
    @DisplayName("Should list transactions with default pagination using authenticated principal ID")
    void shouldListWithDefaultPagination() throws Exception {
        Mockito.when(findUseCase.execute(Category.GROCERIES, userId, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(0, 20)))
                .thenReturn(new PageResult<>(List.of(output), 0, 20, 1));

        mockMvc.perform(get(URL).with(user(principal)).param("category", "GROCERIES").param("from", "01/11/2026").param("to", "30/11/2026")
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(transactionId.id().toString()))
                .andExpect(jsonPath("$.content[0].date").value("01/11/2026"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        Mockito.verify(findUseCase).execute(Category.GROCERIES, userId, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(0, 20));
    }

    @Test
    @DisplayName("Should forward explicit pagination and serialize an empty page")
    void shouldListWithExplicitPagination() throws Exception {
        Mockito.when(findUseCase.execute(Category.GROCERIES, userId, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(2, 5)))
                .thenReturn(new PageResult<>(List.of(), 2, 5, 0));

        mockMvc.perform(get(URL).with(user(principal)).param("category", "GROCERIES").param("from", "01/11/2026").param("to", "30/11/2026")
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.size").value(5));

        Mockito.verify(findUseCase).execute(Category.GROCERIES, userId, PERIOD.atDay(1), PERIOD.atEndOfMonth(), new PageQuery(2, 5));
    }

    @Test
    @DisplayName("Should return monthly expenses using authenticated principal ID")
    void shouldReturnMonthlyExpenses() throws Exception {
        Mockito.when(expensesUseCase.execute(PERIOD, Category.GROCERIES, userId))
                .thenReturn(new MonthlyExpensesOutput(Category.GROCERIES, new BigDecimal("150.30")));

        mockMvc.perform(get(URL + "/monthly/expenses").with(user(principal))
                        .param("yearMonth", "11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.total").value(150.30));

        Mockito.verify(expensesUseCase).execute(PERIOD, Category.GROCERIES, userId);
    }

    @Test
    @DisplayName("Should return monthly summary fields and requested period")
    void shouldReturnMonthlySummary() throws Exception {
        Mockito.when(summaryUseCase.execute(PERIOD, userId)).thenReturn(
                new MonthlySummaryOutput(new BigDecimal("150.30"), new BigDecimal("1000.00"), new BigDecimal("849.70")));

        mockMvc.perform(get(URL + "/monthly/summary").with(user(principal)).param("yearMonth", "11/2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.period").value("11/2026"))
                .andExpect(jsonPath("$.expenses").value(150.30)).andExpect(jsonPath("$.income").value(1000.00))
                .andExpect(jsonPath("$.net").value(849.70));

        Mockito.verify(summaryUseCase).execute(PERIOD, userId);
    }

    @Test
    @DisplayName("Should forward partial update with path ID and authenticated principal ID")
    void shouldUpdateTransactionAmount() throws Exception {
        UpdateTransactionRequest update = new UpdateTransactionRequest(null, new BigDecimal("200.00"), null, null);
        TransactionOutput updated = new TransactionOutput(output.id(), output.description(), update.amount(),
                output.category(), output.date(), output.type());
        Mockito.when(updateUseCase.execute(update.toInput(), transactionId, userId)).thenReturn(updated);

        mockMvc.perform(patch(URL + "/" + transactionId.id()).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(transactionId.id().toString()))
                .andExpect(jsonPath("$.amount").value(200.00));

        Mockito.verify(updateUseCase).execute(update.toInput(), transactionId, userId);
    }

    @Test
    @DisplayName("Should delete transaction using path ID and authenticated principal ID")
    void shouldDeleteTransaction() throws Exception {
        mockMvc.perform(delete(URL + "/" + transactionId.id()).with(user(principal)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        Mockito.verify(deleteUseCase).execute(transactionId, userId);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedEndpoints")
    @DisplayName("Should reject unauthenticated requests before invoking controller dependencies")
    void shouldRequireAuthentication(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(persistUseCase, findUseCase, expensesUseCase, summaryUseCase,
                updateUseCase, deleteUseCase);
    }

    @ParameterizedTest(name = "{index}: invalid {0}")
    @MethodSource("invalidCreateRequests")
    @DisplayName("Should reject invalid transaction fields before invoking create use case")
    void shouldRejectInvalidCreateRequest(String field, TransactionRequest invalid) throws Exception {
        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors." + field).isString());

        Mockito.verifyNoInteractions(persistUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should reject nonpositive update amount before invoking update use case")
    void shouldRejectInvalidUpdateAmount(String amount) throws Exception {
        mockMvc.perform(patch(URL + "/" + transactionId.id()).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors.amount").isString());

        Mockito.verifyNoInteractions(updateUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE"})
    @DisplayName("Should return not found when update or delete use case cannot find transaction")
    void shouldTranslateNotFound(String method) throws Exception {
        UpdateTransactionRequest update = new UpdateTransactionRequest(null, BigDecimal.TEN, null, null);
        if (method.equals("PATCH")) {
            Mockito.when(updateUseCase.execute(update.toInput(), transactionId, userId))
                    .thenThrow(new ResourceNotFoundException("Transaction not found"));
        } else {
            Mockito.doThrow(new ResourceNotFoundException("Transaction not found"))
                    .when(deleteUseCase).execute(transactionId, userId);
        }

        mockMvc.perform(request(HttpMethod.valueOf(method), URL + "/" + transactionId.id()).with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidListQueries")
    @DisplayName("Should reject missing or invalid list parameters without invoking use case")
    void shouldRejectInvalidListParameters(String scenario, String query) throws Exception {
        mockMvc.perform(get(URL + query).with(user(principal))).andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(findUseCase);
    }

    @Test
    @DisplayName("Should reject malformed path UUID before invoking delete use case")
    void shouldRejectInvalidTransactionId() throws Exception {
        mockMvc.perform(delete(URL + "/invalid-id").with(user(principal)))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(deleteUseCase);
    }

    static Stream<Arguments> protectedEndpoints() {
        String path = URL + "/00000000-0000-0000-0000-000000000001";
        return Stream.of(Arguments.of("POST", URL), Arguments.of("GET", URL),
                Arguments.of("GET", URL + "/monthly/expenses"), Arguments.of("GET", URL + "/monthly/summary"),
                Arguments.of("PATCH", path), Arguments.of("DELETE", path));
    }

    static Stream<Arguments> invalidCreateRequests() {
        return Stream.of(
                Arguments.of("description", new TransactionRequest(null, BigDecimal.TEN, Category.GROCERIES, null)),
                Arguments.of("description", new TransactionRequest(" ", BigDecimal.TEN, Category.GROCERIES, null)),
                Arguments.of("amount", new TransactionRequest("Groceries", null, Category.GROCERIES, null)),
                Arguments.of("amount", new TransactionRequest("Groceries", BigDecimal.ZERO, Category.GROCERIES, null)),
                Arguments.of("amount", new TransactionRequest("Groceries", BigDecimal.TEN.negate(), Category.GROCERIES, null)),
                Arguments.of("category", new TransactionRequest("Groceries", BigDecimal.TEN, null, null)));
    }

    static Stream<Arguments> invalidListQueries() {
        return Stream.of(Arguments.of("missing category", "?from=01/11/2026&to=30/11/2026"),
                Arguments.of("invalid end date", "?category=GROCERIES&to=invalid"),
                Arguments.of("invalid category", "?category=INVALID&from=01/11/2026&to=30/11/2026"),
                Arguments.of("invalid start date", "?category=GROCERIES&from=invalid"),
                Arguments.of("negative page", "?category=GROCERIES&from=01/11/2026&to=30/11/2026&page=-1"),
                Arguments.of("zero size", "?category=GROCERIES&from=01/11/2026&to=30/11/2026&size=0"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MockConfiguration {

        @Bean PersistTransactionUseCase persistUseCase() {
            return Mockito.mock(PersistTransactionUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean FindTransactionsByCategoryAndPeriodUseCase findUseCase() {
            return Mockito.mock(FindTransactionsByCategoryAndPeriodUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean FindMonthlyExpensesByCategoryUseCase expensesUseCase() {
            return Mockito.mock(FindMonthlyExpensesByCategoryUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean GetMonthlySummaryUseCase summaryUseCase() {
            return Mockito.mock(GetMonthlySummaryUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean UpdateTransactionUseCase updateUseCase() {
            return Mockito.mock(UpdateTransactionUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean DeleteTransactionUseCase deleteUseCase() {
            return Mockito.mock(DeleteTransactionUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean TokenProvider tokenProvider() {
            return Mockito.mock(TokenProvider.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean CustomUserDetailsService customUserDetailsService() {
            return Mockito.mock(CustomUserDetailsService.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

    }
}
