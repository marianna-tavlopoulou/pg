package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.dto.AuthRequest;
import com.marianna.gateway.dto.AuthResponse;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;

public class PaymentControllerIT extends BaseIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        private String jwtToken;

        @BeforeEach
        void authenticate() throws Exception {
                // Get a real JWT before each test
                String body = objectMapper.writeValueAsString(new AuthRequest("local-dev-only"));
                MvcResult mvcResult = mockMvc.perform(
                                post("/api/v1/auth/token")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn();

                AuthResponse response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(),
                                AuthResponse.class);
                jwtToken = response.token();
        }

        @Test
        @DisplayName("Valid payment completes successfully and returns 201")
        void shouldCompleteValidPayment() throws Exception {

                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");

                MvcResult mvcResult = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + jwtToken)
                                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                PaymentResponse response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(),
                                PaymentResponse.class);
                assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(1500));
                assertThat(response.currency()).isEqualTo(Currency.EUR);
                assertThat(response.id()).isNotNull();
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);

        }

        @Test
        @DisplayName("Send paymentwith  same Idempotency-Key twice, second response should be identical, still 201")
        void shouldReturnIdenticalPayment() throws Exception {

                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                                PaymentMethod.CARD, "Order #1");
                String idempotenceKey = UUID.randomUUID().toString();

                MvcResult mvcResult = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + jwtToken)
                                                .header("Idempotency-Key", idempotenceKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                PaymentResponse response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(),
                                PaymentResponse.class);
                assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(1500));
                assertThat(response.currency()).isEqualTo(Currency.EUR);
                assertThat(response.id()).isNotNull();
                assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);

                MvcResult mvcResult2 = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + jwtToken)
                                                .header("Idempotency-Key", idempotenceKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                PaymentResponse response2 = objectMapper.readValue(mvcResult.getResponse().getContentAsString(),
                                PaymentResponse.class);
                assertThat(response2.amount()).isEqualTo(response.amount());
                assertThat(response2.id()).isEqualTo(response.id());
                assertThat(response2.status()).isEqualTo(response.status());
                assertThat(response2.currency()).isEqualTo(response.currency());
                assertThat(response2.createdAt()).isEqualTo(response.createdAt());
                assertThat(response2.updatedAt()).isEqualTo(response.updatedAt());
                assertThat(response2.method()).isEqualTo(response.method());

        }

        @Test
        @DisplayName("Payments with amount 15000 → status DECLINED")
        void shouldDeclinePaymentWithVeryHighAmount() throws Exception {

                PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(15000), Currency.EUR,
                                PaymentMethod.CARD,
                                "Order #1");

                MvcResult mvcResult = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + jwtToken)
                                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                PaymentResponse response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(),
                                PaymentResponse.class);
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
                                                .header("Authorization", "Bearer " + jwtToken)
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

}
