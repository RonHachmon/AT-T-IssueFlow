package com.att.tdp.issueflow.ticket.attachment;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.att.tdp.issueflow.ticket.attachment.dto.AttachmentResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AttachmentMapper {

  @Mapping(target = "ticketId", source = "ticket.id")
  AttachmentResponse toResponse(Attachment attachment);
}
