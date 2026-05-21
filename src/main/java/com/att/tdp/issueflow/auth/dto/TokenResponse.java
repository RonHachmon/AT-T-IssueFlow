package com.att.tdp.issueflow.auth.dto;

/**
 * JWT response returned by a successful login.
 *
 * @param accessToken the signed compact JWT string
 * @param tokenType always {@code "Bearer"}
 * @param expiresIn token lifetime in seconds (matches the configured {@code access-token-ttl})
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
