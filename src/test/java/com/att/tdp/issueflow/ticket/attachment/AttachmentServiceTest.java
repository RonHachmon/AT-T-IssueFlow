package com.att.tdp.issueflow.ticket.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.tika.Tika;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

  @Mock private AttachmentRepository attachmentRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private UserRepository userRepository;
  @Mock private FileStorageStrategy storageStrategy;
  @Mock private AttachmentMapper attachmentMapper;
  @Mock private Tika tika;
  @Mock private MultipartFile file;

  @InjectMocks private AttachmentService attachmentService;

  private Ticket ticket;
  private User uploader;
  private Attachment savedAttachment;
  private AttachmentResponse response;

  @BeforeEach
  void setup() {
    ticket = new Ticket();
    ticket.setId(1L);

    uploader = new User();
    uploader.setId(99L);
    uploader.setUsername("alice");

    savedAttachment = new Attachment();
    savedAttachment.setId(42L);
    savedAttachment.setTicket(ticket);
    savedAttachment.setFilename("screenshot.png");
    savedAttachment.setContentType("image/png");
    savedAttachment.setSizeBytes(1024L);
    savedAttachment.setStorageKey("abc-uuid");

    response = new AttachmentResponse(42L, 1L, "screenshot.png", "image/png");
  }

  @Test
  void throwsNotFoundWhenTicketAbsent() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> attachmentService.upload(1L, file, "alice"))
        .isInstanceOf(NotFoundException.class);

    verify(attachmentRepository, never()).save(any());
  }

  @Test
  void throwsFileTooLargeWhenSizeExceedsLimit() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
    when(file.getSize()).thenReturn(11L * 1024 * 1024);

    assertThatThrownBy(() -> attachmentService.upload(1L, file, "alice"))
        .isInstanceOf(FileTooLargeException.class);

    verify(attachmentRepository, never()).save(any());
  }

  @Test
  void throwsUnsupportedFileTypeWhenDeclaredMimeNotAllowed() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
    when(file.getSize()).thenReturn(1024L);
    when(file.getContentType()).thenReturn("application/exe");

    assertThatThrownBy(() -> attachmentService.upload(1L, file, "alice"))
        .isInstanceOf(UnsupportedFileTypeException.class);

    verify(tika, never()).detect(any(byte[].class));
    verify(attachmentRepository, never()).save(any());
  }

  @Test
  void throwsUnsupportedFileTypeWhenTikaDetectsSpoofedMime() throws IOException {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
    when(file.getSize()).thenReturn(1024L);
    when(file.getContentType()).thenReturn("image/png");
    when(file.getBytes()).thenReturn(new byte[] {1, 2, 3});
    when(tika.detect(any(byte[].class))).thenReturn("application/x-msdownload");

    assertThatThrownBy(() -> attachmentService.upload(1L, file, "alice"))
        .isInstanceOf(UnsupportedFileTypeException.class);

    verify(attachmentRepository, never()).save(any());
  }

  @Test
  void uploadStoresFileAndReturnsResponse() throws IOException {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ticket));
    when(file.getSize()).thenReturn(1024L);
    when(file.getContentType()).thenReturn("image/png");
    when(file.getBytes()).thenReturn(new byte[] {1, 2, 3});
    when(tika.detect(any(byte[].class))).thenReturn("image/png");
    when(file.getOriginalFilename()).thenReturn("screenshot.png");
    when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(uploader));
    when(attachmentRepository.save(any(Attachment.class))).thenReturn(savedAttachment);
    when(attachmentMapper.toResponse(savedAttachment)).thenReturn(response);

    AttachmentResponse result = attachmentService.upload(1L, file, "alice");

    assertThat(result).isEqualTo(response);
    verify(storageStrategy).store(anyString(), any(InputStream.class));
    verify(attachmentRepository).save(any(Attachment.class));
  }

  @Test
  void deleteRemovesDbRecordAndStoredFile() throws IOException {
    savedAttachment.setStorageKey("abc-uuid");
    when(attachmentRepository.findByIdAndTicketId(42L, 1L))
        .thenReturn(Optional.of(savedAttachment));

    attachmentService.delete(1L, 42L);

    verify(storageStrategy).delete("abc-uuid");
    verify(attachmentRepository).delete(savedAttachment);
  }

  @Test
  void throwsNotFoundWhenAttachmentNotFound() throws IOException {
    when(attachmentRepository.findByIdAndTicketId(99L, 1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> attachmentService.delete(1L, 99L))
        .isInstanceOf(NotFoundException.class);

    verify(storageStrategy, never()).delete(anyString());
    verify(attachmentRepository, never()).delete(any());
  }
}
