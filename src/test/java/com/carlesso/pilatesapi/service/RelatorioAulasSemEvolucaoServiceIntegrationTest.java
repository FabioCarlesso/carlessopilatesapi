package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO;
import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao;
import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO.TotalPorProfissional;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.repository.AulaRepository;
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

/** Aulas realizadas sem evolução (#234) contra o PostgreSQL: correspondência aula ↔ sessão, filtro e resumo. */
@PostgresDataJpaTest
class RelatorioAulasSemEvolucaoServiceIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    TestEntityManager entityManager;

    RelatorioAulasSemEvolucaoService service;

    private int sequencial;
    private Profissional camila;
    private Profissional claudia;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(
                LocalDate.of(2026, 11, 25).atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
        service = new RelatorioAulasSemEvolucaoService(aulaRepository, profissionalRepository, relogio);
    }

    @Test
    void gerar_listaSoAsRealizadasSemSessaoComEvolucaoNoDia() {
        montarMassa();

        RelatorioAulasSemEvolucaoDTO relatorio = service.gerar(INICIO, FIM, null);

        // Fora: com evolução (mesmo paciente e data), não realizada, fora do período, evolução em outra data e
        // evolução de outro paciente no dia não salvam a aula.
        assertThat(relatorio.aulas())
                .extracting(
                        AulaSemEvolucao::pacienteNome,
                        AulaSemEvolucao::data,
                        AulaSemEvolucao::horario,
                        AulaSemEvolucao::profissionalNome)
                .containsExactly(
                        tuple("Bruno", LocalDate.of(2026, 11, 1), LocalTime.of(8, 0), "Camila"),
                        tuple("Ana", LocalDate.of(2026, 11, 3), LocalTime.of(7, 0), "Claudia"),
                        tuple("Carla", LocalDate.of(2026, 11, 3), null, null),
                        tuple("Ana", LocalDate.of(2026, 11, 30), LocalTime.of(9, 0), "Camila"));
        assertThat(relatorio.resumo().totalAulas()).isEqualTo(4);
        assertThat(relatorio.filtros().profissionalId()).isNull();
        assertThat(relatorio.periodo().inicio()).isEqualTo(INICIO);
    }

    @Test
    void gerar_sessaoSemEvolucaoNaoBasta() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        camila = entityManager.persist(profissional("Camila"));
        aula(ana, LocalDate.of(2026, 11, 10), LocalTime.of(8, 0), camila, true);
        sessao(ana, LocalDate.of(2026, 11, 10), false);
        limpar();

        assertThat(service.gerar(INICIO, FIM, null).aulas())
                .extracting(AulaSemEvolucao::pacienteNome)
                .containsExactly("Ana");
    }

    @Test
    void gerar_resumoPorProfissional_porNomeESemProfissionalPorUltimo() {
        montarMassa();

        List<TotalPorProfissional> resumo =
                service.gerar(INICIO, FIM, null).resumo().porProfissional();

        assertThat(resumo)
                .extracting(
                        TotalPorProfissional::profissionalId,
                        TotalPorProfissional::profissionalNome,
                        TotalPorProfissional::quantidade)
                .containsExactly(
                        tuple(camila.getId(), "Camila", 2L),
                        tuple(claudia.getId(), "Claudia", 1L),
                        tuple(null, "Sem profissional", 1L));
        assertThat(resumo.stream().mapToLong(TotalPorProfissional::quantidade).sum())
                .isEqualTo(4);
    }

    @Test
    void gerar_filtroDeProfissional_restringeAsLinhasEOResumo() {
        montarMassa();

        RelatorioAulasSemEvolucaoDTO relatorio = service.gerar(INICIO, FIM, camila.getId());

        assertThat(relatorio.aulas())
                .extracting(AulaSemEvolucao::profissionalId)
                .containsOnly(camila.getId())
                .hasSize(2);
        assertThat(relatorio.resumo().porProfissional())
                .extracting(TotalPorProfissional::profissionalNome, TotalPorProfissional::quantidade)
                .containsExactly(tuple("Camila", 2L));
        assertThat(relatorio.filtros().profissionalNome()).isEqualTo("Camila");
    }

    @Test
    void gerar_periodoSemAulas_retornaVazio() {
        RelatorioAulasSemEvolucaoDTO relatorio = service.gerar(INICIO, FIM, null);

        assertThat(relatorio.aulas()).isEmpty();
        assertThat(relatorio.resumo().totalAulas()).isZero();
        assertThat(relatorio.resumo().porProfissional()).isEmpty();
    }

    @Test
    void gerar_periodoInvalido_recusa() {
        assertThatThrownBy(() -> service.gerar(FIM, INICIO, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar(LocalDate.of(2025, 11, 1), FIM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
        assertThatThrownBy(() -> service.gerar(null, FIM, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void montarMassa() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Paciente bruno = entityManager.persist(paciente("Bruno", true));
        // Paciente inativo: a aula dela continua no controle do prontuário.
        Paciente carla = entityManager.persist(paciente("Carla", false));
        camila = entityManager.persist(profissional("Camila"));
        claudia = entityManager.persist(profissional("Claudia"));

        // Sem sessão: aparece (limites inclusivos do período).
        aula(bruno, LocalDate.of(2026, 11, 1), LocalTime.of(8, 0), camila, true);
        aula(ana, LocalDate.of(2026, 11, 30), LocalTime.of(9, 0), camila, true);
        // Sessão com evolução no mesmo dia: não aparece.
        aula(ana, LocalDate.of(2026, 11, 5), LocalTime.of(8, 0), camila, true);
        sessao(ana, LocalDate.of(2026, 11, 5), true);
        // Sessão sem evolução no mesmo dia: aparece.
        aula(ana, LocalDate.of(2026, 11, 3), LocalTime.of(7, 0), claudia, true);
        sessao(ana, LocalDate.of(2026, 11, 3), false);
        // Evolução em outra data, ou de outro paciente no mesmo dia, não vale para a aula.
        sessao(ana, LocalDate.of(2026, 11, 4), true);
        sessao(bruno, LocalDate.of(2026, 11, 3), true);
        // Sem profissional e sem horário: aparece, no grupo próprio e depois das aulas com horário do dia.
        aula(carla, LocalDate.of(2026, 11, 3), null, null, true);
        // Não realizada e fora do período: não aparecem.
        aula(bruno, LocalDate.of(2026, 11, 10), LocalTime.of(8, 0), camila, false);
        aula(bruno, LocalDate.of(2026, 10, 31), LocalTime.of(8, 0), camila, true);
        aula(bruno, LocalDate.of(2026, 12, 1), LocalTime.of(8, 0), camila, true);
        limpar();
    }

    private void limpar() {
        entityManager.flush();
        entityManager.clear();
    }

    private Paciente paciente(String nome, boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail("sem-evolucao" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        paciente.setAtivo(ativo);
        return paciente;
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

    private void aula(
            Paciente paciente, LocalDate data, LocalTime horario, Profissional profissional, boolean realizada) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento(paciente));
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setHorario(horario);
        aula.setRealizada(realizada);
        entityManager.persist(aula);
    }

    private void sessao(Paciente paciente, LocalDate data, boolean comEvolucao) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setStatus(StatusSessao.REALIZADA);
        sessao.setData(data);
        entityManager.persist(sessao);
        if (comEvolucao) {
            EvolucaoSessao evolucao = new EvolucaoSessao();
            evolucao.setSessao(sessao);
            evolucao.setDataHoraRegistro(data.atTime(12, 0));
            entityManager.persist(evolucao);
        }
    }

    /** Um plano e um pagamento por aula: a aula exige pagamento, e o pagamento é UNIQUE por (plano, período). */
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
        return entityManager.persist(pagamento);
    }
}
