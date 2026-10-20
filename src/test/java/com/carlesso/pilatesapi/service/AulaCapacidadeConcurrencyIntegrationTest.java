package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
 * Duas atribuições simultâneas à última vaga de um horário (#106): só uma pode passar. Nenhuma constraint do banco
 * garante a capacidade, então quem garante é o lock no profissional — sem ele, a segunda transação contaria a ocupação
 * sem enxergar a primeira, ainda não comitada, e as duas passariam juntas do limite.
 */
@SpringBootTest
class AulaCapacidadeConcurrencyIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalDate DIA = LocalDate.of(2026, 12, 1);
    private static final LocalTime OITO_HORAS = LocalTime.of(8, 0);

    @Autowired
    private AulaService aulaService;

    @Autowired
    private AulaRepository aulaRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private ProfissionalRepository profissionalRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Paciente> pacientes = new ArrayList<>();
    private final List<Aula> aulas = new ArrayList<>();
    private Profissional profissional;
    private Plano plano;
    private Pagamento pagamento;

    @BeforeEach
    void setUp() {
        profissional = new Profissional();
        profissional.setNome("Paula Capacidade");
        profissional.setEmail("paula-capacidade@email.com");
        profissional.setCpf("90000000106");
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2026, 1, 1));
        profissional = profissionalRepository.save(profissional);

        for (int i = 0; i < 4; i++) {
            Paciente paciente = new Paciente();
            paciente.setNome("Paciente Capacidade " + i);
            paciente.setEmail("capacidade" + i + "@email.com");
            paciente.setCpf("9000000010" + i);
            pacientes.add(pacienteRepository.save(paciente));
        }

        plano = new Plano();
        plano.setPaciente(pacientes.get(0));
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.TUESDAY));
        plano.setDataInicio(DIA);
        plano = planoRepository.save(plano);

        pagamento = new Pagamento();
        pagamento.setPaciente(pacientes.get(0));
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataVencimento(DIA.plusDays(9));
        pagamento.setPeriodoInicio(DIA);
        pagamento.setPeriodoFim(DIA.plusMonths(1).minusDays(1));
        pagamento = pagamentoRepository.save(pagamento);

        // Capacidade 3 (application.properties de teste): duas vagas já ocupadas, a terceira em disputa.
        for (int i = 0; i < 4; i++) {
            Aula aula = new Aula();
            aula.setPaciente(pacientes.get(i));
            aula.setPagamento(pagamento);
            aula.setData(DIA);
            aula.setHorario(OITO_HORAS);
            aula.setProfissional(i < 2 ? profissional : null);
            aulas.add(aulaRepository.save(aula));
        }
    }

    // Os dados comitam de verdade: sem limpar, vazariam para os demais testes que compartilham o container.
    @AfterEach
    void tearDown() {
        aulaRepository.deleteAllById(aulas.stream().map(Aula::getId).toList());
        pagamentoRepository.deleteById(pagamento.getId());
        planoRepository.deleteById(plano.getId());
        pacienteRepository.deleteAllById(pacientes.stream().map(Paciente::getId).toList());
        profissionalRepository.deleteById(profissional.getId());
    }

    @Test
    void duasAtribuicoesPelaUltimaVaga_aSegundaEsperaOCommitERecebeConflito() throws Exception {
        Long terceira = aulas.get(2).getId();
        Long quarta = aulas.get(3).getId();
        CountDownLatch primeiraComLock = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                aulaService.atribuirProfissional(terceira, profissional.getId());
                primeiraComLock.countDown();
                // Segura o lock tempo bastante para a segunda atribuição chegar à checagem e ficar esperando.
                dormir(500);
            }));
            var segunda = executor.submit(() -> {
                assertThat(primeiraComLock.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    aulaService.atribuirProfissional(quarta, profissional.getId());
                    return null;
                } catch (ConflictException e) {
                    return e;
                }
            });

            primeira.get(10, TimeUnit.SECONDS);
            assertThat(segunda.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("capacidade por horário é 3");
        }

        assertThat(aulaRepository.countConcorrentesDoProfissional(
                        profissional.getId(), DIA, OITO_HORAS.minusHours(1), OITO_HORAS.plusHours(1), -1L))
                .isEqualTo(3);
        assertThat(aulaRepository.findById(quarta).orElseThrow().getProfissional())
                .isNull();
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
