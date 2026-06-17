package com.marianna.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.dto.AuthRequest;
import com.marianna.gateway.dto.AuthResponse;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;

abstract class BaseIntegrationTest extends BaseContainerTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        private String cachedToken;

        protected String authToken() throws Exception {
                if (cachedToken != null)
                        return cachedToken;

                String body = objectMapper.writeValueAsString(new AuthRequest("local-dev-only"));

                MvcResult result = mockMvc.perform(
                                post("/api/v1/auth/token")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn();

                cachedToken = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                AuthResponse.class).token();

                return cachedToken;
        }

        protected PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) throws Exception {
                MvcResult result = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + authToken())
                                                .header("Idempotency-Key", idempotencyKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andReturn();

                return objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        }

        protected PaymentResponse createPayment(PaymentRequest request, String idempotencyKey, String authToken)
                        throws Exception {
                MvcResult result = mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + authToken)
                                                .header("Idempotency-Key", idempotencyKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andReturn();

                return objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        }

        protected PaymentResponse getPayment(String id) throws Exception {
                MvcResult result = mockMvc.perform(
                                get("/api/v1/payments/{id}", id)
                                                .header("Authorization", "Bearer " + authToken()))
                                .andReturn();

                return objectMapper.readValue(result.getResponse().getContentAsString(), PaymentResponse.class);
        }

        protected MvcResult createPaymentRaw(PaymentRequest request, String idempotencyKey) throws Exception {
                return mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + authToken())
                                                .header("Idempotency-Key", idempotencyKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andReturn();
        }

        protected MvcResult assertBadRequest(PaymentRequest request, String idempotencyKey) throws Exception {
                return mockMvc.perform(
                                post("/api/v1/payments")
                                                .header("Authorization", "Bearer " + authToken())
                                                .header("Idempotency-Key", idempotencyKey)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andReturn();
        }

}
