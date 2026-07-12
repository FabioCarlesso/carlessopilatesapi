package com.carlesso.pilatesapi.scheduler;

import com.carlesso.pilatesapi.service.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptCleanupScheduler.class);

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptCleanupScheduler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void limparTentativasExpiradas() {
        loginAttemptService.limparEntradasExpiradas();
        log.debug("Tentativas de login expiradas removidas do cache em memória");
    }
}
