package acsanfrancisco.budgeting.infrastructure.ai.chat;

import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ChatModelFactoryTest {

    private final ChatModelFactory factory = new ChatModelFactory();

    @ParameterizedTest
    @EnumSource(value = ChatProvider.class, names = {"OPENAI", "DEEPSEEK"})
    void shouldConfigureTimeoutForOpenAiCompatibleProviders(ChatProvider provider) {
        var options = (OpenAiChatOptions) factory.create(provider, "test-model", "test-key").getOptions();

        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getMaxRetries()).isZero();
    }

    @Test
    void shouldConfigureTimeoutForAnthropic() {
        var options = (AnthropicChatOptions) factory.create(ChatProvider.ANTHROPIC, "test-model", "test-key")
                .getOptions();

        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getMaxRetries()).isZero();
    }

    @Test
    void shouldBuildGeminiWithTheSingleAttemptPolicy() {
        assertThat(factory.create(ChatProvider.GEMINI, "test-model", "test-key"))
                .isInstanceOf(GoogleGenAiChatModel.class);
    }
}
