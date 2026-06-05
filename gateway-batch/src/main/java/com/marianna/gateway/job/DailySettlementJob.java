package com.marianna.gateway.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.step.SettlementProcessor;
import com.marianna.gateway.step.SettlementRecord;

/**
 * Spring Batch daily settlement job — same pattern used at SWIFT.
 * Chunk size 100: reads 100 records, processes, writes, commits. Repeat.
 * If one chunk fails, only that chunk retries — not the whole job.
 */
@Configuration
public class DailySettlementJob {

    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementStep) {
        return new JobBuilder("dailySettlementJob", jobRepository)
                .start(settlementStep).build();
    }

    @Bean
    public Step settlementStep(JobRepository jobRepository,
            PlatformTransactionManager tx,
            SettlementProcessor processor,
            FlatFileItemWriter<SettlementRecord> csvWriter) {
        return new StepBuilder("settlementStep", jobRepository)
                .<PaymentOrder, SettlementRecord>chunk(100, tx)
                .reader(itemReader())
                .processor(processor)
                .writer(csvWriter)
                .faultTolerant().skipLimit(10).skip(Exception.class)
                .build();
    }

    @Bean
    public org.springframework.batch.item.ItemReader<PaymentOrder> itemReader() {
        // TODO: inject PaymentOrderJpaRepository and read COMPLETED payments
        // Replace with RepositoryItemReader in Week 4
        return () -> null;
    }

    @Bean
    public FlatFileItemWriter<SettlementRecord> csvWriter() {
        return new FlatFileItemWriterBuilder<SettlementRecord>()
                .name("settlementCsvWriter")
                .resource(new FileSystemResource("output/settlement-report.csv"))
                .delimited().delimiter(",")
                .names("paymentId", "merchantId", "amount", "currency", "method", "processedAt")
                .headerCallback(w -> w.write("payment_id,merchant_id,amount,currency,method,processed_at"))
                .build();
    }
}
