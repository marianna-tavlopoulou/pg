package com.marianna.gateway;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers = "localhost:9092";

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3) // 3 partitions for scale. Messages sorted by customerId hash key.
                .replicas(1) // 1 replica because our docker-compose is a single broker
                .build();
    }

    @Bean
    public NewTopic paymentFraudReviewTopic() {
        return TopicBuilder.name("payment-fraud-review")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlt() {
        return TopicBuilder.name("payment-events-dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas to acknowledge
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Enable idempotence to avoid duplicates
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
