package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;

public class PaymentControllerIT extends BaseIntegrationTest {

        @Test
        @DisplayName("Valid payment completes successfully and returns 201")
        void shouldCompleteValidPayment() throws Exception {

                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");

                PaymentResponse response = createPayment(request, UUID.randomUUID().toString());
                assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(1500));
                assertThat(response.currency()).isEqualTo(Currency.EUR);
                assertThat(response.id()).isNotNull();
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);

        }

        @Test
        @DisplayName("Should retrieve existing payment")
        void shouldRetrieveExistingPayment() throws Exception {
                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");
                String idempotenceKey = UUID.randomUUID().toString();

                PaymentResponse response = createPayment(request, idempotenceKey);
                assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(1500));
                assertThat(response.currency()).isEqualTo(Currency.EUR);
                assertThat(response.id()).isNotNull();
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);

                PaymentResponse response2 = getPayment(response.id().toString());
                assertThat(response2.amount()).isEqualByComparingTo(response.amount());
                assertThat(response2.currency()).isEqualTo(Currency.EUR);
                assertThat(response2.id()).isEqualTo(response.id());
                assertThat(response2.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Send payment with  same Idempotency-Key twice, second response should be identical, still 201")
        void shouldReturnIdenticalPayment() throws Exception {

                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");
                String idempotenceKey = UUID.randomUUID().toString();

                PaymentResponse response = createPayment(request, idempotenceKey);
                assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(1500));
                assertThat(response.currency()).isEqualTo(Currency.EUR);
                assertThat(response.id()).isNotNull();
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);

                PaymentResponse response2 = createPayment(request, idempotenceKey);
                assertThat(response2.amount()).isEqualByComparingTo(response.amount());
                assertThat(response2.id()).isEqualTo(response.id());
                assertThat(response2.status()).isEqualTo(response.status());
                assertThat(response2.currency()).isEqualTo(response.currency());
                assertThat(response2.createdAt().truncatedTo(ChronoUnit.MILLIS))
                                .isEqualTo(response.createdAt().truncatedTo(ChronoUnit.MILLIS));
                assertThat(response2.updatedAt().truncatedTo(ChronoUnit.MILLIS))
                                .isEqualTo(response.updatedAt().truncatedTo(ChronoUnit.MILLIS));
                assertThat(response2.method()).isEqualTo(response.method());

        }

        @Test
        @DisplayName("Missing Idempotency-Key should return 400")
        void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");

                mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + authToken())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andReturn();
        }

        @Test
        @DisplayName("Amount zero should return 400")
        void shouldRejectZeroAmount() throws Exception {
                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(0), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");

                assertBadRequest(request, UUID.randomUUID().toString());
        }

        @Test
        @DisplayName("Negative amount should return 400")
        void shouldRejectNegativeAmount() throws Exception {
                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(-10), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");

                assertBadRequest(request, UUID.randomUUID().toString());
        }

        @Test
        @DisplayName("Missing customerId should return 400")
        void shouldRejectNullCustomerId() throws Exception {
                PaymentRequest request = new PaymentRequest(null, new BigDecimal(10), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");

                assertBadRequest(request, UUID.randomUUID().toString());
        }

        @Test
        @DisplayName("Payments with amount 15000 → status DECLINED")
        void shouldDeclinePaymentWithVeryHighAmount() throws Exception {

                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(15000), Currency.EUR,
                                PaymentMethod.CARD,
                                "Order #1");

                PaymentResponse response = createPayment(request, UUID.randomUUID().toString());
                assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(15000));
                assertThat(response.currency()).isEqualTo(Currency.EUR);
                assertThat(response.id()).isNotNull();
                assertThat(response.status()).isEqualTo(PaymentStatus.DECLINED);

        }

        @Test
        @DisplayName("Search payment with non existant id should return 404")
        void shouldReturn404WhenSearchingForNonExistantId() throws Exception {

                MvcResult mvcResult = mockMvc.perform(
                                get("/api/v1/payments/{id}", UUID.randomUUID())
                                                .header("Authorization", "Bearer " + authToken())
                                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound())
                                .andReturn();
        }

        @Test
        @DisplayName("Missing JWT should return 401")
        void shouldReturn401WhenTokenMissing() throws Exception {
                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD,
                                "Order #1");

                MvcResult mvcResult = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized())
                                .andReturn();
        }

        @Test
        @DisplayName("Invalid JWT should return 401")
        void shouldReturn401WhenTokenInvalid() throws Exception {
                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD,
                                "Order #1");

                MvcResult mvcResult = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer invalidToken")
                                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized())
                                .andReturn();
        }

}
