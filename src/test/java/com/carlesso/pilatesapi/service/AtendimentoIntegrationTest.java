package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.config.AgendaProperties;
import com.carlesso.pilatesapi.dto.AtendimentoEvolucaoDTO;
import com.carlesso.pilatesapi.dto.AtendimentoRequestDTO;
import com.carlesso.pilatesapi.dto.AtendimentoResponseDTO;
import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesRequestDTO;
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
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.AulaSemEvolucaoProjection;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.EvolucaoSessaoRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.SessaoPilatesRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Atendimento a partir da aula (#246) contra o PostgreSQL: a sessão vinculada, a evolução, o reflexo na agenda e no
 * relatório de aulas sem evolução, e o vínculo das sessões existentes pela V50.
 */
@PostgresDataJpaTest
class AtendimentoIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 18);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 18, 10, 30);

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    BloqueioAgendaRepository bloqueioRepository;

    @Autowired
    DisponibilidadeProfissionalRepository disponibilidadeRepository;

    @Autowired
    SessaoPilatesRepository sessaoRepository;

    @Autowired
    EvolucaoSessaoRepository evolucaoRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    PlanoTratamentoRepository planoTratamentoRepository;

    @Autowired
    TestEntityManager entityManager;

    AulaService aulaService;
    AtendimentoService atendimentoService;
    SessaoPilatesService sessaoService;

    private int sequencial;
    private Profissional camila;
    private Profissional claudia;

    @BeforeEach
    void setUp() {
        aulaService = new AulaService(
                aulaRepository,
                profissionalRepository,
                new AgendaProperties(60, 3),
                bloqueioRepository,
                disponibilidadeRepository,
                sessaoRepository);
        EvolucaoSessaoService evolucaoService =
                new EvolucaoSessaoService(evolucaoRepository, sessaoRepository, pacienteRepository);
        atendimentoService = new AtendimentoService(
                aulaService,
                sessaoRepository,
                evolucaoService,
                evolucaoRepository,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
        sessaoService = new SessaoPilatesService(
                sessaoRepository,
                pacienteRepository,
                profissionalRepository,
                planoTratamentoRepository,
                evolucaoRepository,
                () -> Optional.of("teste@email.com"),
                () -> Optional.of(AGORA));
        camila = entityManager.persist(profissional("Camila"));
        claudia = entityManager.persist(profissional("Claudia"));
    }

    @Test
    void registrar_aulaNova_criaSessaoVinculadaComEvolucaoERealizaAAula() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Long aulaId = aula(ana, DIA, LocalTime.of(8, 0), camila, false);
        limpar();

        AtendimentoResponseDTO atendimento =
                atendimentoService.registrar(aulaId, new AtendimentoRequestDTO(null, null, evolucao(4)));
        limpar();

        assertThat(atendimento.aula().realizada()).isTrue();
        assertThat(atendimento.aula().sessaoId()).isEqualTo(atendimento.sessao().id());
        assertThat(atendimento.aula().temEvolucao()).isTrue();
        assertThat(atendimento.sessao().aulaId()).isEqualTo(aulaId);
        assertThat(atendimento.sessao().status()).isEqualTo(StatusSessao.REALIZADA);
        assertThat(atendimento.sessao().tipo()).isEqualTo(TipoSessao.PILATES);
        assertThat(atendimento.sessao().data()).isEqualTo(DIA);
        assertThat(atendimento.sessao().horario()).isEqualTo(LocalTime.of(8, 0));
        assertThat(atendimento.sessao().profissionalId()).isEqualTo(camila.getId());
        assertThat(atendimento.evolucao().dorAntes()).isEqualTo(4);
        assertThat(atendimento.evolucao().profissionalNome()).isEqualTo("Camila");
        // Sem dataHoraRegistro no pedido, vale o relógio do estúdio.
        assertThat(atendimento.evolucao().dataHoraRegistro()).isEqualTo(AGORA);

        Aula gravada = aulaRepository.findById(aulaId).orElseThrow();
        assertThat(gravada.isRealizada()).isTrue();
        assertThat(evolucaoRepository.existsBySessaoId(atendimento.sessao().id()))
                .isTrue();
    }

    @Test
    void registrar_deNovo_reaproveitaASessaoEEvolucaoRepetidaDa409() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Long aulaId = aula(ana, DIA, LocalTime.of(8, 0), camila, false);
        limpar();
        Long sessaoId = atendimentoService
                .registrar(aulaId, new AtendimentoRequestDTO(null, null, evolucao(2)))
                .sessao()
                .id();
        limpar();

        // Sem evolução: devolve o mesmo atendimento, com a evolução já registrada.
        AtendimentoResponseDTO deNovo = atendimentoService.registrar(aulaId, null);
        limpar();

        assertThat(deNovo.sessao().id()).isEqualTo(sessaoId);
        assertThat(deNovo.evolucao().dorAntes()).isEqualTo(2);
        assertThat(sessaoService
                        .listarPorPaciente(ana.getId(), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThatThrownBy(
                        () -> atendimentoService.registrar(aulaId, new AtendimentoRequestDTO(null, null, evolucao(5))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PUT /evolucoes-sessao");
    }

    @Test
    void registrar_semEvolucao_eDepoisComEvolucao_registraNaMesmaSessao() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Long aulaId = aula(ana, DIA, LocalTime.of(8, 0), null, false);
        limpar();

        AtendimentoResponseDTO primeiro = atendimentoService.registrar(
                aulaId, new AtendimentoRequestDTO(claudia.getId(), TipoSessao.FISIOTERAPIA, null));
        limpar();
        assertThat(primeiro.evolucao()).isNull();
        assertThat(primeiro.aula().temEvolucao()).isFalse();
        // Aula ainda não realizada: o profissional informado vai para a aula, como no realizar.
        assertThat(primeiro.aula().profissionalId()).isEqualTo(claudia.getId());
        assertThat(primeiro.sessao().tipo()).isEqualTo(TipoSessao.FISIOTERAPIA);

        AtendimentoResponseDTO segundo =
                atendimentoService.registrar(aulaId, new AtendimentoRequestDTO(null, null, evolucao(1)));

        assertThat(segundo.sessao().id()).isEqualTo(primeiro.sessao().id());
        assertThat(segundo.evolucao().sessaoId()).isEqualTo(primeiro.sessao().id());
        assertThat(segundo.aula().temEvolucao()).isTrue();
    }

    @Test
    void registrar_aulaJaRealizada_naoMudaOProfissionalDaAulaSoODaSessao() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Long aulaId = aula(ana, DIA, LocalTime.of(8, 0), camila, true);
        limpar();

        AtendimentoResponseDTO atendimento =
                atendimentoService.registrar(aulaId, new AtendimentoRequestDTO(claudia.getId(), null, null));
        limpar();

        assertThat(atendimento.aula().profissionalId()).isEqualTo(camila.getId());
        assertThat(atendimento.sessao().profissionalId()).isEqualTo(claudia.getId());
        assertThat(aulaRepository
                        .findById(aulaId)
                        .orElseThrow()
                        .getProfissional()
                        .getId())
                .isEqualTo(camila.getId());
    }

    @Test
    void registrar_aulaDeFeriado_recusaCom422SemCriarSessao() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Aula aula = novaAula(ana, DIA, null, camila);
        aula.marcarFeriado(null);
        Long aulaId = entityManager.persist(aula).getId();
        limpar();

        assertThatThrownBy(() -> atendimentoService.registrar(aulaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Aula de feriado não aceita atendimento");
        assertThat(sessaoRepository.findByAulaId(aulaId)).isEmpty();
    }

    @Test
    void registrarEBuscar_pacienteInativo_respondem404() {
        Paciente inativa = entityManager.persist(paciente("Carla", false));
        Long aulaId = aula(inativa, DIA, null, camila, false);
        limpar();

        assertThatThrownBy(() -> atendimentoService.registrar(aulaId, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> atendimentoService.buscar(aulaId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscar_comESemAtendimento() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Long aulaId = aula(ana, DIA, LocalTime.of(8, 0), camila, false);
        limpar();

        assertThatThrownBy(() -> atendimentoService.buscar(aulaId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("sem atendimento");

        Long sessaoId = atendimentoService.registrar(aulaId, null).sessao().id();
        limpar();

        AtendimentoResponseDTO atendimento = atendimentoService.buscar(aulaId);
        assertThat(atendimento.sessao().id()).isEqualTo(sessaoId);
        assertThat(atendimento.evolucao()).isNull();
        assertThat(atendimento.aula().sessaoId()).isEqualTo(sessaoId);
    }

    @Test
    void agendaEBuscaPorId_informamSessaoEEvolucaoDeCadaAula() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Paciente bruno = entityManager.persist(paciente("Bruno", true));
        Paciente carla = entityManager.persist(paciente("Carla", true));
        Long comEvolucao = aula(ana, DIA, LocalTime.of(8, 0), camila, false);
        Long semEvolucao = aula(bruno, DIA, LocalTime.of(9, 0), camila, false);
        Long semAtendimento = aula(carla, DIA, LocalTime.of(10, 0), camila, false);
        limpar();
        Long sessaoAna = atendimentoService
                .registrar(comEvolucao, new AtendimentoRequestDTO(null, null, evolucao(3)))
                .sessao()
                .id();
        Long sessaoBruno =
                atendimentoService.registrar(semEvolucao, null).sessao().id();
        limpar();

        List<AulaResponseDTO> agenda = aulaService.listarPorPeriodo(DIA, DIA, null, null, null);
        assertThat(agenda)
                .extracting(AulaResponseDTO::id, AulaResponseDTO::sessaoId, AulaResponseDTO::temEvolucao)
                .containsExactly(
                        tuple(comEvolucao, sessaoAna, true),
                        tuple(semEvolucao, sessaoBruno, false),
                        tuple(semAtendimento, null, false));

        assertThat(aulaService.buscarPorId(comEvolucao).temEvolucao()).isTrue();
        assertThat(aulaService.buscarPorId(semAtendimento).sessaoId()).isNull();
        assertThat(aulaService
                        .buscarPorPaciente(ana.getId(), PageRequest.of(0, 10, Sort.by("data")))
                        .getContent())
                .extracting(AulaResponseDTO::sessaoId, AulaResponseDTO::temEvolucao)
                .containsExactly(tuple(sessaoAna, true));
        Long pagamentoAna = aulaRepository
                .findById(comEvolucao)
                .orElseThrow()
                .getPagamento()
                .getId();
        assertThat(aulaService.buscarPorPagamento(pagamentoAna))
                .extracting(AulaResponseDTO::sessaoId)
                .containsExactly(sessaoAna);
    }

    @Test
    void relatorioSemEvolucao_usaOVinculoQuandoExisteEPacienteMaisDataSemEle() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Paciente bruno = entityManager.persist(paciente("Bruno", true));
        Paciente carla = entityManager.persist(paciente("Carla", true));
        Paciente davi = entityManager.persist(paciente("Davi", true));
        // Vinculada com evolução: sai do relatório.
        Long anaVinculadaComEvolucao = aula(ana, DIA, LocalTime.of(8, 0), camila, false);
        // Vinculada sem evolução: fica, mesmo com outra sessão do paciente no dia evoluída.
        Long brunoVinculadaSemEvolucao = aula(bruno, DIA, LocalTime.of(9, 0), camila, false);
        // Sem vínculo, com sessão avulsa evoluída no dia: sai (casamento por paciente + data).
        Long carlaSemVinculoEvoluida = aula(carla, DIA, LocalTime.of(10, 0), camila, true);
        // Sem vínculo e sem evolução: fica.
        Long daviSemVinculo = aula(davi, DIA, LocalTime.of(11, 0), camila, true);
        sessaoAvulsa(bruno, DIA, true);
        sessaoAvulsa(carla, DIA, true);
        limpar();
        atendimentoService.registrar(anaVinculadaComEvolucao, new AtendimentoRequestDTO(null, null, evolucao(0)));
        atendimentoService.registrar(brunoVinculadaSemEvolucao, null);
        limpar();

        assertThat(aulaRepository.findRealizadasSemEvolucao(DIA, DIA))
                .extracting(AulaSemEvolucaoProjection::getAulaId)
                .containsExactly(brunoVinculadaSemEvolucao, daviSemVinculo)
                .doesNotContain(anaVinculadaComEvolucao, carlaSemVinculoEvoluida);
    }

    @Test
    void sessaoAvulsa_continuaSendoCriadaEExcluirSessaoVinculadaLiberaOVinculo() {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Long aulaId = aula(ana, DIA, LocalTime.of(8, 0), camila, false);
        limpar();

        var avulsa = sessaoService.criar(new SessaoPilatesRequestDTO(
                ana.getId(), null, null, TipoSessao.FISIOTERAPIA, DIA.plusDays(1), null, null, null, null));
        assertThat(avulsa.aulaId()).isNull();

        Long sessaoId = atendimentoService
                .registrar(aulaId, new AtendimentoRequestDTO(null, null, evolucao(2)))
                .sessao()
                .id();
        limpar();
        assertThat(sessaoService.buscarPorId(sessaoId).aulaId()).isEqualTo(aulaId);

        sessaoService.excluir(sessaoId);
        limpar();

        AulaResponseDTO aula = aulaService.buscarPorId(aulaId);
        assertThat(aula.sessaoId()).isNull();
        assertThat(aula.temEvolucao()).isFalse();
        // Excluir a sessão não desfaz a realização: a aula segue no repasse.
        assertThat(aula.realizada()).isTrue();
        assertThatThrownBy(() -> atendimentoService.buscar(aulaId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void migrationV50_vinculaSoAsCorrespondenciasInequivocas() throws IOException {
        Paciente ana = entityManager.persist(paciente("Ana", true));
        Paciente bruno = entityManager.persist(paciente("Bruno", true));
        Paciente carla = entityManager.persist(paciente("Carla", true));
        Paciente davi = entityManager.persist(paciente("Davi", true));
        // Uma aula e uma sessão no dia: vincula.
        Long aulaAna = aula(ana, DIA, LocalTime.of(8, 0), camila, true);
        Long sessaoAna = sessaoAvulsa(ana, DIA, false);
        // Duas sessões no dia: ambígua, nenhuma vincula.
        aula(bruno, DIA, LocalTime.of(8, 0), camila, true);
        Long brunoUm = sessaoAvulsa(bruno, DIA, false);
        Long brunoDois = sessaoAvulsa(bruno, DIA, false);
        // Sessão cancelada não registra a aula; a outra, não cancelada, vincula.
        Long aulaCarla = aula(carla, DIA, LocalTime.of(8, 0), camila, true);
        Long carlaCancelada = sessaoAvulsa(carla, DIA, false, StatusSessao.CANCELADA);
        Long carlaRealizada = sessaoAvulsa(carla, DIA, false);
        // Aula de feriado não teve atendimento.
        Aula feriado = novaAula(davi, DIA, null, camila);
        feriado.marcarFeriado(null);
        entityManager.persist(feriado);
        Long sessaoDavi = sessaoAvulsa(davi, DIA, false);
        // Sessão sem aula no dia continua avulsa.
        Long anaOutroDia = sessaoAvulsa(ana, DIA.plusDays(1), false);
        limpar();

        int vinculadas = entityManager
                .getEntityManager()
                .createNativeQuery(backfillDaV50())
                .executeUpdate();
        limpar();

        assertThat(vinculadas).isEqualTo(2);
        assertThat(aulaDaSessao(sessaoAna)).isEqualTo(aulaAna);
        assertThat(aulaDaSessao(carlaRealizada)).isEqualTo(aulaCarla);
        assertThat(aulaDaSessao(brunoUm)).isNull();
        assertThat(aulaDaSessao(brunoDois)).isNull();
        assertThat(aulaDaSessao(carlaCancelada)).isNull();
        assertThat(aulaDaSessao(sessaoDavi)).isNull();
        assertThat(aulaDaSessao(anaOutroDia)).isNull();
    }

    /** O bloco de vínculo das sessões existentes, lido do próprio arquivo da migration. */
    private String backfillDaV50() throws IOException {
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("db/migration/V50__add_aula_sessoes_pilates.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return sql.substring(sql.indexOf("UPDATE sessoes_pilates")).trim().replaceAll(";$", "");
        }
    }

    private Long aulaDaSessao(Long sessaoId) {
        SessaoPilates sessao = sessaoRepository.findById(sessaoId).orElseThrow();
        return sessao.getAula() != null ? sessao.getAula().getId() : null;
    }

    private void limpar() {
        entityManager.flush();
        entityManager.clear();
    }

    private static AtendimentoEvolucaoDTO evolucao(int dorAntes) {
        return new AtendimentoEvolucaoDTO(
                null, "Ponte", "Reformer", "2 molas vermelhas", dorAntes, 0, "Boa", null, null, null);
    }

    private Paciente paciente(String nome, boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail("atendimento" + sequencial + "@email.com");
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

    private Long aula(
            Paciente paciente, LocalDate data, LocalTime horario, Profissional profissional, boolean realizada) {
        Aula aula = novaAula(paciente, data, horario, profissional);
        aula.setRealizada(realizada);
        return entityManager.persist(aula).getId();
    }

    private Aula novaAula(Paciente paciente, LocalDate data, LocalTime horario, Profissional profissional) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento(paciente));
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setHorario(horario);
        return aula;
    }

    private Long sessaoAvulsa(Paciente paciente, LocalDate data, boolean comEvolucao) {
        return sessaoAvulsa(paciente, data, comEvolucao, StatusSessao.REALIZADA);
    }

    private Long sessaoAvulsa(Paciente paciente, LocalDate data, boolean comEvolucao, StatusSessao status) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setStatus(status);
        sessao.setData(data);
        entityManager.persist(sessao);
        if (comEvolucao) {
            EvolucaoSessao evolucao = new EvolucaoSessao();
            evolucao.setSessao(sessao);
            evolucao.setDataHoraRegistro(data.atTime(12, 0));
            entityManager.persist(evolucao);
        }
        return sessao.getId();
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
