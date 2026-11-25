package acsanfrancisco.budgeting.infrastructure.ai.chat;

import acsanfrancisco.budgeting.domain.aiconfiguration.UserAiConfiguration;
import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.infrastructure.ai.AiProviderErrorTranslator;
import acsanfrancisco.budgeting.infrastructure.ai.tools.BudgetLimitTools;
import acsanfrancisco.budgeting.infrastructure.ai.tools.ToolContexts;
import acsanfrancisco.budgeting.infrastructure.ai.tools.TransactionTools;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantChatClient {

    private final ChatModelFactory chatModelFactory;

    private final ChatMemory chatMemory;

    private final TransactionTools transactionTools;

    private final BudgetLimitTools budgetLimitTools;

    @Value("classpath:/prompts/assistant_prompt.st")
    private Resource prompt;

    public String reply(String text, UserId userId, UserAiConfiguration configuration, UUID conversationId,
            TransactionRequestKey key, String apiKey) {
        ChatModel chatModel = chatModelFactory.create(
                configuration.getChatProvider(),
                configuration.getChatModel(),
                apiKey);

        ChatClient chatClient = ChatClient.builder(new ChatModel() {

            @Override
            public ChatOptions getOptions() {
                return chatModel.getOptions();
            }

            @Override
            public ChatResponse call(Prompt request) {
                log.debug("Calling chat model. conversation={} provider={} model={}",
                        conversationId, configuration.getChatProvider(), configuration.getChatModel());
                long start = System.nanoTime();
                try {
                    ChatResponse response = chatModel.call(request);
                    log.info("Chat model responded. conversation={} provider={} durationMs={}",
                            conversationId, configuration.getChatProvider(), (System.nanoTime() - start) / 1_000_000);
                    return response;
                } catch (RuntimeException exception) {
                    RuntimeException translated = AiProviderErrorTranslator.translate(exception, "chat");
                    log.warn("Chat model call failed. conversation={} provider={} reason={}",
                            conversationId, configuration.getChatProvider(), AiProviderErrorTranslator.describe(translated));
                    throw translated;
                }
            }
        })
                .defaultSystem(system -> system.text(prompt).param("currentDate", java.time.LocalDate.now().toString()))
                .defaultTools(transactionTools, budgetLimitTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        return chatClient.prompt()
                .user(text)
                .advisors( advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID, conversationId.toString()
                ))
                .toolContext(key == null ? Map.of(ToolContexts.USER_ID, userId)
                        : Map.of(ToolContexts.USER_ID, userId, ToolContexts.TRANSACTION_REQUEST_KEY, key))
                .call()
                .content();
    }
}
