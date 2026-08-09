package com.balancetrail.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BatchAsyncConfiguration {

  @Bean(name = "batchTaskExecutor")
  public TaskExecutor batchTaskExecutor(BatchTuningProperties properties) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("reconciliation-");
    executor.setCorePoolSize(properties.workerThreads());
    executor.setMaxPoolSize(properties.workerThreads());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
