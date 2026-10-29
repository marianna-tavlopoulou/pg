package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaConfigTest {

    @Test
    void shouldSetMaxBlockMsToMatchPublisherTimeout() {
        DefaultKafkaProducerFactory<String, String> producerFactory =
                (DefaultKafkaProducerFactory<String, String>) new KafkaConfig().producerFactory();

        assertThat(producerFactory.getConfigurationProperties())
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 15_000);
    }
}
