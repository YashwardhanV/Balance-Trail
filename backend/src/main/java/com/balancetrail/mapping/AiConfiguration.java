package com.balancetrail.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class AiConfiguration {

  @Bean
  public HeaderAliasSuggester headerAliasSuggester() {
    return new HeaderAliasSuggester();
  }

  /** Only created when GEMINI_API_KEY is set. Without it the app works the same, minus AI. */
  @Bean
  @ConditionalOnExpression("!'${app.ai.gemini.api-key:}'.isBlank()")
  public AiColumnMappingSuggester geminiColumnMappingSuggester(
      GeminiProperties properties, ObjectMapper objectMapper) {
    var httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.timeout());
    return new GeminiColumnMappingSuggester(
        RestClient.builder().requestFactory(requestFactory), properties, objectMapper);
  }
}
