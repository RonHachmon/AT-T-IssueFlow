package com.att.tdp.issueflow.ticket.attachment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

  List<Attachment> findAllByTicketId(Long ticketId);

  Optional<Attachment> findByIdAndTicketId(Long id, Long ticketId);
}
