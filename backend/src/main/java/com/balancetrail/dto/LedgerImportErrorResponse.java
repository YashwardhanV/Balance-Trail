package com.balancetrail.dto;

public record LedgerImportErrorResponse(long lineNumber, String transactionId, String reason) {}
