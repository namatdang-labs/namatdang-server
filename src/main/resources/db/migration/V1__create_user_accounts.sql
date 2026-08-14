CREATE TABLE user_accounts
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    email        VARCHAR(255) NOT NULL,
    password     VARCHAR(255) NOT NULL,
    name         VARCHAR(50)  NOT NULL,
    phone_number VARCHAR(20)  NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_user_accounts PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT chk_user_accounts_role CHECK (role IN ('OWNER', 'CONSUMER'))
);
