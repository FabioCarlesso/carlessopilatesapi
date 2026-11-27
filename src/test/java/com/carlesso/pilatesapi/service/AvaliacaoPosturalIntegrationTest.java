package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalVinculoDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.AvaliacaoPosturalRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import com.carlesso.pilatesapi.storage.FotoStorage;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

/**
 * Análise postural sem avaliação (#247) contra o PostgreSQL: a análise solta, os vínculos com avaliação e reavaliação,
 * a listagem do paciente, o CHECK de no máximo um vínculo e o backfill do paciente pela V51.
 */
@PostgresDataJpaTest
class AvaliacaoPosturalIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    AvaliacaoPosturalRepository analiseRepository;

    @Autowired
    AvaliacaoFisioterapeuticaRepository avaliacaoRepository;

    @Autowired
    ReavaliacaoRepository reavaliacaoRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    TestEntityManager entityManager;

    AvaliacaoPosturalService service;

    private Paciente ana;
    private Paciente bruno;
    private AvaliacaoFisioterapeutica avaliacaoAna;
    private Reavaliacao reavaliacaoAna;
    private AvaliacaoFisioterapeutica avaliacaoBruno;

    @BeforeEach
    void setUp() {
        service = new AvaliacaoPosturalService(
                analiseRepository,
                avaliacaoRepository,
                reavaliacaoRepository,
                pacienteRepository,
                new ObjectMapper(),
                mock(FotoStorage.class));
        ana = entityManager.persist(paciente("Ana"));
        bruno = entityManager.persist(paciente("Bruno"));
        avaliacaoAna = entityManager.persist(avaliacao(ana));
        reavaliacaoAna = entityManager.persist(reavaliacao(ana));
        avaliacaoBruno = entityManager.persist(avaliacao(bruno));
        limpar();
    }

    @Test
    void criar_soComPaciente_gravaAAnaliseSolta() {
        AvaliacaoPosturalResponseDTO criada = criar(ana, null, null, VistaPostural.FRENTE);
        limpar();

        AvaliacaoPostural relida = analiseRepository.findAtivaById(criada.id()).orElseThrow();
        assertThat(relida.getPaciente().getId()).isEqualTo(ana.getId());
        assertThat(relida.getAvaliacaoFisioterapeutica()).isNull();
        assertThat(relida.getReavaliacao()).isNull();
        assertThat(service.buscarPorId(criada.id()).pacienteId()).isEqualTo(ana.getId());
    }

    @Test
    void criar_comAvaliacaoOuReavaliacao_gravaOVinculo() {
        AvaliacaoPosturalResponseDTO daAvaliacao = criar(ana, avaliacaoAna.getId(), null, VistaPostural.FRENTE);
        AvaliacaoPosturalResponseDTO daReavaliacao = criar(ana, null, reavaliacaoAna.getId(), VistaPostural.FRENTE);
        limpar();

        assertThat(service.buscarPorId(daAvaliacao.id()).avaliacaoFisioterapeuticaId())
                .isEqualTo(avaliacaoAna.getId());
        assertThat(service.buscarPorId(daReavaliacao.id()).reavaliacaoId()).isEqualTo(reavaliacaoAna.getId());
        assertThat(service.listarPorAvaliacaoFisioterapeutica(avaliacaoAna.getId()))
                .extracting(AvaliacaoPosturalResponseDTO::id)
                .containsExactly(daAvaliacao.id());
    }

    @Test
    void criar_comOsDoisVinculos_eRecusadoNoServicoENoBanco() {
        assertThatThrownBy(() -> criar(ana, avaliacaoAna.getId(), reavaliacaoAna.getId(), VistaPostural.FRENTE))
                .isInstanceOf(IllegalArgumentException.class);

        AvaliacaoPostural invalida = new AvaliacaoPostural();
        invalida.setPaciente(entityManager.find(Paciente.class, ana.getId()));
        invalida.setAvaliacaoFisioterapeutica(
                entityManager.find(AvaliacaoFisioterapeutica.class, avaliacaoAna.getId()));
        invalida.setReavaliacao(entityManager.find(Reavaliacao.class, reavaliacaoAna.getId()));
        invalida.setVista(VistaPostural.COSTAS);

        assertThatThrownBy(() -> analiseRepository.saveAndFlush(invalida))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void criar_comRegistroDeOutroPaciente_eRecusado() {
        assertThatThrownBy(() -> criar(ana, avaliacaoBruno.getId(), null, VistaPostural.FRENTE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> criar(bruno, null, reavaliacaoAna.getId(), VistaPostural.FRENTE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void analiseSoltaNaoTemLimiteDeVista_masAReavaliacaoTem() {
        criar(ana, null, null, VistaPostural.FRENTE);
        criar(ana, null, null, VistaPostural.FRENTE);
        criar(ana, null, reavaliacaoAna.getId(), VistaPostural.FRENTE);

        assertThatThrownBy(() -> criar(ana, null, reavaliacaoAna.getId(), VistaPostural.FRENTE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listarPorPaciente_trazSoltasEVinculadasAtivasDaMaisRecente() {
        Long solta = criar(ana, null, null, VistaPostural.FRENTE).id();
        Long daAvaliacao =
                criar(ana, avaliacaoAna.getId(), null, VistaPostural.COSTAS).id();
        Long daReavaliacao = criar(ana, null, reavaliacaoAna.getId(), VistaPostural.LADO_DIREITO)
                .id();
        Long cancelada = criar(ana, null, null, VistaPostural.LADO_ESQUERDO).id();
        service.cancelar(cancelada);
        criar(bruno, avaliacaoBruno.getId(), null, VistaPostural.FRENTE);
        limpar();

        assertThat(service.listarPorPaciente(ana.getId()))
                .extracting(AvaliacaoPosturalResponseDTO::id)
                .containsExactly(daReavaliacao, daAvaliacao, solta);
    }

    @Test
    void alterarVinculo_vinculaDepoisTrocaEDesvincula() {
        Long id = criar(ana, null, null, VistaPostural.FRENTE).id();
        limpar();

        AvaliacaoPosturalResponseDTO vinculada =
                service.alterarVinculo(id, new AvaliacaoPosturalVinculoDTO(avaliacaoAna.getId(), null));
        assertThat(vinculada.avaliacaoFisioterapeuticaId()).isEqualTo(avaliacaoAna.getId());
        limpar();

        // Reenviar o mesmo vínculo não é duplicata da própria análise.
        service.alterarVinculo(id, new AvaliacaoPosturalVinculoDTO(avaliacaoAna.getId(), null));
        limpar();

        AvaliacaoPosturalResponseDTO trocada =
                service.alterarVinculo(id, new AvaliacaoPosturalVinculoDTO(null, reavaliacaoAna.getId()));
        assertThat(trocada.avaliacaoFisioterapeuticaId()).isNull();
        assertThat(trocada.reavaliacaoId()).isEqualTo(reavaliacaoAna.getId());
        limpar();

        AvaliacaoPosturalResponseDTO solta = service.alterarVinculo(id, new AvaliacaoPosturalVinculoDTO(null, null));
        assertThat(solta.reavaliacaoId()).isNull();
        limpar();

        AvaliacaoPostural relida = analiseRepository.findAtivaById(id).orElseThrow();
        assertThat(relida.getAvaliacaoFisioterapeutica()).isNull();
        assertThat(relida.getReavaliacao()).isNull();
        assertThat(relida.getDataAtualizacao()).isNotNull();
    }

    @Test
    void alterarVinculo_paraDestinoComAVistaOcupadaOuDeOutroPaciente_eRecusado() {
        criar(ana, avaliacaoAna.getId(), null, VistaPostural.FRENTE);
        Long solta = criar(ana, null, null, VistaPostural.FRENTE).id();
        limpar();

        assertThatThrownBy(() ->
                        service.alterarVinculo(solta, new AvaliacaoPosturalVinculoDTO(avaliacaoAna.getId(), null)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() ->
                        service.alterarVinculo(solta, new AvaliacaoPosturalVinculoDTO(avaliacaoBruno.getId(), null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void migrationV51_preencheOPacienteDasAnalisesExistentesAPartirDaAvaliacao() throws IOException {
        var em = entityManager.getEntityManager();
        // Volta a coluna ao estado de antes do backfill: anulável e vazia (o DDL é desfeito no rollback do teste).
        em.createNativeQuery("ALTER TABLE avaliacoes_posturais ALTER COLUMN paciente_id DROP NOT NULL")
                .executeUpdate();
        Long daAna = inserirSemPaciente(avaliacaoAna.getId(), "FRENTE");
        Long daAnaCancelada = inserirSemPaciente(avaliacaoAna.getId(), "COSTAS");
        Long doBruno = inserirSemPaciente(avaliacaoBruno.getId(), "FRENTE");

        int preenchidas = em.createNativeQuery(backfillDaV51()).executeUpdate();

        assertThat(preenchidas).isEqualTo(3);
        assertThat(pacienteDaAnalise(daAna)).isEqualTo(ana.getId());
        assertThat(pacienteDaAnalise(daAnaCancelada)).isEqualTo(ana.getId());
        assertThat(pacienteDaAnalise(doBruno)).isEqualTo(bruno.getId());
        Number semPaciente =
                (Number) em.createNativeQuery("SELECT COUNT(*) FROM avaliacoes_posturais WHERE paciente_id IS NULL")
                        .getSingleResult();
        assertThat(semPaciente.longValue()).isZero();
    }

    private Long inserirSemPaciente(Long avaliacaoId, String vista) {
        Number id = (Number) entityManager
                .getEntityManager()
                .createNativeQuery(
                        """
                        INSERT INTO avaliacoes_posturais (avaliacao_fisioterapeutica_id, vista, status, ativo, data_criacao)
                        VALUES (?1, ?2, 'RASCUNHO', ?3, NOW())
                        RETURNING id
                        """)
                .setParameter(1, avaliacaoId)
                .setParameter(2, vista)
                .setParameter(3, !"COSTAS".equals(vista))
                .getSingleResult();
        return id.longValue();
    }

    private Long pacienteDaAnalise(Long analiseId) {
        Number paciente = (Number) entityManager
                .getEntityManager()
                .createNativeQuery("SELECT paciente_id FROM avaliacoes_posturais WHERE id = ?1")
                .setParameter(1, analiseId)
                .getSingleResult();
        return paciente.longValue();
    }

    /** O UPDATE do backfill, lido do próprio arquivo da migration. */
    private String backfillDaV51() throws IOException {
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream("db/migration/V51__add_paciente_reavaliacao_avaliacoes_posturais.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int inicio = sql.indexOf("UPDATE avaliacoes_posturais");
            return sql.substring(inicio, sql.indexOf(';', inicio)).trim();
        }
    }

    private AvaliacaoPosturalResponseDTO criar(
            Paciente paciente, Long avaliacaoId, Long reavaliacaoId, VistaPostural vista) {
        AvaliacaoPosturalResponseDTO criada =
                service.criar(new AvaliacaoPosturalRequestDTO(paciente.getId(), avaliacaoId, reavaliacaoId, vista));
        entityManager.flush();
        return criada;
    }

    private void limpar() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Paciente paciente(String nome) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        return paciente;
    }

    private static AvaliacaoFisioterapeutica avaliacao(Paciente paciente) {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(LocalDate.of(2026, 7, 1));
        avaliacao.setQueixaFuncional("Dor ao agachar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico("Disfunção lombopélvica");
        return avaliacao;
    }

    private static Reavaliacao reavaliacao(Paciente paciente) {
        Reavaliacao reavaliacao = new Reavaliacao();
        reavaliacao.setPaciente(paciente);
        reavaliacao.setDataReavaliacao(LocalDate.of(2026, 10, 1));
        return reavaliacao;
    }
}
