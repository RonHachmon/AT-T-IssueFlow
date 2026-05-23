package com.att.tdp.issueflow.comment.mention;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.user.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join entity linking a {@link Comment} to a {@link User} that the comment text mentions. A single
 * row represents the canonical fact "this comment mentions this user". Repeated occurrences of the
 * same handle within the comment text collapse to one row via the {@code (comment_id, user_id)}
 * primary key.
 *
 * <p>The {@code created_at} column is database-defaulted and never written by JPA; it exists for
 * audit-style observability. Canonical comment timing comes from {@code comments.created_at}.
 */
@Entity
@Table(name = "comment_mentions")
@Getter
@Setter
@NoArgsConstructor
public class CommentMention {

  @EmbeddedId private CommentMentionId id;

  @MapsId("commentId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "comment_id", insertable = false, updatable = false)
  private Comment comment;

  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;
}
