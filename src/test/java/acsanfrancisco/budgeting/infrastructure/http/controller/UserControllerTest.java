package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.exception.PasswordMismatchException;
import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.input.ChangePasswordInput;
import acsanfrancisco.budgeting.application.input.CreateUserInput;
import acsanfrancisco.budgeting.application.output.UserOutput;
import acsanfrancisco.budgeting.application.usecase.user.ChangeUserPasswordUseCase;
import acsanfrancisco.budgeting.application.usecase.user.PersistUserUseCase;
import acsanfrancisco.budgeting.application.usecase.useraiconfiguration.AddAiConfigurationUseCase;
import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.http.request.ChangePasswordRequest;
import acsanfrancisco.budgeting.infrastructure.http.request.UserRequest;
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
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import tools.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JWTAuthenticationFilter.class, UserControllerTest.MockConfiguration.class})
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistUserUseCase persistUserUseCase;

    @Autowired
    private ChangeUserPasswordUseCase changeUserPasswordUseCase;

    private UserRequest request;
    private UserOutput output;

    @BeforeEach
    void setUp() {
        Mockito.reset(persistUserUseCase, changeUserPasswordUseCase);
        request = new UserRequest("First Test User", "first@example.com", "test-password");
        output = new UserOutput(UUID.randomUUID(), request.fullName(), request.email());
    }

    @Test
    @DisplayName("Should create user without authentication or CSRF token and omit password from response")
    void shouldCreateUserWithValidRequest() throws Exception {
        CreateUserInput expectedInput = new CreateUserInput(request.fullName(), request.email(), request.password());
        Mockito.when(persistUserUseCase.execute(expectedInput)).thenReturn(output);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(output.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fullName").value(output.fullName()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(output.email()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.password").doesNotExist());

        Mockito.verify(persistUserUseCase).execute(expectedInput);
        Mockito.verifyNoMoreInteractions(persistUserUseCase);
    }

    @Test
    @DisplayName("Should return conflict when use case reports duplicate email")
    void shouldReturnConflictForDuplicateEmail() throws Exception {
        Mockito.when(persistUserUseCase.execute(Mockito.any(CreateUserInput.class)))
                .thenThrow(new ResourceAlreadyExistsException("Email already exists"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.statusCode").value(409))
                .andExpect(MockMvcResultMatchers.jsonPath("$.statusText").value("Conflict"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Email already exists"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.method").value("POST"));
    }

    @ParameterizedTest(name = "{index}: invalid {0}")
    @MethodSource("invalidRequests")
    @DisplayName("Should reject invalid request fields without calling use case")
    void shouldRejectInvalidFields(String field, UserRequest invalidRequest) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(MockMvcResultMatchers.status().is(422))
                .andExpect(MockMvcResultMatchers.jsonPath("$.statusCode").value(422))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Validation Failed."))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errors." + field).isString());

        Mockito.verifyNoInteractions(persistUserUseCase);
    }

    @ParameterizedTest(name = "{index}: name length {0}, password length {1}")
    @MethodSource("validLengths")
    @DisplayName("Should accept minimum and maximum full name and password lengths")
    void shouldAcceptValidLengthBoundaries(int fullNameLength, int passwordLength) throws Exception {
        UserRequest boundaryRequest = new UserRequest("a".repeat(fullNameLength), "first@example.com", "a".repeat(passwordLength));
        UserOutput boundaryOutput = new UserOutput(output.id(), boundaryRequest.fullName(), boundaryRequest.email());
        CreateUserInput expectedInput = new CreateUserInput(boundaryRequest.fullName(), boundaryRequest.email(), boundaryRequest.password());
        Mockito.when(persistUserUseCase.execute(expectedInput)).thenReturn(boundaryOutput);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundaryRequest)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.fullName").value(boundaryRequest.fullName()));

        Mockito.verify(persistUserUseCase).execute(expectedInput);
    }

    @Test
    @DisplayName("Should return bad request for malformed JSON without calling use case")
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invalid request body"));

        Mockito.verifyNoInteractions(persistUserUseCase);
    }

    @Test
    @DisplayName("Should return bad request for missing body without calling use case")
    void shouldRejectMissingBody() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invalid request body"));

        Mockito.verifyNoInteractions(persistUserUseCase);
    }

    private static final String PASSWORD_URL = "/api/v1/users/password";
    private static final ChangePasswordRequest PASSWORD_CHANGE =
            new ChangePasswordRequest("secret123", "new-secret456", "new-secret456");

    private CustomUserDetails principal(UserId userId) {
        return new CustomUserDetails(userId, "first@example.com", "encoded-password", Set.of(Role.ROLE_USER));
    }

    @Test
    @DisplayName("Should change the authenticated user's password and return no content")
    void shouldChangePasswordOfAuthenticatedUser() throws Exception {
        UserId userId = new UserId();

        mockMvc.perform(MockMvcRequestBuilders.post(PASSWORD_URL)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PASSWORD_CHANGE)))
                .andExpect(MockMvcResultMatchers.status().isNoContent())
                .andExpect(MockMvcResultMatchers.content().string(""));

        Mockito.verify(changeUserPasswordUseCase)
                .execute(new ChangePasswordInput("secret123", "new-secret456", "new-secret456"), userId);
    }

    @Test
    @DisplayName("Should require authentication to change the password")
    void shouldRequireAuthenticationToChangePassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PASSWORD_CHANGE)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        Mockito.verifyNoInteractions(changeUserPasswordUseCase);
    }

    @Test
    @DisplayName("Should return bad request when the use case rejects the passwords")
    void shouldReturnBadRequestWhenPasswordsAreRejected() throws Exception {
        Mockito.doThrow(new PasswordMismatchException("Current password is incorrect"))
                .when(changeUserPasswordUseCase).execute(Mockito.any(), Mockito.any());

        mockMvc.perform(MockMvcRequestBuilders.post(PASSWORD_URL)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal(new UserId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PASSWORD_CHANGE)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.statusCode").value(400))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Current password is incorrect"));
    }

    @ParameterizedTest(name = "{index}: invalid {0}")
    @MethodSource("invalidPasswordChanges")
    @DisplayName("Should reject invalid password change fields without calling use case")
    void shouldRejectInvalidPasswordChangeFields(String field, ChangePasswordRequest invalidRequest) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(PASSWORD_URL)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal(new UserId())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(MockMvcResultMatchers.status().is(422))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errors." + field).isString());

        Mockito.verifyNoInteractions(changeUserPasswordUseCase);
    }

    static Stream<Arguments> invalidPasswordChanges() {
        return Stream.of(
                Arguments.of("currentPassword", new ChangePasswordRequest(null, "new-secret456", "new-secret456")),
                Arguments.of("newPassword", new ChangePasswordRequest("secret123", "a".repeat(5), "a".repeat(5))),
                Arguments.of("newPassword", new ChangePasswordRequest("secret123", "a".repeat(31), "a".repeat(31))),
                Arguments.of("confirmNewPassword", new ChangePasswordRequest("secret123", "new-secret456", "  "))
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MockConfiguration {
        @Bean
        AddAiConfigurationUseCase addAiConfigurationUseCase() {
            return Mockito.mock(AddAiConfigurationUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Bean
        ChangeUserPasswordUseCase changeUserPasswordUseCase() {
            return Mockito.mock(ChangeUserPasswordUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Bean
        PersistUserUseCase persistUserUseCase() {
            return Mockito.mock(PersistUserUseCase.class, Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));
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

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("fullName", new UserRequest(null, "first@example.com", "test-password")),
                Arguments.of("fullName", new UserRequest("      ", "first@example.com", "test-password")),
                Arguments.of("fullName", new UserRequest("a".repeat(5), "first@example.com", "test-password")),
                Arguments.of("fullName", new UserRequest("a".repeat(101), "first@example.com", "test-password")),
                Arguments.of("email", new UserRequest("First Test User", null, "test-password")),
                Arguments.of("email", new UserRequest("First Test User", "", "test-password")),
                Arguments.of("email", new UserRequest("First Test User", "invalid-email", "test-password")),
                Arguments.of("password", new UserRequest("First Test User", "first@example.com", null)),
                Arguments.of("password", new UserRequest("First Test User", "first@example.com", "      ")),
                Arguments.of("password", new UserRequest("First Test User", "first@example.com", "a".repeat(5))),
                Arguments.of("password", new UserRequest("First Test User", "first@example.com", "a".repeat(31)))
        );
    }

    static Stream<Arguments> validLengths() {
        return Stream.of(Arguments.of(6, 6), Arguments.of(100, 30));
    }
}
