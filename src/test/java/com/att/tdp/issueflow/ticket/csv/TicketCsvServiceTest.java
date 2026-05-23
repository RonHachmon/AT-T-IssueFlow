package com.att.tdp.issueflow.ticket.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.CsvImportResponse;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;

@ExtendWith(MockitoExtension.class)
class TicketCsvServiceTest {

  private static final Long PROJECT_ID = 7L;

  private static final PlatformTransactionManager NOOP_TX_MANAGER =
      new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
          return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
      };

  @Mock private TicketService ticketService;

  private Validator validator;
  private TicketCsvService csvService;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    csvService = new TicketCsvService(ticketService, NOOP_TX_MANAGER, validator);
  }

  // ---------------- import ----------------

  @Test
  void importParsesCommasQuotesAndNewlinesInDescription() throws IOException {
    String trickyDescription = "Line 1, with comma\nLine 2 with \"quoted\" word";
    String csv =
        "title,description,status,priority,type,assigneeId\n"
            + "\"Tricky ticket\","
            + "\"Line 1, with comma\nLine 2 with \"\"quoted\"\" word\","
            + ",HIGH,BUG,\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    assertThat(response.created()).isEqualTo(1);
    assertThat(response.failed()).isZero();

    ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
    verify(ticketService).createWithInitialStatus(captor.capture(), eq(TicketStatus.TODO));
    assertThat(captor.getValue().description()).isEqualTo(trickyDescription);
    assertThat(captor.getValue().title()).isEqualTo("Tricky ticket");
  }

  @Test
  void importContinuesAfterRowFailure() throws IOException {
    String csv =
        "title,priority,type\n"
            + "First ticket,HIGH,BUG\n"
            + "Bad ticket,BANANA,BUG\n"
            + "Third ticket,LOW,FEATURE\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    assertThat(response.created()).isEqualTo(2);
    assertThat(response.failed()).isEqualTo(1);
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).message()).contains("priority").contains("BANANA");
  }

  @Test
  void importReportsRowNumberMatchingSpreadsheetView() throws IOException {
    String csv = "title,priority,type\n" + "Good,HIGH,BUG\n" + "Bad,BANANA,BUG\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    // header is spreadsheet row 1, first data row is row 2, second data row is row 3
    assertThat(response.errors()).hasSize(1);
    assertThat(response.errors().get(0).row()).isEqualTo(3);
  }

  @Test
  void importHonorsExplicitStatusFromCsv() throws IOException {
    String csv = "title,status,priority,type\n" + "Already done,DONE,LOW,BUG\n";

    csvService.importCsv(multipart(csv), PROJECT_ID);

    verify(ticketService)
        .createWithInitialStatus(any(CreateTicketRequest.class), eq(TicketStatus.DONE));
  }

  @Test
  void importDefaultsBlankStatusToTodo() throws IOException {
    String csv = "title,status,priority,type\n" + "Fresh ticket,,LOW,BUG\n";

    csvService.importCsv(multipart(csv), PROJECT_ID);

    verify(ticketService)
        .createWithInitialStatus(any(CreateTicketRequest.class), eq(TicketStatus.TODO));
  }

  @Test
  void importPassesNullAssigneeWhenColumnIsBlankToTriggerAutoAssignment() throws IOException {
    String csv = "title,priority,type,assigneeId\n" + "Auto-assigned,HIGH,BUG,\n";

    csvService.importCsv(multipart(csv), PROJECT_ID);

    ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
    verify(ticketService).createWithInitialStatus(captor.capture(), any());
    assertThat(captor.getValue().assigneeId()).isNull();
    assertThat(captor.getValue().projectId()).isEqualTo(PROJECT_ID);
  }

  @Test
  void importRejectsBlankTitleWithValidationError() throws IOException {
    String csv = "title,priority,type\n" + ",HIGH,BUG\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    assertThat(response.created()).isZero();
    assertThat(response.failed()).isEqualTo(1);
    assertThat(response.errors().get(0).message()).contains("title");
    verify(ticketService, never()).createWithInitialStatus(any(), any());
  }

  @Test
  void importReturnsZeroCountsForHeaderOnlyCsv() throws IOException {
    String csv = "title,priority,type\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    assertThat(response.created()).isZero();
    assertThat(response.failed()).isZero();
    assertThat(response.errors()).isEmpty();
    verify(ticketService, never()).createWithInitialStatus(any(), any());
  }

  @Test
  void importTranslatesServiceNotFoundExceptionIntoRowError() throws IOException {
    doThrow(new NotFoundException("User", 999L))
        .when(ticketService)
        .createWithInitialStatus(any(CreateTicketRequest.class), any());

    String csv = "title,priority,type,assigneeId\n" + "Phantom assignee,HIGH,BUG,999\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    assertThat(response.failed()).isEqualTo(1);
    assertThat(response.errors().get(0).message()).isEqualTo("User 999 not found");
  }

  @Test
  void importIgnoresIdColumn() throws IOException {
    // id column is silently dropped — import always creates new tickets
    String csv = "id,title,priority,type\n" + "42,Imported ticket,LOW,BUG\n";

    CsvImportResponse response = csvService.importCsv(multipart(csv), PROJECT_ID);

    assertThat(response.created()).isEqualTo(1);
    verify(ticketService, times(1)).createWithInitialStatus(any(CreateTicketRequest.class), any());
  }

  // ---------------- export round-trip ----------------

  @Test
  void exportRoundtripsTicketWithTrickyDescription() throws IOException {
    String trickyDescription = "Has , comma and \"quotes\" and\nnewline";
    TicketResponse ticket =
        new TicketResponse(
            42L,
            "Round trip",
            trickyDescription,
            TicketStatus.IN_PROGRESS,
            TicketPriority.HIGH,
            TicketType.BUG,
            PROJECT_ID,
            5L,
            null,
            false);

    StringWriter buffer = new StringWriter();
    TicketCsvWriter.write(buffer, List.of(ticket));
    String csv = buffer.toString();

    try (CSVParser parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .build()
            .parse(
                new InputStreamReader(
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8))) {

      List<CSVRecord> records = parser.getRecords();
      assertThat(records).hasSize(1);
      CSVRecord row = records.get(0);
      assertThat(row.get("id")).isEqualTo("42");
      assertThat(row.get("title")).isEqualTo("Round trip");
      assertThat(row.get("description")).isEqualTo(trickyDescription);
      assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
      assertThat(row.get("priority")).isEqualTo("HIGH");
      assertThat(row.get("type")).isEqualTo("BUG");
      assertThat(row.get("assigneeId")).isEqualTo("5");
    }
  }

  // ---------------- helpers ----------------

  private MockMultipartFile multipart(String csvContent) {
    return new MockMultipartFile(
        "file", "tickets.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
  }
}
