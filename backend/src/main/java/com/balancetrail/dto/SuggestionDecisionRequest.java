package com.balancetrail.dto;

import jakarta.validation.constraints.Size;

/** Optional note, e.g. why a pair was rejected. */
public record SuggestionDecisionRequest(@Size(max = 200) String note) {}
