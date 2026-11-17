package com.balancetrail.batch;

public record RawLedgerRecord(
    long lineNumber,
    String transactionId,
    String accountNumber,
    String amount,
    String transactionDate,
    String description,
    String parseError) {}
