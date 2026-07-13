package com.carlesso.pilatesapi.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Contadores de negócio expostos em /actuator/prometheus. Os contadores são
 * registrados na criação do bean para aparecerem no scrape com valor 0 desde a
 * subida da aplicação, permitindo alertas por taxa (rate) sem esperar o primeiro
 * evento.
 */
@Component
public class BusinessMetrics {

    private final Counter cobrancasGeradas;
    private final Counter cobrancasVencidas;
    private final Counter pagamentosConfirmados;
    private final Counter loginsBloqueados;
    private final Counter emailsResetEnviados;

    public BusinessMetrics(MeterRegistry registry) {
        this.cobrancasGeradas = Counter.builder("pilates.cobrancas.geradas")
                .description("Cobranças futuras geradas pelo scheduler")
                .register(registry);
        this.cobrancasVencidas = Counter.builder("pilates.cobrancas.vencidas")
                .description("Pagamentos marcados como VENCIDO pelo scheduler")
                .register(registry);
        this.pagamentosConfirmados = Counter.builder("pilates.pagamentos.confirmados")
                .description("Pagamentos confirmados")
                .register(registry);
        this.loginsBloqueados = Counter.builder("pilates.logins.bloqueados")
                .description("Tentativas de login bloqueadas pelo rate limit")
                .register(registry);
        this.emailsResetEnviados = Counter.builder("pilates.emails.reset.enviados")
                .description("E-mails de redefinição de senha enviados")
                .register(registry);
    }

    public void registrarCobrancasGeradas(int total) {
        cobrancasGeradas.increment(total);
    }

    public void registrarCobrancasVencidas(int total) {
        cobrancasVencidas.increment(total);
    }

    public void registrarPagamentoConfirmado() {
        pagamentosConfirmados.increment();
    }

    public void registrarLoginBloqueado() {
        loginsBloqueados.increment();
    }

    public void registrarEmailResetEnviado() {
        emailsResetEnviados.increment();
    }
}
