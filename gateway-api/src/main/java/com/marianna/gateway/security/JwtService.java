package com.marianna.gateway.security;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService (@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = expiration;
    }

    //Generate token
    public String generateToken(UUID userId, String username) {
        Date now =  new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
        .subject(username)
        .claim("userId", userId.toString())
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(secretKey)
        .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {

        return extractClaims(token).getSubject();

    }

    public UUID extractUserId(String token) {
        String userId = extractClaims(token).get("userId", String.class);
        return UUID.fromString(userId);
    }

    public Claims extractClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

}
