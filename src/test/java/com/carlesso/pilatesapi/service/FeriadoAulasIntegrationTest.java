package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.BloqueioAgendaRequestDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoBloqueio;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
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

/** Aula de feriado (#244) contra o PostgreSQL: marcação pelo bloqueio, desfazer e geração do pacote. */
@PostgresDataJpaTest
class FeriadoAulasIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalDate FERIADO = LocalDate.of(2026, 11, 16); // segunda

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    BloqueioAgendaRepository bloqueioRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    DisponibilidadeProfissionalRepository disponibilidadeRepository;

    @Autowired
    TestEntityManager entityManager;

    AulaService aulaService;
    BloqueioAgendaService bloqueioService;

    private int sequencial;

    @BeforeEach
    void setUp() {
        aulaService = new AulaService(
                aulaRepository,
                profissionalRepository,
                new AgendaProperties(60, 3),
                bloqueioRepository,
                disponibilidadeRepository);
        bloqueioService = new BloqueioAgendaService(bloqueioRepository, aulaService);
    }

    @Test
    void criarFeriado_marcaSoAsPendentesDoDiaENaoTocaNaRealizadaDeVerdade() {
        Pagamento ana = pagamento(paciente("Ana"));
        Pagamento bruno = pagamento(paciente("Bruno"));
        Long pendente = aula(ana, FERIADO, false);
        Long realizada = aula(bruno, FERIADO, true);
        Long outroDia = aula(ana, FERIADO.plusDays(2), false);
        limpar();

        Long bloqueio = bloqueioService.criar(feriado(FERIADO, FERIADO)).id();
        limpar();

        assertFeriado(pendente, bloqueio);
        Aula deVerdade = aulaRepository.findById(realizada).orElseThrow();
        assertThat(deVerdade.isRealizada()).isTrue();
        assertThat(deVerdade.isFeriado()).isFalse();
        assertNaoMarcada(outroDia);
        // A aula de feriado não teve atendimento: fica fora do relatório de aulas sem evolução (#234).
        assertThat(aulaRepository.findRealizadasSemEvolucao(FERIADO, FERIADO))
                .extracting(p -> p.getAulaId())
                .containsExactly(realizada);
    }

    @Test
    void bloqueioOutroOuParcial_naoMarcaAulas() {
        Long aula = aula(pagamento(paciente("Ana")), FERIADO, false);
        limpar();

        bloqueioService.criar(
                new BloqueioAgendaRequestDTO(FERIADO, FERIADO, null, null, "Manutenção", TipoBloqueio.OUTRO));
        bloqueioService.criar(new BloqueioAgendaRequestDTO(
                FERIADO, FERIADO, LocalTime.of(8, 0), LocalTime.of(12, 0), "Meio feriado", TipoBloqueio.FERIADO));
        bloqueioService.criar(new BloqueioAgendaRequestDTO(FERIADO, FERIADO, null, null, "Sem tipo", null));
        limpar();

        assertNaoMarcada(aula);
    }

    @Test
    void excluirFeriado_desfazAMarcacaoEOutroFeriadoNaMesmaDataAssume() {
        Long aula = aula(pagamento(paciente("Ana")), FERIADO, false);
        limpar();
        Long primeiro = bloqueioService.criar(feriado(FERIADO, FERIADO)).id();
        Long segundo =
                bloqueioService.criar(feriado(FERIADO.minusDays(1), FERIADO)).id();
        limpar();

        bloqueioService.excluir(primeiro);
        limpar();
        assertFeriado(aula, segundo);

        bloqueioService.excluir(segundo);
        limpar();
        assertNaoMarcada(aula);
    }

    @Test
    void editarFeriado_mudarDataOuTipoRefazAMarcacao() {
        Pagamento pagamento = pagamento(paciente("Ana"));
        Long antiga = aula(pagamento, FERIADO, false);
        Long nova = aula(pagamento, FERIADO.plusDays(7), false);
        limpar();
        Long bloqueio = bloqueioService.criar(feriado(FERIADO, FERIADO)).id();
        limpar();

        bloqueioService.atualizar(bloqueio, feriado(FERIADO.plusDays(7), FERIADO.plusDays(7)));
        limpar();
        assertNaoMarcada(antiga);
        assertFeriado(nova, bloqueio);

        bloqueioService.atualizar(
                bloqueio,
                new BloqueioAgendaRequestDTO(
                        FERIADO.plusDays(7), FERIADO.plusDays(7), null, null, "Era evento", TipoBloqueio.OUTRO));
        limpar();
        assertNaoMarcada(nova);
    }

    @Test
    void editarFeriado_soOMotivo_mantemAMarcacao() {
        Long aula = aula(pagamento(paciente("Ana")), FERIADO, false);
        limpar();
        Long bloqueio = bloqueioService.criar(feriado(FERIADO, FERIADO)).id();
        limpar();

        bloqueioService.atualizar(
                bloqueio,
                new BloqueioAgendaRequestDTO(FERIADO, FERIADO, null, null, "Proclamação", TipoBloqueio.FERIADO));
        limpar();

        assertFeriado(aula, bloqueio);
    }

    @Test
    void gerarAulas_aulaEmFeriadoJaCadastradoNasceMarcadaEConsomeOPacote() {
        Paciente ana = paciente("Ana");
        Pagamento pagamento = pagamento(ana);
        pagamento.getPlano().setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        pagamento.getPlano().setDiasSemana(List.of(DayOfWeek.MONDAY));
        pagamento.setPeriodoInicio(LocalDate.of(2026, 11, 2));
        Long bloqueio = bloqueioService.criar(feriado(FERIADO, FERIADO)).id();
        limpar();

        List<Aula> aulas = aulaService.gerarAulas(entityManager.find(Pagamento.class, pagamento.getId()));
        limpar();

        assertThat(aulas)
                .extracting(Aula::getData)
                .containsExactly(
                        LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), FERIADO, LocalDate.of(2026, 11, 23));
        Long doFeriado = aulas.stream()
                .filter(a -> a.getData().equals(FERIADO))
                .findFirst()
                .orElseThrow()
                .getId();
        assertFeriado(doFeriado, bloqueio);
        assertThat(aulaRepository.contarPorPagamento(List.of(pagamento.getId())))
                .singleElement()
                .satisfies(c -> assertThat(c.getRealizadas()).isEqualTo(1L));
    }

    @Test
    void feriado_aulaSemProfissionalRecebeOPadraoESemPadraoAceitaAtribuicao() {
        Profissional camila = entityManager.persist(profissional("Camila"));
        Paciente comPadrao = paciente("Ana");
        comPadrao.setProfissionalPadrao(camila);
        Pagamento semPadrao = pagamento(paciente("Bruno"));
        Long daAna = aula(pagamento(comPadrao), FERIADO, false);
        Long doBruno = aula(semPadrao, FERIADO, false);
        limpar();

        bloqueioService.criar(feriado(FERIADO, FERIADO));
        limpar();

        // Realizada pelo feriado, a aula gera repasse: sem profissional, ele não teria dono.
        assertThat(aulaRepository
                        .findById(daAna)
                        .orElseThrow()
                        .getProfissional()
                        .getId())
                .isEqualTo(camila.getId());
        assertThat(aulaRepository.findById(doBruno).orElseThrow().getProfissional())
                .isNull();
        assertThat(aulaRepository.contarPorPagamento(List.of(semPadrao.getId())))
                .singleElement()
                .satisfies(c -> assertThat(c.getSemProfissional()).isEqualTo(1L));

        assertThat(aulaService.atribuirProfissional(doBruno, camila.getId()).profissionalId())
                .isEqualTo(camila.getId());
        limpar();
        assertThat(aulaRepository.contarPorPagamento(List.of(semPadrao.getId())))
                .singleElement()
                .satisfies(c -> assertThat(c.getSemProfissional()).isZero());
    }

    @Test
    void remarcarParaFeriado_marcaAAulaNaHoraEEditarOMotivoNaoMudaNada() {
        Long aula = aula(pagamento(paciente("Ana")), FERIADO.plusDays(1), false);
        Long bloqueio = bloqueioService.criar(feriado(FERIADO, FERIADO)).id();
        limpar();

        var remarcada = aulaService.remarcar(aula, FERIADO, LocalTime.of(10, 0));
        limpar();

        assertThat(remarcada.feriado()).isTrue();
        assertThat(remarcada.realizada()).isTrue();
        assertFeriado(aula, bloqueio);

        bloqueioService.atualizar(
                bloqueio,
                new BloqueioAgendaRequestDTO(FERIADO, FERIADO, null, null, "Outro nome", TipoBloqueio.FERIADO));
        limpar();
        assertFeriado(aula, bloqueio);
    }

    private void assertFeriado(Long aulaId, Long bloqueioId) {
        Aula aula = aulaRepository.findById(aulaId).orElseThrow();
        assertThat(aula.isRealizada()).isTrue();
        assertThat(aula.isFeriado()).isTrue();
        assertThat(aula.getBloqueioFeriado().getId()).isEqualTo(bloqueioId);
    }

    private void assertNaoMarcada(Long aulaId) {
        Aula aula = aulaRepository.findById(aulaId).orElseThrow();
        assertThat(aula.isRealizada()).isFalse();
        assertThat(aula.isFeriado()).isFalse();
        assertThat(aula.getBloqueioFeriado()).isNull();
    }

    private static BloqueioAgendaRequestDTO feriado(LocalDate inicio, LocalDate fim) {
        return new BloqueioAgendaRequestDTO(inicio, fim, null, null, "Feriado", TipoBloqueio.FERIADO);
    }

    private void limpar() {
        entityManager.flush();
        entityManager.clear();
    }

    private Paciente paciente(String nome) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail("feriado" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        return entityManager.persist(paciente);
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
        return entityManager.persist(pagamento);
    }

    private Long aula(Pagamento pagamento, LocalDate data, boolean realizada) {
        Aula aula = new Aula();
        aula.setPaciente(pagamento.getPaciente());
        aula.setPagamento(pagamento);
        aula.setData(data);
        aula.setRealizada(realizada);
        return entityManager.persist(aula).getId();
    }

    private Profissional profissional(String nome) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail(nome.toLowerCase() + sequencial + "@feriado.com");
        profissional.setCpf(String.format("8%010d", sequencial));
        profissional.setTipoContrato(TipoContrato.AUTONOMO);
        profissional.setPercentualPagamentoAula(new BigDecimal("50.00"));
        profissional.setDataInicio(LocalDate.of(2026, 1, 1));
        return profissional;
    }
}
