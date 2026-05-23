package com.att.tdp.issueflow.comment.mention;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link CommentMention}. The {@code commentId} column is populated via
 * {@code @MapsId} after the parent {@code Comment} is persisted, so callers may construct a fresh
 * instance with a {@code null} {@code commentId} before save.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentMentionId implements Serializable {

  @Column(name = "comment_id")
  private Long commentId;

  @Column(name = "user_id")
  private Long userId;

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CommentMentionId that)) {
      return false;
    }
    return Objects.equals(commentId, that.commentId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commentId, userId);
  }
}
