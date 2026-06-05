package com.marianna.gateway.step;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.marianna.gateway.domain.PaymentOrder;

@Component
public class SettlementProcessor implements ItemProcessor<PaymentOrder, SettlementRecord> {
    @Override
    public SettlementRecord process(@NonNull PaymentOrder order) {
        return new SettlementRecord(order.id().toString(), order.merchantId().toString(),
                order.amount(), order.currency(), order.method().name(), order.updatedAt().toString());
    }
}
