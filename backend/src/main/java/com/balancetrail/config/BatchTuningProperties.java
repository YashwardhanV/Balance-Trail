package com.balancetrail.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.batch")
public record BatchTuningProperties(
    @Min(1) @Max(10_000) int chunkSize,
    @Min(0) @Max(10) int retryLimit,
    @Min(1) int skipLimit,
    @Min(0) long initialRetryDelayMs,
    @Min(0) long maxRetryDelayMs,
    @Min(1) @Max(4) int workerThreads,
    @Min(1) @Max(100) int queueCapacity) {}
