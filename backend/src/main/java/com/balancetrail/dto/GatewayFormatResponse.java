package com.balancetrail.dto;

import java.time.Instant;
import java.util.List;

public record GatewayFormatResponse(
    long id,
    String name,
    List<String> headers,
    String transactionIdColumn,
    String accountNumberColumn,
    String amountColumn,
    String transactionDateColumn,
    String dateFormat,
    String suggestedBy,
    String createdBy,
    Instant createdAt) {}
