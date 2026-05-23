package com.att.tdp.issueflow.comment.mention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.att.tdp.issueflow.comment.Comment;

/**
 * Spring Data JPA repository for {@link CommentMention}. The reverse lookup is implemented as an
 * explicit JPQL query rather than relying on the {@code Comment.mentions} collection so we can join
 * through the ticket and project to exclude soft-deleted parents at the database, not in the
 * service.
 */
public interface CommentMentionRepository extends JpaRepository<CommentMention, CommentMentionId> {

  /**
   * Returns one page of comments mentioning the given user, newest first. Comments whose ticket or
   * project is soft-deleted are excluded — this matches the project-wide cascade rule that
   * soft-deleted parents hide their dependents from public reads.
   *
   * @param userId the mentioned user's id
   * @param pageable the page request (0-based page index for Spring Data; the controller translates
   *     the public 1-based page)
   * @return the page of comments mentioning the user, ordered by creation time descending
   */
  @Query(
      value =
          """
          SELECT c FROM Comment c
            JOIN CommentMention m ON m.id.commentId = c.id
            JOIN c.ticket t
            JOIN t.project p
           WHERE m.id.userId = :userId
             AND t.deletedAt IS NULL
             AND p.deletedAt IS NULL
           ORDER BY c.createdAt DESC
          """,
      countQuery =
          """
          SELECT COUNT(c) FROM Comment c
            JOIN CommentMention m ON m.id.commentId = c.id
            JOIN c.ticket t
            JOIN t.project p
           WHERE m.id.userId = :userId
             AND t.deletedAt IS NULL
             AND p.deletedAt IS NULL
          """)
  Page<Comment> findMentioningCommentsForUser(@Param("userId") Long userId, Pageable pageable);
}
