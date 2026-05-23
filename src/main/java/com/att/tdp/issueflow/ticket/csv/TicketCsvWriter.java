package com.att.tdp.issueflow.ticket.csv;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.att.tdp.issueflow.ticket.dto.TicketResponse;

/**
 * Writes ticket rows in RFC 4180 CSV format. Field order and header names are fixed by the README
 * canonical template for {@code GET /tickets/export}: {@code id,title,description,status,priority,
 * type,assigneeId}. Commas, double-quotes, and embedded newlines in {@code description} are escaped
 * by {@link CSVFormat#DEFAULT} — no manual quoting required.
 */
public final class TicketCsvWriter {

  /** Header row required by the README export contract. */
  static final String[] HEADERS = {
    "id", "title", "description", "status", "priority", "type", "assigneeId"
  };

  private TicketCsvWriter() {}

  /**
   * Writes a header row followed by one row per ticket to {@code out}. The writer is not closed —
   * the caller owns the lifecycle of the underlying stream.
   *
   * @param out the writer to emit CSV bytes to
   * @param tickets the tickets to serialise, in the order they should appear
   * @throws IOException if the underlying writer fails
   */
  public static void write(Writer out, List<TicketResponse> tickets) throws IOException {
    CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).build();
    try (CSVPrinter printer = new CSVPrinter(out, format)) {
      for (TicketResponse ticket : tickets) {
        printer.printRecord(
            ticket.id(),
            ticket.title(),
            ticket.description(),
            ticket.status(),
            ticket.priority(),
            ticket.type(),
            ticket.assigneeId());
      }
      printer.flush();
    }
  }
}
