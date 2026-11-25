package acsanfrancisco.budgeting.infrastructure.ai.chat;

import acsanfrancisco.budgeting.domain.aiconfiguration.ChatProvider;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import java.time.Duration;

@Component
public class ChatModelFactory {

    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(30);

    public ChatModel create(
            ChatProvider chatProvider,
            String model,
            String apiKey){

        Assert.notNull(chatProvider, "ChatProvider must not be null");
        Assert.hasText(model, "ChatModel must not be empty");
        Assert.hasText(apiKey, "ApiKey must not be null");

        return switch (chatProvider){
            case OPENAI -> createOpenAiCompatible( "https://api.openai.com/v1", model, apiKey);
            case DEEPSEEK -> createOpenAiCompatible( "https://api.deepseek.com", model, apiKey);
            case ANTHROPIC -> createAnthropic(model, apiKey);
            case GEMINI -> createGemini(model, apiKey);
        };
    }

    private ChatModel createOpenAiCompatible(String baseUrl, String model, String apiKey){
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .timeout(CHAT_TIMEOUT)
                .maxRetries(0)
                .build();

        return OpenAiChatModel.builder()
                .options(options)
                .build();
    }

    private ChatModel createGemini(String model, String apiKey){
        Client client = Client.builder()
                .httpOptions(HttpOptions.builder().timeout(Math.toIntExact(CHAT_TIMEOUT.toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1)).build())
                .apiKey(apiKey)
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(model)
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(options)
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }

    private ChatModel createAnthropic(String model, String apiKey){
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .apiKey(apiKey)
                .timeout(CHAT_TIMEOUT)
                .maxRetries(0)
                .build();

        return AnthropicChatModel.builder()
                .options(options)
                .build();
    }

}
