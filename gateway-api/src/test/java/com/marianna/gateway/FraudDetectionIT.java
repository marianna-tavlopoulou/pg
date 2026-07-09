package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;

/**
 * Fraud Risk Matrix
 *
 * +-------------------------------+-------+----------+
 * | Signals | Score | Decision |
 * +-------------------------------+-------+----------+
 * | None | 0 | CLEAN |
 * | HIGH_AMOUNT | 25 | CLEAN |
 * | VELOCITY | 30 | CLEAN |
 * | DUPLICATE_AMOUNT | 25 | CLEAN |
 * | HIGH_AMOUNT + HIGH_WALLET | 40 | REVIEW |
 * | HIGH_AMOUNT + VELOCITY | 55 | REVIEW |
 * | VELOCITY + DUPLICATE_AMOUNT | 55 | REVIEW |
 * | HIGH_AMOUNT + WALLET + VELOCITY| 70 | DECLINE |
 * | HIGH_AMOUNT + VELOCITY + DUPL | 80 | DECLINE |
 * | VERY_HIGH_AMOUNT | 75 | DECLINE |
 * +-------------------------------+-------+----------+
 *
 * REVIEW:
 * 40 <= score < 70
 *
 * DECLINE:
 * score >= 70
 */
class FraudDetectionIT extends BaseIntegrationTest {

        private PaymentRequest createPaymentRequest(int amount, PaymentMethod method, UUID customerId) {
                return new PaymentRequest(customerId, new BigDecimal(amount), Currency.EUR, method,
                                "order #1");
        }

        @Test
        @DisplayName("""
                        None → CLEAN (0 score)
                        Normal transaction should be approved
                        """)
        void shouldApproveNormalTransaction() throws Exception {
                PaymentResponse response = createPayment(
                                createPaymentRequest(500, PaymentMethod.CARD, UUID.randomUUID()),
                                UUID.randomUUID().toString());
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("""
                        HIGH_AMOUNT only → CLEAN (25 score)
                        Large amount alone is not fraud
                        """)
        void shouldAllowHighAmountOnly() throws Exception {
                PaymentResponse response = createPayment(
                                createPaymentRequest(9000, PaymentMethod.CARD, UUID.randomUUID()),
                                UUID.randomUUID().toString());
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("""
                        VELOCITY only → CLEAN (30 score)
                        High frequency alone is not fraud
                        """)
        void shouldAllowVelocityOnly() throws Exception {
                List<Integer> range = IntStream.rangeClosed(100, 120)
                                .boxed()
                                .toList();
                UUID customerId = UUID.randomUUID();
                for (Integer i : range) {
                        createPayment(
                                        createPaymentRequest(i, PaymentMethod.CARD, customerId),
                                        UUID.randomUUID().toString());
                }
                PaymentResponse response = createPayment(
                                createPaymentRequest(111, PaymentMethod.CARD, customerId),
                                UUID.randomUUID().toString());
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("""
                        DUPLICATE_AMOUNT only → CLEAN (25 score)
                        Recurring identical payments are allowed
                        """)
        void shouldAllowDuplicateAmountOnly() throws Exception {

                UUID customerId = UUID.randomUUID();
                createPayment(createPaymentRequest(200, PaymentMethod.CARD, customerId), UUID.randomUUID().toString());
                PaymentResponse response = createPayment(createPaymentRequest(200, PaymentMethod.CARD, customerId),
                                UUID.randomUUID().toString());

                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("""
                        HIGH_AMOUNT + HIGH_WALLET → REVIEW (40 score)
                        Elevated risk requires manual review
                        """)
        void shouldMarkHighAmountHighWalletAsReview() throws Exception {
                PaymentResponse response = createPayment(
                                createPaymentRequest(9000, PaymentMethod.WALLET, UUID.randomUUID()),
                                UUID.randomUUID().toString());
                assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
        }

}
