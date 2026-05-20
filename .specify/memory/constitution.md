<!--
SYNC IMPACT REPORT
==================
Version: (none) -> 1.0.0
Bump rationale: Initial ratification. All template placeholders replaced with
concrete principles, so this is the project's first canonical version (1.0.0,
MAJOR=1 by semver convention for a first ratified version).

Modified principles (template placeholder -> ratified principle):
  - [PRINCIPLE_1_NAME] -> I. Clean Code
  - [PRINCIPLE_2_NAME] -> II. Testing Standards
  - [PRINCIPLE_3_NAME] -> III. Documentation Discipline
  - [PRINCIPLE_4_NAME] -> IV. API Consistency
  - [PRINCIPLE_5_NAME] -> (intentionally omitted; user requested 4 principles)

Added sections:
  - Technology Standards (concrete stack constraints derived from pom.xml + user input)
  - Development Workflow (PR gates that enforce the four principles)
  - Governance (amendment + versioning policy)

Removed sections:
  - Generic placeholder section_2 / section_3 (replaced with concrete sections above)
  - 5th principle slot (user spec uses 4 principles)

Templates / docs status:
  - [OK]      .specify/memory/constitution.md (this file)
  - [OK]      .specify/templates/plan-template.md (Constitution Check section populated with four gates)
  - [PENDING] .specify/templates/tasks-template.md - template still labels tests
              "OPTIONAL"; our constitution makes sanity tests MANDATORY and complex
              tests MANDATORY where branching/state/concurrency exists. Update at
              next tasks-template revision.
  - [PENDING] .specify/templates/spec-template.md - no edits required today; spec
              stays user-facing, but reviewers should reject specs that contradict
              REST conventions in Principle IV.
  - [PENDING] README.md - several endpoints document "200 OK" for POST/DELETE;
              Principle IV requires 201/204. Reconcile when those endpoints are
              implemented (not retroactively, but new endpoints MUST comply).
  - [PENDING] CLAUDE.md - currently a stub; add a "Constitution" pointer when
              runtime guidance is expanded.

Follow-up TODOs:
  - None. Ratification date set to 2026-05-20 (today).
-->

# IssueFlow Constitution

## Core Principles

### I. Clean Code

Code in this repository follows Robert C. Martin's *Clean Code* as the
authoritative reference. The following rules are non-negotiable:

- **Names reveal intent.** No abbreviations, no single-letter identifiers
  outside of short loop counters, no encoded type prefixes.
- **Functions are short and do one thing.** A function operates at a single
  level of abstraction; mixing high-level orchestration with low-level
  string/SQL manipulation in the same method is a defect.
- **No flag arguments.** A boolean parameter that switches behavior MUST be
  split into two methods with intention-revealing names.
- **No dead or commented-out code.** Delete it. Git remembers.
- **No magic numbers or magic strings.** Extract to named constants or enums.
- **Comments explain WHY, not WHAT.** If a comment restates the code, the code
  needs a better name, not a comment. Comments are reserved for non-obvious
  constraints, workarounds, and invariants.

**Rationale**: Maintainability compounds. Every "broken window" left in place
licenses the next one. These rules are cheap to follow up front and ruinously
expensive to retrofit.

### II. Testing Standards

Every feature ships with BOTH layers of tests; neither layer alone is
sufficient:

- **Code-level unit tests** using **JUnit 5 + Mockito**, with **no Spring
  context** (no `@SpringBootTest`, no `@DataJpaTest`). Unit tests run on the
  pure JVM, instantiate the class under test directly, and mock collaborators.
- **Postman API tests** stored under `postman/` and runnable via Newman, that
  exercise the HTTP contract end-to-end.

Coverage rules:

- **One or two sanity tests per feature are MANDATORY.** A feature without a
  smoke test does not ship.
- **Complex tests are MANDATORY wherever any of the following exists**:
  branching logic, state transitions, concurrency or scheduling, or edge cases
  (empty inputs, boundary values, conflicts, race windows, idempotency).
- **Coverage target is small-to-medium.** Cover critical paths and tricky
  logic. Do NOT write tests for trivial getters, setters, DTO mappers, or
  generated code; they add maintenance cost without adding signal.

Test style:

- Test names read as sentences describing behavior, e.g.
  `returnsConflictWhenIssueIsAlreadyClosed`. No `test1`, no
  `testCreateUser_success`.
- Tests follow **Arrange-Act-Assert** with clear blank-line separation between
  the three blocks.

**Rationale**: Pure-JVM unit tests are fast and force good design (dependencies
become explicit collaborators). Postman covers the wire contract. Trivial
tests dilute the suite's signal-to-noise ratio and slow CI without catching
real bugs.

### III. Documentation Discipline

- **Javadoc on public methods only.** Every public method MUST document its
  purpose, every `@param`, the `@return` value (or note `void`), and every
  exception it can throw (`@throws`). Private methods rely on clean naming
  (Principle I) and do NOT get Javadoc.
- **README endpoint coverage.** Every API endpoint MUST appear in `README.md`
  with verb, path, request body shape, response status, and response body
  shape. Adding or changing an endpoint without updating the README is a
  defect.
- **`run.md` MUST always work from a fresh clone.** Every step (install,
  database start, build, run, test) is verified before merge whenever it
  changes. A `run.md` that fails on a clean machine is treated as a broken
  build.
- **`prompts.md` honestly records AI usage.** It names the model used and
  records the key prompts that shaped the code or specs. Do not embellish, do
  not omit.

**Rationale**: Documentation is the human-facing API surface. Rotten or
dishonest docs cost more than no docs at all, because they mislead.

### IV. API Consistency

The HTTP surface follows REST conventions strictly. Reviewers MUST cite this
principle when rejecting deviations.

- **HTTP verbs and status codes**:
  - `POST` that creates a resource -> `201 Created` with a `Location` header
  - `DELETE` and `PATCH`/`PUT` with no response body -> `204 No Content`
  - Missing resource -> `404 Not Found`
  - Conflict with existing state (duplicate, illegal transition) -> `409 Conflict`
  - Semantic validation failure -> `422 Unprocessable Entity`
  - Syntactic / type validation failure -> `400 Bad Request`
- **Resource paths use plural nouns**: `/users`, `/projects`, `/tickets`,
  `/tickets/{ticketId}/comments`.
- **Errors use RFC 7807 Problem Details** (`application/problem+json`) with an
  informative `title`, a human-readable `detail`, and a stable `type` URI per
  error class. No bare error strings, no inconsistent error envelopes.
- **JSON field naming is `camelCase`. SQL identifier naming is `snake_case`.**
  Mapping happens at the boundary; persistence naming MUST NOT leak into the
  API.
- **Standard field names**: primary key is `id`; foreign keys are
  `<entity>Id` (e.g., `projectId`, `assigneeId`); timestamps are `createdAt`
  and `updatedAt`.
- **DTOs at every controller boundary.** JPA entities MUST NOT appear in
  request or response bodies, ever. Map explicitly between entities and DTOs.
- **Pagination has one canonical shape** across all endpoints (e.g.,
  `{ "data": [...], "page": N, "pageSize": N, "total": N }`). New endpoints
  MUST adopt the same envelope rather than inventing new ones.
- **Prefer popular, well-maintained libraries** over bespoke code when one
  exists for the job: Bean Validation (`jakarta.validation`) for input
  validation, MapStruct for entity/DTO mapping, springdoc-openapi for OpenAPI
  generation, Zalando Problem (or equivalent) for RFC 7807, Spring Data for
  pagination. Hand-rolled equivalents require justification in the plan's
  Complexity Tracking section.

**Rationale**: Consistency is the only thing that makes a REST API
discoverable. Once a client has learned one endpoint, they should be able to
predict the rest. Inconsistent status codes and error shapes turn every
endpoint into a separate learning task for callers.

## Technology Standards

- **Runtime**: Java 21+ on Spring Boot 3.x (per `pom.xml`).
- **Build**: Maven, single `pom.xml` at the project root, Maven wrapper
  (`mvnw`) used in all documentation.
- **Persistence**: PostgreSQL via Spring Data JPA in production; H2 acceptable
  for tests that genuinely need a SQL backend (rare — most unit tests are
  pure JVM per Principle II).
- **Testing libraries**: JUnit 5 (`junit-jupiter`), Mockito, AssertJ. Spring
  context is permitted ONLY for narrowly-scoped integration tests; the
  default test is pure JVM.
- **API tests**: Postman collections under `postman/`, executable via Newman
  in CI.
- **Code generation / boilerplate**: Lombok and MapStruct are acceptable where
  they reduce noise without hiding intent. Avoid niche, unmaintained, or
  single-author libraries.

## Development Workflow

Every pull request MUST:

1. Pass `./mvnw verify` (compile, unit tests). Newman-based Postman runs
   execute against any new or changed endpoints.
2. Include both a unit test and a Postman test for any new branching logic,
   state transition, concurrent code path, or non-trivial edge case
   (Principle II).
3. Update `README.md` if endpoints were added, removed, or changed
   (Principle III).
4. Update `run.md` if startup, build, or test invocation changed
   (Principle III).
5. Add a `prompts.md` entry if AI assistance materially shaped the change
   (Principle III).
6. Include a short "Constitution Check" in the PR description either
   attesting compliance or listing accepted deviations with justification.

Code review:

- Reviewers MUST cite the violated principle (e.g., "Principle I: flag
  argument") when requesting changes. Vague review comments are themselves
  rejected.
- Any deviation from a principle MUST be justified in the plan's Complexity
  Tracking section before review begins.

## Governance

This constitution supersedes all other practice documents in this repository.
When a doc conflicts with the constitution, the constitution wins until the
constitution is amended.

**Amendment procedure**: Open a PR modifying `.specify/memory/constitution.md`.
The PR MUST update the version line, the last-amended date, the Sync Impact
Report at the top of this file, and any dependent templates flagged in that
report.

**Versioning policy** (semantic):

- **MAJOR**: backward-incompatible removal or redefinition of a principle, or
  removal of a Governance rule.
- **MINOR**: a new principle or section is added, or an existing principle is
  materially expanded.
- **PATCH**: clarifications, wording, typos, or non-semantic refinements.

If a version bump type is ambiguous, the PR author MUST state the reasoning in
the PR description.

**Compliance review**: Every PR description includes the "Constitution Check"
line item above. The `/speckit-plan` and `/speckit-tasks` commands MUST also
re-validate against this file (see plan-template Constitution Check gates).

Runtime development guidance lives in `CLAUDE.md` and `run.md`; both MUST stay
aligned with this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-05-20 | **Last Amended**: 2026-05-20
