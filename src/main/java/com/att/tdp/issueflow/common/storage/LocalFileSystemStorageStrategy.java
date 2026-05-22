package com.att.tdp.issueflow.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.strategy", havingValue = "local", matchIfMissing = true)
public class LocalFileSystemStorageStrategy implements FileStorageStrategy {

  private final Path baseDir;

  public LocalFileSystemStorageStrategy(LocalFileStorageProperties properties) {
    this.baseDir = Paths.get(properties.basePath());
  }

  @Override
  public void store(String storageKey, InputStream content) throws IOException {
    Files.createDirectories(baseDir);
    Files.copy(content, baseDir.resolve(storageKey));
  }

  @Override
  public InputStream retrieve(String storageKey) throws IOException {
    return Files.newInputStream(baseDir.resolve(storageKey));
  }

  @Override
  public void delete(String storageKey) throws IOException {
    Files.deleteIfExists(baseDir.resolve(storageKey));
  }
}
