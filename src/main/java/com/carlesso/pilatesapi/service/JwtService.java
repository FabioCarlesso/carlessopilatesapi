package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final JwtParser jwtParser;
    private final long expirationMillis;

    public JwtService(
            @Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms:86400000}") long expirationMillis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(secretKey).build();
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)));

        if (userDetails instanceof User user) {
            builder.claim("role", user.getRole().name())
                    .claim("userId", user.getId())
                    .claim("tokenVersion", user.getTokenVersion());
        }

        return builder.signWith(secretKey).compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public Long extractTokenVersion(String token) {
        Object tokenVersion = extractAllClaims(token).get("tokenVersion");
        return tokenVersion instanceof Number number ? number.longValue() : null;
    }

    private Claims extractAllClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }
}
