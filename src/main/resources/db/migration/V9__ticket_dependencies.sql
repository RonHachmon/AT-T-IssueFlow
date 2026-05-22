CREATE TABLE ticket_dependencies (
    id                BIGSERIAL    PRIMARY KEY,
    blocked_ticket_id BIGINT       NOT NULL REFERENCES tickets(id),
    blocker_ticket_id BIGINT       NOT NULL REFERENCES tickets(id),
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_ticket_dependency UNIQUE (blocked_ticket_id, blocker_ticket_id),
    CONSTRAINT chk_ticket_dependency_no_self CHECK (blocked_ticket_id <> blocker_ticket_id)
);

CREATE INDEX ix_ticket_deps_blocked ON ticket_dependencies(blocked_ticket_id);
CREATE INDEX ix_ticket_deps_blocker ON ticket_dependencies(blocker_ticket_id);
