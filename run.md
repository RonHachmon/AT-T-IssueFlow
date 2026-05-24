# Running IssueFlow

## Prerequisites

- JDK 21
- Docker + Docker Compose
- Ports `8080`, `5432`, `8081` free

Verify:

```bash
java -version            # 21.x
docker compose version
```

Maven is bundled — use `./mvnw` (Linux/macOS) or `.\mvnw.cmd` (Windows).

---

## 1. Install dependencies

Optional pre-warm of the Maven cache:

```bash
./mvnw dependency:go-offline
```

Otherwise dependencies download on the first build.

---

## 2. Start the database

```bash
docker compose up -d
docker compose ps
```

| Service   | Port | Notes                              |
|-----------|------|------------------------------------|
| `db`      | 5432 | Postgres — user/pass/db `issueflow` |
| `adminer` | 8081 | Web UI at <http://localhost:8081>  |

Stop: `docker compose down`.

---

## 3. Build

```bash
./mvnw clean verify
```

Runs Spotless + compile + unit tests. Exit code must be `0`.

Produce a runnable jar:

```bash
./mvnw package
```

Auto-fix formatting: `./mvnw spotless:apply`.

---

## 4. Run

```bash
./mvnw spring-boot:run
```

Startup log should include:

```text
HikariPool-1 - Start completed.
Flyway ... Successfully validated N migrations
Tomcat started on port(s): 8080
Started IssueFlowApplication
```

Smoke test:

```bash
curl -s -i http://localhost:8080/health
```

Expected: `200 OK` with `{"status":"UP", ...}`.

---

## 5. Run the tests

Unit tests (no DB needed):

```bash
./mvnw test
./mvnw test -Dtest=GlobalExceptionHandlerTest         # one class
./mvnw test -Dtest=CommentServiceTest#methodName      # one method
```

Postman / Newman (app must be running):

```bash
npx newman run postman/issueflow.postman_collection.json \
  -e postman/issueflow.local.postman_environment.json
```

---

## JWT secret

Required env var, ≥32 chars:

```bash
# Linux/macOS
export APP_SECURITY_JWT_SECRET=$(openssl rand -hex 32)

# Windows PowerShell
$env:APP_SECURITY_JWT_SECRET = (openssl rand -hex 32)
```

Default `change-me-in-production-use-32-plus-chars` is for local dev only. Token TTL is 12 min; override with `app.security.jwt.access-token-ttl=PT30M`.

---

## Seed users

| Username    | Password     | Role        |
|-------------|--------------|-------------|
| `admin`     | `Admin1234!` | `ADMIN`     |
| `developer` | `Dev1234!`   | `DEVELOPER` |

Inserted by `V3__seed_test_users.sql` with `ON CONFLICT DO NOTHING`.

---

## Configuration overrides

| Variable                     | Default                                         |
|------------------------------|-------------------------------------------------|
| `SERVER_PORT`                | `8080`                                          |
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/issueflow`    |
| `SPRING_DATASOURCE_USERNAME` | `issueflow`                                     |
| `SPRING_DATASOURCE_PASSWORD` | `issueflow`                                     |

---

## Troubleshooting

| Symptom                                             | Fix                                                       |
|-----------------------------------------------------|-----------------------------------------------------------|
| `Connection refused: localhost:5432`                | `docker compose up -d`, wait ~5s.                         |
| `Unsupported class file major version`              | Install JDK 21, set `JAVA_HOME`, restart the shell.       |
| `Port 8080 is already in use`                       | Free the port or set `SERVER_PORT=8090`.                  |
| `FlywayException: Validate failed`                  | Drop the database and re-run `docker compose up`.         |
| `Format check failed` (Spotless)                    | `./mvnw spotless:apply` and rebuild.                      |
