package com.balancetrail.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI balanceTrailOpenApi() {
    String scheme = "basicAuth";
    return new OpenAPI()
        .info(
            new Info()
                .title("BalanceTrail API")
                .version("v1")
                .contact(
                    new Contact()
                        .name("Yashwardhan Verma")
                        .url("https://github.com/YashwardhanV")
                        .email("yashwardhanverma108@gmail.com"))
                .description("Upload and inspect transaction reconciliation runs"))
        .addSecurityItem(new SecurityRequirement().addList(scheme))
        .components(
            new Components()
                .addSecuritySchemes(
                    scheme,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("Local demo authentication; use HTTPS outside localhost")));
  }
}
