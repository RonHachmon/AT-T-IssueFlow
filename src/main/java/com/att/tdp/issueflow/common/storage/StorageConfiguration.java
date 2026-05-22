package com.att.tdp.issueflow.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocalFileStorageProperties.class)
public class StorageConfiguration {}
