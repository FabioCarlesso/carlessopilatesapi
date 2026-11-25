package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO;
import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO.PacienteFrequencia;
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
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** Frequência e faltas (#233) contra o PostgreSQL: o que é falta, o corte em hoje, taxa, filtros e ordenação. */
@PostgresDataJpaTest
class RelatorioFrequenciaServiceIntegrationTest extends PostgresTestcontainerSupport {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    TestEntityManager entityManager;

    RelatorioFrequenciaService service;

    private int sequencial;
    private Paciente ana;
    private Paciente bruno;
    private Paciente carla;
    private Profissional paula;
    private Profissional ricardo;

    @BeforeEach
    void setUp() {
        // 23h no fuso do estúdio: em UTC já é dia 26, e o corte tem de continuar sendo o dia 25 local.
        Clock relogio = Clock.fixed(HOJE.atTime(23, 0).atZone(FUSO).toInstant(), FUSO);
        service = new RelatorioFrequenciaService(aulaRepository, profissionalRepository, relogio);
    }

    @Test
    void gerar_oitoAulasPassadasESeisRealizadas_mostraDuasFaltasE75PorCento() {
        montarMassa();

        RelatorioFrequenciaDTO relatorio = service.gerar(INICIO, FIM, null, null, null);

        PacienteFrequencia daAna = linha(relatorio, ana);
        // As aulas de hoje (25) e as futuras não contam, nem como prevista nem como falta.
        assertThat(daAna.aulasPrevistas()).isEqualTo(8);
        assertThat(daAna.aulasRealizadas()).isEqualTo(6);
        assertThat(daAna.faltas()).isEqualTo(2);
        assertThat(daAna.taxaPresenca()).isEqualByComparingTo("75.0");
        assertThat(daAna.ultimaAulaRealizada()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(relatorio.consideradoAte()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void gerar_ordenaPelaMenorPresenca_eSomaOsTotais() {
        montarMassa();

        RelatorioFrequenciaDTO relatorio = service.gerar(INICIO, FIM, null, null, null);

        // Bruno 1/3 (33,3%), Ana 6/8 (75%), Carla inativa 2/2 (100%).
        assertThat(relatorio.pacientes())
                .extracting(
                        PacienteFrequencia::pacienteNome,
                        PacienteFrequencia::ativo,
                        PacienteFrequencia::faltas,
                        p -> p.taxaPresenca().toPlainString())
                .containsExactly(
                        tuple("Bruno", true, 2L, "33.3"),
                        tuple("Ana", true, 2L, "75.0"),
                        tuple("Carla", false, 0L, "100.0"));
        assertThat(relatorio.totais())
                .isEqualTo(new RelatorioFrequenciaDTO.Totais(3, 13, 9, 4, new BigDecimal("69.2")));
    }

    @Test
    void gerar_taxaMaxima_trazSoQuemEstaAbaixoDoValor() {
        montarMassa();

        // 75 exato não entra: o filtro é "abaixo de".
        assertThat(service.gerar(INICIO, FIM, null, null, new BigDecimal("75")).pacientes())
                .extracting(PacienteFrequencia::pacienteNome)
                .containsExactly("Bruno");
        assertThat(service.gerar(INICIO, FIM, null, null, new BigDecimal("75.1"))
                        .pacientes())
                .extracting(PacienteFrequencia::pacienteNome)
                .containsExactly("Bruno", "Ana");
        assertThat(service.gerar(INICIO, FIM, null, null, BigDecimal.ZERO).pacientes())
                .isEmpty();
    }

    @Test
    void gerar_profissionalEPaciente_restringemAsAulasContadas() {
        montarMassa();

        RelatorioFrequenciaDTO daPaula = service.gerar(INICIO, FIM, paula.getId(), null, null);
        // Só as aulas com a Paula: as 8 da Ana; as do Bruno são com o Ricardo e as da Carla, sem profissional.
        assertThat(daPaula.pacientes())
                .extracting(PacienteFrequencia::pacienteNome, PacienteFrequencia::aulasPrevistas)
                .containsExactly(tuple("Ana", 8L));
        assertThat(daPaula.filtros().profissionalNome()).isEqualTo("Paula");

        RelatorioFrequenciaDTO doBruno = service.gerar(INICIO, FIM, null, bruno.getId(), null);
        assertThat(doBruno.pacientes())
                .extracting(PacienteFrequencia::pacienteNome)
                .containsExactly("Bruno");
        assertThat(doBruno.totais().pacientes()).isEqualTo(1);
    }

    @Test
    void gerar_periodoTodoNoFuturo_vemVazioSemErro() {
        montarMassa();

        RelatorioFrequenciaDTO relatorio = service.gerar(HOJE, LocalDate.of(2026, 12, 31), null, null, null);

        assertThat(relatorio.pacientes()).isEmpty();
        assertThat(relatorio.totais()).isEqualTo(new RelatorioFrequenciaDTO.Totais(0, 0, 0, 0, null));
        assertThat(relatorio.consideradoAte()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void gerar_entradasInvalidas_lancamIllegalArgument() {
        assertThatThrownBy(() -> service.gerar(null, FIM, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar(FIM, INICIO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
        assertThatThrownBy(() -> service.gerar(INICIO, FIM, null, null, new BigDecimal("100.5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taxaMaxima");
        assertThatThrownBy(() -> service.gerar(INICIO, FIM, null, null, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PacienteFrequencia linha(RelatorioFrequenciaDTO relatorio, Paciente paciente) {
        return relatorio.pacientes().stream()
                .filter(p -> p.pacienteId().equals(paciente.getId()))
                .findFirst()
                .orElseThrow();
    }

    private void montarMassa() {
        paula = entityManager.persist(profissional("Paula"));
        ricardo = entityManager.persist(profissional("Ricardo"));
        ana = entityManager.persist(paciente("Ana", true));
        bruno = entityManager.persist(paciente("Bruno", true));
        carla = entityManager.persist(paciente("Carla", false));

        // Ana: 8 aulas passadas (6 realizadas), mais hoje e duas futuras não realizadas, que não contam.
        Pagamento pagamentoAna = pagamento(ana);
        int[] diasAna = {2, 4, 6, 9, 11, 13, 16, 20};
        boolean[] realizadas = {true, true, false, true, true, false, true, true};
        for (int i = 0; i < diasAna.length; i++) {
            aula(ana, pagamentoAna, paula, LocalDate.of(2026, 11, diasAna[i]), realizadas[i]);
        }
        aula(ana, pagamentoAna, paula, HOJE, false);
        aula(ana, pagamentoAna, paula, LocalDate.of(2026, 11, 27), false);
        aula(ana, pagamentoAna, paula, LocalDate.of(2026, 11, 30), false);
        // Fora do período: não conta.
        aula(ana, pagamentoAna, paula, LocalDate.of(2026, 10, 30), false);

        // Bruno: 3 passadas com o Ricardo, só uma realizada.
        Pagamento pagamentoBruno = pagamento(bruno);
        aula(bruno, pagamentoBruno, ricardo, LocalDate.of(2026, 11, 3), true);
        aula(bruno, pagamentoBruno, ricardo, LocalDate.of(2026, 11, 10), false);
        aula(bruno, pagamentoBruno, ricardo, LocalDate.of(2026, 11, 17), false);

        // Carla (inativa): 2 passadas sem profissional, ambas realizadas; aparece no relatório.
        Pagamento pagamentoCarla = pagamento(carla);
        aula(carla, pagamentoCarla, null, LocalDate.of(2026, 11, 5), true);
        aula(carla, pagamentoCarla, null, LocalDate.of(2026, 11, 12), true);

        entityManager.flush();
        entityManager.clear();
    }

    private void aula(Paciente paciente, Pagamento pagamento, Profissional profissional, LocalDate data, boolean ok) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setRealizada(ok);
        entityManager.persist(aula);
    }

    private Paciente paciente(String nome, boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail("frequencia" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        paciente.setAtivo(ativo);
        return paciente;
    }

    private Profissional profissional(String nome) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail("prof-frequencia" + sequencial + "@email.com");
        profissional.setCpf(String.format("%011d", 90_000 + sequencial));
        profissional.setTipoContrato(TipoContrato.AUTONOMO);
        profissional.setPercentualPagamentoAula(new BigDecimal("50.00"));
        profissional.setDataInicio(LocalDate.of(2026, 1, 1));
        return profissional;
    }

    private Pagamento pagamento(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("300.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.DUAS_VEZES);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        plano.setDataInicio(LocalDate.of(2026, 1, 1));
        entityManager.persist(plano);

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("300.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataVencimento(LocalDate.of(2026, 11, 1));
        pagamento.setPeriodoInicio(LocalDate.of(2026, 11, 1));
        pagamento.setPeriodoFim(LocalDate.of(2026, 11, 30));
        pagamento.setDataPagamento(LocalDate.of(2026, 11, 1));
        return entityManager.persist(pagamento);
    }
}
