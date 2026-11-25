package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.dto.PendenciaCadastroPaciente;
import com.carlesso.pilatesapi.dto.RelatorioPacientesDTO;
import com.carlesso.pilatesapi.dto.SituacaoPacienteRelatorio;
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
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** Relatório de pacientes (#230) contra o PostgreSQL: filtros, pendências, plano ativo e profissional. */
@PostgresDataJpaTest
class RelatorioPacientesServiceIntegrationTest extends PostgresTestcontainerSupport {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    PlanoRepository planoRepository;

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    TestEntityManager entityManager;

    RelatorioPacientesService service;

    private int sequencial;
    private Profissional paula;
    private Profissional ana;
    private Paciente bruna;
    private Paciente carlos;
    private Paciente diana;
    private Paciente eduardo;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
        service = new RelatorioPacientesService(
                pacienteRepository, planoRepository, aulaRepository, profissionalRepository, relogio);
        montarMassa();
    }

    @Test
    void ativos_porPadrao_ordenadosPorNome_comPlanoEProfissional() {
        RelatorioPacientesDTO relatorio = service.gerar(null, null, null, null, null);

        assertThat(relatorio.filtros().situacao()).isEqualTo(SituacaoPacienteRelatorio.ATIVOS);
        assertThat(relatorio.pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::nome)
                .containsExactly("Bruna", "Carlos", "Diana");
        assertThat(relatorio.totais()).isEqualTo(new RelatorioPacientesDTO.Totais(3, 3, 0, 2));

        RelatorioPacientesDTO.PacienteItem bruna = relatorio.pacientes().getFirst();
        assertThat(bruna.idade()).isEqualTo(36);
        assertThat(bruna.pendencias()).isEmpty();
        assertThat(bruna.planoAtivo().frequenciaSemanal()).isEqualTo(FrequenciaSemanal.DUAS_VEZES);
        assertThat(bruna.planoAtivo().diasSemana()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(bruna.planoAtivo().horario()).isEqualTo(LocalTime.of(8, 0));
        assertThat(bruna.planoAtivo().valor()).isEqualByComparingTo("300.00");
        // A próxima aula não realizada é da Ana; a de ontem (Paula) e a realizada não contam.
        assertThat(bruna.profissional().nome()).isEqualTo("Ana");
    }

    @Test
    void todos_trazAtivosEInativos_eInativoNaoTemPendenciaDePlano() {
        RelatorioPacientesDTO relatorio = service.gerar(SituacaoPacienteRelatorio.TODOS, null, null, null, null);

        assertThat(relatorio.pacientes()).hasSize(4);
        assertThat(relatorio.totais()).isEqualTo(new RelatorioPacientesDTO.Totais(4, 3, 1, 2));
        RelatorioPacientesDTO.PacienteItem eduardo = relatorio.pacientes().getLast();
        assertThat(eduardo.nome()).isEqualTo("Eduardo");
        assertThat(eduardo.ativo()).isFalse();
        assertThat(eduardo.dataInativacao()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(eduardo.pendencias()).isEmpty();

        assertThat(service.gerar(SituacaoPacienteRelatorio.INATIVOS, null, null, null, null)
                        .pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::id)
                .containsExactly(this.eduardo.getId());
    }

    @Test
    void semPlanoAtivo_trazSoAtivosSemPlano_mesmoComSituacaoTodos() {
        RelatorioPacientesDTO relatorio = service.gerar(SituacaoPacienteRelatorio.TODOS, true, null, null, null);

        assertThat(relatorio.pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::id)
                .containsExactly(diana.getId());
        assertThat(relatorio.filtros().semPlanoAtivo()).isTrue();
    }

    @Test
    void cadastroIncompleto_listaCadaPendencia() {
        RelatorioPacientesDTO relatorio = service.gerar(SituacaoPacienteRelatorio.TODOS, null, true, null, null);

        assertThat(relatorio.pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::id)
                .containsExactly(carlos.getId(), diana.getId());
        assertThat(relatorio.pacientes().get(0).pendencias())
                .containsExactly(
                        PendenciaCadastroPaciente.SEM_CPF,
                        PendenciaCadastroPaciente.SEM_EMAIL,
                        PendenciaCadastroPaciente.SEM_TELEFONE,
                        PendenciaCadastroPaciente.SEM_DATA_NASCIMENTO);
        assertThat(relatorio.pacientes().get(1).pendencias())
                .containsExactly(PendenciaCadastroPaciente.SEM_PLANO_ATIVO);
        assertThat(relatorio.totais().comPendencia()).isEqualTo(2);
    }

    @Test
    void mesNascimento_trazAniversariantes() {
        assertThat(service.gerar(SituacaoPacienteRelatorio.TODOS, null, null, 12, null)
                        .pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::id)
                .containsExactly(bruna.getId(), eduardo.getId());
        assertThat(service.gerar(SituacaoPacienteRelatorio.TODOS, null, null, 6, null)
                        .pacientes())
                .isEmpty();
    }

    @Test
    void mesNascimento_foraDoIntervalo_recusa() {
        assertThatThrownBy(() -> service.gerar(null, null, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar(null, null, null, 13, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profissional_filtraPeloProfissionalDasProximasAulas() {
        RelatorioPacientesDTO daAna = service.gerar(null, null, null, null, ana.getId());
        assertThat(daAna.pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::id)
                .containsExactly(bruna.getId());
        assertThat(daAna.filtros().profissionalNome()).isEqualTo("Ana");

        RelatorioPacientesDTO daPaula = service.gerar(null, null, null, null, paula.getId());
        assertThat(daPaula.pacientes())
                .extracting(RelatorioPacientesDTO.PacienteItem::id)
                .containsExactly(carlos.getId());
    }

    private void montarMassa() {
        paula = entityManager.persist(profissional("Paula"));
        ana = entityManager.persist(profissional("Ana"));

        // Completa, com plano ativo e aulas: a próxima é da Ana.
        bruna = entityManager.persist(paciente("Bruna", true, LocalDate.of(1989, 12, 3), true));
        Plano planoBruna = plano(bruna, true, LocalTime.of(8, 0), DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY);
        Pagamento pagamentoBruna = pagamento(bruna, planoBruna);
        aula(bruna, pagamentoBruna, paula, HOJE.minusDays(1), false);
        aula(bruna, pagamentoBruna, paula, HOJE.minusDays(3), true);
        aula(bruna, pagamentoBruna, ana, HOJE, false);
        aula(bruna, pagamentoBruna, paula, HOJE.plusDays(2), false);
        aula(bruna, pagamentoBruna, null, HOJE.plusDays(4), false);

        // Cadastro incompleto (sem CPF, e-mail, telefone e nascimento), com plano: próxima aula da Paula.
        carlos = entityManager.persist(paciente("Carlos", true, null, false));
        Plano planoCarlos = plano(carlos, true, null, DayOfWeek.TUESDAY);
        Pagamento pagamentoCarlos = pagamento(carlos, planoCarlos);
        aula(carlos, pagamentoCarlos, null, HOJE, false);
        aula(carlos, pagamentoCarlos, paula, HOJE.plusDays(1), false);

        // Ativa, completa, só com plano inativo: pendência de plano.
        diana = entityManager.persist(paciente("Diana", true, LocalDate.of(1990, 3, 10), true));
        plano(diana, false, null, DayOfWeek.FRIDAY);

        // Inativo, sem plano ativo: não é pendência para quem saiu.
        eduardo = entityManager.persist(paciente("Eduardo", false, LocalDate.of(1975, 12, 20), true));
        eduardo.setDataInativacao(LocalDate.of(2026, 10, 1));

        entityManager.flush();
        entityManager.clear();
    }

    private Paciente paciente(String nome, boolean ativo, LocalDate nascimento, boolean completo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setAtivo(ativo);
        paciente.setDataNascimento(nascimento);
        paciente.setDataCadastro(LocalDate.of(2026, 1, sequencial));
        if (completo) {
            paciente.setEmail("pacientes" + sequencial + "@email.com");
            paciente.setCpf(String.format("%011d", sequencial));
            paciente.setTelefone("4599999000" + sequencial);
        } else {
            paciente.setEmail(" ");
        }
        return paciente;
    }

    private Profissional profissional(String nome) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail("prof-pacientes" + sequencial + "@email.com");
        profissional.setCpf(String.format("9%010d", sequencial));
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        profissional.setAtivo(true);
        return profissional;
    }

    private Plano plano(Paciente paciente, boolean ativo, LocalTime horario, DayOfWeek... dias) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("300.00"));
        plano.setFrequenciaSemanal(dias.length == 1 ? FrequenciaSemanal.UMA_VEZ : FrequenciaSemanal.DUAS_VEZES);
        plano.setDiasSemana(List.of(dias));
        plano.setHorario(horario);
        plano.setDataInicio(LocalDate.of(2026, 1, 1));
        plano.setAtivo(ativo);
        return entityManager.persist(plano);
    }

    private Pagamento pagamento(Paciente paciente, Plano plano) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(plano.getValor());
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataPagamento(HOJE.withDayOfMonth(1));
        pagamento.setDataVencimento(HOJE.withDayOfMonth(1));
        pagamento.setPeriodoInicio(HOJE.withDayOfMonth(1));
        pagamento.setPeriodoFim(HOJE.withDayOfMonth(HOJE.lengthOfMonth()));
        return entityManager.persist(pagamento);
    }

    private void aula(
            Paciente paciente, Pagamento pagamento, Profissional profissional, LocalDate data, boolean realizada) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setHorario(LocalTime.of(8, 0));
        aula.setRealizada(realizada);
        entityManager.persist(aula);
    }
}
