package com.carlesso.pilatesapi.scheduler;

import com.carlesso.pilatesapi.service.LoginAttemptService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptCleanupScheduler {

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptCleanupScheduler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void limparTentativasExpiradas() {
        loginAttemptService.limparEntradasExpiradas();
    }
}
