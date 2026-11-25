package acsanfrancisco.budgeting.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMemoryConfigTest {

    @Test
    @DisplayName("Should retain only fifteen most recent messages")
    void shouldRetainOnlyFifteenMostRecentMessages() {
        var memory = new ChatMemoryConfig().chatMemory(new InMemoryChatMemoryRepository());
        IntStream.rangeClosed(1, 16).forEach(i -> memory.add("first", new UserMessage("message-" + i)));

        assertThat(memory.get("first")).hasSize(15);
        assertThat(memory.get("first").getFirst().getText()).isEqualTo("message-2");
        assertThat(memory.get("first").getLast().getText()).isEqualTo("message-16");
    }

    @Test
    @DisplayName("Should isolate and clear history by conversation ID")
    void shouldIsolateAndClearHistoryByConversationId() {
        var memory = new ChatMemoryConfig().chatMemory(new InMemoryChatMemoryRepository());
        var first = new UserMessage("first user's expense");
        var second = new UserMessage("second user's expense");
        memory.add("first", first);
        memory.add("second", second);

        assertThat(memory.get("first")).containsExactly(first);
        assertThat(memory.get("second")).containsExactly(second);
        memory.clear("first");
        assertThat(memory.get("first")).isEmpty();
        assertThat(memory.get("second")).containsExactly(second);
    }
}
