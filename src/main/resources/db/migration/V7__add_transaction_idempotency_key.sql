ALTER TABLE tb_transaction
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN idempotency_request_hash VARCHAR(64),
    ADD CONSTRAINT ck_transaction_idempotency_pair
        CHECK ((idempotency_key IS NULL) = (idempotency_request_hash IS NULL)),
    ADD CONSTRAINT uk_transaction_user_idempotency_key UNIQUE (user_id, idempotency_key);
