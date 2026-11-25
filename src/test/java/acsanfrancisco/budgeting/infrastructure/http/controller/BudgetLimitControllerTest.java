package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.application.usecase.budget.DeleteBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.UpdateBudgetLimitUseCase;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.http.request.BudgetLimitRequest;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BudgetLimitController.class)
@Import({SecurityConfig.class, JWTAuthenticationFilter.class, BudgetLimitControllerTest.MockConfiguration.class})
public class BudgetLimitControllerTest {

    private static final String URL = "/api/v1/budgets";
    private static final YearMonth PERIOD = YearMonth.of(2026, 11);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PersistBudgetLimitUseCase persistUseCase;
    @Autowired private GetBudgetLimitStatusUseCase statusUseCase;
    @Autowired private UpdateBudgetLimitUseCase updateUseCase;
    @Autowired private DeleteBudgetLimitUseCase deleteUseCase;

    private UserId userId;
    private CustomUserDetails principal;
    private BudgetLimitId budgetLimitId;
    private BudgetLimitRequest validRequest;
    private BudgetLimitOutput output;

    @BeforeEach
    void setUp() {
        Mockito.reset(persistUseCase, statusUseCase, updateUseCase, deleteUseCase);
        userId = new UserId();
        principal = new CustomUserDetails(userId, "first@example.com", "encoded-password", Set.of(Role.ROLE_USER));
        budgetLimitId = new BudgetLimitId();
        validRequest = new BudgetLimitRequest(Category.GROCERIES, PERIOD, new BigDecimal("500.00"));
        output = new BudgetLimitOutput(budgetLimitId.id(), "GROCERIES", "2026-11", validRequest.limitAmount());
    }

    @Test
    @DisplayName("Should create budget from MM/yyyy request period using authenticated principal ID")
    void shouldCreateBudgetLimit() throws Exception {
        Mockito.when(persistUseCase.execute(validRequest.toInput(), userId)).thenReturn(output);

        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "category" : "GROCERIES",
                                    "period" : "11/2026",
                                    "limitAmount" : 500.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(budgetLimitId.id().toString()))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.period").value("11/2026"))
                .andExpect(jsonPath("$.limitAmount").value(500.00));

        Mockito.verify(persistUseCase).execute(validRequest.toInput(), userId);
    }

    @Test
    @DisplayName("Should return conflict when use case reports duplicate budget")
    void shouldReturnConflictForDuplicateBudget() throws Exception {
        Mockito.when(persistUseCase.execute(validRequest.toInput(), userId))
                .thenThrow(new ResourceAlreadyExistsException("Budget limit already exists for this category and period"));

        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message").value("Budget limit already exists for this category and period"));
    }

    @Test
    @DisplayName("Should return monthly budget status using authenticated principal ID")
    void shouldReturnBudgetLimitStatus() throws Exception {
        Mockito.when(statusUseCase.execute(PERIOD, userId, Category.GROCERIES)).thenReturn(
                new MonthlyBudgetLimitStatusOutput(BudgetStatus.NEAR_LIMIT, new BigDecimal("500.00"),
                        new BigDecimal("400.00"), new BigDecimal("100.00"), new BigDecimal("80.00")));

        mockMvc.perform(get(URL).with(user(principal)).param("period", "11/2026").param("category", "GROCERIES")
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.period").value("11/2026"))
                .andExpect(jsonPath("$.status").value("NEAR_LIMIT"))
                .andExpect(jsonPath("$.limit").value(500.00)).andExpect(jsonPath("$.spent").value(400.00))
                .andExpect(jsonPath("$.remaining").value(100.00)).andExpect(jsonPath("$.percentage").value(80.00));

        Mockito.verify(statusUseCase).execute(PERIOD, userId, Category.GROCERIES);
    }

    @Test
    @DisplayName("Should serialize unconfigured budget status returned by use case")
    void shouldReturnUnconfiguredStatus() throws Exception {
        Mockito.when(statusUseCase.execute(PERIOD, userId, Category.GROCERIES)).thenReturn(
                new MonthlyBudgetLimitStatusOutput(BudgetStatus.NOT_CONFIGURED, null, BigDecimal.ZERO, null, null));

        mockMvc.perform(get(URL).with(user(principal)).param("period", "11/2026").param("category", "GROCERIES"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.spent").value(0)).andExpect(jsonPath("$.limit").isEmpty())
                .andExpect(jsonPath("$.remaining").isEmpty()).andExpect(jsonPath("$.percentage").isEmpty());
    }

    @Test
    @DisplayName("Should update only limit amount from query parameter using path ID and authenticated principal ID")
    void shouldUpdateBudgetLimitAmount() throws Exception {
        BigDecimal newAmount = new BigDecimal("650.00");
        Mockito.when(updateUseCase.execute(newAmount, budgetLimitId, userId)).thenReturn(
                new BudgetLimitOutput(output.id(), output.category(), output.period(), newAmount));

        mockMvc.perform(patch(URL + "/" + budgetLimitId.id()).with(user(principal)).param("limitAmount", "650.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(budgetLimitId.id().toString()))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.period").value("11/2026"))
                .andExpect(jsonPath("$.limitAmount").value(650.00));

        Mockito.verify(updateUseCase).execute(newAmount, budgetLimitId, userId);
    }

    @Test
    @DisplayName("Should delete budget using path ID and authenticated principal ID")
    void shouldDeleteBudgetLimit() throws Exception {
        mockMvc.perform(delete(URL + "/" + budgetLimitId.id()).with(user(principal)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        Mockito.verify(deleteUseCase).execute(budgetLimitId, userId);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedEndpoints")
    @DisplayName("Should require authentication for each budget endpoint")
    void shouldRequireAuthentication(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(persistUseCase, statusUseCase, updateUseCase, deleteUseCase);
    }

    @ParameterizedTest(name = "{index}: invalid {0}")
    @MethodSource("invalidCreateRequests")
    @DisplayName("Should reject invalid budget fields before invoking create use case")
    void shouldRejectInvalidCreateRequest(String field, BudgetLimitRequest invalid) throws Exception {
        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors." + field).isString());

        Mockito.verifyNoInteractions(persistUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should return bad request when update use case rejects nonpositive limit amount")
    void shouldTranslateNonPositiveUpdateAmount(String amount) throws Exception {
        Mockito.when(updateUseCase.execute(new BigDecimal(amount), budgetLimitId, userId))
                .thenThrow(new IllegalArgumentException("Limit amount must be positive"));

        mockMvc.perform(patch(URL + "/" + budgetLimitId.id()).with(user(principal)).param("limitAmount", amount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Limit amount must be positive"));

        Mockito.verify(updateUseCase).execute(new BigDecimal(amount), budgetLimitId, userId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATCH", "DELETE"})
    @DisplayName("Should return not found when update or delete use case cannot find budget")
    void shouldTranslateNotFound(String method) throws Exception {
        if (method.equals("PATCH")) {
            Mockito.when(updateUseCase.execute(BigDecimal.TEN, budgetLimitId, userId))
                    .thenThrow(new ResourceNotFoundException("Budget limit not found"));
        } else {
            Mockito.doThrow(new ResourceNotFoundException("Budget limit not found"))
                    .when(deleteUseCase).execute(budgetLimitId, userId);
        }

        mockMvc.perform(request(HttpMethod.valueOf(method), URL + "/" + budgetLimitId.id())
                        .with(user(principal)).param("limitAmount", "10"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("Budget limit not found"));
    }

    @Test
    @DisplayName("Should return bad request when status use case rejects income category")
    void shouldTranslateInvalidStatusCategory() throws Exception {
        Mockito.when(statusUseCase.execute(PERIOD, userId, Category.SALARY))
                .thenThrow(new IllegalArgumentException("Must inform a Expense Category"));

        mockMvc.perform(get(URL).with(user(principal)).param("period", "11/2026").param("category", "SALARY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Must inform a Expense Category"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidParameters")
    @DisplayName("Should reject missing or malformed parameters before invoking use cases")
    void shouldRejectInvalidParameters(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(user(principal)))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(persistUseCase, statusUseCase, updateUseCase, deleteUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{\"category\":"})
    @DisplayName("Should reject absent body or malformed JSON before invoking create use case")
    void shouldRejectInvalidBody(String body) throws Exception {
        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request body"));

        Mockito.verifyNoInteractions(persistUseCase);
    }

    static Stream<Arguments> protectedEndpoints() {
        String path = URL + "/00000000-0000-0000-0000-000000000001";
        return Stream.of(Arguments.of("POST", URL), Arguments.of("GET", URL),
                Arguments.of("PATCH", path), Arguments.of("DELETE", path));
    }

    static Stream<Arguments> invalidCreateRequests() {
        return Stream.of(
                Arguments.of("category", new BudgetLimitRequest(null, PERIOD, BigDecimal.TEN)),
                Arguments.of("period", new BudgetLimitRequest(Category.GROCERIES, null, BigDecimal.TEN)),
                Arguments.of("limitAmount", new BudgetLimitRequest(Category.GROCERIES, PERIOD, null)),
                Arguments.of("limitAmount", new BudgetLimitRequest(Category.GROCERIES, PERIOD, BigDecimal.ZERO)),
                Arguments.of("limitAmount", new BudgetLimitRequest(Category.GROCERIES, PERIOD, BigDecimal.TEN.negate())));
    }

    static Stream<Arguments> invalidParameters() {
        String path = URL + "/00000000-0000-0000-0000-000000000001";
        return Stream.of(Arguments.of("GET", URL + "?category=GROCERIES"),
                Arguments.of("GET", URL + "?period=11/2026"),
                Arguments.of("GET", URL + "?period=invalid&category=GROCERIES"),
                Arguments.of("GET", URL + "?period=11/2026&category=INVALID"),
                Arguments.of("PATCH", path), Arguments.of("PATCH", path + "?limitAmount=invalid"),
                Arguments.of("PATCH", URL + "/invalid-id?limitAmount=10"),
                Arguments.of("DELETE", URL + "/invalid-id"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MockConfiguration {


        @Bean PersistBudgetLimitUseCase persistUseCase() {
            return Mockito.mock(PersistBudgetLimitUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean GetBudgetLimitStatusUseCase statusUseCase() {
            return Mockito.mock(GetBudgetLimitStatusUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean UpdateBudgetLimitUseCase updateUseCase() {
            return Mockito.mock(UpdateBudgetLimitUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean DeleteBudgetLimitUseCase deleteUseCase() {
            return Mockito.mock(DeleteBudgetLimitUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean TokenProvider tokenProvider() {
            return Mockito.mock(TokenProvider.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean CustomUserDetailsService customUserDetailsService() {
            return Mockito.mock(CustomUserDetailsService.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
    }
}
