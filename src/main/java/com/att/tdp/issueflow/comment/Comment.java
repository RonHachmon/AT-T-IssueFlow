package com.att.tdp.issueflow.comment;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.att.tdp.issueflow.auditlog.AuditableEntityListener;
import com.att.tdp.issueflow.comment.mention.CommentMention;
import com.att.tdp.issueflow.comment.mention.CommentMentionId;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A plain-text comment left by a user on a {@link Ticket}. Comments are hard-deleted — there is no
 * {@code deletedAt} column. {@code createdAt} and {@code updatedAt} are managed by Spring Data JPA
 * auditing. {@code version} is a JPA optimistic-lock counter that auto-increments on every
 * successful update.
 *
 * <p>The {@code mentions} collection records every user this comment {@code @}-mentions. It is
 * maintained as a single source of truth: callers replace the whole set via {@link
 * #replaceMentions(Collection)} on create/update, and orphan removal deletes withdrawn rows on
 * flush.
 */
@Entity
@Table(name = "comments")
@EntityListeners({AuditingEntityListener.class, AuditableEntityListener.class})
@Getter
@Setter
@NoArgsConstructor
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
  private Ticket ticket;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", nullable = false, updatable = false)
  private User author;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<CommentMention> mentions = new HashSet<>();

  /**
   * Replaces the entire mention set with one row per supplied user. Existing rows for users not in
   * {@code users} are removed via orphan removal on flush.
   *
   * @param users the users this comment now mentions (already deduplicated by caller)
   */
  public void replaceMentions(Collection<User> users) {
    this.mentions.clear();
    for (User u : users) {
      CommentMention mention = new CommentMention();
      mention.setId(new CommentMentionId(null, u.getId()));
      mention.setComment(this);
      mention.setUser(u);
      this.mentions.add(mention);
    }
  }
}
