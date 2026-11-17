package com.balancetrail.config;

import com.balancetrail.batch.DuplicateDetector;
import com.balancetrail.batch.LedgerCsvLineMapper;
import com.balancetrail.batch.LedgerImportItemProcessor;
import com.balancetrail.batch.LedgerImportItemWriter;
import com.balancetrail.batch.LedgerImportJobListener;
import com.balancetrail.batch.LedgerLine;
import com.balancetrail.batch.RawLedgerRecord;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The ledger import job. Same shape as the reconciliation job (read CSV, validate, write in
 * chunks, retry temporary database errors) and runs on the same 2-thread pool.
 */
@Configuration
public class LedgerImportBatchConfiguration {

  @Bean
  public Job ledgerImportJob(
      JobRepository jobRepository,
      @Qualifier("ledgerImportStep") Step ledgerImportStep,
      LedgerImportJobListener listener) {
    return new JobBuilder("ledgerImportJob", jobRepository)
        .listener(listener)
        .start(ledgerImportStep)
        .build();
  }

  @Bean
  public Step ledgerImportStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      FlatFileItemReader<RawLedgerRecord> ledgerCsvReader,
      LedgerImportItemProcessor ledgerImportProcessor,
      LedgerImportItemWriter ledgerImportWriter,
      @Value("${app.batch.chunk-size:100}") int chunkSize) {
    var backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(100);
    backOff.setMaxInterval(1000);
    return new StepBuilder("ledgerImportStep", jobRepository)
        .<RawLedgerRecord, LedgerLine>chunk(chunkSize, transactionManager)
        .reader(ledgerCsvReader)
        .processor(ledgerImportProcessor)
        .writer(ledgerImportWriter)
        .faultTolerant()
        .retry(TransientDataAccessException.class)
        .retryLimit(3)
        .backOffPolicy(backOff)
        .build();
  }

  @Bean
  @StepScope
  public FlatFileItemReader<RawLedgerRecord> ledgerCsvReader(
      @Value("#{jobParameters['inputFile']}") String inputFile) {
    return new FlatFileItemReaderBuilder<RawLedgerRecord>()
        .name("ledgerCsvReader")
        .resource(new FileSystemResource(inputFile))
        .encoding("UTF-8")
        .linesToSkip(1)
        .lineMapper(new LedgerCsvLineMapper())
        .build();
  }

  @Bean
  @StepScope
  public LedgerImportItemProcessor ledgerImportProcessor() {
    // A fresh detector per job, so IDs from an earlier ledger file don't count as duplicates.
    return new LedgerImportItemProcessor(new DuplicateDetector());
  }

  @Bean
  @StepScope
  public LedgerImportItemWriter ledgerImportWriter(
      @Value("#{jobParameters['importId']}") String importId, JdbcTemplate jdbcTemplate) {
    return new LedgerImportItemWriter(jdbcTemplate, Long.parseLong(importId));
  }
}
