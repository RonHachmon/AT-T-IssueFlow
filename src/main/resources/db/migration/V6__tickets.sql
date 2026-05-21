CREATE TABLE tickets (
    id           BIGSERIAL       PRIMARY KEY,
    title        VARCHAR(255)    NOT NULL,
    description  TEXT,
    status       VARCHAR(20)     NOT NULL DEFAULT 'TODO',
    priority     VARCHAR(10)     NOT NULL,
    type         VARCHAR(15)     NOT NULL,
    project_id   BIGINT          NOT NULL REFERENCES projects(id),
    assignee_id  BIGINT          REFERENCES users(id),
    due_date     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ     NOT NULL,
    updated_at   TIMESTAMPTZ     NOT NULL,
    deleted_at   TIMESTAMPTZ,
    version      BIGINT          NOT NULL DEFAULT 0
);

-- Efficient lookups: active tickets per project ordered by id
CREATE INDEX ix_tickets_project_active
    ON tickets (project_id, id)
    WHERE deleted_at IS NULL;
