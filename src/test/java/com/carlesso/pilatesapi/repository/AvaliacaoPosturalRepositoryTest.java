package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.enums.StatusAvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@PostgresDataJpaTest
class AvaliacaoPosturalRepositoryTest extends PostgresTestcontainerSupport {

    private static final String LANDMARKS_JSON =
            "[{\"codigo\":\"OLHO_ESQ\",\"x\":0.462,\"y\":0.118},{\"codigo\":\"OLHO_DIR\",\"x\":0.543,\"y\":0.121}]";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AvaliacaoPosturalRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private AvaliacaoFisioterapeutica avaliacaoFisioterapeutica;

    @BeforeEach
    void setUp() {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Souza");
        paciente = entityManager.persist(paciente);

        avaliacaoFisioterapeutica = entityManager.persist(avaliacaoFisioterapeutica(paciente));
        entityManager.flush();
    }

    @Test
    void persisteEReleLandmarksJsonbSemPerda() throws JsonProcessingException {
        AvaliacaoPostural analise = analise(VistaPostural.FRENTE);
        analise.setLinhaPrumoX(new BigDecimal("0.512"));
        analise.setCalibracaoCmPorUnidade(new BigDecimal("172.5"));
        analise.setObservacoes("Ombro direito elevado");
        Long id = repository.saveAndFlush(analise).getId();
        entityManager.clear();

        AvaliacaoPostural relida = repository.findAtivaById(id).orElseThrow();

        assertThat(OBJECT_MAPPER.readTree(relida.getLandmarks())).isEqualTo(OBJECT_MAPPER.readTree(LANDMARKS_JSON));
        assertThat(relida.getAvaliacaoFisioterapeutica().getId()).isEqualTo(avaliacaoFisioterapeutica.getId());
        assertThat(relida.getVista()).isEqualTo(VistaPostural.FRENTE);
        assertThat(relida.getLinhaPrumoX()).isEqualByComparingTo("0.512");
        assertThat(relida.getCalibracaoCmPorUnidade()).isEqualByComparingTo("172.5");
        assertThat(relida.getObservacoes()).isEqualTo("Ombro direito elevado");
    }

    @Test
    void registroNovoNasceComoRascunhoAtivoEComDataCriacao() {
        Long id = repository.saveAndFlush(analise(VistaPostural.COSTAS)).getId();
        entityManager.clear();

        AvaliacaoPostural relida = repository.findAtivaById(id).orElseThrow();

        assertThat(relida.getStatus()).isEqualTo(StatusAvaliacaoPostural.RASCUNHO);
        assertThat(relida.isAtivo()).isTrue();
        assertThat(relida.getDataCriacao()).isNotNull();
        assertThat(relida.getDataAtualizacao()).isNull();
        assertThat(relida.getCalibracaoCmPorUnidade()).isNull();
    }

    @Test
    void indiceParcialImpedeSegundaAnaliseAtivaDaMesmaVista() {
        repository.saveAndFlush(analise(VistaPostural.FRENTE));

        assertThatThrownBy(() -> repository.saveAndFlush(analise(VistaPostural.FRENTE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permiteRecriarAnaliseDaVistaAposCancelamento() {
        AvaliacaoPostural cancelada = repository.saveAndFlush(analise(VistaPostural.LADO_DIREITO));
        cancelada.setAtivo(false);
        cancelada.setStatus(StatusAvaliacaoPostural.CONCLUIDA);
        cancelada.setDataAtualizacao(LocalDateTime.now());
        repository.saveAndFlush(cancelada);

        AvaliacaoPostural recriada = repository.saveAndFlush(analise(VistaPostural.LADO_DIREITO));

        assertThat(recriada.getId()).isNotEqualTo(cancelada.getId());
        assertThat(repository.existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
                        avaliacaoFisioterapeutica.getId(), VistaPostural.LADO_DIREITO))
                .isTrue();
    }

    @Test
    void consultasConsideramApenasAnalisesAtivas() {
        AvaliacaoPostural ativa = repository.saveAndFlush(analise(VistaPostural.FRENTE));
        AvaliacaoPostural inativa = analise(VistaPostural.LADO_ESQUERDO);
        inativa.setAtivo(false);
        inativa = repository.saveAndFlush(inativa);
        entityManager.clear();

        assertThat(repository.findAtivaById(ativa.getId())).isPresent();
        assertThat(repository.findAtivaById(inativa.getId())).isEmpty();

        assertThat(repository.findAtivasByAvaliacaoFisioterapeutica(avaliacaoFisioterapeutica.getId()))
                .extracting(AvaliacaoPostural::getId)
                .containsExactly(ativa.getId());

        assertThat(repository.existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
                        avaliacaoFisioterapeutica.getId(), VistaPostural.FRENTE))
                .isTrue();
        assertThat(repository.existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
                        avaliacaoFisioterapeutica.getId(), VistaPostural.LADO_ESQUERDO))
                .isFalse();
    }

    private AvaliacaoFisioterapeutica avaliacaoFisioterapeutica(Paciente paciente) {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(LocalDate.of(2026, 7, 1));
        avaliacao.setQueixaFuncional("Dor ao agachar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico("Disfunção lombopélvica");
        return avaliacao;
    }

    private AvaliacaoPostural analise(VistaPostural vista) {
        AvaliacaoPostural analise = new AvaliacaoPostural();
        analise.setAvaliacaoFisioterapeutica(avaliacaoFisioterapeutica);
        analise.setVista(vista);
        analise.setLandmarks(LANDMARKS_JSON);
        return analise;
    }
}
