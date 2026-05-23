package com.att.tdp.issueflow.ticket.dto;

import java.util.List;

/**
 * Response body for {@code POST /tickets/import}. Reports per-row outcomes from a partial-success
 * bulk import: each data row is attempted independently in its own transaction, so a malformed row
 * never aborts the rest of the upload.
 *
 * @param created count of rows that produced a persisted ticket
 * @param failed count of rows that raised an error and were skipped
 * @param errors per-row failure details; empty when {@code failed == 0}
 */
public record CsvImportResponse(int created, int failed, List<CsvImportError> errors) {

  /**
   * One entry in {@link CsvImportResponse#errors}. {@code row} is the 1-based spreadsheet row
   * number (header is row 1, the first data row is row 2) so users can locate the offending line in
   * Excel or LibreOffice without arithmetic.
   *
   * @param row 1-based spreadsheet row number of the failing record
   * @param message human-readable reason the row was rejected
   */
  public record CsvImportError(int row, String message) {}
}
