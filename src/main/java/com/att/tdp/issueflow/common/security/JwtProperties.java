package com.att.tdp.issueflow.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code app.security.jwt} configuration block.
 *
 * <p>Register via {@code @EnableConfigurationProperties(JwtProperties.class)} on the application
 * class or any {@code @Configuration} class.
 *
 * @param secret the HS256 signing key (at least 32 characters; required in production)
 * @param accessTokenTtl lifetime of issued access tokens as an ISO-8601 duration (e.g. {@code
 *     PT12M})
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {}
