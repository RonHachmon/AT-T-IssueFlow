package com.att.tdp.issueflow.comment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Comment}. Comments are hard-deleted — no soft-delete filter
 * methods are needed.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

  /**
   * Returns all comments belonging to the given ticket ordered by creation time ascending.
   *
   * @param ticketId the owning ticket identifier
   * @return all comments for the ticket, oldest first
   */
  List<Comment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
