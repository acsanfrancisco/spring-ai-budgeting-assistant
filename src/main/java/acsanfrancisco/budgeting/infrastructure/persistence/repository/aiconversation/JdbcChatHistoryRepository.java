package acsanfrancisco.budgeting.infrastructure.persistence.repository.aiconversation;

import acsanfrancisco.budgeting.domain.aiconversation.AiConversationId;
import acsanfrancisco.budgeting.domain.aiconversation.ChatHistoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Deletes rows from the table written by Spring AI's JdbcChatMemoryRepository. Shares the
 * DataSource, so it takes part in the caller's transaction.
 */
@Repository
public class JdbcChatHistoryRepository implements ChatHistoryRepository {

    private final JdbcTemplate jdbc;

    public JdbcChatHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void deleteByConversationId(AiConversationId id) {
        jdbc.update("DELETE FROM SPRING_AI_CHAT_MEMORY WHERE conversation_id = ?", id.id().toString());
    }

    @Override
    public int deleteWithoutConversation() {
        // conversation_id is VARCHAR in the Spring AI schema, while tb_ai_conversation.id is UUID.
        return jdbc.update("""
                DELETE FROM SPRING_AI_CHAT_MEMORY m
                WHERE NOT EXISTS (SELECT 1 FROM tb_ai_conversation c WHERE c.id::text = m.conversation_id)
                """);
    }
}
