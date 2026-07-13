package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.email.EmailSender;
import com.carlesso.pilatesapi.email.EmailTemplateService;
import com.carlesso.pilatesapi.entity.PasswordResetToken;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import com.carlesso.pilatesapi.repository.PasswordResetTokenRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.util.EmailNormalizer;
import com.carlesso.pilatesapi.util.LogMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "forgot-password:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;
    private final LoginAttemptService loginAttemptService;
    private final BusinessMetrics businessMetrics;
    private final String resetPasswordUrl;
    private final long tokenTtlMinutos;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 UserService userService,
                                 EmailSender emailSender,
                                 EmailTemplateService emailTemplateService,
                                 LoginAttemptService loginAttemptService,
                                 BusinessMetrics businessMetrics,
                                 @Value("${app.email.reset-password-url}") String resetPasswordUrl,
                                 @Value("${app.email.reset-password-token-ttl-minutos:30}") long tokenTtlMinutos) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userService = userService;
        this.emailSender = emailSender;
        this.emailTemplateService = emailTemplateService;
        this.loginAttemptService = loginAttemptService;
        this.businessMetrics = businessMetrics;
        this.resetPasswordUrl = resetPasswordUrl;
        this.tokenTtlMinutos = tokenTtlMinutos;
    }

    @Transactional
    public void solicitarRedefinicao(String email) {
        String normalizedEmail = EmailNormalizer.normalizar(email);
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + normalizedEmail;
        if (loginAttemptService.isBlocked(rateLimitKey)) {
            log.warn("Solicitação de redefinição de senha bloqueada por excesso de tentativas: email={}",
                    LogMasker.email(normalizedEmail));
            throw new TooManyRequestsException("Muitas solicitações. Tente novamente em 15 minutos.");
        }
        loginAttemptService.registerFailure(rateLimitKey);

        userRepository.findByEmail(normalizedEmail)
                .filter(User::isAtivo)
                .ifPresent(this::gerarTokenEEnviarEmail);
        log.info("Solicitação de redefinição de senha processada: email={}", LogMasker.email(normalizedEmail));
    }

    @Transactional
    public void redefinirSenha(ResetPasswordRequestDTO dto) {
        if (!dto.novaSenha().equals(dto.confirmacaoNovaSenha())) {
            throw new BusinessException("Confirmação de senha não confere");
        }

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(dto.token()))
                .filter(PasswordResetToken::isValido)
                .orElseThrow(() -> {
                    log.warn("Redefinição de senha rejeitada: token inválido ou expirado");
                    return new BusinessException("Token inválido ou expirado");
                });

        User user = resetToken.getUser();
        userService.aplicarNovaSenha(user, dto.novaSenha());
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);
        log.info("Senha redefinida com sucesso: userId={}", user.getId());
    }

    private void gerarTokenEEnviarEmail(User user) {
        tokenRepository.invalidarTokensAtivos(user, LocalDateTime.now());

        String rawToken = gerarTokenAleatorio();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutos));
        tokenRepository.save(token);

        String link = resetPasswordUrl + "?token=" + rawToken;
        emailSender.send(emailTemplateService.criarEmailRedefinicaoSenha(user, link));
        businessMetrics.registrarEmailResetEnviado();
    }

    private String gerarTokenAleatorio() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash indisponível", e);
        }
    }
}
