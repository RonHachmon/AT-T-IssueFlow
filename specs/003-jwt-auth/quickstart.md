# Quickstart: JWT Authentication (003-jwt-auth)

## Prerequisites

- PostgreSQL running (via `docker compose up -d db` or equivalent)
- Java 21 and Maven wrapper available

## Environment Setup

The JWT secret **must** be set before starting the application. For local development, set the environment variable:

```bash
export APP_SECURITY_JWT_SECRET="local-dev-secret-at-least-32-chars-long"
```

For production, use a secret of at least 256 bits (32 bytes) generated with a CSPRNG:

```bash
openssl rand -hex 32
```

## Build and Run

```bash
./mvnw clean package -DskipTests
java -Dapp.security.jwt.secret=local-dev-secret-at-least-32-chars-long \
     -jar target/issueflow-*.jar
```

Or with the Maven wrapper:

```bash
APP_SECURITY_JWT_SECRET=local-dev-secret-at-least-32-chars-long ./mvnw spring-boot:run
```

## Seed Users (created by V3 migration)

| Username    | Password     | Role      |
|-------------|--------------|-----------|
| `admin`     | `Admin1234!` | ADMIN     |
| `developer` | `Dev1234!`   | DEVELOPER |

## Obtain a Token

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin1234!"}' | jq .
```

Expected response:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 720
}
```

## Call a Protected Endpoint

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin1234!"}' | jq -r .accessToken)

curl -s http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq .

curl -s http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN" | jq .
```

## Run Tests

```bash
./mvnw test
```

## Run Postman Tests

```bash
npx newman run postman/issueflow.postman_collection.json \
  --environment postman/issueflow.local.postman_environment.json
```

## Token TTL Configuration

Default TTL is 12 minutes. To change it (ISO-8601 duration):

```yaml
# application.yaml
app:
  security:
    jwt:
      access-token-ttl: PT30M   # 30 minutes
```
