package acsanfrancisco.budgeting.infrastructure.security;

import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.application.output.UserOutput;
import acsanfrancisco.budgeting.application.usecase.budget.DeleteBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.GetBudgetLimitStatusUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.PersistBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.budget.UpdateBudgetLimitUseCase;
import acsanfrancisco.budgeting.application.usecase.user.ChangeUserPasswordUseCase;
import acsanfrancisco.budgeting.application.usecase.user.PersistUserUseCase;
import acsanfrancisco.budgeting.application.usecase.user.UserLoginUseCase;
import acsanfrancisco.budgeting.application.usecase.useraiconfiguration.AddAiConfigurationUseCase;
import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import acsanfrancisco.budgeting.infrastructure.http.controller.AuthController;
import acsanfrancisco.budgeting.infrastructure.http.controller.BudgetLimitController;
import acsanfrancisco.budgeting.infrastructure.http.controller.UserController;
import acsanfrancisco.budgeting.infrastructure.http.response.TokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UserController.class, BudgetLimitController.class}, properties = {
        "JWT_KEY=security-config-test-key-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFG",
        "JWT_EXPIRATION=300000"
})
@Import({SecurityConfig.class, JWTAuthenticationFilter.class, TokenProvider.class,
        CustomUserDetailsService.class, UserLoginUseCase.class, SecurityConfigTest.MockConfiguration.class})
public class SecurityConfigTest {

    private static final String TEST_KEY = "security-config-test-key-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFG";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String STATUS_URL = "/api/v1/budgets?period=11/2026&category=GROCERIES";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PersistUserUseCase persistUserUseCase;
    @Autowired private GetBudgetLimitStatusUseCase statusUseCase;
    @Autowired private PersistBudgetLimitUseCase persistBudgetUseCase;
    @Autowired private UpdateBudgetLimitUseCase updateUseCase;
    @Autowired private DeleteBudgetLimitUseCase deleteUseCase;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean private TranscriptionModel transcriptionModel;
    @MockitoBean private ChatClient chatClient;

    private User user;

    @BeforeEach
    void setUp() {
        Mockito.reset(persistUserUseCase, statusUseCase, persistBudgetUseCase, updateUseCase, deleteUseCase);
        user = new User("First Test User", "first@example.com", passwordEncoder.encode("test-password"));
        Mockito.when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("Should log in with real BCrypt authentication and JWT then access protected budget endpoint")
    void shouldAuthenticateLoginAndAuthorizeWithRealToken() throws Exception {
        Mockito.when(statusUseCase.execute(YearMonth.of(2026, 11), user.getId(), Category.GROCERIES))
                .thenReturn(new MonthlyBudgetLimitStatusOutput(BudgetStatus.NOT_CONFIGURED, null,
                        java.math.BigDecimal.ZERO, null, null));

        MvcResult login = mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                            "email" : "first@example.com",
                            "password" : "test-password"
                        }
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString()).andReturn();
        String token = objectMapper.readValue(login.getResponse().getContentAsString(), TokenResponse.class).token();

        Assertions.assertEquals(user.getEmail(), Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload().get("username", String.class));

        MvcResult authorized = mockMvc.perform(get(STATUS_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_CONFIGURED")).andReturn();

        Assertions.assertNull(login.getRequest().getSession(false));
        Assertions.assertNull(authorized.getRequest().getSession(false));
        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        Mockito.verify(statusUseCase).execute(YearMonth.of(2026, 11), user.getId(), Category.GROCERIES);

        mockMvc.perform(get(STATUS_URL)).andExpect(status().isUnauthorized());
        Mockito.verifyNoMoreInteractions(statusUseCase);
    }

    @Test
    @DisplayName("Should reject incorrect password through real AuthenticationManager without returning token")
    void shouldRejectIncorrectPassword() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {
                            "email" : "first@example.com",
                            "password" : "wrong-password"
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.token").doesNotExist());

        Mockito.verifyNoInteractions(statusUseCase);
    }

    @Test
    @DisplayName("Should allow public user registration without authentication or CSRF token")
    void shouldAllowPublicRegistration() throws Exception {
        Mockito.when(persistUserUseCase.execute(Mockito.any()))
                .thenReturn(new UserOutput(user.getId().id(), user.getFullName(), user.getEmail()));

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                            "fullName" : "First Test User",
                            "email" : "first@example.com",
                            "password" : "test-password"
                        }
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(user.getEmail()));

        Mockito.verifyNoInteractions(userRepository);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("protectedRequests")
    @DisplayName("Should return unauthorized for anonymous requests outside public route rules")
    void shouldProtectOtherRequests(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(persistUserUseCase, statusUseCase, persistBudgetUseCase, updateUseCase, deleteUseCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/swagger-config"})
    @DisplayName("Should let anonymous documentation requests pass security independently of resource availability")
    void shouldPermitDocumentationPaths(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();

        // This MVC slice does not load the documentation generator or require its resources to exist.
        Assertions.assertNotEquals(401, result.getResponse().getStatus());
        Assertions.assertNotEquals(403, result.getResponse().getStatus());
        Mockito.verifyNoInteractions(userRepository, statusUseCase);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("Should reject malformed, expired and incorrectly signed JWT on protected route")
    void shouldRejectInvalidBearerToken(String scenario, String token) throws Exception {
        mockMvc.perform(get(STATUS_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        Mockito.verifyNoInteractions(userRepository, statusUseCase);
    }

    static Stream<Arguments> protectedRequests() {
        String budgetPath = "/api/v1/budgets/00000000-0000-0000-0000-000000000001";
        return Stream.of(Arguments.of("GET", STATUS_URL), Arguments.of("POST", "/api/v1/budgets"),
                Arguments.of("PATCH", budgetPath + "?limitAmount=10"), Arguments.of("DELETE", budgetPath),
                Arguments.of("GET", "/api/v1/users"), Arguments.of("GET", LOGIN_URL));
    }

    static Stream<Arguments> invalidTokens() {
        String expired = Jwts.builder().claim("username", "first@example.com")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY.getBytes(StandardCharsets.UTF_8))).compact();
        String wrongKey = Jwts.builder().claim("username", "first@example.com")
                .expiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(
                        "another-config-test-key-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFG".getBytes(StandardCharsets.UTF_8)))
                .compact();
        return Stream.of(Arguments.of("malformed token", "not-a-token"),
                Arguments.of("expired token", expired), Arguments.of("token signed with another key", wrongKey));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MockConfiguration {
        @Bean AddAiConfigurationUseCase addAiConfigurationUseCase() {
            return Mockito.mock(AddAiConfigurationUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Bean ChatClient.Builder chatClientBuilder(ChatClient chatClient) {
            ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class, Mockito.RETURNS_SELF);
            Mockito.when(builder.build()).thenReturn(chatClient);
            return builder;
        }
        @Bean ChangeUserPasswordUseCase changeUserPasswordUseCase() {
            return Mockito.mock(ChangeUserPasswordUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean PersistUserUseCase persistUserUseCase() {
            return Mockito.mock(PersistUserUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean GetBudgetLimitStatusUseCase statusUseCase() {
            return Mockito.mock(GetBudgetLimitStatusUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean PersistBudgetLimitUseCase persistBudgetUseCase() {
            return Mockito.mock(PersistBudgetLimitUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean UpdateBudgetLimitUseCase updateUseCase() {
            return Mockito.mock(UpdateBudgetLimitUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
        @Bean DeleteBudgetLimitUseCase deleteUseCase() {
            return Mockito.mock(DeleteBudgetLimitUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
    }
}
