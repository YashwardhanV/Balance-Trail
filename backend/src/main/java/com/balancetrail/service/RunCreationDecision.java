package com.balancetrail.service;

import com.balancetrail.entity.ReconciliationRunEntity;

public record RunCreationDecision(ReconciliationRunEntity run, boolean reused) {}
