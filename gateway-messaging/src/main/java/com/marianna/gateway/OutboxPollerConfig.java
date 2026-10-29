package com.marianna.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "gateway.poller.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollerConfig {

}
