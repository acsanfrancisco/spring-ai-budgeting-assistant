ALTER TABLE tb_user_ai_configuration
    ALTER COLUMN chat_provider DROP NOT NULL,
    ALTER COLUMN chat_model DROP NOT NULL,
    ALTER COLUMN chat_api_key DROP NOT NULL,
    ALTER COLUMN transcription_provider DROP NOT NULL,
    ADD CHECK (chat_provider IS NOT NULL OR transcription_provider IS NOT NULL),
    ADD CHECK ((chat_provider IS NULL) = (chat_model IS NULL)
           AND (chat_provider IS NULL) = (chat_api_key IS NULL)),
    ADD CHECK (transcription_provider IS NOT NULL OR
               (transcription_model IS NULL AND transcription_api_key IS NULL));
