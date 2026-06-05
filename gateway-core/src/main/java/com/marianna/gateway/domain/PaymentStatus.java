package com.marianna.gateway.domain;

import java.util.Set;

public enum PaymentStatus {
    PENDING {
        @Override
        public Set<PaymentStatus> allowedTransitions() {
            return Set.of(PROCESSING, DECLINED);
        }
    },
    PROCESSING {
        @Override
        public Set<PaymentStatus> allowedTransitions() {
            return Set.of(COMPLETED, FAILED, DECLINED);
        }
    },
    COMPLETED {
        @Override
        public Set<PaymentStatus> allowedTransitions() {
            return Set.of(REFUNDED);
        }
    },
    FAILED {
        @Override
        public Set<PaymentStatus> allowedTransitions() {
            return Set.of();
        }
    },
    DECLINED {
        @Override
        public Set<PaymentStatus> allowedTransitions() {
            return Set.of();
        }
    },
    REFUNDED {
        @Override
        public Set<PaymentStatus> allowedTransitions() {
            return Set.of();
        }
    };

    public abstract Set<PaymentStatus> allowedTransitions();

    public boolean canTransitionTo(PaymentStatus next) {
        return allowedTransitions().contains(next);
    }
}
