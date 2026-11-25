CREATE TABLE tb_user(
    id UUID PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE tb_transaction(
    id UUID PRIMARY KEY,
    description TEXT NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    category VARCHAR(50) NOT NULL,
    date DATE NOT NULL,
    type VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    FOREIGN KEY (user_id) REFERENCES tb_user(id),
    CHECK ( amount > 0 ),
    CHECK ( type in ('EXPENSE', 'INCOME'))
);

CREATE TABLE tb_user_role(
    user_id UUID NOT NULL REFERENCES tb_user(id),
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);