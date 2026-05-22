package com.att.tdp.issueflow.auditlog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.att.tdp.issueflow.auditlog.dto.AuditLogFilter;
import com.att.tdp.issueflow.auth.IssueFlowUserDetailsService;
import com.att.tdp.issueflow.common.security.JwtService;
import com.att.tdp.issueflow.common.security.PasswordEncoderConfiguration;
import com.att.tdp.issueflow.common.security.SecurityConfiguration;

/**
 * Spring Security slice test (one accepted exception to the "no Spring context" rule — security
 * filter behaviour cannot be unit-tested without a filter chain).
 */
@WebMvcTest(controllers = AuditLogController.class)
@Import({SecurityConfiguration.class, PasswordEncoderConfiguration.class})
class AuditLogControllerAuthorizationTest {

  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private IssueFlowUserDetailsService userDetailsService;

  // Replace your old filter mock with the actual token validator bean it relies on:
  @MockitoBean private JwtService jwtService;

  @Autowired private MockMvc mockMvc;

  @Test
  @WithMockUser(authorities = "ADMIN")
  void returnsTwoHundredForAdmin() throws Exception {
    when(auditLogService.findAll(any(AuditLogFilter.class))).thenReturn(List.of());

    mockMvc.perform(get("/audit-logs")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = "DEVELOPER")
  void returnsForbiddenForDeveloper() throws Exception {
    mockMvc.perform(get("/audit-logs")).andExpect(status().isForbidden());
  }

  @Test
  void returnsUnauthorizedForUnauthenticated() throws Exception {
    mockMvc.perform(get("/audit-logs")).andExpect(status().isUnauthorized());
  }
}
