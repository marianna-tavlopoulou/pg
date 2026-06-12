package com.marianna.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.dto.AuthRequest;
import com.marianna.gateway.dto.AuthResponse;

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
public class FraudDetectionIT extends BaseIntegrationTest {

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
    @DisplayName("""
            RULE:
            None → 0 → CLEAN
            Normal transaction, no risk indicators
            """)
    void shouldBeCleanForNormalTransaction() throws Exception {

    }

}
