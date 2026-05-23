package com.att.tdp.issueflow.ticket.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.CsvImportResponse;
import com.att.tdp.issueflow.ticket.dto.CsvImportResponse.CsvImportError;

/**
 * Orchestrates CSV-driven bulk ticket creation. Each data row is parsed, validated, and persisted
 * inside its own transaction via {@link TransactionTemplate} so a single bad row never aborts the
 * rest of the upload — the response reports per-row outcomes.
 *
 * <p>Composes over {@link TicketService#createWithInitialStatus} to reuse project lookup, assignee
 * resolution (including auto-assignment when the CSV's {@code assigneeId} is blank), and audit
 * logging. The CSV's optional {@code status} column is honoured so a round-trip export → import
 * preserves status, including terminal {@code DONE}.
 *
 * <p>Row numbering in error reports is 1-based and matches the spreadsheet view: the header is row
 * 1, the first data row is row 2.
 */
@Service
public class TicketCsvService {

  private static final String COLUMN_TITLE = "title";
  private static final String COLUMN_DESCRIPTION = "description";
  private static final String COLUMN_STATUS = "status";
  private static final String COLUMN_PRIORITY = "priority";
  private static final String COLUMN_TYPE = "type";
  private static final String COLUMN_ASSIGNEE_ID = "assigneeId";
  private static final String COLUMN_DUE_DATE = "dueDate";

  private final TicketService ticketService;
  private final TransactionTemplate transactionTemplate;
  private final Validator validator;

  public TicketCsvService(
      TicketService ticketService,
      PlatformTransactionManager transactionManager,
      Validator validator) {
    this.ticketService = ticketService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.validator = validator;
  }

  /**
   * Parses a CSV upload and creates one ticket per data row in the given project. Each row is
   * attempted in an independent transaction; failures are collected into {@link
   * CsvImportResponse#errors} rather than aborting the import.
   *
   * @param file the multipart upload; first row must be the header
   * @param projectId the project all created tickets will belong to; takes precedence over any
   *     {@code projectId} column in the CSV
   * @return per-row outcome counts and error details
   * @throws IOException if the upload stream cannot be read
   */
  public CsvImportResponse importCsv(MultipartFile file, Long projectId) throws IOException {
    int created = 0;
    int failed = 0;
    List<CsvImportError> errors = new ArrayList<>();

    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    try (Reader reader =
            new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        CSVParser parser = format.parse(reader)) {

      for (CSVRecord record : parser) {
        int rowNumber = spreadsheetRowNumberOf(record);
        try {
          CreateTicketRequest request = toCreateRequest(record, projectId);
          TicketStatus initialStatus = parseStatus(record);
          runValidation(request);
          transactionTemplate.executeWithoutResult(
              status -> ticketService.createWithInitialStatus(request, initialStatus));
          created++;
        } catch (Exception ex) {
          failed++;
          errors.add(new CsvImportError(rowNumber, errorMessageOf(ex)));
        }
      }
    }

    return new CsvImportResponse(created, failed, errors);
  }

  private CreateTicketRequest toCreateRequest(CSVRecord record, Long projectId) {
    String title = cell(record, COLUMN_TITLE);
    String description = cellOrNull(record, COLUMN_DESCRIPTION);
    TicketPriority priority = parseEnum(record, COLUMN_PRIORITY, TicketPriority.class);
    TicketType type = parseEnum(record, COLUMN_TYPE, TicketType.class);
    Long assigneeId = parseLong(record, COLUMN_ASSIGNEE_ID);
    Instant dueDate = parseInstant(record, COLUMN_DUE_DATE);

    return new CreateTicketRequest(
        title, description, priority, type, projectId, assigneeId, dueDate);
  }

  private TicketStatus parseStatus(CSVRecord record) {
    String raw = cellOrNull(record, COLUMN_STATUS);
    if (raw == null || raw.isBlank()) {
      return TicketStatus.TODO;
    }
    try {
      return TicketStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid value for status: " + raw);
    }
  }

  private void runValidation(CreateTicketRequest request) {
    var violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private String cell(CSVRecord record, String column) {
    String value = cellOrNull(record, column);
    return value == null ? "" : value;
  }

  private String cellOrNull(CSVRecord record, String column) {
    if (!record.isMapped(column)) {
      return null;
    }
    String value = record.get(column);
    return value == null || value.isEmpty() ? null : value;
  }

  private <E extends Enum<E>> E parseEnum(CSVRecord record, String column, Class<E> type) {
    String raw = cellOrNull(record, column);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid value for " + column + ": " + raw);
    }
  }

  private Long parseLong(CSVRecord record, String column) {
    String raw = cellOrNull(record, column);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("invalid value for " + column + ": " + raw);
    }
  }

  private Instant parseInstant(CSVRecord record, String column) {
    String raw = cellOrNull(record, column);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(
          "invalid value for " + column + ": " + raw + " (expected ISO-8601 instant)");
    }
  }

  private int spreadsheetRowNumberOf(CSVRecord record) {
    // CSVParser reports record numbers 1-based for data records (header is skipped from the count
    // when setSkipHeaderRecord is true). Add 1 so the number matches the spreadsheet view, where
    // the header occupies row 1.
    return (int) record.getRecordNumber() + 1;
  }

  private String errorMessageOf(Throwable ex) {
    if (ex instanceof ConstraintViolationException cve) {
      return cve.getConstraintViolations().stream()
          .map(this::formatViolation)
          .collect(Collectors.joining("; "));
    }
    if (ex instanceof NotFoundException nfe) {
      return nfe.getResource() + " " + nfe.getResourceId() + " not found";
    }
    String message = ex.getMessage();
    return message == null ? ex.getClass().getSimpleName() : message;
  }

  private String formatViolation(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    return (path.isEmpty() ? "value" : path) + ": " + violation.getMessage();
  }
}
