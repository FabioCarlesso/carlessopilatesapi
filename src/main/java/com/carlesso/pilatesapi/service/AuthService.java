package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
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

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final BusinessMetrics businessMetrics;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            BusinessMetrics businessMetrics) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.businessMetrics = businessMetrics;
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(AuthLoginRequestDTO dto) {
        String email = EmailNormalizer.normalizar(dto.email());
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
            String token = jwtService.generateToken(user);
            log.info("Login bem-sucedido: userId={}, email={}", user.getId(), LogMasker.email(email));
            return AuthResponseDTO.bearer(token, UserResponseDTO.from(user));
        } catch (AuthenticationException e) {
            loginAttemptService.registerFailure(email);
            log.warn("Falha de autenticação: email={}", LogMasker.email(email));
            throw e;
        }
    }
}
