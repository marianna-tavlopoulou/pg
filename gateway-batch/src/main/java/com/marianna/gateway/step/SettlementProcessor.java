package com.marianna.gateway.step;

import com.marianna.gateway.domain.PaymentOrder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class SettlementProcessor implements ItemProcessor<PaymentOrder, SettlementRecord> {
    @Override
    public SettlementRecord process(@NonNull PaymentOrder order) {
        return new SettlementRecord(order.id().toString(), order.merchantId().toString(),
            order.amount(), order.currency(), order.method().name(), order.updatedAt().toString());
    }
}
