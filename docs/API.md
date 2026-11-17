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

Uploading identical bytes again for the same user returns `200 OK`, the original run, and `idempotentReplay: true`, as long as the ledger hasn't changed since (no new ledger import) and the original run did not fail. Otherwise the file is processed again as a new run (`202`). While a ledger import is running, uploads are refused with `409`.

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

The summary includes `status`, `totalCount`, `matchedCount`, `amountMismatchCount`, `accountMismatchCount`, `dateMismatchCount`, `missingInLedgerCount`, `missingInGatewayCount`, `invalidCount` and `duplicateCount`. `totalCount` is the number of CSV lines; ledger-only `MISSING_IN_GATEWAY` rows are not part of it.

## Get discrepancies

```http
GET /reconciliations/{id}/discrepancies?page=0&size=20
```

Returns all non-matched rows (`AMOUNT_MISMATCH`, `ACCOUNT_MISMATCH`, `DATE_MISMATCH`, `MISSING_IN_LEDGER`, `MISSING_IN_GATEWAY`, `INVALID`, `DUPLICATE`) in original CSV line order, followed by the ledger-only `MISSING_IN_GATEWAY` rows (their `lineNumber` is `null`). Each item has both sides where available (`transactionId`, `accountNumber`, `gatewayAmount`, `transactionDate` from the file; `ledgerTransactionId`, `ledgerAccountNumber`, `ledgerAmount`, `ledgerTransactionDate` from the ledger), the status, and a human-readable reason that lists every difference.

## Download discrepancies as CSV

```bash
curl -u analyst:change-me-now -OJ \
  http://localhost:8080/reconciliations/{id}/discrepancies/export
```

Streams every non-matched row with the columns `line_number,status,gateway_transaction_id,gateway_account_number,gateway_amount,gateway_date,ledger_transaction_id,ledger_account_number,ledger_amount,ledger_date,reason`. Cells starting with `=`, `+`, `-` or `@` get a leading apostrophe so spreadsheets don't run them as formulas.

## Import a ledger

```bash
curl -i -u analyst:change-me-now \
  -F "file=@backend/src/main/resources/demo/ledger-update.csv" \
  http://localhost:8080/ledger-imports
```

The file needs the header `transaction_id,account_number,amount,transaction_date,description` (description may be empty). Returns `202 Accepted` with the import (its `id` is the new ledger version). New transaction IDs are added and existing ones are updated. Refused with `409` while any reconciliation or ledger import is running.

```http
GET /ledger-imports?page=0&size=20
GET /ledger-imports/{id}
GET /ledger-imports/{id}/errors?page=0&size=20
```

An import has `status`, `totalCount`, `importedCount` and `invalidCount`. The errors endpoint lists each rejected line with `lineNumber`, `transactionId` and `reason`, in file order.

## Suggested pairs

```http
GET  /reconciliations/{id}/suggestions?page=0&size=20
POST /reconciliations/{id}/suggestions/{suggestionId}/confirm
POST /reconciliations/{id}/suggestions/{suggestionId}/reject     body (optional): {"note": "Different customer"}
```

Each suggestion has both rows (`gatewayLineNumber`, `gatewayTransactionId`, `gatewayAccountNumber`, `gatewayAmount`, `gatewayDate`, `ledgerTransactionId`, `ledgerAccountNumber`, `ledgerAmount`, `ledgerDate`), `idDistance`, `ambiguous`, `status` (`PENDING`, `CONFIRMED`, `REJECTED`), `decidedBy`, `decidedAt` and `decisionNote`. Deciding an already decided suggestion returns `409`. Confirming closes other open suggestions that share a row with it. The run's `suggestedPairCount` says how many were created.

## Gateway formats

Suggest a mapping for a sample file (nothing is saved):

```bash
curl -u analyst:change-me-now \
  -F "file=@backend/src/main/resources/demo/gateway-settlement-report.csv" \
  http://localhost:8080/gateway-formats/suggest
```

The answer has the file's `headers`, the suggested `transactionIdColumn`, `accountNumberColumn`, `amountColumn`, `transactionDateColumn` (null when not found), `dateFormat` and `dateFormatCandidates`, `allDateFormats`, `aiEnabled`, `aiUsed`, `suggestedBy` (`GEMINI` or `HEADER_RULES`), `warnings`, and `maskedSampleRows`: exactly what an AI service would see.

Save a reviewed mapping:

```http
POST /gateway-formats
Content-Type: application/json

{"name": "Settlement report v1",
 "headers": ["Txn Ref", "Customer Email", "Acct No", "Amount (INR)", "Settlement Dt", "Status"],
 "transactionIdColumn": "Txn Ref", "accountNumberColumn": "Acct No",
 "amountColumn": "Amount (INR)", "transactionDateColumn": "Settlement Dt",
 "dateFormat": "dd/MM/yyyy", "suggestedBy": "HEADER_RULES"}
```

`201` with the format; `409` if the name exists (case-insensitive; formats can't be edited, use a new name); `400` if a column is not one of `headers`, a column is used twice, or `dateFormat` is not on the allow-list. `GET /gateway-formats` lists them.

Upload a file in a saved format with `POST /reconciliations?formatId={id}`. Its header must match the format's headers (same order, case ignored), otherwise `400`. An unknown `formatId` is `404`.

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
| `202` | New gateway or ledger file accepted for asynchronous processing |
| `400` | Invalid file/request parameters |
| `401` | Missing or invalid credentials |
| `404` | Run absent or not owned by the caller |
| `409` | Another job is running (ledger import vs reconciliation), or a database constraint conflict (for example two identical uploads at the same instant) |
| `413` | Upload exceeds the configured limit |
| `500` | Unexpected server failure with internal details withheld |
