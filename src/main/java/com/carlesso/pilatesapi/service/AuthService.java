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

    @Transactional
    public AuthResponseDTO login(AuthLoginRequestDTO dto, String ip) {
        String email = EmailNormalizer.normalizar(dto.email());
        String ipRateLimitKey = IP_RATE_LIMIT_KEY_PREFIX + ip;
        if (loginAttemptService.isIpBlocked(ipRateLimitKey)) {
            businessMetrics.registrarLoginBloqueado();
            log.warn("Login bloqueado por excesso de tentativas do mesmo IP: ip={}", ip);
            throw new TooManyRequestsException("Muitas tentativas. Tente novamente em "
                    + loginAttemptService.windowPorIp().toMinutes() + " minutos.");
        }
        if (loginAttemptService.isBlocked(email)) {
            businessMetrics.registrarLoginBloqueado();
            log.warn("Login bloqueado por excesso de tentativas: email={}", LogMasker.email(email));
            throw new TooManyRequestsException("Muitas tentativas. Tente novamente em 15 minutos.");
        }
        try {
            Authentication auth =
                    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, dto.password()));
            loginAttemptService.registerSuccess(email);
            User user = (User) auth.getPrincipal();
            String accessToken = jwtService.generateToken(user);
            String refreshToken = refreshTokenService.emitir(user);
            log.info("Login bem-sucedido: userId={}, email={}", user.getId(), LogMasker.email(email));
            return AuthResponseDTO.bearer(accessToken, refreshToken, UserResponseDTO.from(user));
        } catch (AuthenticationException e) {
            loginAttemptService.registerFailure(email);
            loginAttemptService.registerIpFailure(ipRateLimitKey);
            log.warn("Falha de autenticação: email={}, ip={}", LogMasker.email(email), ip);
            throw e;
        }
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
