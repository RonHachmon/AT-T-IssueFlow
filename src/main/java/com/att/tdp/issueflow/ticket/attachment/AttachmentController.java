package com.att.tdp.issueflow.ticket.attachment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.ticket.attachment.dto.AttachmentResponse;

@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
public class AttachmentController {

  private final AttachmentService attachmentService;

  public AttachmentController(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public AttachmentResponse upload(
      @PathVariable Long ticketId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal UserDetails userDetails) {
    return attachmentService.upload(ticketId, file, userDetails.getUsername());
  }

  @DeleteMapping("/{attachmentId}")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long ticketId, @PathVariable Long attachmentId) {
    attachmentService.delete(ticketId, attachmentId);
  }
}
