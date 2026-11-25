package acsanfrancisco.budgeting.application.usecase.useraiconfiguration;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.AiConfigurationInput;
import acsanfrancisco.budgeting.domain.aiconfiguration.*;
import acsanfrancisco.budgeting.domain.user.*;
import acsanfrancisco.budgeting.infrastructure.config.EncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddAiConfigurationUseCaseTest {
    @Mock UserRepository users;
    @Mock AiConfigurationRepository configurations;
    private final BytesEncryptor encryptor = new EncryptionConfig().apiKeyEncryptor("test-only-password", "0123456789abcdef");
    private AddAiConfigurationUseCase useCase;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "encoded-password");
        useCase = new AddAiConfigurationUseCase(encryptor, users, configurations);
    }

    @Test
    @DisplayName("Should encrypt both individual API keys before saving and return only public configuration")
    void shouldEncryptBothKeysBeforeSaving() {
        AiConfigurationInput input = new AiConfigurationInput(ChatProvider.GEMINI, "chat-model", "chat-test-key",
                TranscriptionProvider.OPENAI, "speech-model", "speech-test-key");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(configurations.save(any())).thenAnswer(invocation -> {
            UserAiConfiguration saved = invocation.getArgument(0);
            assertThat(saved.getChatApiKey()).isNotEqualTo(input.chatApiKey());
            assertThat(saved.getTranscriptionApiKey()).isNotEqualTo(input.transcriptionApiKey());
            assertThat(decrypt(saved.getChatApiKey())).isEqualTo(input.chatApiKey());
            assertThat(decrypt(saved.getTranscriptionApiKey())).isEqualTo(input.transcriptionApiKey());
            return saved;
        });

        var output = useCase.execute(input, user.getId());

        var captor = ArgumentCaptor.forClass(UserAiConfiguration.class);
        verify(configurations).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(output.chatProvider()).isEqualTo(ChatProvider.GEMINI);
        assertThat(output.chatModel()).isEqualTo("chat-model");
        assertThat(output.transcriptionProvider()).isEqualTo(TranscriptionProvider.OPENAI);
        assertThat(output.transcriptionModel()).isEqualTo("speech-model");
        String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(output);
        assertThat(json).doesNotContain("chatApiKey", "transcriptionApiKey", input.chatApiKey(), input.transcriptionApiKey(),
                captor.getValue().getChatApiKey(), captor.getValue().getTranscriptionApiKey());
    }

    @Test
    @DisplayName("Should reject missing user without saving configuration")
    void shouldRejectMissingUser() {
        when(users.findById(user.getId())).thenReturn(Optional.empty());
        AiConfigurationInput input = new AiConfigurationInput(ChatProvider.GEMINI, "chat-model", "chat-test-key",
                TranscriptionProvider.OPENAI, "speech-model", "speech-test-key");

        assertThatThrownBy(() -> useCase.execute(input, user.getId())).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(configurations);
    }

    @Test
    @DisplayName("Should propagate domain validation and not save invalid configuration")
    void shouldNotSaveInvalidConfiguration() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        AiConfigurationInput input = new AiConfigurationInput(ChatProvider.GEMINI, "chat-model", " ",
                TranscriptionProvider.OPENAI, "speech-model", "speech-test-key");

        assertThatThrownBy(() -> useCase.execute(input, user.getId())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(configurations);
    }

    @Test
    void addingTranscriptionPreservesPreviouslyEncryptedChatCredentials() {
        var userId = user.getId();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(configurations.save(any())).thenAnswer(call -> call.getArgument(0));
        useCase.execute(new AiConfigurationInput(ChatProvider.GEMINI, "model", "key", null, null, null), userId);
        var captor = ArgumentCaptor.forClass(UserAiConfiguration.class);
        verify(configurations).save(captor.capture());
        String encryptedChatKey = captor.getValue().getChatApiKey();
        when(configurations.findById(userId)).thenReturn(Optional.of(captor.getValue()));

        useCase.execute(new AiConfigurationInput(null, null, null, TranscriptionProvider.OPENAI,
                "speech", "voice-key"), userId);

        verify(configurations, times(2)).save(captor.capture());
        assertThat(captor.getValue().getChatApiKey()).isEqualTo(encryptedChatKey);
        assertThat(decrypt(captor.getValue().getTranscriptionApiKey())).isEqualTo("voice-key");
    }

    private String decrypt(String value) {
        return new String(encryptor.decrypt(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
    }
}
