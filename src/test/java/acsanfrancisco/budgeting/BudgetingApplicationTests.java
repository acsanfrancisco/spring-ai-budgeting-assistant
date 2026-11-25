package acsanfrancisco.budgeting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "JWT_KEY=context-test-key-0123456789-abcdefghijklmnopqrstuvwxyz",
        "JWT_EXPIRATION=300000"
})
@Testcontainers
@Transactional
@Import(BudgetingApplicationTests.OfflineAiConfiguration.class)
class BudgetingApplicationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
    @Autowired ChatMemory memory;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;

	@Test
	@DisplayName("Should load context with migrated database and persist chat history")
	void shouldLoadContextWithMigratedDatabaseAndPersistChatHistory() {
        String conversationId = UUID.randomUUID().toString();
        memory.add(conversationId, new UserMessage("Minha despesa"));

        assertThat(memory.get(conversationId)).extracting(message -> message.getText())
                .containsExactly("Minha despesa");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?",
                Integer.class, conversationId)).isEqualTo(1);
	}

    @Test
    @DisplayName("Should clear JDBC history without affecting another conversation")
    void shouldClearJdbcHistoryWithoutAffectingAnotherConversation() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        memory.add(first, new UserMessage("Primeiro usuário"));
        memory.add(second, new UserMessage("Segundo usuário"));
        memory.clear(first);

        assertThat(memory.get(first)).isEmpty();
        assertThat(memory.get(second)).extracting(message -> message.getText()).containsExactly("Segundo usuário");
    }

    @Test
    @DisplayName("Should start without global transcription or speech models: each user brings their own keys")
    void shouldStartWithoutGlobalAudioModels() {
        assertThat(context.getBeanProvider(TranscriptionModel.class).getIfAvailable()).isNull();
        assertThat(context.getBeanProvider(TextToSpeechModel.class).getIfAvailable()).isNull();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineAiConfiguration {
        @Bean ChatModel chatModel() { return mock(ChatModel.class); }
        @Bean ChatClient.Builder chatClientBuilder(ChatModel model) { return ChatClient.builder(model); }
    }

}
