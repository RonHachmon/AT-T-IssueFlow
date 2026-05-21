# Architecture Decision Records

## ADR-001: Authority strings use no `ROLE_` prefix

**Date**: 2026-05-21  
**Feature**: 003-jwt-auth  
**Status**: Accepted

### Context

Spring Security supports two authority conventions:

- **`ROLE_`-prefixed** (e.g. `ROLE_ADMIN`): required when using `hasRole('ADMIN')` in
  `@PreAuthorize` expressions or `HttpSecurity` config. Spring strips the prefix internally.
- **Plain names** (e.g. `ADMIN`): used as-is with `hasAuthority('ADMIN')`.

The `users.role` column stores plain values (`ADMIN`, `DEVELOPER`). The JWT `role` claim also stores
the plain name. Using a `ROLE_` prefix would require a translation layer at every boundary.

### Decision

Authorities are built as:

```java
new SimpleGrantedAuthority(user.getRole().name())
```

This produces `ADMIN` or `DEVELOPER` — no prefix. The same string appears in:

1. The database `role` column
2. The JWT `role` claim
3. Any future `@PreAuthorize` expression

### Consequences

- Future `@PreAuthorize` expressions **must** use `hasAuthority('ADMIN')`, **not** `hasRole('ADMIN')`.
- Using `hasRole('ADMIN')` would silently fail because Spring would look for `ROLE_ADMIN` in the
  authority list and never find it.
- This decision eliminates a hidden prefix convention that is a common source of confusion and
  hard-to-debug authorization failures.
