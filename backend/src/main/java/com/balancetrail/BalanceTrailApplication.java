package com.balancetrail;

import com.balancetrail.config.BootstrapUserProperties;
import com.balancetrail.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    BootstrapUserProperties.class,
    StorageProperties.class
})
public class BalanceTrailApplication {

  public static void main(String[] args) {
    SpringApplication.run(BalanceTrailApplication.class, args);
  }
}
