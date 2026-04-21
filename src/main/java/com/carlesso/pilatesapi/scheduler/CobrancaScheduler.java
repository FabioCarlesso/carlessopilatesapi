package com.carlesso.pilatesapi.scheduler;

import com.carlesso.pilatesapi.service.PagamentoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CobrancaScheduler {

    private static final Logger log = LoggerFactory.getLogger(CobrancaScheduler.class);

    private final PagamentoService pagamentoService;

    public CobrancaScheduler(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    // Executa todo dia às 06:00 — marca como VENCIDO pagamentos pendentes expirados
    @Scheduled(cron = "0 0 6 * * *")
    public void atualizarPagamentosVencidos() {
        int total = pagamentoService.atualizarVencidos();
        log.info("Pagamentos marcados como VENCIDO: {}", total);
    }

    // Executa todo dia às 07:00 — gera cobranças futuras para planos ativos
    @Scheduled(cron = "0 0 7 * * *")
    public void gerarCobrancasFuturas() {
        int total = pagamentoService.gerarCobrancasFuturas();
        log.info("Cobranças futuras geradas: {}", total);
    }
}
