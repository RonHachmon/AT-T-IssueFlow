package com.att.tdp.issueflow.ticket.attachment.dto;

public record AttachmentResponse(Long id, Long ticketId, String filename, String contentType) {}
