    package com.marianna.gateway;

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

    import static org.assertj.core.api.Assertions.assertThat;
    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

    import java.math.BigDecimal;
    import java.util.UUID;

    public class PaymentControllerIT extends BaseIntegrationTest{

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
                        .content(body)
                ).andExpect(status().isOk())
                .andReturn();

            AuthResponse response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), AuthResponse.class);
            jwtToken = response.token();
        }

        @Test
        @DisplayName("Valid payment completes successfully and returns 201")
        void shouldCompleteValidPayment() throws Exception {

            PaymentRequest request = new PaymentRequest(new BigDecimal(1500), Currency.EUR, PaymentMethod.CARD, "Order #1");

            MvcResult mvcResult = mockMvc.perform(
                post("/api/v1/payments")
                    .header("Authorization", "Bearer " + jwtToken)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                ).andExpect(status().isCreated())
                .andReturn();

            PaymentResponse response = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), PaymentResponse.class);
            assertThat(response.amount()).isEqualByComparingTo(new BigDecimal(1500));
            assertThat(response.currency()).isEqualTo(Currency.EUR);
            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);

        }

    }
