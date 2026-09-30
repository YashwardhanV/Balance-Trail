package com.balancetrail.config;

import com.balancetrail.batch.DuplicateDetector;
import com.balancetrail.batch.GatewayCsvLineMapper;
import com.balancetrail.batch.RawGatewayRecord;
import com.balancetrail.batch.ReconciliationItemProcessor;
import com.balancetrail.batch.ReconciliationJobListener;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import com.balancetrail.service.TransactionMatcher;
import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfiguration {

  /** At most two reconciliations run at the same time; later uploads wait in the queue. */
  @Bean
  public ThreadPoolTaskExecutor batchTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setThreadNamePrefix("reconciliation-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }

  /**
   * Spring Boot's default JobLauncher runs the job on the calling thread. This one hands it to
   * {@code batchTaskExecutor}, so {@code run()} returns immediately and the API can answer 202.
   */
  @Bean
  public JobLauncher asyncJobLauncher(
      JobRepository jobRepository,
      @Qualifier("batchTaskExecutor") ThreadPoolTaskExecutor batchTaskExecutor)
      throws Exception {
    var launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(jobRepository);
    launcher.setTaskExecutor(batchTaskExecutor);
    launcher.afterPropertiesSet();
    return launcher;
  }

  @Bean
  public Job reconciliationJob(
      JobRepository jobRepository,
      @Qualifier("reconciliationStep") Step reconciliationStep,
      ReconciliationJobListener listener) {
    return new JobBuilder("reconciliationJob", jobRepository)
        .listener(listener)
        .start(reconciliationStep)
        .build();
  }

  @Bean
  public Step reconciliationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FlatFileItemReader<RawGatewayRecord> gatewayCsvReader,
      ItemProcessor<RawGatewayRecord, ReconciliationItemEntity> reconciliationProcessor,
      JpaItemWriter<ReconciliationItemEntity> reconciliationWriter,
      BatchTuningProperties properties) {
    var backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(properties.initialRetryDelayMs());
    backOff.setMaxInterval(properties.maxRetryDelayMs());
    return new StepBuilder("reconciliationStep", jobRepository)
        .<RawGatewayRecord, ReconciliationItemEntity>chunk(
            properties.chunkSize(), transactionManager)
        .reader(gatewayCsvReader)
        .processor(reconciliationProcessor)
        .writer(reconciliationWriter)
        .faultTolerant()
        .retry(TransientDataAccessException.class)
        .retryLimit(properties.retryLimit())
        .backOffPolicy(backOff)
        .build();
  }

  @Bean
  @StepScope
  public FlatFileItemReader<RawGatewayRecord> gatewayCsvReader(
      @Value("#{jobParameters['inputFile']}") String inputFile) {
    return new FlatFileItemReaderBuilder<RawGatewayRecord>()
        .name("gatewayCsvReader")
        .resource(new FileSystemResource(inputFile))
        .encoding("UTF-8")
        .linesToSkip(1)
        .lineMapper(new GatewayCsvLineMapper())
        .build();
  }

  @Bean
  @StepScope
  public DuplicateDetector duplicateDetector() {
    return new DuplicateDetector();
  }

  @Bean
  @StepScope
  public ReconciliationItemProcessor reconciliationProcessor(
      @Value("#{jobParameters['runId']}") String runId,
      ReconciliationRunRepository runRepository,
      DuplicateDetector duplicateDetector,
      LedgerTransactionRepository ledgerRepository,
      TransactionMatcher matcher) {
    return new ReconciliationItemProcessor(
        runRepository.getReferenceById(UUID.fromString(runId)),
        duplicateDetector,
        ledgerRepository,
        matcher);
  }

  @Bean
  public JpaItemWriter<ReconciliationItemEntity> reconciliationWriter(
      EntityManagerFactory entityManagerFactory) {
    return new JpaItemWriterBuilder<ReconciliationItemEntity>()
        .entityManagerFactory(entityManagerFactory)
        .build();
  }
}
