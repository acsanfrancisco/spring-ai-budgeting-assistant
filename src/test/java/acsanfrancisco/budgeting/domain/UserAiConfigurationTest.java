package acsanfrancisco.budgeting.domain;

import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.TranscriptionProvider;
import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UserAiConfigurationTest {

    static Stream<Arguments> invalidConfigurations() {
        UserId userId = new UserId();
        return Stream.of(
                Arguments.of(null, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, null, "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, null, "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, " ", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", null, TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", " ", TranscriptionProvider.OPENAI, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", null, "speech-model", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, null, "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, "", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, " ", "speech-key"),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", null),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", ""),
                Arguments.of(userId, ChatProvider.OPENAI, "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", " ")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    @DisplayName("Should reject missing fields and incompatible transcription configuration")
    void shouldRejectInvalidConfiguration(UserId userId, ChatProvider chatProvider, String chatModel,
            String chatKey, TranscriptionProvider transcriptionProvider, String transcriptionModel, String transcriptionKey) {
        assertThrows(IllegalArgumentException.class, () -> new UserAiConfiguration(userId, chatProvider,
                chatModel, chatKey, transcriptionProvider, transcriptionModel, transcriptionKey));
    }

    @ParameterizedTest
    @EnumSource(ChatProvider.class)
    @DisplayName("Should accept each chat provider with individual OpenAI transcription configuration")
    void shouldCreateConfigurationWithOpenAiTranscription(ChatProvider chatProvider) {
        UserId userId = new UserId();
        UserAiConfiguration configuration = new UserAiConfiguration(userId, chatProvider,
                "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key");

        assertEquals(userId, configuration.getUserId());
        assertEquals(chatProvider, configuration.getChatProvider());
        assertEquals("chat-model", configuration.getChatModel());
        assertEquals("chat-key", configuration.getChatApiKey());
        assertEquals(TranscriptionProvider.OPENAI, configuration.getTranscriptionProvider());
        assertEquals("speech-model", configuration.getTranscriptionModel());
        assertEquals("speech-key", configuration.getTranscriptionApiKey());
    }

    @ParameterizedTest
    @EnumSource(TranscriptionProvider.class)
    @DisplayName("Should preserve chat key when rejecting null, empty or blank replacements")
    void shouldPreserveChatKeyForEachTranscriptionProvider(TranscriptionProvider provider) {
        UserAiConfiguration configuration = new UserAiConfiguration(new UserId(), ChatProvider.OPENAI,
                "chat-model", "chat-key", provider,
                provider == TranscriptionProvider.OPENAI ? "speech-model" : null,
                provider == TranscriptionProvider.OPENAI ? "speech-key" : null);

        assertThrows(IllegalArgumentException.class, () -> configuration.setChatApiKey(null));
        assertEquals("chat-key", configuration.getChatApiKey());
        assertThrows(IllegalArgumentException.class, () -> configuration.setChatApiKey(""));
        assertEquals("chat-key", configuration.getChatApiKey());
        assertThrows(IllegalArgumentException.class, () -> configuration.setChatApiKey(" "));
        assertEquals("chat-key", configuration.getChatApiKey());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Should preserve OpenAI transcription key when rejecting missing replacements")
    void shouldPreserveOpenAiTranscriptionKey(String key) {
        UserAiConfiguration configuration = new UserAiConfiguration(new UserId(), ChatProvider.OPENAI,
                "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key");

        assertThrows(IllegalArgumentException.class, () -> configuration.setTranscriptionApiKey(key));
        assertEquals("speech-key", configuration.getTranscriptionApiKey());
    }

    @Test
    @DisplayName("Should replace valid keys without changing user, providers or models")
    void shouldReplaceValidKeys() {
        UserId userId = new UserId();
        UserAiConfiguration configuration = new UserAiConfiguration(userId, ChatProvider.GEMINI,
                "chat-model", "chat-key", TranscriptionProvider.OPENAI, "speech-model", "speech-key");

        configuration.setChatApiKey("updated-chat-key");
        configuration.setTranscriptionApiKey("updated-speech-key");

        assertEquals("updated-chat-key", configuration.getChatApiKey());
        assertEquals("updated-speech-key", configuration.getTranscriptionApiKey());
        assertEquals(userId, configuration.getUserId());
        assertEquals(ChatProvider.GEMINI, configuration.getChatProvider());
        assertEquals("chat-model", configuration.getChatModel());
        assertEquals(TranscriptionProvider.OPENAI, configuration.getTranscriptionProvider());
        assertEquals("speech-model", configuration.getTranscriptionModel());
    }

}
