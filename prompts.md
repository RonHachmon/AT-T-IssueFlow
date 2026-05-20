# AI Prompts Log

Honest record of how AI assisted this codebase. Constitution Principle III
requires this file to name the model and the key prompts that shaped each
slice of work.

## Model

- **Claude Opus 4.7** (`claude-opus-4-7`) via Claude Code, run inside the
  VS Code extension on Windows 11.

## Feature 001 — Project Skeleton

Driven by the Spec Kit `/speckit-*` pipeline. Each invocation is a single
prompt; the model's output is captured in `.specify/memory/constitution.md`,
`specs/001-project-skeleton/spec.md`, `…/plan.md`, and `…/tasks.md`.

### `/speckit-constitution`

> Principle 1 — Clean Code. Follow Uncle Bob's Clean Code: names reveal
> intent, functions are short and do one thing, one level of abstraction
> per function, no flag arguments, no dead or commented-out code, no
> magic numbers or strings, and comments explain why not what.
>
> Principle 2 — Testing Standards. Every feature ships with both
> code-level unit tests (JUnit 5 + Mockito, no Spring context) and Postman
> API tests. At least one or two simple sanity tests are mandatory; complex
> tests are mandatory wherever branching logic, state transitions,
> concurrency, or edge cases exist. Coverage target is small-to-medium —
> critical paths and tricky logic only, not trivial getters or mappers.
> Tests are named like sentences and follow Arrange-Act-Assert.
>
> Principle 3 — Documentation Discipline. Document public methods only with
> Javadoc covering purpose, parameters, return, and exceptions; private
> methods rely on clean naming. Every API endpoint is documented in the
> README, run.md always works from a fresh clone, and prompts.md honestly
> records AI model and key prompts.
>
> Principle 4 — API Consistency. Follow REST conventions strictly: correct
> verbs, correct status codes (201/204/404/409/422), plural nouns, RFC 7807
> Problem Details for errors with informative messages, camelCase JSON and
> snake_case SQL, consistent field naming (id, <entity>Id,
> createdAt/updatedAt), DTOs never expose entities, and pagination uses the
> same shape everywhere. also use popular libraries where seems necessary

Produced `.specify/memory/constitution.md` v1.0.0 ratified 2026-05-20.

### `/speckit-specify`

> cover project so it compiles, connects to a database, and runs. Nothing
> useful happens yet, but the foundation is in place: a running server, a
> running database, and the plumbing between them. Think of it as building
> the empty house — walls, plumbing, electricity — before any furniture
> goes in.

Produced `specs/001-project-skeleton/spec.md` with 3 user stories (P1/P1/P2),
10 functional requirements, 6 measurable success criteria, and explicit
Out-of-Scope and Assumptions sections. No `[NEEDS CLARIFICATION]` markers
remained.

### `/speckit-plan`

> Spring Boot 3.4 + Java 21 project initialized via Spring Initializr or the
> provided skeleton, built with Maven (mvnw). Core dependencies:
> spring-boot-starter-web, spring-boot-starter-data-jpa,
> spring-boot-starter-validation, postgresql driver, lombok, and
> spring-boot-starter-test with JUnit 5 and Mockito. PostgreSQL via the
> provided compose.yml. Use Flyway (flyway-core + flyway-database-postgresql)
> for schema migrations rather than ddl-auto=update — migrations are
> reviewable and version-controlled. Configure application.yml with
> datasource properties and Hibernate dialect. Add a global exception
> handler scaffold (@RestControllerAdvice) returning RFC 7807 ProblemDetail
> responses, ready to be extended in later phases. Project structure:
> package-by-feature (user, project, ticket, comment, auth, common) rather
> than package-by-layer.

Produced `plan.md`, `research.md` (8 decisions, including Spring Actuator
for health, Flyway over `ddl-auto`, Spring's built-in `ProblemDetail`),
`data-model.md`, two contract files under `contracts/`, and `quickstart.md`.

### `/speckit-tasks`

(no prompt argument)

Produced `tasks.md` — 23 tasks across 6 phases, organized by user story
with explicit `[P]` parallel markers and exact file paths.

### `/speckit-implement`

(no prompt argument)

Executed the 23 tasks. Notable manual corrections during execution:

- The actual Spring Boot class is `IssueFlowApplication` (capital `F`), not
  `IssueflowApplication` as the plan assumed. The `ApplicationStartupTest`
  uses the real class name.
- `pom.xml` had a Spotless / Google Java Format plugin added between
  `/speckit-plan` and `/speckit-implement`. Java sources were run through
  `./mvnw spotless:apply` after writing to satisfy the format check.
- The existing `postman/` collection had placeholder Tickets requests
  pre-authored; T018 only replaced the Health folder, leaving Tickets in
  place for future features.
