package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
import com.carlesso.pilatesapi.dto.RefreshTokenRequestDTO;
import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import com.carlesso.pilatesapi.util.EmailNormalizer;
import com.carlesso.pilatesapi.util.LogMasker;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String IP_RATE_LIMIT_KEY_PREFIX = "login:";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final BusinessMetrics businessMetrics;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptService loginAttemptService,
            BusinessMetrics businessMetrics) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.businessMetrics = businessMetrics;
    }

    /**
     * As tentativas são reservadas nos dois baldes <em>antes</em> do bcrypt e devolvidas no
     * sucesso. Checar o limite e só contar a falha depois do {@code authenticate()} deixaria
     * uma rajada concorrente inteira passar pela checagem enquanto o bcrypt roda.
     *
     * @param origem chave de origem do cliente, de {@code ClientIpResolver#resolverOrigem}
     */
    @Transactional
    public AuthResponseDTO login(AuthLoginRequestDTO dto, String origem) {
        String email = EmailNormalizer.normalizar(dto.email());
        String origemRateLimitKey = IP_RATE_LIMIT_KEY_PREFIX + origem;
        if (!loginAttemptService.tryAcquireIp(origemRateLimitKey)) {
            businessMetrics.registrarLoginBloqueado();
            log.warn("Login bloqueado por excesso de tentativas da mesma origem: ip={}", LogMasker.ip(origem));
            throw new TooManyRequestsException(mensagemBloqueio(loginAttemptService.windowPorIp()));
        }
        if (!loginAttemptService.tryAcquire(email)) {
            // Barrada pelo e-mail: não consome a cota da origem.
            loginAttemptService.releaseIp(origemRateLimitKey);
            businessMetrics.registrarLoginBloqueado();
            log.warn("Login bloqueado por excesso de tentativas: email={}", LogMasker.email(email));
            throw new TooManyRequestsException(mensagemBloqueio(loginAttemptService.window()));
        }
        try {
            Authentication auth =
                    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, dto.password()));
            loginAttemptService.registerSuccess(email);
            loginAttemptService.releaseIp(origemRateLimitKey);
            User user = (User) auth.getPrincipal();
            String accessToken = jwtService.generateToken(user);
            String refreshToken = refreshTokenService.emitir(user);
            log.info("Login bem-sucedido: userId={}, email={}", user.getId(), LogMasker.email(email));
            return AuthResponseDTO.bearer(accessToken, refreshToken, UserResponseDTO.from(user));
        } catch (AuthenticationException e) {
            // As reservas feitas acima ficam: a falha já está contada nos dois baldes.
            log.warn("Falha de autenticação: email={}, ip={}", LogMasker.email(email), LogMasker.ip(origem));
            throw e;
        }
    }

    private static String mensagemBloqueio(Duration janela) {
        return "Muitas tentativas. Tente novamente em " + janela.toMinutes() + " minutos.";
    }

    /**
     * Sem {@code @Transactional} próprio de propósito: a transação é a de
     * {@link RefreshTokenService#rotacionar}, cujo {@code noRollbackFor} preserva a
     * revogação da família quando o reuso é detectado. Uma transação externa aqui
     * seria marcada para rollback pela mesma exceção e desfaria a revogação.
     */
    public AuthResponseDTO refresh(RefreshTokenRequestDTO dto) {
        var rotacionado = refreshTokenService.rotacionar(dto.refreshToken());
        User user = rotacionado.user();
        return AuthResponseDTO.bearer(
                jwtService.generateToken(user), rotacionado.refreshToken(), UserResponseDTO.from(user));
    }

    public void logout(RefreshTokenRequestDTO dto) {
        refreshTokenService.revogar(dto.refreshToken());
    }
}
