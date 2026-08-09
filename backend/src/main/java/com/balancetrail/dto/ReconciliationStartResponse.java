package com.balancetrail.dto;

public record ReconciliationStartResponse(
    ReconciliationRunResponse reconciliation, boolean idempotentReplay) {}
