package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.email.EmailSender;
import com.carlesso.pilatesapi.email.EmailTemplateService;
import com.carlesso.pilatesapi.entity.PasswordResetToken;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.repository.PasswordResetTokenRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {

    private static final String RATE_LIMIT_KEY_PREFIX = "forgot-password:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;
    private final LoginAttemptService loginAttemptService;
    private final String resetPasswordUrl;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 EmailSender emailSender,
                                 EmailTemplateService emailTemplateService,
                                 LoginAttemptService loginAttemptService,
                                 @Value("${app.email.reset-password-url}") String resetPasswordUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.emailTemplateService = emailTemplateService;
        this.loginAttemptService = loginAttemptService;
        this.resetPasswordUrl = resetPasswordUrl;
    }

    @Transactional
    public void solicitarRedefinicao(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + normalizedEmail;
        if (loginAttemptService.isBlocked(rateLimitKey)) {
            throw new TooManyRequestsException("Muitas solicitações. Tente novamente em 15 minutos.");
        }
        loginAttemptService.registerFailure(rateLimitKey);

        userRepository.findByEmail(normalizedEmail)
                .filter(User::isAtivo)
                .ifPresent(this::gerarTokenEEnviarEmail);
    }

    @Transactional
    public void redefinirSenha(ResetPasswordRequestDTO dto) {
        if (!dto.novaSenha().equals(dto.confirmacaoNovaSenha())) {
            throw new BusinessException("Confirmação de senha não confere");
        }

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(dto.token()))
                .filter(t -> t.isValido(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("Token inválido ou expirado"));

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(dto.novaSenha()));
        user.incrementarTokenVersion();
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);
    }

    private void gerarTokenEEnviarEmail(User user) {
        String rawToken = gerarTokenAleatorio();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
        tokenRepository.save(token);

        String link = resetPasswordUrl + "?token=" + rawToken;
        emailSender.send(emailTemplateService.criarEmailRedefinicaoSenha(user, link));
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
