package com.att.tdp.issueflow.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage.local")
public record LocalFileStorageProperties(String basePath) {}
