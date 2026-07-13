package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.config.AppProperties;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.carlesso.pilatesapi.support.MetricsTestConfig;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@PostgresDataJpaTest
@Import({PagamentoService.class, AulaService.class, MetricsTestConfig.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.cobranca.cron-vencidos=0 0 6 * * *",
        "app.cobranca.cron-cobrancas-futuras=0 0 7 * * *",
        "app.cobranca.vencimento-dias=10"
})
class PagamentoServiceAtomicidadeIntegrationTest extends PostgresTestcontainerSupport {

    @SpyBean
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * Verifica que um erro no meio do loop faz rollback de todos os saves anteriores.
     * A @Transactional garante que nenhum pagamento parcial seja persistido.
     */
    @Test
    void gerarCobrancasFuturas_rollbackQuandoSegundoSaveFalha() {
        // Salva e commita dois planos ativos
        Paciente p1 = pacienteRepository.save(paciente("Ana", "ana@email.com", "11111111111", true));
        Paciente p2 = pacienteRepository.save(paciente("Bia", "bia@email.com", "22222222222", true));
        planoRepository.save(plano(p1));
        planoRepository.save(plano(p2));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // Primeiro save usa EntityManager para persistir de verdade na transação do serviço;
        // segundo save lança exceção simulando falha de banco a meio do loop.
        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(inv -> {
            if (callCount.incrementAndGet() >= 2) {
                throw new RuntimeException("simulated DB failure");
            }
            Pagamento p = inv.getArgument(0);
            entityManager.persistAndFlush(p);
            return p;
        }).when(pagamentoRepository).save(any(Pagamento.class));

        assertThatThrownBy(() -> pagamentoService.gerarCobrancasFuturas())
                .isInstanceOf(RuntimeException.class);

        // Após o rollback, nenhum pagamento deve ter sido persistido
        reset(pagamentoRepository);
        assertThat(pagamentoRepository.count()).isEqualTo(0);

        // Limpa os dados commitados no setup
        TestTransaction.start();
        pagamentoRepository.deleteAll();
        planoRepository.deleteAll();
        pacienteRepository.deleteAll();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private Paciente paciente(String nome, String email, String cpf, boolean ativo) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setEmail(email);
        p.setCpf(cpf);
        p.setAtivo(ativo);
        return p;
    }

    private Plano plano(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("250.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2025, 1, 1));
        return plano;
    }
}
