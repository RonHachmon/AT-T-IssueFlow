package com.att.tdp.issueflow.common.error;

/**
 * Thrown when a query filter combination is logically invalid (e.g. entityId without entityType).
 */
public class InvalidFilterException extends RuntimeException {

  public InvalidFilterException(String message) {
    super(message);
  }
}
