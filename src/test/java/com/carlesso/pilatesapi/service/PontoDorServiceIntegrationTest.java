package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.dto.PontoDorRequestDTO;
import com.carlesso.pilatesapi.dto.PontoDorResponseDTO;
import com.carlesso.pilatesapi.dto.PontosDorRegistroDTO;
import com.carlesso.pilatesapi.dto.PontosDorRequestDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PontoDor;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.enums.OrigemPontoDor;
import com.carlesso.pilatesapi.entity.enums.VistaCorporal;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PontoDorRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/** Pontos de dor (#249) contra o PostgreSQL: substituição, histórico, cascata e os CHECKs da V53. */
@PostgresDataJpaTest
class PontoDorServiceIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    PontoDorRepository pontoDorRepository;

    @Autowired
    AvaliacaoFisioterapeuticaRepository avaliacaoRepository;

    @Autowired
    ReavaliacaoRepository reavaliacaoRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    TestEntityManager entityManager;

    PontoDorService service;

    private int sequencial;

    @BeforeEach
    void setUp() {
        service =
                new PontoDorService(pontoDorRepository, avaliacaoRepository, reavaliacaoRepository, pacienteRepository);
    }

    @Test
    void substituir_gravaTrocaEApagaOConjuntoDaAvaliacao() {
        Paciente paciente = paciente();
        AvaliacaoFisioterapeutica avaliacao = avaliacao(paciente, LocalDate.of(2026, 10, 1));

        List<PontoDorResponseDTO> gravados = service.substituirDaAvaliacao(
                avaliacao.getId(),
                pontos(
                        new PontoDorRequestDTO(VistaCorporal.FRENTE, 0.42, 0.31, 1, 7, "Dor ao flexionar"),
                        new PontoDorRequestDTO(VistaCorporal.LATERAL_ESQUERDA, 1.0, 0.0, 1, null, null)));
        sincronizar();

        assertThat(gravados).allSatisfy(p -> assertThat(p.id()).isNotNull());
        assertThat(service.listarDaAvaliacao(avaliacao.getId()))
                .extracting(
                        PontoDorResponseDTO::vista,
                        PontoDorResponseDTO::x,
                        PontoDorResponseDTO::y,
                        PontoDorResponseDTO::intensidade,
                        PontoDorResponseDTO::observacao)
                .containsExactly(
                        tuple(VistaCorporal.FRENTE, 0.42, 0.31, 7, "Dor ao flexionar"),
                        tuple(VistaCorporal.LATERAL_ESQUERDA, 1.0, 0.0, null, null));
        PontoDor gravado = pontoDorRepository.findById(gravados.get(0).id()).orElseThrow();
        assertThat(gravado.getPaciente().getId()).isEqualTo(paciente.getId());
        assertThat(gravado.getDataCriacao()).isNotNull();

        service.substituirDaAvaliacao(
                avaliacao.getId(), pontos(new PontoDorRequestDTO(VistaCorporal.COSTAS, 0.5, 0.6, 2, 3, null)));
        sincronizar();

        assertThat(service.listarDaAvaliacao(avaliacao.getId())).singleElement().satisfies(p -> {
            assertThat(p.vista()).isEqualTo(VistaCorporal.COSTAS);
            assertThat(p.desenhoVersao()).isEqualTo(2);
        });

        service.substituirDaAvaliacao(avaliacao.getId(), pontos());
        sincronizar();

        assertThat(service.listarDaAvaliacao(avaliacao.getId())).isEmpty();
        assertThat(pontoDorRepository.count()).isZero();
    }

    @Test
    void substituirDaReavaliacao_naoMexeNosPontosDaAvaliacao() {
        Paciente paciente = paciente();
        AvaliacaoFisioterapeutica avaliacao = avaliacao(paciente, LocalDate.of(2026, 10, 1));
        Reavaliacao reavaliacao = reavaliacao(paciente, LocalDate.of(2026, 11, 1));
        service.substituirDaAvaliacao(avaliacao.getId(), pontos(ponto(VistaCorporal.FRENTE)));
        service.substituirDaReavaliacao(reavaliacao.getId(), pontos(ponto(VistaCorporal.COSTAS)));
        sincronizar();

        service.substituirDaReavaliacao(reavaliacao.getId(), pontos());
        sincronizar();

        assertThat(service.listarDaReavaliacao(reavaliacao.getId())).isEmpty();
        assertThat(service.listarDaAvaliacao(avaliacao.getId())).hasSize(1);
    }

    @Test
    void historico_agrupaPorRegistroDoMaisAntigoAoMaisRecenteSoComOsQueTemPontos() {
        Paciente paciente = paciente();
        Paciente outro = paciente();
        AvaliacaoFisioterapeutica avaliacao = avaliacao(paciente, LocalDate.of(2026, 9, 10));
        Reavaliacao recente = reavaliacao(paciente, LocalDate.of(2026, 11, 20));
        Reavaliacao intermediaria = reavaliacao(paciente, LocalDate.of(2026, 10, 15));
        reavaliacao(paciente, LocalDate.of(2026, 10, 30)); // sem pontos: fica fora do histórico
        AvaliacaoFisioterapeutica avaliacaoDoOutro = avaliacao(outro, LocalDate.of(2026, 9, 1));

        // Gravados fora da ordem cronológica, para o histórico ter de reordenar.
        service.substituirDaReavaliacao(
                recente.getId(), pontos(ponto(VistaCorporal.FRENTE), ponto(VistaCorporal.COSTAS)));
        service.substituirDaAvaliacao(avaliacao.getId(), pontos(ponto(VistaCorporal.LATERAL_DIREITA)));
        service.substituirDaReavaliacao(intermediaria.getId(), pontos(ponto(VistaCorporal.COSTAS)));
        service.substituirDaAvaliacao(avaliacaoDoOutro.getId(), pontos(ponto(VistaCorporal.FRENTE)));
        sincronizar();

        List<PontosDorRegistroDTO> historico = service.historicoDoPaciente(paciente.getId());

        assertThat(historico)
                .extracting(PontosDorRegistroDTO::origem, PontosDorRegistroDTO::registroId, PontosDorRegistroDTO::data)
                .containsExactly(
                        tuple(OrigemPontoDor.AVALIACAO, avaliacao.getId(), LocalDate.of(2026, 9, 10)),
                        tuple(OrigemPontoDor.REAVALIACAO, intermediaria.getId(), LocalDate.of(2026, 10, 15)),
                        tuple(OrigemPontoDor.REAVALIACAO, recente.getId(), LocalDate.of(2026, 11, 20)));
        assertThat(historico.get(2).pontos())
                .extracting(PontoDorResponseDTO::vista)
                .containsExactly(VistaCorporal.FRENTE, VistaCorporal.COSTAS);
        assertThat(service.historicoDoPaciente(paciente().getId())).isEmpty();
    }

    @Test
    void excluirAvaliacaoOuReavaliacao_apagaOsPontosEmCascata() {
        Paciente paciente = paciente();
        AvaliacaoFisioterapeutica avaliacao = avaliacao(paciente, LocalDate.of(2026, 10, 1));
        Reavaliacao reavaliacao = reavaliacao(paciente, LocalDate.of(2026, 11, 1));
        service.substituirDaAvaliacao(
                avaliacao.getId(), pontos(ponto(VistaCorporal.FRENTE), ponto(VistaCorporal.COSTAS)));
        service.substituirDaReavaliacao(reavaliacao.getId(), pontos(ponto(VistaCorporal.FRENTE)));
        sincronizar();

        avaliacaoRepository.deleteById(avaliacao.getId());
        sincronizar();

        assertThat(pontoDorRepository.findByAvaliacaoFisioterapeuticaIdOrderByIdAsc(avaliacao.getId()))
                .isEmpty();
        assertThat(pontoDorRepository.count()).isEqualTo(1);

        reavaliacaoRepository.deleteById(reavaliacao.getId());
        sincronizar();

        assertThat(pontoDorRepository.count()).isZero();
    }

    // Cada violação aborta a transação do teste no PostgreSQL: um caso por teste.
    @Test
    void banco_recusaPontoSemRegistro() {
        PontoDor semRegistro = pontoDireto(paciente());

        assertThatThrownBy(() -> pontoDorRepository.saveAndFlush(semRegistro))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_pontos_dor_um_registro");
    }

    @Test
    void banco_recusaPontoComAvaliacaoEReavaliacao() {
        Paciente paciente = paciente();
        PontoDor comOsDois = pontoDireto(paciente);
        comOsDois.setAvaliacaoFisioterapeutica(avaliacao(paciente, LocalDate.of(2026, 10, 1)));
        comOsDois.setReavaliacao(reavaliacao(paciente, LocalDate.of(2026, 11, 1)));

        assertThatThrownBy(() -> pontoDorRepository.saveAndFlush(comOsDois))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_pontos_dor_um_registro");
    }

    @Test
    void banco_recusaCoordenadaForaDoIntervalo() {
        Paciente paciente = paciente();
        AvaliacaoFisioterapeutica avaliacao = avaliacao(paciente, LocalDate.of(2026, 10, 1));

        PontoDor ponto = pontoDireto(paciente);
        ponto.setAvaliacaoFisioterapeutica(avaliacao);
        ponto.setX(new BigDecimal("1.000001"));
        assertThatThrownBy(() -> pontoDorRepository.saveAndFlush(ponto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static PontosDorRequestDTO pontos(PontoDorRequestDTO... pontos) {
        return new PontosDorRequestDTO(List.of(pontos));
    }

    private static PontoDorRequestDTO ponto(VistaCorporal vista) {
        return new PontoDorRequestDTO(vista, 0.5, 0.5, 1, 5, null);
    }

    private static PontoDor pontoDireto(Paciente paciente) {
        PontoDor ponto = new PontoDor();
        ponto.setPaciente(paciente);
        ponto.setVista(VistaCorporal.FRENTE);
        ponto.setX(new BigDecimal("0.5"));
        ponto.setY(new BigDecimal("0.5"));
        ponto.setDesenhoVersao(1);
        return ponto;
    }

    /** Grava o pendente e esvazia o contexto, para cada leitura vir do banco e não do cache da sessão. */
    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private Paciente paciente() {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente " + sequencial);
        paciente.setEmail("ponto-dor" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", 7000 + sequencial));
        return entityManager.persist(paciente);
    }

    private AvaliacaoFisioterapeutica avaliacao(Paciente paciente, LocalDate data) {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(data);
        avaliacao.setQueixaFuncional("Dor lombar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico("Lombalgia mecânica");
        return entityManager.persist(avaliacao);
    }

    private Reavaliacao reavaliacao(Paciente paciente, LocalDate data) {
        Reavaliacao reavaliacao = new Reavaliacao();
        reavaliacao.setPaciente(paciente);
        reavaliacao.setDataReavaliacao(data);
        return entityManager.persist(reavaliacao);
    }
}
