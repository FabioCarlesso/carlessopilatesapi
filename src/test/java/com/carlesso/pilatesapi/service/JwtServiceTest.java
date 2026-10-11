package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789012345678901234567890101234567890123456789012345678901";

    @Test
    void generateToken_deveExpirarNoTtlConfigurado() {
        long ttl = Duration.ofMinutes(15).toMillis();
        JwtService service = new JwtService(SECRET, ttl);

        String token = service.generateToken(usuario());

        Date expiracao = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        long restante = expiracao.getTime() - System.currentTimeMillis();
        assertThat(restante).isBetween(ttl - 5_000, ttl);
    }

    @Test
    void tokenComTtlCurtoInjetado_aposExpirar_deveSerRejeitado() {
        JwtService service = new JwtService(SECRET, -1_000);

        String token = service.generateToken(usuario());

        assertThatThrownBy(() -> service.extractUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }

    private User usuario() {
        User user = new User();
        user.setName("Maria");
        user.setEmail("maria@email.com");
        user.setPassword("hash");
        user.setRole(Role.USER);
        return user;
    }
}
