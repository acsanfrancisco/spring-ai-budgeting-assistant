package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.output.TokenOutput;
import acsanfrancisco.budgeting.application.usecase.user.UserLoginUseCase;
import acsanfrancisco.budgeting.infrastructure.http.request.LoginRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JWTAuthenticationFilter.class, AuthControllerTest.MockConfiguration.class})
public class AuthControllerTest {

    private static final String URL = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserLoginUseCase loginUseCase;

    private LoginRequest request;

    @BeforeEach
    void setUp() {
        Mockito.reset(loginUseCase);
        request = new LoginRequest("first@example.com", "test-password");
    }

    @Test
    @DisplayName("Should accept public login without CSRF token and return token from use case")
    void shouldReturnTokenForValidLogin() throws Exception {
        Mockito.when(loginUseCase.execute(request.toLoginInput())).thenReturn(new TokenOutput("generated-token"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("generated-token"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Mockito.verify(loginUseCase).execute(request.toLoginInput());
        Mockito.verifyNoMoreInteractions(loginUseCase);
    }

    @Test
    @DisplayName("Should return unauthorized when use case rejects credentials")
    void shouldReturnUnauthorizedForInvalidCredentials() throws Exception {
        Mockito.when(loginUseCase.execute(request.toLoginInput()))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.statusText").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.token").doesNotExist());

        Mockito.verify(loginUseCase).execute(request.toLoginInput());
    }

    @ParameterizedTest(name = "{index}: invalid {0}")
    @MethodSource("invalidRequests")
    @DisplayName("Should reject invalid login fields before invoking use case")
    void shouldRejectInvalidFields(String field, LoginRequest invalidRequest) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.statusCode").value(422))
                .andExpect(jsonPath("$.message").value("Validation Failed."))
                .andExpect(jsonPath("$.errors." + field).isString());

        Mockito.verifyNoInteractions(loginUseCase);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 30})
    @DisplayName("Should accept minimum and maximum password lengths at HTTP validation")
    void shouldAcceptPasswordLengthBoundaries(int length) throws Exception {
        LoginRequest boundaryRequest = new LoginRequest(request.email(), "a".repeat(length));
        Mockito.when(loginUseCase.execute(boundaryRequest.toLoginInput())).thenReturn(new TokenOutput("generated-token"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundaryRequest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("generated-token"));

        Mockito.verify(loginUseCase).execute(boundaryRequest.toLoginInput());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{\"email\":"})
    @DisplayName("Should reject absent body or malformed JSON without invoking login use case")
    void shouldRejectInvalidBody(String body) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        Mockito.verifyNoInteractions(loginUseCase);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("email", new LoginRequest(null, "test-password")),
                Arguments.of("email", new LoginRequest("", "test-password")),
                Arguments.of("email", new LoginRequest("invalid-email", "test-password")),
                Arguments.of("password", new LoginRequest("first@example.com", null)),
                Arguments.of("password", new LoginRequest("first@example.com", "      ")),
                Arguments.of("password", new LoginRequest("first@example.com", "a".repeat(5))),
                Arguments.of("password", new LoginRequest("first@example.com", "a".repeat(31))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MockConfiguration {
        @Bean
        UserLoginUseCase loginUseCase() {
            return Mockito.mock(UserLoginUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Bean
        TokenProvider tokenProvider() {
            return Mockito.mock(TokenProvider.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Bean
        CustomUserDetailsService customUserDetailsService() {
            return Mockito.mock(CustomUserDetailsService.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }
    }
}
