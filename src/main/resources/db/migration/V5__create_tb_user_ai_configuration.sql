CREATE TABLE tb_user_ai_configuration(
    user_id UUID PRIMARY KEY REFERENCES tb_user(id),
    chat_provider VARCHAR(20) NOT NULL,
    chat_model VARCHAR(255) NOT NULL,
    chat_api_key TEXT NOT NULL,
    transcription_provider VARCHAR(20) NOT NULL,
    transcription_model VARCHAR(255),
    transcription_api_key TEXT,
    CHECK( chat_provider IN ('OPENAI', 'DEEPSEEK', 'ANTHROPIC', 'GEMINI')),
    CHECK ( BTRIM(chat_model) <> ''
        AND BTRIM(chat_api_key) <> ''),
    CHECK ( transcription_provider IN ('OPENAI')),
    CHECK ( transcription_provider IS NULL
        OR (transcription_model IS NOT NULL
            AND BTRIM(transcription_model) <> ''
            AND transcription_api_key IS NOT NULL
            AND BTRIM(transcription_api_key) <> ''))
);