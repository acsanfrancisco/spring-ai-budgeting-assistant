CREATE UNIQUE INDEX uk_ai_conversation_active_user
    ON tb_ai_conversation (user_id)
    WHERE status = 'ACTIVE';
