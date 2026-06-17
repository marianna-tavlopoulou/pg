package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.dto.PaymentRequest;

@ActiveProfiles("test")
class PaymentConcurrencyIT extends BaseIntegrationTest {

    private record CallResult(int status, String body) {
    }

    @Test
    @DisplayName("20 concurrent submits with same Idempotency-Key -> exactly one payment created, all responses identical")
    void concurrentSubmits_sameIdempotencyKey_resultInSingleRecord() throws Exception {
        int threadCount = 20;
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal(1500), Currency.EUR,
                PaymentMethod.CARD, "Order #1");
        String authToken = authToken(); // make sure that all requests use the same token
        String idempotencyKey = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(request);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch go = new CountDownLatch(1);

        List<Callable<CallResult>> tasks = IntStream.range(0, threadCount).<Callable<CallResult>>mapToObj(i -> () -> {
            go.await();
            MvcResult result = mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/payments")
                            .header("Authorization", "Bearer " + authToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
            int status = result.getResponse().getStatus();
            String responsebody = result.getResponse().getContentAsString();
            return new CallResult(status, responsebody);
        }).toList();

        List<Future<CallResult>> futures = new ArrayList<>();
        for (Callable<CallResult> task : tasks) {
            futures.add(pool.submit(task));
        }
        go.countDown();

        List<CallResult> results = new ArrayList<>();
        for (Future<CallResult> f : futures) {
            results.add(f.get(15, TimeUnit.SECONDS));
        }

        pool.shutdown();

        List<Integer> statuses = results.stream().map(CallResult::status).toList();
        assertThat(statuses)
                .as("Expected all responses to be 201")
                .allMatch(s -> s == HttpStatus.CREATED.value());

        List<UUID> ids = results.stream().map(r -> {
            JsonNode node;
            try {
                node = objectMapper.readTree(r.body());
            } catch (Exception e) {
                throw new RuntimeException();
            }
            return UUID.fromString(node.get("id").asText());
        }).toList();
        assertThat(ids.stream().distinct().count())
                .as("Expected a single distinct id")
                .isEqualTo(1);
    }

}
