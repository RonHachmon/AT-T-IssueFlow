package com.att.tdp.issueflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.att.tdp.issueflow.common.security.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class IssueFlowApplication {

  public static void main(String[] args) {
    SpringApplication.run(IssueFlowApplication.class, args);
  }
}
