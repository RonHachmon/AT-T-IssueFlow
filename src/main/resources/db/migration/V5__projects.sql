CREATE TABLE projects (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    owner_id    BIGINT          NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,
    deleted_at  TIMESTAMPTZ
);

-- Partial unique index: name uniqueness enforced on active projects only.
-- Allows a deleted project's name to be reclaimed.
CREATE UNIQUE INDEX ux_projects_name_active
    ON projects (LOWER(name))
    WHERE deleted_at IS NULL;
