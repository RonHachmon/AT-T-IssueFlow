package com.att.tdp.issueflow.auditlog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.att.tdp.issueflow.common.error.GlobalExceptionHandler;
import com.att.tdp.issueflow.common.security.JwtService;

@WebMvcTest(controllers = AuditLogController.class)
@AutoConfigureMockMvc(
    addFilters = false) // Disables the Security Filters so MockMvc can map the Handler directly
@Import(GlobalExceptionHandler.class)
class AuditLogControllerFilterValidationTest {

  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private JwtService jwtService;

  @Autowired private MockMvc mockMvc;

  @Test
  void rejectsEntityIdWithoutEntityTypeWith400() throws Exception {
    mockMvc.perform(get("/audit-logs").param("entityId", "5")).andExpect(status().isBadRequest());
  }

  @Test
  void rejectsUnknownEntityTypeEnumWith400() throws Exception {
    mockMvc
        .perform(get("/audit-logs").param("entityType", "BANANA"))
        .andExpect(status().isBadRequest());
  }
}
