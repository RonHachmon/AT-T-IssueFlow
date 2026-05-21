# Running IssueFlow

Fresh-clone runbook. If any step here fails on a clean machine, the project
skeleton is broken — open an issue rather than working around it.

## 1. Prerequisites

| Tool             | Required version    | Verify                              |
|------------------|---------------------|-------------------------------------|
| JDK              | **21** (LTS)         | `java -version` reports `21.x`      |
| Docker + Compose | recent              | `docker compose version` succeeds   |
| Free ports       | `8080`, `5432`, `8081` | nothing else listening              |

On Windows: use PowerShell or Git Bash. The Maven wrapper (`mvnw.cmd`) is
checked in; you do not need a local Maven install.

## 2. Start PostgreSQL

```bash
docker compose up -d
```

Verify the database is healthy:

```bash
docker compose ps
```

You should see two services: `db` (Postgres) and `adminer` (web UI at
`http://localhost:8081`).

## 3. Build

```bash
./mvnw clean verify          # macOS/Linux
.\mvnw.cmd clean verify      # Windows
```

This runs Google Java Format check (Spotless), compiles, and executes the
JUnit 5 suite. Exit code must be `0`.

## 4. Run

```bash
./mvnw spring-boot:run
.\mvnw.cmd spring-boot:run   # Windows
```

Look for these log lines (order matters):

```text
HikariPool-1 - Start completed.
Flyway ... Successfully validated 1 migration
Tomcat started on port(s): 8080
Started IssueFlowApplication
```

If you see `Connection refused: localhost:5432`, the database container
isn't running — repeat step 2.

## 5. Hit the health endpoint

```bash
curl -s -i http://localhost:8080/health
```

Expected — `200 OK` with `application/json` body:

```json
{
  "status": "UP",
  "components": {
    "application": { "status": "UP", "detail": "" },
    "db":          { "status": "UP", "detail": "" }
  },
  "timestamp": "2026-05-20T10:42:13.512Z"
}
```

## 6. Confirm the unhealthy path

```bash
docker compose stop db
curl -s -i http://localhost:8080/health
docker compose start db
```

The middle curl returns `503 Service Unavailable` with the `db` component
reporting `"status": "DOWN"`.

## 7. Run the Postman collection (optional)

```bash
npx newman run postman/issueflow.postman_collection.json \
  -e postman/issueflow.local.postman_environment.json
```

Newman exits zero with the Health requests passing.

## 8. Run just the tests

```bash
./mvnw test          # all tests
./mvnw test -Dtest=GlobalExceptionHandlerTest   # one class
```

## Configuration overrides

The defaults in `application.yaml` are env-var overridable — no source
edits needed to retarget the database:

| Variable                     | Default                                         |
|------------------------------|-------------------------------------------------|
| `SERVER_PORT`                | `8080`                                          |
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/issueflow`    |
| `SPRING_DATASOURCE_USERNAME` | `issueflow`                                     |
| `SPRING_DATASOURCE_PASSWORD` | `issueflow`                                     |

## JWT Authentication

### Required environment variable

| Variable                  | Required | Description                                          |
|---------------------------|----------|------------------------------------------------------|
| `APP_SECURITY_JWT_SECRET` | **Yes**  | HS256 signing key — minimum 32 characters            |

Generate a secure secret:

```bash
openssl rand -hex 32
```

Set the variable before starting the app:

```bash
# macOS/Linux
export APP_SECURITY_JWT_SECRET=$(openssl rand -hex 32)
./mvnw spring-boot:run

# Windows PowerShell
$env:APP_SECURITY_JWT_SECRET = (openssl rand -hex 32)
.\mvnw.cmd spring-boot:run
```

The default fallback (`change-me-in-production-use-32-plus-chars`) is intentionally weak and must
not be used outside local development.

### Token lifetime

Tokens expire after **12 minutes** by default. Override with:

```yaml
app.security.jwt.access-token-ttl: PT30M   # 30 minutes
```

### Seed users (development only)

The Flyway migration `V3__seed_test_users.sql` inserts two users on first startup:

| Username    | Password     | Role        |
|-------------|--------------|-------------|
| `admin`     | `Admin1234!` | `ADMIN`     |
| `developer` | `Dev1234!`   | `DEVELOPER` |

These users are created with `ON CONFLICT DO NOTHING` — safe to run against an existing database.

## Troubleshooting

| Symptom                                              | Likely cause                                              | Fix                                                                |
|------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------------------|
| `Connection refused: localhost:5432` at startup      | Database container not running                            | `docker compose up -d` and wait ~5s before starting the app.       |
| `Unsupported class file major version` during build  | Java version older than 21                                | Install JDK 21; set `JAVA_HOME`; restart the shell.                |
| `Port 8080 is already in use`                        | Another process is bound                                  | Stop the process, or set `SERVER_PORT=8090` and retry.             |
| `FlywayException: Validate failed`                   | Hand-edited migration script after it was already applied | Drop the `issueflow` database and re-run `docker compose up`.       |
| Spotless `Format check failed`                       | New code not in Google Java Format                        | Run `./mvnw spotless:apply`, then rebuild.                          |
