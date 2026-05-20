# Quickstart — Project Skeleton

The five-minute version of `run.md`. If anything below fails, the
project-skeleton feature is not done.

## Prerequisites

- Java **21** (`java -version` reports `21.x.x`)
- Docker / Docker Compose (`docker compose version` succeeds)
- A free port `8080` (HTTP) and `5432` (PostgreSQL), `8081` (Adminer)

## Steps

1. **Clone and enter the repo**

   ```bash
   git clone <repo-url> issueflow-java
   cd issueflow-java
   ```

2. **Start the database**

   ```bash
   docker compose up -d
   ```

   Confirm:

   ```bash
   docker compose ps
   ```

   The `db` service is `running` and healthy.

3. **Build the application**

   ```bash
   ./mvnw clean verify
   ```

   - Exits zero.
   - Runs the unit tests (`HealthControllerTest`,
     `GlobalExceptionHandlerTest`) — all pass.
   - Runs the single Spring-context smoke test
     (`ApplicationStartupTest`) — passes.

4. **Run the application**

   ```bash
   ./mvnw spring-boot:run
   ```

   Look for log lines:

   ```text
   HikariPool-1 - Start completed.
   Flyway ... Successfully validated 1 migration
   Tomcat started on port(s): 8080
   Started IssueflowApplication
   ```

5. **Hit the health endpoint**

   ```bash
   curl -s -i http://localhost:8080/health
   ```

   Expected:

   ```text
   HTTP/1.1 200
   Content-Type: application/json
   ...
   {"status":"UP","components":{"application":{"status":"UP",...},"db":{"status":"UP",...}},"timestamp":"..."}
   ```

6. **(Optional) Run the Postman collection**

   ```bash
   npx newman run postman/issueflow.postman_collection.json
   ```

   All requests pass.

7. **Confirm the unhealthy path**

   ```bash
   docker compose stop db
   curl -s -i http://localhost:8080/health
   docker compose start db
   ```

   Expected: `HTTP/1.1 503` with the `db` component reporting `DOWN`.

## When this is "done"

- `./mvnw clean verify` exits zero on a fresh clone with only the
  prerequisites installed.
- `curl http://localhost:8080/health` returns `200 OK` with both
  components `UP`.
- Stopping the database and re-querying returns a `503` with the `db`
  component clearly marked `DOWN`.
- `run.md` matches this list line-for-line.

## Troubleshooting

| Symptom                                              | Likely cause                                              | Fix                                                                |
|------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------|
| `Connection refused: localhost:5432` at startup      | Database container not running                            | `docker compose up -d` and wait ~5s before starting the app.       |
| `Unsupported class file major version` during build  | Java version older than 21                                | Install JDK 21; on Windows set `JAVA_HOME` and restart the shell.  |
| `Port 8080 is already in use`                        | Another process is bound                                  | Stop the conflicting process, or set `SERVER_PORT=8090` and retry. |
| Flyway `FlywayException: Validate failed`            | Hand-edited migration script after it was already applied | Drop the `issueflow` database and re-run `docker compose up`.       |
| `application.yaml` reports `mssql`                   | Stale template file                                       | Pull latest; this file is rewritten by the project-skeleton feature.|
