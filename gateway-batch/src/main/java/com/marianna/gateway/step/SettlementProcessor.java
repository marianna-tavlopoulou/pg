package com.marianna.gateway.step;

import com.marianna.gateway.domain.PaymentOrder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SettlementProcessor implements ItemProcessor<PaymentOrder, SettlementRecord> {
    @Override
    public SettlementRecord process(PaymentOrder order) {
        return new SettlementRecord(order.id().toString(), order.merchantId().toString(),
            order.amount(), order.currency(), order.method().name(), Instant.now().toString());
    }
}
