package com.balancetrail.config;

import com.balancetrail.batch.DuplicateDetector;
import com.balancetrail.batch.GatewayCsvLineMapper;
import com.balancetrail.batch.RawGatewayRecord;
import com.balancetrail.batch.ReconciliationItemProcessor;
import com.balancetrail.batch.ReconciliationJobListener;
import com.balancetrail.batch.ReconciliationSkipListener;
import com.balancetrail.batch.ReconciliationSummaryTasklet;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.exception.DuplicateTransactionException;
import com.balancetrail.exception.InvalidRecordException;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.repository.ReconciliationItemRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import com.balancetrail.service.RunStateService;
import com.balancetrail.service.SkippedItemRecorder;
import com.balancetrail.service.TransactionMatcher;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
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
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfiguration {

  @Bean
  public Job reconciliationJob(
      JobRepository jobRepository,
      @Qualifier("reconciliationStep") Step reconciliationStep,
      @Qualifier("reconciliationSummaryStep") Step summaryStep,
      ReconciliationJobListener listener) {
    return new JobBuilder("reconciliationJob", jobRepository)
        .listener(listener)
        .start(reconciliationStep)
        .on("*")
        .to(summaryStep)
        .end()
        .build();
  }

  @Bean
  public Step reconciliationStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FlatFileItemReader<RawGatewayRecord> gatewayCsvReader,
      ItemProcessor<RawGatewayRecord, ReconciliationItemEntity> reconciliationProcessor,
      JpaItemWriter<ReconciliationItemEntity> reconciliationWriter,
      ReconciliationSkipListener skipListener,
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
        .skip(InvalidRecordException.class)
        .skip(DuplicateTransactionException.class)
        .skipLimit(properties.skipLimit())
        .listener(skipListener)
        .build();
  }

  @Bean
  public Step reconciliationSummaryStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ReconciliationSummaryTasklet summaryTasklet) {
    return new StepBuilder("reconciliationSummaryStep", jobRepository)
        .tasklet(summaryTasklet, transactionManager)
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
        .strict(true)
        .saveState(true)
        .build();
  }

  @Bean
  @StepScope
  public DuplicateDetector duplicateDetector(
      @Value("#{jobParameters['runId']}") String runId,
      ReconciliationItemRepository itemRepository) {
    List<String> existing =
        itemRepository.findProcessedGatewayIds(
            UUID.fromString(runId), List.of(com.balancetrail.domain.ItemStatus.INVALID, com.balancetrail.domain.ItemStatus.DUPLICATE));
    return new DuplicateDetector(existing);
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

  @Bean
  @StepScope
  public ReconciliationSkipListener reconciliationSkipListener(
      @Value("#{jobParameters['runId']}") String runId,
      SkippedItemRecorder skippedItemRecorder) {
    return new ReconciliationSkipListener(UUID.fromString(runId), skippedItemRecorder);
  }

  @Bean
  @StepScope
  public ReconciliationSummaryTasklet reconciliationSummaryTasklet(
      @Value("#{jobParameters['runId']}") String runId, RunStateService runStateService) {
    return new ReconciliationSummaryTasklet(UUID.fromString(runId), runStateService);
  }
}
