package com.att.tdp.issueflow.common.error;

public class FileTooLargeException extends RuntimeException {

  public FileTooLargeException(long maxBytes) {
    super("File exceeds the maximum allowed size of " + maxBytes + " bytes");
  }
}
