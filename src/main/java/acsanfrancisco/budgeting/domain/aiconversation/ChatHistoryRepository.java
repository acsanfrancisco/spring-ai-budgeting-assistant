package acsanfrancisco.budgeting.domain.aiconversation;

/**
 * Messages exchanged with the assistant. They exist only as context for the active
 * conversation and are deleted together with it.
 */
public interface ChatHistoryRepository {

    void deleteByConversationId(AiConversationId id);

    /**
     * Deletes messages whose conversation no longer exists, such as a reply saved after the
     * user closed the conversation.
     *
     * @return the number of deleted messages
     */
    int deleteWithoutConversation();
}
