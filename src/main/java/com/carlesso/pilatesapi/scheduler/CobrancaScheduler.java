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

    @Scheduled(cron = "${app.cobranca.cron-vencidos}")
    public void atualizarPagamentosVencidos() {
        int total = pagamentoService.atualizarVencidos();
        log.info("Pagamentos marcados como VENCIDO: {}", total);
    }

    @Scheduled(cron = "${app.cobranca.cron-cobrancas-futuras}")
    public void gerarCobrancasFuturas() {
        int total = pagamentoService.gerarCobrancasFuturas();
        log.info("Cobranças futuras geradas: {}", total);
    }
}
