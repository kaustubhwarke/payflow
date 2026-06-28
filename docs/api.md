# PayFlow — API Reference

> REST contract for every endpoint: method, path, required role, request/response
> schema, status codes, and runnable `curl` examples (including how to obtain a
> Keycloak token).

- **Base path:** `/api/v1`
- **Auth:** every endpoint requires a valid **Keycloak-issued JWT** (Bearer). There
  are **no anonymous endpoints** in the API itself; only Actuator health/info/
  prometheus and the Swagger/OpenAPI docs are public.
- **Content type:** `application/json`. Errors are RFC 7807
  `application/problem+json` with `errorCode`, `traceId`, `timestamp`.
- **Interactive docs (Swagger UI):** `http://localhost:8080/swagger-ui.html`
  (OpenAPI JSON at `/v3/api-docs`). Use the **Authorize** button to paste a bearer
  token and exercise secured endpoints.

## Obtaining a Keycloak access token (password grant)

The realm `payflow` ships a public client `payflow-public` with direct access grants
enabled, plus two seeded users:

| Username | Password | Realm roles |
| --- | --- | --- |
| `alice` | `alice123` | `USER` |
| `admin` | `admin123` | `USER`, `ADMIN` |

Keycloak runs on `http://localhost:8081` (mapped from container `8080`).

```bash
# Get a token for alice (USER)
export TOKEN=$(curl -s \
  -X POST 'http://localhost:8081/realms/payflow/protocol/openid-connect/token' \
  -d 'grant_type=password' \
  -d 'client_id=payflow-public' \
  -d 'username=alice' \
  -d 'password=alice123' \
  | jq -r '.access_token')

# For admin endpoints, swap username/password to admin / admin123
export ADMIN_TOKEN=$(curl -s \
  -X POST 'http://localhost:8081/realms/payflow/protocol/openid-connect/token' \
  -d 'grant_type=password' \
  -d 'client_id=payflow-public' \
  -d 'username=admin' \
  -d 'password=admin123' \
  | jq -r '.access_token')
```

All examples below assume `$TOKEN` / `$ADMIN_TOKEN` are set as above and the app is on
`http://localhost:8080`.

---

## Users API — `/api/v1/users`

### POST `/api/v1/users` — Register a user

- **Role:** `USER` or `ADMIN`
- **Request body** (`CreateUserRequest`):

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | required, ≤ 120 chars |
| `upiId` | string | required, ≤ 80, pattern `handle@provider` |
| `phoneNumber` | string | required, 10-digit Indian mobile (`^[6-9]\d{9}$`) |
| `openingBalance` | number | required, ≥ 0, ≤ 2 decimals |

- **Response 201** (`UserResponse`) + `Location: /api/v1/users/{referenceId}`
- **Status:** `201` created · `400` validation · `409` duplicate UPI ID · `401`/`429`

```bash
curl -s -X POST 'http://localhost:8080/api/v1/users' \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Priya Sharma",
    "upiId": "priya@okaxis",
    "phoneNumber": "9876543210",
    "openingBalance": 5000.00
  }'
```

Response:

```json
{
  "referenceId": "USR_01HZX3K8N2Q7R5...",
  "name": "Priya Sharma",
  "upiId": "priya@okaxis",
  "phoneNumber": "9876543210",
  "balance": 5000.00,
  "currency": "INR",
  "createdAt": "2026-05-31T10:15:30Z"
}
```

### GET `/api/v1/users` — List all users (paginated)

- **Role:** `ADMIN`
- **Query:** standard Spring `Pageable` (`page`, `size` [default 20], `sort`)
- **Response 200:** `PagedResponse<UserResponse>`

```bash
curl -s 'http://localhost:8080/api/v1/users?page=0&size=20' \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### GET `/api/v1/users/{referenceId}` — Get a user by reference

- **Role:** `USER` or `ADMIN`
- **Status:** `200` · `404` not found

```bash
curl -s 'http://localhost:8080/api/v1/users/USR_01HZX3K8N2Q7R5' \
  -H "Authorization: Bearer $TOKEN"
```

### GET `/api/v1/users/upi/{upiId}` — Look up a user by UPI ID

- **Role:** `USER` or `ADMIN` · Backs the pre-transfer payee lookup; result is cached.
- **Status:** `200` · `404` not found

```bash
curl -s 'http://localhost:8080/api/v1/users/upi/priya@okaxis' \
  -H "Authorization: Bearer $TOKEN"
```

### GET `/api/v1/users/search/by-balance` — Users above a balance (paginated)

- **Role:** `ADMIN` · Demonstrates the custom JPQL `@Query`.
- **Query:** `threshold` (required number) + `Pageable`
- **Response 200:** `PagedResponse<UserResponse>`

```bash
curl -s 'http://localhost:8080/api/v1/users/search/by-balance?threshold=1000.00&size=20' \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## Transactions API — `/api/v1/transactions`

### POST `/api/v1/transactions` — Send money

- **Role:** `USER` or `ADMIN`
- **Request body** (`SendMoneyRequest`):

| Field | Type | Rules |
| --- | --- | --- |
| `senderUpiId` | string | required, pattern `handle@provider` |
| `receiverUpiId` | string | required, pattern `handle@provider` |
| `amount` | number | required, ≥ 0.01, ≤ 2 decimals |
| `note` | string | optional, ≤ 255 chars |

- **Response 201** (`TransactionResponse`) + `Location: /api/v1/transactions/{referenceId}`
- **Status:** `201` recorded · `400` invalid transfer (e.g. sender == receiver) ·
  `404` sender/receiver not found · `422` insufficient balance ·
  `409` concurrency conflict (retry) · `401`/`429`

```bash
curl -s -X POST 'http://localhost:8080/api/v1/transactions' \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "senderUpiId": "priya@okaxis",
    "receiverUpiId": "rohan@oksbi",
    "amount": 250.00,
    "note": "dinner split"
  }'
```

Response:

```json
{
  "referenceId": "TXN_01HZX3K8N2Q7R5...",
  "senderUpiId": "priya@okaxis",
  "receiverUpiId": "rohan@oksbi",
  "amount": 250.00,
  "currency": "INR",
  "status": "COMPLETED",
  "note": "dinner split",
  "failureReason": null,
  "createdAt": "2026-05-31T10:15:30Z"
}
```

### GET `/api/v1/transactions/{referenceId}` — Get a transaction

- **Role:** `USER` or `ADMIN`
- **Status:** `200` · `404` not found

```bash
curl -s 'http://localhost:8080/api/v1/transactions/TXN_01HZX3K8N2Q7R5' \
  -H "Authorization: Bearer $TOKEN"
```

### GET `/api/v1/transactions?upiId=...` — Transaction history (paginated)

- **Role:** `USER` or `ADMIN`
- **Query:** `upiId` (required) + `Pageable`; **default sort `createdAt,DESC`**
  (newest first). Returns transfers where the party is sender **or** receiver.
- **Response 200:** `PagedResponse<TransactionResponse>`

```bash
curl -s 'http://localhost:8080/api/v1/transactions?upiId=priya@okaxis&page=0&size=20' \
  -H "Authorization: Bearer $TOKEN"
```

---

## Cross-cutting response behaviour

- **Tracing:** every response carries `X-Trace-Id` (32 hex) and `X-Span-Id` (16 hex).
  Send your own `X-Trace-Id` or W3C `traceparent` to propagate a trace.
- **Rate limiting:** successful `/api/**` responses include `X-Rate-Limit-Remaining`.
  Exceeding the quota returns `429 Too Many Requests` with a `Retry-After` header and
  a problem body (`errorCode: RATE_LIMIT_EXCEEDED`). Default quota: 100 requests / 60s
  per authenticated subject (or client IP if unauthenticated).
- **Error shape** (RFC 7807):

```json
{
  "type": "https://docs.payflow.com/errors/user_not_found",
  "title": "Not Found",
  "status": 404,
  "detail": "No user found for identifier 'priya@okaxis'",
  "instance": "/api/v1/users/upi/priya@okaxis",
  "errorCode": "USER_NOT_FOUND",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2026-05-31T10:15:30.123Z"
}
```

## Endpoint summary

| Method | Path | Role | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | USER/ADMIN | 201 |
| GET | `/api/v1/users` | ADMIN | 200 |
| GET | `/api/v1/users/{referenceId}` | USER/ADMIN | 200 |
| GET | `/api/v1/users/upi/{upiId}` | USER/ADMIN | 200 |
| GET | `/api/v1/users/search/by-balance` | ADMIN | 200 |
| POST | `/api/v1/transactions` | USER/ADMIN | 201 |
| GET | `/api/v1/transactions/{referenceId}` | USER/ADMIN | 200 |
| GET | `/api/v1/transactions?upiId=...` | USER/ADMIN | 200 |
