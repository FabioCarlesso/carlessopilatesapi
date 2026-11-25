package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO;
import com.carlesso.pilatesapi.dto.RelatorioOcupacaoDTO.Celula;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import com.carlesso.pilatesapi.entity.ListaEspera;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ListaEsperaRepository;
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

/**
 * Ocupação da grade (#235) contra o PostgreSQL: agrupamento por dia, horário e profissional, vagas, disponibilidade,
 * lista de espera e o grupo sem profissional.
 *
 * <p>Hoje é quarta, 25/11/2026: a semana vai de segunda, 23/11, a domingo, 29/11. Capacidade 3 por horário.
 */
@PostgresDataJpaTest
class RelatorioOcupacaoServiceIntegrationTest extends PostgresTestcontainerSupport {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);
    private static final LocalDate TERCA = LocalDate.of(2026, 11, 24);
    private static final LocalTime OITO = LocalTime.of(8, 0);
    private static final LocalTime NOVE = LocalTime.of(9, 0);
    private static final LocalTime CATORZE = LocalTime.of(14, 0);

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ListaEsperaRepository listaEsperaRepository;

    @Autowired
    DisponibilidadeProfissionalRepository disponibilidadeRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    TestEntityManager entityManager;

    RelatorioOcupacaoService service;

    private int sequencial;
    private Profissional paula;
    private Profissional ana;
    private Paciente bruna;
    private Paciente carlos;
    private Paciente diana;
    private Paciente eduardo;
    private Paciente fabia;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
        service = new RelatorioOcupacaoService(
                aulaRepository,
                listaEsperaRepository,
                disponibilidadeRepository,
                profissionalRepository,
                new AgendaProperties(60, 3),
                relogio);
        montarMassa();
    }

    @Test
    void semanaPadrao_eDeSegundaADomingoDeHoje_eQualquerDataDaSemanaDaOMesmoResultado() {
        RelatorioOcupacaoDTO padrao = service.gerar(null, null);

        assertThat(padrao.semana()).isEqualTo(new RelatorioOcupacaoDTO.Semana(SEGUNDA, LocalDate.of(2026, 11, 29)));
        assertThat(padrao.capacidadePorHorario()).isEqualTo(3);
        assertThat(service.gerar(LocalDate.of(2026, 11, 29), null).celulas()).isEqualTo(padrao.celulas());
        assertThat(service.gerar(SEGUNDA, null).celulas()).isEqualTo(padrao.celulas());
    }

    @Test
    void celulas_ordenadasPorDataHorarioEProfissional_semAulasDeOutraSemanaNemDePacienteInativo() {
        List<Celula> celulas = service.gerar(null, null).celulas();

        assertThat(celulas)
                .extracting(c -> c.data() + " " + c.horario() + " " + nome(c))
                .containsExactly(
                        "2026-11-23 08:00 Ana",
                        "2026-11-23 08:00 Paula",
                        "2026-11-23 14:00 Ana",
                        "2026-11-24 09:00 Paula",
                        "2026-11-25 08:00 -",
                        "2026-11-25 null Paula");
        assertThat(celulas.getFirst().diaSemana()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void doisAlunosComCapacidadeTres_temUmaVaga_eTrazOsAlunosPorNome() {
        Celula paulaSegunda = celula(service.gerar(null, null), SEGUNDA, OITO, paula);

        assertThat(paulaSegunda.ocupacao()).isEqualTo(2);
        assertThat(paulaSegunda.capacidade()).isEqualTo(3);
        assertThat(paulaSegunda.vagas()).isEqualTo(1);
        assertThat(paulaSegunda.lotado()).isFalse();
        assertThat(paulaSegunda.dentroDaDisponibilidade()).isTrue();
        assertThat(paulaSegunda.alunos())
                .containsExactly(
                        new RelatorioOcupacaoDTO.Aluno(bruna.getId(), "Bruna"),
                        new RelatorioOcupacaoDTO.Aluno(carlos.getId(), "Carlos"));
    }

    @Test
    void horarioLotado_temZeroVagas_eContaAListaDeEsperaQueCobreODiaEOHorario() {
        Celula paulaTerca = celula(service.gerar(null, null), TERCA, NOVE, paula);

        assertThat(paulaTerca.ocupacao()).isEqualTo(3);
        assertThat(paulaTerca.vagas()).isZero();
        assertThat(paulaTerca.lotado()).isTrue();
        // Contam 08:00–10:00 e 09:00–09:30; não contam 10:00–11:00 (não contém 09:00), a entrada que já saiu da fila,
        // a de paciente inativo e a de outro dia.
        assertThat(paulaTerca.listaEspera()).isEqualTo(2);
    }

    @Test
    void disponibilidadeCadastrada_foraDaFaixaNaoOfereceVaga_eSemFaixaEstaDisponivel() {
        RelatorioOcupacaoDTO relatorio = service.gerar(null, null);

        Celula anaDentro = celula(relatorio, SEGUNDA, OITO, ana);
        assertThat(anaDentro.dentroDaDisponibilidade()).isTrue();
        assertThat(anaDentro.vagas()).isEqualTo(2);

        Celula anaFora = celula(relatorio, SEGUNDA, CATORZE, ana);
        assertThat(anaFora.dentroDaDisponibilidade()).isFalse();
        assertThat(anaFora.ocupacao()).isEqualTo(1);
        assertThat(anaFora.vagas()).isZero();
        assertThat(anaFora.lotado()).isFalse();
    }

    @Test
    void semProfissionalESemHorario_aparecem_masNaoContamVaga() {
        RelatorioOcupacaoDTO relatorio = service.gerar(null, null);

        Celula semProfissional = relatorio.celulas().get(4);
        assertThat(semProfissional.profissional()).isNull();
        assertThat(semProfissional.ocupacao()).isEqualTo(1);
        assertThat(semProfissional.capacidade()).isNull();
        assertThat(semProfissional.vagas()).isNull();
        assertThat(semProfissional.dentroDaDisponibilidade()).isNull();

        Celula semHorario = relatorio.celulas().get(5);
        assertThat(semHorario.horario()).isNull();
        assertThat(semHorario.vagas()).isNull();
        assertThat(semHorario.listaEspera()).isZero();
    }

    @Test
    void totais_somaDasOcupacoesEIgualAsAulasComProfissional_eOGrupoSemProfissionalNaoAfetaAsVagas() {
        RelatorioOcupacaoDTO relatorio = service.gerar(null, null);
        long aulasComProfissional = relatorio.celulas().stream()
                .filter(c -> c.profissional() != null)
                .mapToLong(Celula::ocupacao)
                .sum();

        // 9 aulas na semana: 8 com profissional (2 + 1 + 1 + 3 + 1) e 1 sem.
        assertThat(relatorio.totais()).isEqualTo(new RelatorioOcupacaoDTO.Totais(9, 8, 1, 9, 3));
        assertThat(relatorio.totais().ocupacao()).isEqualTo(aulasComProfissional);
        // Capacidade só das células que contam vaga e estão na disponibilidade: Ana 08:00, Paula 08:00 e 09:00.
        // Vagas: 2 + 1 + 0 + 0 (Ana fora da faixa).
    }

    @Test
    void profissional_restringeAsCelulas_eDevolveONome() {
        RelatorioOcupacaoDTO daAna = service.gerar(null, ana.getId());

        assertThat(daAna.celulas())
                .extracting(RelatorioOcupacaoServiceIntegrationTest::nome)
                .containsOnly("Ana");
        assertThat(daAna.celulas()).hasSize(2);
        assertThat(daAna.filtros()).isEqualTo(new RelatorioOcupacaoDTO.Filtros(ana.getId(), "Ana"));
        assertThat(daAna.totais()).isEqualTo(new RelatorioOcupacaoDTO.Totais(2, 2, 0, 3, 2));
    }

    @Test
    void semanaSemAulas_devolveGradeVazia() {
        RelatorioOcupacaoDTO vazia = service.gerar(LocalDate.of(2026, 12, 10), null);

        assertThat(vazia.celulas()).isEmpty();
        assertThat(vazia.totais()).isEqualTo(new RelatorioOcupacaoDTO.Totais(0, 0, 0, 0, 0));
    }

    private void montarMassa() {
        paula = entityManager.persist(profissional("Paula"));
        ana = entityManager.persist(profissional("Ana"));
        // Ana atende só segunda de manhã; Paula não cadastrou faixas (disponível em qualquer horário).
        faixa(ana, DayOfWeek.MONDAY, OITO, LocalTime.of(12, 0));

        bruna = paciente("Bruna", true);
        carlos = paciente("Carlos", true);
        diana = paciente("Diana", true);
        eduardo = paciente("Eduardo", true);
        fabia = paciente("Fabia", true);
        Paciente inativo = paciente("Gil", false);

        // Segunda 08:00 Paula: 2 alunos.
        aula(bruna, paula, SEGUNDA, OITO);
        aula(carlos, paula, SEGUNDA, OITO);
        // Segunda 08:00 Ana (dentro da faixa) e 14:00 Ana (fora).
        aula(diana, ana, SEGUNDA, OITO);
        aula(eduardo, ana, SEGUNDA, CATORZE);
        // Terça 09:00 Paula: lotado.
        aula(bruna, paula, TERCA, NOVE);
        aula(carlos, paula, TERCA, NOVE);
        aula(diana, paula, TERCA, NOVE);
        // Quarta: sem profissional e sem horário.
        aula(eduardo, null, HOJE, OITO);
        aula(fabia, paula, HOJE, null);
        // Fora da conta: paciente inativo e aula da semana anterior.
        aula(inativo, paula, SEGUNDA, OITO);
        aula(fabia, paula, SEGUNDA.minusDays(7), OITO);

        espera(eduardo, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), true);
        espera(fabia, DayOfWeek.TUESDAY, NOVE, LocalTime.of(9, 30), true);
        espera(diana, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 0), true);
        espera(carlos, DayOfWeek.TUESDAY, NOVE, LocalTime.of(10, 0), false);
        espera(inativo, DayOfWeek.TUESDAY, NOVE, LocalTime.of(10, 0), true);
        espera(bruna, DayOfWeek.MONDAY, NOVE, LocalTime.of(10, 0), true);

        entityManager.flush();
        entityManager.clear();
    }

    private static String nome(Celula celula) {
        return celula.profissional() == null ? "-" : celula.profissional().nome();
    }

    private static Celula celula(RelatorioOcupacaoDTO relatorio, LocalDate data, LocalTime horario, Profissional p) {
        return relatorio.celulas().stream()
                .filter(c -> c.data().equals(data)
                        && horario.equals(c.horario())
                        && c.profissional() != null
                        && c.profissional().id().equals(p.getId()))
                .findFirst()
                .orElseThrow();
    }

    private Paciente paciente(String nome, boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setAtivo(ativo);
        paciente.setEmail("ocupacao" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        return entityManager.persist(paciente);
    }

    private Profissional profissional(String nome) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail("prof-ocupacao" + sequencial + "@email.com");
        profissional.setCpf(String.format("9%010d", sequencial));
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        profissional.setAtivo(true);
        return profissional;
    }

    private void faixa(Profissional profissional, DayOfWeek dia, LocalTime inicio, LocalTime fim) {
        DisponibilidadeProfissional faixa = new DisponibilidadeProfissional();
        faixa.setProfissional(profissional);
        faixa.setDiaSemana(dia);
        faixa.setHoraInicio(inicio);
        faixa.setHoraFim(fim);
        entityManager.persist(faixa);
    }

    private void espera(Paciente paciente, DayOfWeek dia, LocalTime inicio, LocalTime fim, boolean ativo) {
        ListaEspera entrada = new ListaEspera();
        entrada.setPaciente(paciente);
        entrada.setDiaSemana(dia);
        entrada.setHoraInicio(inicio);
        entrada.setHoraFim(fim);
        entrada.setAtivo(ativo);
        entityManager.persist(entrada);
    }

    private void aula(Paciente paciente, Profissional profissional, LocalDate data, LocalTime horario) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("300.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(data.getDayOfWeek()));
        plano.setDataInicio(LocalDate.of(2026, 1, 1));
        plano.setAtivo(true);
        entityManager.persist(plano);

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(plano.getValor());
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataPagamento(data);
        pagamento.setDataVencimento(data);
        pagamento.setPeriodoInicio(data);
        pagamento.setPeriodoFim(data.plusDays(29));
        entityManager.persist(pagamento);

        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setHorario(horario);
        aula.setRealizada(false);
        entityManager.persist(aula);
    }
}
