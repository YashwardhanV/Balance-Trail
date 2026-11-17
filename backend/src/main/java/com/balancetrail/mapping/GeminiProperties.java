package com.balancetrail.mapping;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini settings. With no API key the AI part is simply off and header rules are used.
 *
 * @param apiKey from GEMINI_API_KEY; never logged and never sent anywhere except Google
 * @param model model id, e.g. gemini-3.8-flash (configurable because Google renames models often)
 * @param baseUrl the Gemini API host
 * @param timeout how long to wait before giving up and using header rules
 */
@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl, Duration timeout) {

  public GeminiProperties {
    model = model == null || model.isBlank() ? "gemini-3.8-flash" : model;
    baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://generativelanguage.googleapis.com" : baseUrl;
    timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
  }

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
