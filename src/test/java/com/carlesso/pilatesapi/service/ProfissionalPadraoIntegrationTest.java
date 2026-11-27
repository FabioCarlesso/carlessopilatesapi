package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.TomadorRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;

/** Fisioterapeuta padrão (#245) contra o PostgreSQL: geração com capacidade e listagem por profissional. */
@PostgresDataJpaTest
class ProfissionalPadraoIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalTime OITO = LocalTime.of(8, 0);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 2);

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    BloqueioAgendaRepository bloqueioRepository;

    @Autowired
    DisponibilidadeProfissionalRepository disponibilidadeRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    TomadorRepository tomadorRepository;

    @Autowired
    TestEntityManager entityManager;

    AulaService aulaService;
    PacienteService pacienteService;

    private int sequencial;

    @BeforeEach
    void setUp() {
        // Capacidade 1 por horário: uma aula de outro aluno já lota o horário.
        aulaService = new AulaService(
                aulaRepository,
                profissionalRepository,
                new AgendaProperties(60, 1),
                bloqueioRepository,
                disponibilidadeRepository);
        pacienteService =
                new PacienteService(pacienteRepository, tomadorRepository, profissionalRepository, aulaService);
    }

    @Test
    void gerarAulas_usaOPadraoEDeixaSemProfissionalOHorarioLotado() {
        Profissional camila = entityManager.persist(profissional("Camila"));
        Paciente ana = paciente("Ana", camila);
        Pagamento outro = pagamento(paciente("Bruno", null));
        Aula ocupante = new Aula();
        ocupante.setPaciente(outro.getPaciente());
        ocupante.setPagamento(outro);
        ocupante.setProfissional(camila);
        ocupante.setData(SEGUNDA.plusDays(7));
        ocupante.setHorario(OITO);
        entityManager.persist(ocupante);
        Pagamento pagamento = pagamento(ana);
        limpar();

        List<Aula> aulas = aulaService.gerarAulas(entityManager.find(Pagamento.class, pagamento.getId()));
        limpar();

        assertThat(aulas)
                .extracting(
                        Aula::getData,
                        a -> a.getProfissional() == null
                                ? null
                                : a.getProfissional().getId())
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(SEGUNDA, camila.getId()),
                        org.assertj.core.api.Assertions.tuple(SEGUNDA.plusDays(7), null),
                        org.assertj.core.api.Assertions.tuple(SEGUNDA.plusDays(14), camila.getId()),
                        org.assertj.core.api.Assertions.tuple(SEGUNDA.plusDays(21), camila.getId()));
        assertThat(aulaRepository.contarPorPagamento(List.of(pagamento.getId())))
                .singleElement()
                .satisfies(c -> assertThat(c.getSemProfissional()).isEqualTo(1L));
    }

    @Test
    void listar_porProfissional_trazSoOsVinculadosAEle() {
        Profissional camila = entityManager.persist(profissional("Camila"));
        Profissional claudia = entityManager.persist(profissional("Claudia"));
        paciente("Ana", camila);
        paciente("Bia", claudia);
        paciente("Caio", null);
        limpar();

        assertThat(pacienteService
                        .listar(null, null, null, null, null, camila.getId(), PageRequest.of(0, 10))
                        .getContent())
                .extracting(p -> p.nome(), p -> p.profissionalPadraoNome())
                .containsExactly(org.assertj.core.api.Assertions.tuple("Ana", "Camila"));
    }

    private void limpar() {
        entityManager.flush();
        entityManager.clear();
    }

    private Profissional profissional(String nome) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail(nome.toLowerCase() + sequencial + "@email.com");
        profissional.setCpf(String.format("9%010d", sequencial));
        profissional.setTipoContrato(TipoContrato.AUTONOMO);
        profissional.setPercentualPagamentoAula(new BigDecimal("50.00"));
        profissional.setDataInicio(LocalDate.of(2026, 1, 1));
        return profissional;
    }

    private Paciente paciente(String nome, Profissional padrao) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail("padrao" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        paciente.setProfissionalPadrao(padrao);
        return entityManager.persist(paciente);
    }

    private Pagamento pagamento(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("300.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setHorario(OITO);
        plano.setDataInicio(SEGUNDA);
        entityManager.persist(plano);

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("300.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataVencimento(SEGUNDA);
        pagamento.setPeriodoInicio(SEGUNDA);
        pagamento.setPeriodoFim(SEGUNDA.plusDays(21));
        return entityManager.persist(pagamento);
    }
}
