package com.balancetrail.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GatewayTransaction(
    long lineNumber,
    String transactionId,
    String accountNumber,
    BigDecimal amount,
    LocalDate transactionDate) {}
