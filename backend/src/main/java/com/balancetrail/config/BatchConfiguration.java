package com.balancetrail.config;

import com.balancetrail.batch.DuplicateDetector;
import com.balancetrail.batch.GatewayCsvLineMapper;
import com.balancetrail.batch.LedgerMatchingItemWriter;
import com.balancetrail.batch.MissingInGatewayTasklet;
import com.balancetrail.batch.PairSuggester;
import com.balancetrail.batch.PairSuggestionTasklet;
import com.balancetrail.batch.ProcessedLine;
import com.balancetrail.batch.RawGatewayRecord;
import com.balancetrail.batch.ReconciliationItemProcessor;
import com.balancetrail.batch.ReconciliationJobListener;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import com.balancetrail.service.GatewayFormatService;
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
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
      @Qualifier("missingInGatewayStep") Step missingInGatewayStep,
      @Qualifier("pairSuggestionStep") Step pairSuggestionStep,
      ReconciliationJobListener listener) {
    // Step 1: every gateway line against the ledger. Step 2: ledger rows the file never mentions.
    // Step 3: suggest "probably the same payment" pairs between the leftovers of steps 1 and 2.
    return new JobBuilder("reconciliationJob", jobRepository)
        .listener(listener)
        .start(reconciliationStep)
        .next(missingInGatewayStep)
        .next(pairSuggestionStep)
        .build();
  }

  @Bean
  public Step pairSuggestionStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      PairSuggestionTasklet pairSuggestionTasklet) {
    return new StepBuilder("pairSuggestionStep", jobRepository)
        .tasklet(pairSuggestionTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public PairSuggestionTasklet pairSuggestionTasklet(
      @Value("#{jobParameters['runId']}") String runId,
      JdbcTemplate jdbcTemplate,
      @Value("${app.matching.date-tolerance-days:2}") int dateToleranceDays,
      @Value("${app.matching.max-id-distance:2}") int maxIdDistance) {
    return new PairSuggestionTasklet(
        jdbcTemplate, new PairSuggester(dateToleranceDays, maxIdDistance), UUID.fromString(runId));
  }

  @Bean
  public Step missingInGatewayStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      MissingInGatewayTasklet missingInGatewayTasklet) {
    return new StepBuilder("missingInGatewayStep", jobRepository)
        .tasklet(missingInGatewayTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public MissingInGatewayTasklet missingInGatewayTasklet(
      @Value("#{jobParameters['runId']}") String runId, JdbcTemplate jdbcTemplate) {
    return new MissingInGatewayTasklet(jdbcTemplate, UUID.fromString(runId));
  }

  @Bean
  public Step reconciliationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FlatFileItemReader<RawGatewayRecord> gatewayCsvReader,
      ItemProcessor<RawGatewayRecord, ProcessedLine> reconciliationProcessor,
      LedgerMatchingItemWriter reconciliationWriter,
      @Value("${app.batch.chunk-size:100}") int chunkSize) {
    // On a temporary database error, try the chunk up to 3 times in total,
    // waiting 100 ms and then 200 ms between attempts (doubling, capped at 1 s).
    var backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(100);
    backOff.setMaxInterval(1000);
    return new StepBuilder("reconciliationStep", jobRepository)
        .<RawGatewayRecord, ProcessedLine>chunk(chunkSize, transactionManager)
        .reader(gatewayCsvReader)
        .processor(reconciliationProcessor)
        .writer(reconciliationWriter)
        .faultTolerant()
        .retry(TransientDataAccessException.class)
        .retryLimit(3)
        .backOffPolicy(backOff)
        .build();
  }

  @Bean
  @StepScope
  public FlatFileItemReader<RawGatewayRecord> gatewayCsvReader(
      @Value("#{jobParameters['inputFile']}") String inputFile,
      @Value("#{jobParameters['formatId']}") String formatId,
      GatewayFormatService formatService) {
    // Standard layout, or a saved gateway format that picks and converts the four columns.
    LineMapper<RawGatewayRecord> lineMapper =
        formatId == null || "0".equals(formatId)
            ? new GatewayCsvLineMapper()
            : formatService.lineMapper(formatService.find(Long.parseLong(formatId)));
    return new FlatFileItemReaderBuilder<RawGatewayRecord>()
        .name("gatewayCsvReader")
        .resource(new FileSystemResource(inputFile))
        .encoding("UTF-8")
        .linesToSkip(1)
        .lineMapper(lineMapper)
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
      DuplicateDetector duplicateDetector) {
    return new ReconciliationItemProcessor(
        runRepository.getReferenceById(UUID.fromString(runId)), duplicateDetector);
  }

  /** Looks up the ledger once per chunk, compares, then hands the results to the JPA writer. */
  @Bean
  @StepScope
  public LedgerMatchingItemWriter reconciliationWriter(
      @Value("#{jobParameters['runId']}") String runId,
      ReconciliationRunRepository runRepository,
      LedgerTransactionRepository ledgerRepository,
      TransactionMatcher matcher,
      JpaItemWriter<ReconciliationItemEntity> reconciliationItemJpaWriter) {
    return new LedgerMatchingItemWriter(
        runRepository.getReferenceById(UUID.fromString(runId)),
        ledgerRepository,
        matcher,
        reconciliationItemJpaWriter);
  }

  @Bean
  public JpaItemWriter<ReconciliationItemEntity> reconciliationItemJpaWriter(
      EntityManagerFactory entityManagerFactory) {
    return new JpaItemWriterBuilder<ReconciliationItemEntity>()
        .entityManagerFactory(entityManagerFactory)
        .build();
  }
}
