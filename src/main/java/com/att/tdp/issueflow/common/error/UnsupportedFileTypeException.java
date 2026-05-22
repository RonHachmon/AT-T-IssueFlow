package com.att.tdp.issueflow.common.error;

public class UnsupportedFileTypeException extends RuntimeException {

  public UnsupportedFileTypeException(String detectedType) {
    super("File type '" + detectedType + "' is not permitted");
  }
}
