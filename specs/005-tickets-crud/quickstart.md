# Quickstart: Tickets Feature Smoke Test

Assumes the app is running locally on port 8080, a PostgreSQL DB is up (via `compose.yml`), and you have completed the migration (`V6__tickets.sql` applied by Flyway on startup).

---

## 1. Authenticate

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret"}' | jq -r .accessToken)
```

---

## 2. Create a Ticket

```bash
curl -s -X POST http://localhost:8080/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Fix login bug",
    "description": "Users cannot log in with SSO",
    "priority": "HIGH",
    "type": "BUG",
    "projectId": 1
  }'
# Expected: 200 OK, body with id, status="TODO", isOverdue=false
```

---

## 3. List Tickets for a Project

```bash
curl -s "http://localhost:8080/tickets?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK, JSON array containing the ticket created above
```

---

## 4. Get Ticket by ID

```bash
TICKET_ID=<id from step 2>
curl -s "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK, full ticket object
```

---

## 5. Advance Status: TODO → IN_PROGRESS

```bash
curl -s -X PATCH "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'
# Expected: 200 OK, empty body
```

---

## 6. Reject Backward Transition

```bash
curl -s -X PATCH "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "TODO"}'
# Expected: 409 Conflict, problem+json with descriptive detail
```

---

## 7. Advance to DONE

```bash
curl -s -X PATCH "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_REVIEW"}'
# Expected: 200 OK

curl -s -X PATCH "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "DONE"}'
# Expected: 200 OK
```

---

## 8. Reject Update on DONE Ticket

```bash
curl -s -X PATCH "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"priority": "LOW"}'
# Expected: 409 Conflict
```

---

## 9. Soft-Delete a Ticket

```bash
curl -s -X DELETE "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK, empty body

curl -s "http://localhost:8080/tickets/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 404 Not Found

curl -s "http://localhost:8080/tickets?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK, deleted ticket absent from array
```

---

## 10. No Auth

```bash
curl -s "http://localhost:8080/tickets?projectId=1"
# Expected: 401 Unauthorized
```
