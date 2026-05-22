package com.att.tdp.issueflow.ticket.attachment;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.common.error.FileTooLargeException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.common.error.UnsupportedFileTypeException;
import com.att.tdp.issueflow.common.storage.FileStorageStrategy;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

@Service
public class AttachmentService {

  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/png", "image/jpeg", "application/pdf", "text/plain");
  private static final long MAX_SIZE = 10L * 1024 * 1024;

  private final AttachmentRepository attachmentRepository;
  private final TicketRepository ticketRepository;
  private final UserRepository userRepository;
  private final FileStorageStrategy storageStrategy;
  private final AttachmentMapper attachmentMapper;
  private final Tika tika;

  public AttachmentService(
      AttachmentRepository attachmentRepository,
      TicketRepository ticketRepository,
      UserRepository userRepository,
      FileStorageStrategy storageStrategy,
      AttachmentMapper attachmentMapper,
      Tika tika) {
    this.attachmentRepository = attachmentRepository;
    this.ticketRepository = ticketRepository;
    this.userRepository = userRepository;
    this.storageStrategy = storageStrategy;
    this.attachmentMapper = attachmentMapper;
    this.tika = tika;
  }

  @Transactional
  public AttachmentResponse upload(Long ticketId, MultipartFile file, String uploaderUsername) {
    Ticket ticket =
        ticketRepository
            .findByIdAndDeletedAtIsNull(ticketId)
            .orElseThrow(() -> new NotFoundException("Ticket", ticketId));

    if (file.getSize() > MAX_SIZE) {
      throw new FileTooLargeException(MAX_SIZE);
    }

    String declaredType = file.getContentType();
    if (declaredType == null || !ALLOWED_TYPES.contains(declaredType)) {
      throw new UnsupportedFileTypeException(declaredType != null ? declaredType : "unknown");
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new UnsupportedFileTypeException("unreadable");
    }

    String detectedType = tika.detect(bytes);
    if (!ALLOWED_TYPES.contains(detectedType)) {
      throw new UnsupportedFileTypeException(detectedType);
    }

    User uploader =
        userRepository
            .findByUsernameIgnoreCase(uploaderUsername)
            .orElseThrow(() -> new NotFoundException("User", null));

    String storageKey = UUID.randomUUID().toString();
    try {
      storageStrategy.store(storageKey, file.getInputStream());
    } catch (IOException e) {
      throw new RuntimeException("Failed to store attachment", e);
    }

    Attachment attachment = new Attachment();
    attachment.setTicket(ticket);
    attachment.setFilename(
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
    attachment.setContentType(detectedType);
    attachment.setSizeBytes(file.getSize());
    attachment.setStorageKey(storageKey);
    attachment.setUploadedBy(uploader);

    return attachmentMapper.toResponse(attachmentRepository.save(attachment));
  }

  @Transactional
  public void delete(Long ticketId, Long attachmentId) {
    Attachment attachment =
        attachmentRepository
            .findByIdAndTicketId(attachmentId, ticketId)
            .orElseThrow(() -> new NotFoundException("Attachment", attachmentId));

    try {
      storageStrategy.delete(attachment.getStorageKey());
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete stored attachment", e);
    }

    attachmentRepository.delete(attachment);
  }
}
