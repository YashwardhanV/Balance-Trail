package com.balancetrail.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.bootstrap-user")
public record BootstrapUserProperties(
    @NotBlank String username,
    @NotBlank @Size(min = 8) String password) {}
