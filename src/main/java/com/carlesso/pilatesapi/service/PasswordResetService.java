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
import com.carlesso.pilatesapi.util.TokenOpaco;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final String RATE_LIMIT_KEY_PREFIX = "forgot-password:";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;
    private final LoginAttemptService loginAttemptService;
    private final BusinessMetrics businessMetrics;
    private final String resetPasswordUrl;
    private final long tokenTtlMinutos;

    public PasswordResetService(
            UserRepository userRepository,
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
    public void solicitarRedefinicao(String email, String ip) {
        String normalizedEmail = EmailNormalizer.normalizar(email);
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + normalizedEmail;
        String ipRateLimitKey = RATE_LIMIT_KEY_PREFIX + ip;
        if (loginAttemptService.isIpBlocked(ipRateLimitKey)) {
            log.warn("Solicitação de redefinição de senha bloqueada por excesso de tentativas do mesmo IP: ip={}", ip);
            throw new TooManyRequestsException("Muitas solicitações. Tente novamente em "
                    + loginAttemptService.windowPorIp().toMinutes() + " minutos.");
        }
        if (loginAttemptService.isBlocked(rateLimitKey)) {
            log.warn(
                    "Solicitação de redefinição de senha bloqueada por excesso de tentativas: email={}",
                    LogMasker.email(normalizedEmail));
            throw new TooManyRequestsException("Muitas solicitações. Tente novamente em 15 minutos.");
        }
        loginAttemptService.registerFailure(rateLimitKey);
        loginAttemptService.registerIpFailure(ipRateLimitKey);

        userRepository.findByEmail(normalizedEmail).filter(User::isAtivo).ifPresent(this::gerarTokenEEnviarEmail);
        log.info("Solicitação de redefinição de senha processada: email={}", LogMasker.email(normalizedEmail));
    }

    @Transactional
    public void redefinirSenha(ResetPasswordRequestDTO dto) {
        if (!dto.novaSenha().equals(dto.confirmacaoNovaSenha())) {
            throw new BusinessException("Confirmação de senha não confere");
        }

        PasswordResetToken resetToken = tokenRepository
                .findByTokenHash(TokenOpaco.hash(dto.token()))
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

        String rawToken = TokenOpaco.gerar();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenOpaco.hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutos));
        tokenRepository.save(token);

        String link = resetPasswordUrl + "?token=" + rawToken;
        emailSender.send(emailTemplateService.criarEmailRedefinicaoSenha(user, link));
        businessMetrics.registrarEmailResetEnviado();
    }
}
