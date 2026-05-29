package com.marianna.gateway.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marianna.gateway.dto.AuthRequest;
import com.marianna.gateway.dto.AuthResponse;
import com.marianna.gateway.security.JwtService;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtService jwtService;
    private final String validApiKey;
    // Stub — replace with MerchantRepository.findByApiKey() in Week 2
    private static final UUID STUB_MERCHANT_ID =
    UUID.fromString("00000000-0000-0000-0000-000000000001");

    public AuthController(JwtService jwtService, @Value("${auth.api-key}") String validApiKey) {
        this.jwtService = jwtService;
        this.validApiKey = validApiKey;
    }

    @PostMapping("/token")
    public ResponseEntity<AuthResponse> getToken(@RequestBody AuthRequest request) {

        if (!validApiKey.equals(request.apiKey())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = jwtService.generateToken(STUB_MERCHANT_ID, "merchant-user");

        return ResponseEntity.ok(new AuthResponse(token));
    }

}
