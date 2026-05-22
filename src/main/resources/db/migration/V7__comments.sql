CREATE TABLE comments (
    id         BIGSERIAL    PRIMARY KEY,
    content    TEXT         NOT NULL,
    ticket_id  BIGINT       NOT NULL REFERENCES tickets(id),
    author_id  BIGINT       NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_comments_ticket_id ON comments(ticket_id);
