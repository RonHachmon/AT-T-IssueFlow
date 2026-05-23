package com.att.tdp.issueflow.ticket;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.att.tdp.issueflow.ticket.csv.TicketCsvService;
import com.att.tdp.issueflow.ticket.csv.TicketCsvWriter;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.CsvImportResponse;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;

/**
 * REST controller for the tickets surface. The endpoint contract is fixed by the project's {@code
 * README.md} Tickets APIs table — every verb, path, and status code matches that table one-to-one.
 * All five endpoints return {@code 200 OK} per the README canonical template. Validation triggering
 * lives here; business logic lives in {@link TicketService}.
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

  private final TicketService ticketService;
  private final TicketCsvService ticketCsvService;

  public TicketController(TicketService ticketService, TicketCsvService ticketCsvService) {
    this.ticketService = ticketService;
    this.ticketCsvService = ticketCsvService;
  }

  /**
   * Creates a new ticket with status {@code TODO}.
   *
   * @param request the validated request body
   * @return {@code 200 OK} with the persisted ticket
   */
  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
    return ticketService.create(request);
  }

  /**
   * Returns all active tickets belonging to the given project as a plain JSON array. Soft-deleted
   * tickets and tickets belonging to a soft-deleted project are excluded.
   *
   * @param projectId the owning project identifier
   * @return {@code 200 OK} with all active tickets for the project
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<TicketResponse> listByProject(@RequestParam Long projectId) {
    return ticketService.listByProject(projectId);
  }

  /**
   * Fetches a single active ticket by id.
   *
   * @param ticketId the ticket identifier
   * @return {@code 200 OK} with the ticket; {@code 404} via global advice if unknown or deleted
   */
  @GetMapping("/{ticketId}")
  @ResponseStatus(HttpStatus.OK)
  public TicketResponse getById(@PathVariable Long ticketId) {
    return ticketService.getById(ticketId);
  }

  /**
   * Partially updates a ticket's fields and/or advances its status. Returns {@code 200 OK} with no
   * body. Attempting to modify a {@code DONE} ticket, or requesting an invalid status transition,
   * returns {@code 409} via global advice.
   *
   * @param ticketId the ticket identifier
   * @param request the validated partial update
   */
  @PatchMapping("/{ticketId}")
  @ResponseStatus(HttpStatus.OK)
  public void update(@PathVariable Long ticketId, @Valid @RequestBody UpdateTicketRequest request) {
    ticketService.update(ticketId, request);
  }

  /**
   * Soft-deletes a ticket. Returns {@code 200 OK} with no body. A second call with the same id
   * returns {@code 404} because the ticket is no longer visible as active.
   *
   * @param ticketId the ticket identifier
   */
  @DeleteMapping("/{ticketId}")
  @ResponseStatus(HttpStatus.OK)
  public void softDelete(@PathVariable Long ticketId) {
    ticketService.softDelete(ticketId);
  }

  /**
   * Streams all active tickets in the given project as an RFC 4180 CSV download. Admin-only —
   * non-admin callers receive {@code 403}. The project must be active; an unknown or soft-deleted
   * project returns {@code 404} before any bytes are streamed.
   *
   * <p>Field order follows the README canonical template: {@code
   * id,title,description,status,priority,type,assigneeId}. Soft-deleted tickets are excluded.
   *
   * @param projectId the owning project identifier
   * @return {@code 200 OK} with {@code Content-Type: text/csv} and a {@code Content-Disposition}
   *     header naming the download
   */
  @GetMapping("/export")
  @PreAuthorize("hasAuthority('ADMIN')")
  public ResponseEntity<StreamingResponseBody> exportTickets(@RequestParam Long projectId) {
    // Fetch eagerly so a missing/deleted project surfaces as 404 before headers are flushed.
    List<TicketResponse> tickets = ticketService.listByProject(projectId);

    StreamingResponseBody body =
        out -> {
          try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            TicketCsvWriter.write(writer, tickets);
          } catch (IOException ex) {
            throw ex;
          }
        };

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"tickets-" + projectId + ".csv\"")
        .body(body);
  }

  /**
   * Bulk-creates tickets from a CSV upload. Admin-only — non-admin callers receive {@code 403}.
   * Each row is parsed and persisted in its own transaction, so a single bad row never aborts the
   * import. The {@code id} column (if present) is ignored; the optional {@code status} column is
   * honoured, defaulting to {@code TODO} when blank. A blank {@code assigneeId} triggers
   * auto-assignment via {@link TicketService#createWithInitialStatus}.
   *
   * @param projectId the project all created tickets belong to (form field)
   * @param file the CSV upload (form field, must be ≤ the configured multipart limit)
   * @return {@code 200 OK} with per-row outcome counts and error details
   * @throws IOException if the upload stream cannot be read
   */
  @PostMapping("/import")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('ADMIN')")
  public CsvImportResponse importTickets(
      @RequestParam Long projectId, @RequestPart("file") MultipartFile file) throws IOException {
    return ticketCsvService.importCsv(file, projectId);
  }

  /**
   * Lists all soft-deleted tickets belonging to the given project. Admin-only — non-admin callers
   * receive {@code 403}. The parent project must itself be active; if it has been soft-deleted, the
   * endpoint returns {@code 404} (restore the project first).
   *
   * @param projectId the owning project identifier
   * @return {@code 200 OK} with all soft-deleted tickets for the project
   */
  @GetMapping("/deleted")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('ADMIN')")
  public List<TicketResponse> listDeletedByProject(@RequestParam Long projectId) {
    return ticketService.listDeletedByProject(projectId);
  }

  /**
   * Restores a soft-deleted ticket by clearing its {@code deletedAt} timestamp. Admin-only —
   * non-admin callers receive {@code 403}. Returns {@code 200 OK} with no body. Restoring a ticket
   * whose parent project is soft-deleted returns {@code 409}.
   *
   * @param ticketId the ticket identifier
   */
  @PostMapping("/{ticketId}/restore")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('ADMIN')")
  public void restore(@PathVariable Long ticketId) {
    ticketService.restore(ticketId);
  }
}
