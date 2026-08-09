package com.balancetrail.config;

import com.balancetrail.domain.UserRole;
import com.balancetrail.entity.AppUserEntity;
import com.balancetrail.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class BootstrapUserConfiguration {
  private static final Logger log = LoggerFactory.getLogger(BootstrapUserConfiguration.class);

  @Bean
  public ApplicationRunner bootstrapAnalyst(
      AppUserRepository repository,
      PasswordEncoder passwordEncoder,
      BootstrapUserProperties properties) {
    return args -> createIfMissing(repository, passwordEncoder, properties);
  }

  @Transactional
  void createIfMissing(
      AppUserRepository repository,
      PasswordEncoder passwordEncoder,
      BootstrapUserProperties properties) {
    if (repository.findByUsernameIgnoreCase(properties.username()).isEmpty()) {
      repository.save(
          new AppUserEntity(
              properties.username(),
              passwordEncoder.encode(properties.password()),
              UserRole.ANALYST));
      log.info("Created bootstrap analyst user '{}'", properties.username());
    }
  }
}
