package com.balancetrail.batch;

public record RawGatewayRecord(
    long lineNumber,
    String transactionId,
    String accountNumber,
    String amount,
    String transactionDate,
    String parseError) {}
