package com.att.tdp.issueflow.common.pagination;

import java.util.List;

/**
 * Canonical pagination envelope used by every list endpoint in IssueFlow. The shape is fixed by
 * project convention — see {@code specs/002-users-crud/contracts/pagination.md}.
 *
 * @param data one page of items (empty list if the page is past the end)
 * @param page 0-indexed page number
 * @param pageSize records per page (echoes the request, capped server-side at 100)
 * @param total total number of records across all pages
 * @param <T> the element type of {@code data}
 */
public record PagedResponse<T>(List<T> data, int page, int pageSize, long total) {}
