CREATE TABLE tb_budget_limit(
    id UUID PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    period DATE NOT NULL,
    limit_amount NUMERIC(19,2) NOT NULL,
    user_id UUID NOT NULL,
    FOREIGN KEY(user_id) REFERENCES tb_user(id),
    CHECK( limit_amount > 0 ),
    CONSTRAINT uk_budget_limit_user_category_period
    UNIQUE (user_id, category, period),
    CHECK(EXTRACT(DAY FROM period) = 1)
);