# API Guide

The interactive OpenAPI UI is available at `http://localhost:8080/swagger-ui.html`. All reconciliation routes require HTTP Basic credentials; `/actuator/health` and the OpenAPI description are public.

Examples use the default local credentials. Use HTTPS before sending Basic credentials outside localhost.

## Create a run

```bash
curl -i -u analyst:change-me-now \
  -F "file=@backend/src/main/resources/demo/gateway-transactions.csv" \
  http://localhost:8080/reconciliations
```

New content returns `202 Accepted`, a `Location` header, and:

```json
{
  "reconciliation": {
    "id": "a UUID",
    "fileName": "gateway-transactions.csv",
    "status": "PENDING",
    "totalCount": 0
  },
  "idempotentReplay": false
}
```

Uploading identical bytes again for the same user returns `200 OK`, the original run, and `idempotentReplay: true`.

## List runs

```http
GET /reconciliations?page=0&size=20
```

Results are newest first and use a stable page envelope with `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`.

## Get one run

```http
GET /reconciliations/{id}
```

Returns lifecycle timestamps, failure details if present, and persisted counts. A nonexistent or non-owned run returns `404`.

## Get a summary

```http
GET /reconciliations/{id}/summary
```

The summary includes `status`, `totalCount`, `matchedCount`, `amountMismatchCount`, `missingInLedgerCount`, `invalidCount` and `duplicateCount`.

## Get discrepancies

```http
GET /reconciliations/{id}/discrepancies?page=0&size=20
```

Returns all non-matched rows (`AMOUNT_MISMATCH`, `MISSING_IN_LEDGER`, `INVALID`, `DUPLICATE`) in original CSV line order. Each item contains both gateway and ledger amounts where available, status, and a human-readable reason.

## Error shape

Validation and application errors use `application/problem+json` fields:

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "CSV header must be: transaction_id,account_number,amount,transaction_date",
  "instance": "/reconciliations",
  "timestamp": "2026-08-07T00:00:00Z"
}
```

## Status codes

| Status | Meaning |
|---|---|
| `200` | Successful read or idempotent replay |
| `202` | New file accepted for asynchronous processing |
| `400` | Invalid file/request parameters |
| `401` | Missing or invalid credentials |
| `404` | Run absent or not owned by the caller |
| `409` | Database constraint conflict (for example two identical uploads at the same instant) |
| `413` | Upload exceeds the configured limit |
| `500` | Unexpected server failure with internal details withheld |
