package com.att.tdp.issueflow.project.dto;

/**
 * Response body for every project-returning endpoint. Shape matches the README Projects APIs table
 * exactly — four fields, no timestamps.
 *
 * @param id server-assigned identifier
 * @param name project name (original casing preserved)
 * @param description optional free-text description; may be {@code null}
 * @param ownerId identifier of the user who owns this project
 */
public record ProjectResponse(Long id, String name, String description, Long ownerId) {}
