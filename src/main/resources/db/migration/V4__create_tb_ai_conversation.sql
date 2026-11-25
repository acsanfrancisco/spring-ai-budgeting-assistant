CREATE TABLE tb_ai_conversation(
       id UUID PRIMARY KEY,
       user_id UUID NOT NULL,
       status VARCHAR(10) NOT NULL,
       expires_at TIMESTAMPTZ NOT NULL,
       FOREIGN KEY(user_id) REFERENCES tb_user(id),
       CHECK( status IN ('ACTIVE', 'CLOSED'))
);