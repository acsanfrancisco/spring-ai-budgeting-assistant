package acsanfrancisco.budgeting.infrastructure.http.controller;

import acsanfrancisco.budgeting.application.exception.ConversationInProgressException;
import acsanfrancisco.budgeting.application.exception.AiProviderException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.application.usecase.aiconversation.CloseByIdAndUserIDUseCase;
import acsanfrancisco.budgeting.infrastructure.ai.AssistantService;
import acsanfrancisco.budgeting.infrastructure.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockMakers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssistantController.class)
@Import({SecurityConfig.class, JWTAuthenticationFilter.class, AssistantControllerTest.MockConfiguration.class})
class AssistantControllerTest {
    private static final String URL = "/api/v1/assistant/voice";
    @Autowired MockMvc mockMvc;
    @Autowired AssistantService service;
    private UserId userId;
    private CustomUserDetails principal;
    private final MockMultipartFile file = new MockMultipartFile(
            "file", "voice.mp3", "audio/mpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        reset(service);
        userId = new UserId();
        principal = new CustomUserDetails(userId, "user@example.com", "encoded-password", Set.of(Role.ROLE_USER));
    }

    @Test
    @DisplayName("Should forward text and the authenticated user and return plain text")
    void shouldForwardTextAndAuthenticatedUser() throws Exception {
        when(service.executeText("Meu saldo", userId)).thenReturn("Saldo consultado");

        mockMvc.perform(post("/api/v1/assistant/text").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Meu saldo\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Saldo consultado"));

        verify(service).executeText("Meu saldo", userId);
    }

    @Test
    void shouldReturnConflictWhenConversationIsInProgress() throws Exception {
        var conflict = new ConversationInProgressException();
        when(service.executeText("Meu saldo", userId)).thenThrow(conflict);

        mockMvc.perform(post("/api/v1/assistant/text").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Meu saldo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(conflict.getMessage()));
    }

    @Test
    void shouldReturnGatewayTimeoutWithSavedTransactionId() throws Exception {
        var key = UUID.randomUUID();
        var failure = new AiProviderException(AiProviderException.Reason.TIMEOUT, "chat", new IllegalStateException("Timeout"), null);
        var transactionId = UUID.randomUUID();
        failure.setTransactionId(transactionId);
        when(service.executeText("Registre R$ 40", userId, key)).thenThrow(failure);

        mockMvc.perform(post("/api/v1/assistant/text").with(user(principal))
                        .header("Idempotency-Key", key.toString())
                        .accept(MediaType.TEXT_PLAIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Registre R$ 40\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Transaction-Id", transactionId.toString()));
    }

    @Test
    void rateLimitIncludesProviderDelay() throws Exception {
        var failure = new AiProviderException(AiProviderException.Reason.RATE_LIMITED, "chat", null, "30");
        when(service.executeText("Saldo", userId)).thenThrow(failure);
        mockMvc.perform(post("/api/v1/assistant/text").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Saldo\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_RATE_LIMITED"));
    }

    @Test
    void providerRejectionReturnsActionableErrorForAuthenticatedUser() throws Exception {
        when(service.executeText("Saldo", userId)).thenThrow(
                new AiProviderException(AiProviderException.Reason.ACCESS_DENIED, "chat", new IllegalStateException("sensitive provider details"), null));
        mockMvc.perform(post("/api/v1/assistant/text").with(user(principal))
                        .accept(MediaType.TEXT_PLAIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Saldo\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("AI provider error (chat): Check your account permissions for the selected model."));
    }

    @Test
    @DisplayName("Should reject blank text before invoking service")
    void shouldRejectBlankText() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/text").with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.message").value("Must inform message"));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Should forward uploaded audio and authenticated user and return natural text")
    void shouldForwardAudioAndAuthenticatedUser() throws Exception {
        when(service.execute(any(Resource.class), eq(userId))).thenReturn("Despesa registrada");

        mockMvc.perform(multipart(URL).file(file).with(user(principal))
                        .param("userId", new UserId().id().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Despesa registrada"));

        var audio = ArgumentCaptor.forClass(Resource.class);
        verify(service).execute(audio.capture(), eq(userId));
        assertThat(audio.getValue().getContentAsByteArray()).isEqualTo(file.getBytes());
    }

    @Test
    @DisplayName("Should forward the Idempotency-Key of a voice request to the service")
    void shouldForwardVoiceIdempotencyKey() throws Exception {
        UUID key = UUID.randomUUID();
        when(service.execute(any(Resource.class), eq(userId), eq(key))).thenReturn("Lançamento já registrado.");

        mockMvc.perform(multipart(URL).file(file).with(user(principal)).header("Idempotency-Key", key.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Lançamento já registrado."));

        verify(service).execute(any(Resource.class), eq(userId), eq(key));
        verify(service, never()).execute(any(Resource.class), any(UserId.class));
    }

    @Test
    @DisplayName("Should reject a voice Idempotency-Key that is not a UUID")
    void shouldRejectInvalidVoiceIdempotencyKey() throws Exception {
        mockMvc.perform(multipart(URL).file(file).with(user(principal)).header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Should reject unauthenticated upload before invoking service")
    void shouldRejectUnauthenticatedUpload() throws Exception {
        mockMvc.perform(multipart(URL).file(file)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Should reject missing file before invoking service")
    void shouldRejectMissingFile() throws Exception {
        mockMvc.perform(multipart(URL).with(user(principal))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Should reject empty audio file before invoking service")
    void shouldRejectEmptyFile() throws Exception {
        var empty = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", new byte[0]);
        mockMvc.perform(multipart(URL).file(empty).with(user(principal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Audio file is empty"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Should reject JSON instead of multipart before invoking service")
    void shouldRejectJsonInsteadOfMultipart() throws Exception {
        mockMvc.perform(post(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Should map invalid audio from service to bad request")
    void shouldMapInvalidAudio() throws Exception {
        when(service.execute(any(Resource.class), eq(userId))).thenThrow(new IllegalArgumentException("Invalid audio"));
        mockMvc.perform(multipart(URL).file(file).with(user(principal))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should map absent AI configuration to not found")
    void shouldMapMissingConfiguration() throws Exception {
        when(service.execute(any(Resource.class), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Ai configuration not found"));
        mockMvc.perform(multipart(URL).file(file).with(user(principal))).andExpect(status().isNotFound());
        verify(service).execute(any(Resource.class), eq(userId));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MockConfiguration {
        @Bean AssistantService assistantService() { return classMock(AssistantService.class); }
        @Bean CloseByIdAndUserIDUseCase closeConversation() { return classMock(CloseByIdAndUserIDUseCase.class); }
        @Bean TokenProvider tokenProvider() { return classMock(TokenProvider.class); }
        @Bean CustomUserDetailsService userDetailsService() { return classMock(CustomUserDetailsService.class); }
        private static <T> T classMock(Class<T> type) {
            return mock(type, withSettings().mockMaker(MockMakers.SUBCLASS));
        }
    }
}
