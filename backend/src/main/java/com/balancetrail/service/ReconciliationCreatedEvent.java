package com.balancetrail.service;

import java.util.UUID;

public record ReconciliationCreatedEvent(UUID runId, String inputFile) {}
