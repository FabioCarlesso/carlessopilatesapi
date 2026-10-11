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

    /** Famílias revogadas ficam um dia no banco só para investigação; depois a limpeza as remove. */
    static final Duration RETENCAO_REVOGADOS = Duration.ofDays(1);

    private final RefreshTokenRepository repository;
    private final BusinessMetrics businessMetrics;
    private final Duration ttl;
    private final Duration duracaoMaximaSessao;
    private final Duration toleranciaReuso;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            BusinessMetrics businessMetrics,
            @Value("${jwt.refresh-expiration-ms:604800000}") long expirationMillis,
            @Value("${jwt.refresh-session-max-ms:2592000000}") long sessaoMaximaMillis,
            @Value("${jwt.refresh-reuse-grace-ms:10000}") long toleranciaReusoMillis) {
        this.repository = repository;
        this.businessMetrics = businessMetrics;
        this.ttl = Duration.ofMillis(expirationMillis);
        this.duracaoMaximaSessao = Duration.ofMillis(sessaoMaximaMillis);
        this.toleranciaReuso = Duration.ofMillis(toleranciaReusoMillis);
    }

    /** Emite o primeiro refresh token de uma nova família (login), com o teto absoluto da sessão. */
    @Transactional
    public String emitir(User user) {
        return emitir(user, UUID.randomUUID(), LocalDateTime.now().plus(duracaoMaximaSessao));
    }

    /**
     * Consome o refresh token e emite o próximo da mesma família. As revogações
     * feitas antes de lançar {@link UnauthorizedException} precisam sobreviver à
     * exceção, daí o {@code noRollbackFor}.
     *
     * <p>A ordem das checagens importa: família já revogada e senha trocada vêm
     * antes do reuso, para que um token antigo de uma sessão já encerrada não
     * seja contado como vazamento.
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
        LocalDateTime agora = LocalDateTime.now();

        if (atual.getRevokedAt() != null) {
            log.warn("Refresh rejeitado: sessão já encerrada, userId={}", user.getId());
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }
        if (!user.isAtivo() || atual.getTokenVersion() != user.getTokenVersion()) {
            repository.revogarFamilia(atual.getFamilyId(), agora);
            log.warn("Refresh rejeitado: usuário inativo ou senha alterada, userId={}", user.getId());
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }
        if (atual.getUsedAt() != null) {
            rejeitarReuso(atual, user, agora);
        }
        if (atual.isExpirado()) {
            log.warn("Refresh rejeitado: token expirado, userId={}", user.getId());
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }

        // Entidade gerenciada (carregada com lock nesta transação): o dirty checking grava o used_at.
        atual.setUsedAt(agora);
        String novo = emitir(user, atual.getFamilyId(), atual.getFamilyExpiresAt());
        log.info("Refresh token rotacionado: userId={}", user.getId());
        return new RefreshTokenRotacionado(user, novo);
    }

    /**
     * Reapresentação de token já rotacionado. Dentro da tolerância
     * ({@code jwt.refresh-reuse-grace-ms}) é tratada como retentativa de boa-fé —
     * resposta perdida, duas abas renovando juntas — e só recebe {@code 401}, sem
     * derrubar a sessão: o sucessor emitido segue válido para quem o recebeu.
     * Fora dela, é reuso e a família inteira cai.
     */
    private void rejeitarReuso(RefreshToken atual, User user, LocalDateTime agora) {
        if (agora.isBefore(atual.getUsedAt().plus(toleranciaReuso))) {
            log.info("Refresh rejeitado: token rotacionado há instantes (retentativa), userId={}", user.getId());
            throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
        }
        int revogados = repository.revogarFamilia(atual.getFamilyId(), agora);
        businessMetrics.registrarRefreshTokenReusado();
        log.warn(
                "Reuso de refresh token já rotacionado: família revogada, userId={}, tokensRevogados={}",
                user.getId(),
                revogados);
        throw new UnauthorizedException(ERRO_TOKEN_INVALIDO);
    }

    /**
     * Logout: revoga a sessão inteira (a família), não só o token apresentado —
     * o cliente pode estar segurando uma cópia já rotacionada, e o sucessor dela
     * continuaria renovando a sessão. Idempotente: token desconhecido ou sessão
     * já encerrada não gera erro, para que o logout nunca falhe nem sirva de
     * oráculo sobre quais tokens existem.
     */
    @Transactional
    public void revogar(String rawToken) {
        repository.findByTokenHash(TokenOpaco.hash(rawToken)).ifPresent(token -> {
            Long userId = token.getUser().getId();
            int revogados = repository.revogarFamilia(token.getFamilyId(), LocalDateTime.now());
            if (revogados > 0) {
                log.info("Logout: sessão revogada, userId={}, tokensRevogados={}", userId, revogados);
            }
        });
    }

    /** Remove cadeias vencidas pelo teto absoluto e revogadas há mais de {@link #RETENCAO_REVOGADOS}. */
    @Transactional
    public int limparObsoletos() {
        LocalDateTime agora = LocalDateTime.now();
        return repository.excluirObsoletos(agora, agora.minus(RETENCAO_REVOGADOS));
    }

    private String emitir(User user, UUID familyId, LocalDateTime familyExpiresAt) {
        String rawToken = TokenOpaco.gerar();
        LocalDateTime expiracao = LocalDateTime.now().plus(ttl);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenOpaco.hash(rawToken));
        token.setFamilyId(familyId);
        token.setTokenVersion(user.getTokenVersion());
        token.setFamilyExpiresAt(familyExpiresAt);
        token.setExpiresAt(expiracao.isAfter(familyExpiresAt) ? familyExpiresAt : expiracao);
        repository.save(token);
        return rawToken;
    }

    public record RefreshTokenRotacionado(User user, String refreshToken) {}
}
