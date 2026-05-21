-- Users table — first business entity in IssueFlow.
-- Case-insensitive uniqueness on username and email is enforced by functional
-- unique indexes on LOWER(...), not by plain UNIQUE constraints, so original
-- casing is preserved in storage while uniqueness ignores case.

CREATE TABLE users (
    id              BIGSERIAL                PRIMARY KEY,
    username        VARCHAR(50)              NOT NULL,
    email           VARCHAR(254)             NOT NULL,
    full_name       VARCHAR(100)             NOT NULL,
    role            VARCHAR(20)              NOT NULL,
    password_hash   VARCHAR(72),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'DEVELOPER'))
);

CREATE UNIQUE INDEX users_username_lower_unique ON users (LOWER(username));
CREATE UNIQUE INDEX users_email_lower_unique    ON users (LOWER(email));
