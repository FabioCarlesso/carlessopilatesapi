package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.ContatoCobrancaRequestDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registrar contato e confirmar o pagamento ao mesmo tempo (#197): o registro precisa esperar a confirmação em curso e
 * então enxergar {@code PAGO}. Sem o lock de escrita na leitura do pagamento, ele leria o {@code PENDENTE} anterior ao
 * commit e gravaria o contato num pagamento já pago.
 */
@SpringBootTest
class ContatoCobrancaConcurrencyIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private ContatoCobrancaService contatoCobrancaService;

    @Autowired
    private ContatoCobrancaRepository contatoCobrancaRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Paciente paciente;
    private Plano plano;
    private Pagamento pagamento;

    @BeforeEach
    void setUp() {
        paciente = new Paciente();
        paciente.setNome("Paciente Concorrência");
        paciente.setEmail("concorrencia-cobranca@email.com");
        paciente.setCpf("90000000099");
        paciente = pacienteRepository.save(paciente);

        plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("250.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2026, 1, 1));
        plano = planoRepository.save(plano);

        pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("250.00"));
        pagamento.setStatus(StatusPagamento.VENCIDO);
        pagamento.setDataVencimento(LocalDate.of(2026, 1, 10));
        pagamento.setPeriodoInicio(LocalDate.of(2026, 1, 1));
        pagamento.setPeriodoFim(LocalDate.of(2026, 1, 31));
        pagamento = pagamentoRepository.save(pagamento);
    }

    // Os dados comitam de verdade: sem limpar, vazariam para os demais testes que compartilham o container.
    @AfterEach
    void tearDown() {
        contatoCobrancaRepository.deleteAll();
        pagamentoRepository.deleteById(pagamento.getId());
        planoRepository.deleteById(plano.getId());
        pacienteRepository.deleteById(paciente.getId());
    }

    @Test
    void registrarDuranteConfirmacao_esperaOCommitERecusaPorPagamentoPago() throws Exception {
        CountDownLatch confirmacaoComLock = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var confirmacao = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                Pagamento emConfirmacao =
                        pagamentoRepository.findByIdForUpdate(pagamento.getId()).orElseThrow();
                emConfirmacao.setStatus(StatusPagamento.PAGO);
                emConfirmacao.setDataPagamento(LocalDate.of(2026, 1, 12));
                confirmacaoComLock.countDown();
                // Segura o lock tempo bastante para o registro chegar à leitura do pagamento e ficar esperando.
                dormir(500);
            }));
            var registro = executor.submit(() -> {
                assertThat(confirmacaoComLock.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    contatoCobrancaService.registrar(
                            pagamento.getId(),
                            new ContatoCobrancaRequestDTO(LocalDate.of(2026, 1, 11), "Ligação"),
                            "admin@email.com");
                    return null;
                } catch (BusinessException e) {
                    return e;
                }
            });

            confirmacao.get(10, TimeUnit.SECONDS);
            assertThat(registro.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessException.class);
        }

        assertThat(contatoCobrancaRepository.count()).isZero();
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
