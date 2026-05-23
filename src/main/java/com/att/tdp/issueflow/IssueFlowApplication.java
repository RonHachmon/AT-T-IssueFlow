package com.att.tdp.issueflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.att.tdp.issueflow.common.security.JwtProperties;
import com.att.tdp.issueflow.ticket.escalation.EscalationProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, EscalationProperties.class})
public class IssueFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(IssueFlowApplication.class, args);
  }
}
