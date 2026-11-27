package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.AnamneseRequestDTO;
import com.carlesso.pilatesapi.dto.AnamneseResponseDTO;
import com.carlesso.pilatesapi.dto.AnamneseUpdateDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaUpdateDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoRequestDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoResponseDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoUpdateDTO;
import com.carlesso.pilatesapi.dto.TesteFuncionalRealizadoDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.service.AnamneseService;
import com.carlesso.pilatesapi.service.AvaliacaoFisioterapeuticaService;
import com.carlesso.pilatesapi.service.ReavaliacaoService;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Mapeamento dos campos estruturados da #248 no schema real (migration V52): {@code text[]}, {@code jsonb} e as
 * colunas de seleção única, passando pelos services para exercitar a validação e a gravação juntas. Cobre também o
 * registro anterior à V52 — gravado só com os campos de texto — abrindo e editando normalmente.
 */
@PostgresDataJpaTest
@Import({AnamneseService.class, AvaliacaoFisioterapeuticaService.class, ReavaliacaoService.class})
class OpcoesAvaliacaoPersistenciaTest extends PostgresTestcontainerSupport {

    private static final LocalDate DATA = LocalDate.of(2026, 11, 20);

    @Autowired
    private AnamneseService anamneseService;

    @Autowired
    private AvaliacaoFisioterapeuticaService avaliacaoService;

    @Autowired
    private ReavaliacaoService reavaliacaoService;

    @Autowired
    private TestEntityManager entityManager;

    private Paciente paciente;

    @BeforeEach
    void setUp() {
        Paciente p = new Paciente();
        p.setNome("Ana Souza");
        paciente = entityManager.persistFlushFind(p);
    }

    // -------------------------------------------------------------------------
    // Anamnese
    // -------------------------------------------------------------------------

    @Test
    void anamnese_gravaEReleOsCamposEstruturados() {
        Long id = anamneseService
                .criar(new AnamneseRequestDTO(
                        paciente.getId(),
                        "Dor lombar",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Caminha às vezes",
                        null,
                        "Voltar a correr",
                        null,
                        List.of("LOMBAR", "CERVICAL", "LOMBAR"),
                        "Piora ao fim do dia",
                        List.of("HERNIA_DE_DISCO"),
                        "L4-L5",
                        "IRREGULARMENTE_ATIVO",
                        "Caminhada 2x por semana",
                        List.of("ALIVIO_DE_DOR", "POSTURA"),
                        null))
                .id();
        entityManager.clear();

        AnamneseResponseDTO relida = anamneseService.buscarPorId(id);

        assertThat(relida.regioesDor()).containsExactly("LOMBAR", "CERVICAL");
        assertThat(relida.regioesDorComplemento()).isEqualTo("Piora ao fim do dia");
        assertThat(relida.condicoesSaude()).containsExactly("HERNIA_DE_DISCO");
        assertThat(relida.condicoesSaudeComplemento()).isEqualTo("L4-L5");
        assertThat(relida.nivelAtividadeFisicaOpcao()).isEqualTo("IRREGULARMENTE_ATIVO");
        assertThat(relida.nivelAtividadeFisicaComplemento()).isEqualTo("Caminhada 2x por semana");
        assertThat(relida.objetivosOpcoes()).containsExactly("ALIVIO_DE_DOR", "POSTURA");
        assertThat(relida.objetivosComplemento()).isNull();
        assertThat(relida.nivelAtividadeFisica()).isEqualTo("Caminha às vezes");
        assertThat(relida.objetivos()).isEqualTo("Voltar a correr");
    }

    @Test
    void anamnese_registroAntigoSoComTexto_abreEEditaNormalmente() {
        Long id = ((Number) entityManager
                        .getEntityManager()
                        .createNativeQuery("INSERT INTO anamneses (paciente_id, queixa_principal, objetivos,"
                                + " nivel_atividade_fisica, data_criacao) VALUES (?, 'Dor no ombro', 'Mobilidade',"
                                + " 'Ativo no passado', now()) RETURNING id")
                        .setParameter(1, paciente.getId())
                        .getSingleResult())
                .longValue();
        entityManager.clear();

        AnamneseResponseDTO aberta = anamneseService.buscarPorId(id);
        assertThat(aberta.queixaPrincipal()).isEqualTo("Dor no ombro");
        assertThat(aberta.nivelAtividadeFisica()).isEqualTo("Ativo no passado");
        assertThat(aberta.regioesDor()).isEmpty();
        assertThat(aberta.condicoesSaude()).isEmpty();
        assertThat(aberta.objetivosOpcoes()).isEmpty();
        assertThat(aberta.nivelAtividadeFisicaOpcao()).isNull();

        // Edição de antes da #248: só texto, sem nenhum campo estruturado.
        anamneseService.atualizar(id, anamneseUpdate("Dor no ombro direito", null, null, null, aberta.version()));
        entityManager.clear();
        AnamneseResponseDTO editada = anamneseService.buscarPorId(id);
        assertThat(editada.queixaPrincipal()).isEqualTo("Dor no ombro direito");
        assertThat(editada.regioesDor()).isEmpty();

        // Edição que passa a usar as opções.
        anamneseService.atualizar(
                id, anamneseUpdate(null, List.of("OMBRO_DIREITO"), "MUITO_ATIVO", null, editada.version()));
        entityManager.clear();
        AnamneseResponseDTO comOpcoes = anamneseService.buscarPorId(id);
        assertThat(comOpcoes.regioesDor()).containsExactly("OMBRO_DIREITO");
        assertThat(comOpcoes.nivelAtividadeFisicaOpcao()).isEqualTo("MUITO_ATIVO");
        assertThat(comOpcoes.nivelAtividadeFisica()).isEqualTo("Ativo no passado");
    }

    @Test
    void anamnese_listaVaziaESelecaoEmBranco_limpamOsCampos() {
        Long id = anamneseService
                .criar(new AnamneseRequestDTO(
                        paciente.getId(),
                        "Dor",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Objetivo",
                        null,
                        List.of("CEFALEIA"),
                        null,
                        null,
                        null,
                        "ATIVO",
                        null,
                        null,
                        null))
                .id();
        entityManager.flush();
        Long version = anamneseService.buscarPorId(id).version();

        anamneseService.atualizar(id, anamneseUpdate(null, List.of(), "", null, version));
        entityManager.clear();

        AnamneseResponseDTO relida = anamneseService.buscarPorId(id);
        assertThat(relida.regioesDor()).isEmpty();
        assertThat(relida.nivelAtividadeFisicaOpcao()).isNull();
    }

    @Test
    void anamnese_valorGravadoQueSaiuDoEnum_continuaSendoLido() {
        Long id = anamneseService
                .criar(new AnamneseRequestDTO(
                        paciente.getId(),
                        "Dor",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Objetivo",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .id();
        entityManager.flush();
        entityManager
                .getEntityManager()
                .createNativeQuery("UPDATE anamneses SET regioes_dor = ARRAY['REGIAO_REMOVIDA'],"
                        + " nivel_atividade_fisica_opcao = 'NIVEL_REMOVIDO' WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate();
        entityManager.clear();

        AnamneseResponseDTO relida = anamneseService.buscarPorId(id);

        assertThat(relida.regioesDor()).containsExactly("REGIAO_REMOVIDA");
        assertThat(relida.nivelAtividadeFisicaOpcao()).isEqualTo("NIVEL_REMOVIDO");
    }

    // -------------------------------------------------------------------------
    // Avaliação fisioterapêutica
    // -------------------------------------------------------------------------

    @Test
    void avaliacao_gravaEReleOsCamposEstruturados() {
        Long id = avaliacaoService
                .criar(new AvaliacaoFisioterapeuticaRequestDTO(
                        paciente.getId(),
                        DATA,
                        "Dor lombar",
                        null,
                        null,
                        null,
                        null,
                        "Instável no apoio",
                        null,
                        null,
                        6,
                        null,
                        "Lombalgia mecânica",
                        null,
                        List.of("HIPERLORDOSE_LOMBAR", "ANTEVERSAO_PELVICA"),
                        "Mais acentuado em pé",
                        "TORACICO_APICAL",
                        null,
                        "ALTERACAO_LEVE",
                        "Oscila no unipodal esquerdo",
                        "PRESERVADO",
                        null,
                        List.of(
                                new TesteFuncionalRealizadoDTO("SCHOBER", "POSITIVO"),
                                new TesteFuncionalRealizadoDTO("LASEGUE", "NEGATIVO"),
                                new TesteFuncionalRealizadoDTO("SCHOBER", "POSITIVO")),
                        "Schober com 3 cm"))
                .id();
        entityManager.flush();
        entityManager.clear();

        AvaliacaoFisioterapeuticaResponseDTO relida = avaliacaoService.buscarPorId(id);

        assertThat(relida.achadosPosturais()).containsExactly("HIPERLORDOSE_LOMBAR", "ANTEVERSAO_PELVICA");
        assertThat(relida.achadosPosturaisComplemento()).isEqualTo("Mais acentuado em pé");
        assertThat(relida.padraoRespiratorioOpcao()).isEqualTo("TORACICO_APICAL");
        assertThat(relida.equilibrioOpcao()).isEqualTo("ALTERACAO_LEVE");
        assertThat(relida.equilibrioComplemento()).isEqualTo("Oscila no unipodal esquerdo");
        assertThat(relida.equilibrio()).isEqualTo("Instável no apoio");
        assertThat(relida.coordenacaoMotoraOpcao()).isEqualTo("PRESERVADO");
        assertThat(relida.testesFuncionais())
                .containsExactly(
                        new TesteFuncionalRealizadoDTO("SCHOBER", "POSITIVO"),
                        new TesteFuncionalRealizadoDTO("LASEGUE", "NEGATIVO"));
        assertThat(relida.testesFuncionaisComplemento()).isEqualTo("Schober com 3 cm");
    }

    @Test
    void avaliacao_registroAntigoSoComTexto_abreEEditaNormalmente() {
        Long id = ((Number) entityManager
                        .getEntityManager()
                        .createNativeQuery("INSERT INTO avaliacoes_fisioterapeuticas (paciente_id, data_avaliacao,"
                                + " queixa_funcional, equilibrio, escala_dor, diagnostico_fisioterapeutico,"
                                + " data_criacao) VALUES (?, ?, 'Dor cervical', 'Bom', 4, 'Cervicalgia', now())"
                                + " RETURNING id")
                        .setParameter(1, paciente.getId())
                        .setParameter(2, DATA)
                        .getSingleResult())
                .longValue();
        entityManager.clear();

        AvaliacaoFisioterapeuticaResponseDTO aberta = avaliacaoService.buscarPorId(id);
        assertThat(aberta.equilibrio()).isEqualTo("Bom");
        assertThat(aberta.achadosPosturais()).isEmpty();
        assertThat(aberta.testesFuncionais()).isEmpty();
        assertThat(aberta.equilibrioOpcao()).isNull();

        avaliacaoService.atualizar(id, avaliacaoUpdate("Cervicalgia tensional", null, null, aberta.version()));
        entityManager.flush();
        entityManager.clear();
        AvaliacaoFisioterapeuticaResponseDTO editada = avaliacaoService.buscarPorId(id);
        assertThat(editada.diagnosticoFisioterapeutico()).isEqualTo("Cervicalgia tensional");
        assertThat(editada.testesFuncionais()).isEmpty();

        avaliacaoService.atualizar(
                id,
                avaliacaoUpdate(
                        null,
                        List.of("ANTERIORIZACAO_DE_CABECA"),
                        List.of(new TesteFuncionalRealizadoDTO("ADAMS", "NEGATIVO")),
                        editada.version()));
        entityManager.flush();
        entityManager.clear();
        AvaliacaoFisioterapeuticaResponseDTO comOpcoes = avaliacaoService.buscarPorId(id);
        assertThat(comOpcoes.achadosPosturais()).containsExactly("ANTERIORIZACAO_DE_CABECA");
        assertThat(comOpcoes.testesFuncionais()).containsExactly(new TesteFuncionalRealizadoDTO("ADAMS", "NEGATIVO"));
        assertThat(comOpcoes.equilibrio()).isEqualTo("Bom");
    }

    @Test
    void avaliacao_testeGravadoQueSaiuDoEnum_continuaSendoLido() {
        Long id = ((Number) entityManager
                        .getEntityManager()
                        .createNativeQuery("INSERT INTO avaliacoes_fisioterapeuticas (paciente_id, data_avaliacao,"
                                + " queixa_funcional, escala_dor, diagnostico_fisioterapeutico, data_criacao,"
                                + " testes_funcionais) VALUES (?, ?, 'Dor', 2, 'Diag', now(),"
                                + " '[{\"teste\":\"TESTE_REMOVIDO\",\"resultado\":\"POSITIVO\"}]') RETURNING id")
                        .setParameter(1, paciente.getId())
                        .setParameter(2, DATA)
                        .getSingleResult())
                .longValue();
        entityManager.clear();

        assertThat(avaliacaoService.buscarPorId(id).testesFuncionais())
                .containsExactly(new TesteFuncionalRealizadoDTO("TESTE_REMOVIDO", "POSITIVO"));
    }

    // -------------------------------------------------------------------------
    // Reavaliação
    // -------------------------------------------------------------------------

    @Test
    void reavaliacao_gravaEReleOsCamposEstruturados() {
        Long id = reavaliacaoService
                .criar(new ReavaliacaoRequestDTO(
                        paciente.getId(),
                        null,
                        null,
                        DATA,
                        null,
                        "Dor caiu de 7 para 3",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("LOMBAR", "QUADRIL_ESQUERDO"),
                        "Só ao agachar",
                        List.of("HIPERLORDOSE_LOMBAR"),
                        null,
                        "DIAFRAGMATICO",
                        null,
                        "PRESERVADO",
                        null,
                        "ALTERACAO_MODERADA",
                        "Dissociação de cinturas",
                        List.of(new TesteFuncionalRealizadoDTO("THOMAS", "POSITIVO")),
                        null))
                .id();
        entityManager.flush();
        entityManager.clear();

        ReavaliacaoResponseDTO relida = reavaliacaoService.buscarPorId(id);

        assertThat(relida.regioesDor()).containsExactly("LOMBAR", "QUADRIL_ESQUERDO");
        assertThat(relida.regioesDorComplemento()).isEqualTo("Só ao agachar");
        assertThat(relida.achadosPosturais()).containsExactly("HIPERLORDOSE_LOMBAR");
        assertThat(relida.padraoRespiratorioOpcao()).isEqualTo("DIAFRAGMATICO");
        assertThat(relida.equilibrioOpcao()).isEqualTo("PRESERVADO");
        assertThat(relida.coordenacaoMotoraOpcao()).isEqualTo("ALTERACAO_MODERADA");
        assertThat(relida.coordenacaoMotoraComplemento()).isEqualTo("Dissociação de cinturas");
        assertThat(relida.testesFuncionais()).containsExactly(new TesteFuncionalRealizadoDTO("THOMAS", "POSITIVO"));
        assertThat(relida.evolucaoDor()).isEqualTo("Dor caiu de 7 para 3");
    }

    @Test
    void reavaliacao_registroAntigoSoComTexto_abreEEditaNormalmente() {
        Long id = ((Number) entityManager
                        .getEntityManager()
                        .createNativeQuery("INSERT INTO reavaliacoes (paciente_id, data_reavaliacao, evolucao_dor,"
                                + " data_criacao) VALUES (?, ?, 'Melhorou', now()) RETURNING id")
                        .setParameter(1, paciente.getId())
                        .setParameter(2, DATA)
                        .getSingleResult())
                .longValue();
        entityManager.clear();

        ReavaliacaoResponseDTO aberta = reavaliacaoService.buscarPorId(id);
        assertThat(aberta.evolucaoDor()).isEqualTo("Melhorou");
        assertThat(aberta.regioesDor()).isEmpty();
        assertThat(aberta.achadosPosturais()).isEmpty();
        assertThat(aberta.testesFuncionais()).isEmpty();

        reavaliacaoService.atualizar(id, reavaliacaoUpdate("Melhorou bastante", null, aberta.version()));
        entityManager.flush();
        entityManager.clear();
        ReavaliacaoResponseDTO editada = reavaliacaoService.buscarPorId(id);
        assertThat(editada.evolucaoDor()).isEqualTo("Melhorou bastante");
        assertThat(editada.regioesDor()).isEmpty();

        reavaliacaoService.atualizar(id, reavaliacaoUpdate(null, List.of("TORACICA"), editada.version()));
        entityManager.flush();
        entityManager.clear();
        assertThat(reavaliacaoService.buscarPorId(id).regioesDor()).containsExactly("TORACICA");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AnamneseUpdateDTO anamneseUpdate(
            String queixa, List<String> regioesDor, String nivelOpcao, List<String> objetivos, Long version) {
        return new AnamneseUpdateDTO(
                queixa,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                regioesDor,
                null,
                null,
                null,
                nivelOpcao,
                null,
                objetivos,
                null,
                version);
    }

    private static AvaliacaoFisioterapeuticaUpdateDTO avaliacaoUpdate(
            String diagnostico, List<String> achados, List<TesteFuncionalRealizadoDTO> testes, Long version) {
        return new AvaliacaoFisioterapeuticaUpdateDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                diagnostico,
                null,
                achados,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                testes,
                null,
                version);
    }

    private static ReavaliacaoUpdateDTO reavaliacaoUpdate(String evolucaoDor, List<String> regioesDor, Long version) {
        return new ReavaliacaoUpdateDTO(
                null,
                null,
                evolucaoDor,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                regioesDor,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                version);
    }
}
