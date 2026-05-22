package com.att.tdp.issueflow.common.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageStrategy {

  void store(String storageKey, InputStream content) throws IOException;

  InputStream retrieve(String storageKey) throws IOException;

  void delete(String storageKey) throws IOException;
}
