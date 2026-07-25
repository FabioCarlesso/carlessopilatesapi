package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Paciente inativo é ex-aluno, não registro apagado: o prontuário dele continua
 * legível. Estes testes cobrem em banco as queries que deixaram de filtrar
 * {@code paciente.ativo}, que é justamente onde um filtro reintroduzido por
 * engano passaria despercebido — os testes de service usam mock e não pegariam.
 * Ver issue #152.
 */
@PostgresDataJpaTest
class HistoricoPacienteInativoRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    private AnamneseRepository anamneseRepository;

    @Autowired
    private AvaliacaoFisioterapeuticaRepository avaliacaoRepository;

    @Autowired
    private PlanoTratamentoRepository planoTratamentoRepository;

    @Autowired
    private ReavaliacaoRepository reavaliacaoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Paciente inativo;

    @BeforeEach
    void setUp() {
        inativo = new Paciente();
        inativo.setNome("Ana Oliveira");
        inativo.setEmail("historico.inativo@email.com");
        inativo.setCpf("55544433322");
        inativo.setAtivo(false);
        inativo = entityManager.persist(inativo);
    }

    @Test
    void anamnese_dePacienteInativo_continuaLegivel() {
        Anamnese anamnese = new Anamnese();
        anamnese.setPaciente(inativo);
        anamnese.setQueixaPrincipal("Dor lombar");
        anamnese.setObjetivos("Reduzir dor e melhorar postura");
        // Anamnese não tem @PrePersist: dataCriacao é responsabilidade de quem grava.
        anamnese.setDataCriacao(LocalDateTime.of(2026, 1, 15, 10, 0));
        Long id = entityManager.persist(anamnese).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(anamneseRepository.findByPacienteId(inativo.getId())).isPresent();
        assertThat(anamneseRepository.findById(id)).isPresent();
    }

    @Test
    void avaliacaoFisioterapeutica_dePacienteInativo_continuaLegivel() {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(inativo);
        avaliacao.setDataAvaliacao(LocalDate.of(2026, 4, 20));
        avaliacao.setQueixaFuncional("Dificuldade para agachar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico("Lombalgia mecânica");
        Long id = entityManager.persist(avaliacao).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(avaliacaoRepository.findByPacienteOrdenadas(inativo.getId())).hasSize(1);
        assertThat(avaliacaoRepository.findByIdComPaciente(id)).isPresent();
    }

    @Test
    void planoTratamento_dePacienteInativo_continuaLegivel() {
        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(inativo);
        plano.setDataInicio(LocalDate.of(2026, 5, 1));
        plano.setObjetivosTratamento("Fortalecimento de core");
        Long id = entityManager.persist(plano).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(planoTratamentoRepository.findAtivosByPacienteOrdenados(inativo.getId()))
                .hasSize(1);
        assertThat(planoTratamentoRepository.findAtivoById(id)).isPresent();
    }

    @Test
    void planoTratamentoInativo_continuaFiltrado_mesmoComPacienteInativo() {
        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(inativo);
        plano.setDataInicio(LocalDate.of(2026, 5, 1));
        plano.setObjetivosTratamento("Plano encerrado");
        plano.setAtivo(false);
        Long id = entityManager.persist(plano).getId();
        entityManager.flush();
        entityManager.clear();

        // O soft delete do próprio plano continua valendo: só o filtro de paciente saiu.
        assertThat(planoTratamentoRepository.findAtivosByPacienteOrdenados(inativo.getId()))
                .isEmpty();
        assertThat(planoTratamentoRepository.findAtivoById(id)).isEmpty();
    }

    @Test
    void reavaliacao_dePacienteInativo_continuaLegivel() {
        Reavaliacao reavaliacao = new Reavaliacao();
        reavaliacao.setPaciente(inativo);
        reavaliacao.setDataReavaliacao(LocalDate.of(2026, 6, 10));
        Long id = entityManager.persist(reavaliacao).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(reavaliacaoRepository.findByPacienteOrdenadas(inativo.getId()))
                .hasSize(1);
        assertThat(reavaliacaoRepository.findByIdComPaciente(id)).isPresent();
    }
}
