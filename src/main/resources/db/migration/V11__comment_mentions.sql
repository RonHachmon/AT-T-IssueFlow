-- Join table linking comments to the users they @-mention.
-- A (comment_id, user_id) pair is unique — multiple textual occurrences of the
-- same handle within one comment collapse to a single mention row.
-- ON DELETE CASCADE on comment_id mirrors the hard-delete lifecycle of comments;
-- user_id has no cascade because users currently have no deletion path.

CREATE TABLE comment_mentions (
    comment_id BIGINT                   NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id    BIGINT                   NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX idx_comment_mentions_user_id ON comment_mentions(user_id);
