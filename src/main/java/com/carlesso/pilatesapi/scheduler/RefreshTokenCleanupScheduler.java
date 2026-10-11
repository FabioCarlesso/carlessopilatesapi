package com.carlesso.pilatesapi.scheduler;

import com.carlesso.pilatesapi.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada rotação grava uma linha nova em {@code refresh_tokens}; sem esta limpeza
 * a tabela cresceria para sempre.
 */
@Component
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupScheduler(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "${app.refresh-token.cron-limpeza:0 30 3 * * *}")
    public void limparRefreshTokensObsoletos() {
        int removidos = refreshTokenService.limparObsoletos();
        log.info("Limpeza de refresh tokens obsoletos: removidos={}", removidos);
    }
}
