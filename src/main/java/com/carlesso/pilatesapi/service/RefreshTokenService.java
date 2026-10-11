package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.RefreshToken;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.exception.UnauthorizedException;
import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import com.carlesso.pilatesapi.repository.RefreshTokenRepository;
import com.carlesso.pilatesapi.util.TokenOpaco;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens opacos e rotativos. Cada login abre uma família; cada
 * {@link #rotacionar} consome o token apresentado e emite o próximo da mesma
 * família. Reapresentar um token já consumido indica que ele vazou (quem o
 * roubou ou o dono legítimo está usando uma cópia antiga), e a família inteira
 * é revogada — os dois lados precisam logar de novo.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    static final String ERRO_TOKEN_INVALIDO = "Refresh token inválido ou expirado";

    private final RefreshTokenRepository repository;
    private final BusinessMetrics businessMetrics;
    private final long expirationMillis;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            BusinessMetrics businessMetrics,
            @Value("${jwt.refresh-expiration-ms:604800000}") long expirationMillis) {
        this.repository = repository;
        this.businessMetrics = businessMetrics;
        this.expirationMillis = expirationMillis;
    }

    /** Emite o primeiro refresh token de uma nova família (login). */
    @Transactional
    public String emitir(User user) {
        return emitir(user, UUID.randomUUID());
    }

    /**
     * Consome o refresh token e emite o próximo da mesma família. As revogações
     * feitas antes de lançar {@link UnauthorizedException} precisam sobreviver à
     * exceção, daí o {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshTokenRotacionado rotacionar(String rawToken) {
        RefreshToken atual = repository
                .findByTokenHash(TokenOpaco.hash(rawToken))
                .orElseThrow(() -> {
                    log.warn("Refresh rejeitado: token desconhecido");
                    return new UnauthorizedException(ERRO_TOKEN_INVALIDO);
                });
        User user = atual.getUser();

        if (atual.getUsedAt() != null) {
            int revogados = repository.revogarFamilia(atual.getFamilyId(), LocalDateTime.now());
            businessMetrics.registrarRefreshTokenReusado();
            log.warn(
                    "Reuso de refresh token já rotacionado: família revogada, userId={}, tokensRevogados={}",
                    user.getId(),
                    revogados);
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }
        if (atual.getRevokedAt() != null || atual.isExpirado()) {
            log.warn("Refresh rejeitado: token revogado ou expirado, userId={}", user.getId());
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }
        if (!user.isAtivo() || atual.getTokenVersion() != user.getTokenVersion()) {
            repository.revogarFamilia(atual.getFamilyId(), LocalDateTime.now());
            log.warn("Refresh rejeitado: usuário inativo ou senha alterada, userId={}", user.getId());
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }

        atual.setUsedAt(LocalDateTime.now());
        repository.save(atual);
        String novo = emitir(user, atual.getFamilyId());
        log.info("Refresh token rotacionado: userId={}", user.getId());
        return new RefreshTokenRotacionado(user, novo);
    }

    /**
     * Revoga o refresh token apresentado no logout. Idempotente: token
     * desconhecido, expirado ou já revogado não gera erro, para que o logout
     * nunca falhe nem sirva de oráculo sobre quais tokens existem.
     */
    @Transactional
    public void revogar(String rawToken) {
        repository.findByTokenHash(TokenOpaco.hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                repository.save(token);
                log.info(
                        "Logout: refresh token revogado, userId={}",
                        token.getUser().getId());
            }
        });
    }

    private String emitir(User user, UUID familyId) {
        String rawToken = TokenOpaco.gerar();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenOpaco.hash(rawToken));
        token.setFamilyId(familyId);
        token.setTokenVersion(user.getTokenVersion());
        token.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(expirationMillis)));
        repository.save(token);
        return rawToken;
    }

    public record RefreshTokenRotacionado(User user, String refreshToken) {}
}
