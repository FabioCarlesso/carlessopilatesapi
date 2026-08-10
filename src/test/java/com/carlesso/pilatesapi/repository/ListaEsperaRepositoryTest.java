package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.ListaEspera;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresDataJpaTest
class ListaEsperaRepositoryTest extends PostgresTestcontainerSupport {

    private static final DayOfWeek SEGUNDA = DayOfWeek.MONDAY;
    private static final LocalTime CONSULTA_INICIO = LocalTime.of(8, 0);
    private static final LocalTime CONSULTA_FIM = LocalTime.of(9, 0);

    @Autowired
    ListaEsperaRepository repository;

    @Autowired
    TestEntityManager entityManager;

    private Paciente paciente;

    /**
     * Os cenários asseram sobre o conteúdo <em>absoluto</em> da fila, e o container Postgres é singleton, compartilhado
     * com testes que comitam. Limpar aqui evita que a suíte fique refém da ordem de execução.
     */
    @BeforeEach
    void limparFila() {
        repository.deleteAll();
        entityManager.flush();
        paciente = persistirPaciente("fila.repository@email.com", true);
    }

    @Test
    void buscarAtivas_faixaContidaNaConsultada_intersecta() {
        persistir(paciente, SEGUNDA, LocalTime.of(8, 15), LocalTime.of(8, 45), "Contida");

        assertThat(buscarNaFaixaConsultada())
                .extracting(ListaEspera::getObservacao)
                .containsExactly("Contida");
    }

    @Test
    void buscarAtivas_faixaQueEnglobaAConsultada_intersecta() {
        persistir(paciente, SEGUNDA, LocalTime.of(7, 30), LocalTime.of(9, 30), "Engloba");

        assertThat(buscarNaFaixaConsultada())
                .extracting(ListaEspera::getObservacao)
                .containsExactly("Engloba");
    }

    @Test
    void buscarAtivas_sobreposicaoParcialNasDuasPontas_intersecta() {
        persistir(paciente, SEGUNDA, LocalTime.of(7, 30), LocalTime.of(8, 30), "Entra pela esquerda");
        persistir(paciente, SEGUNDA, LocalTime.of(8, 30), LocalTime.of(9, 30), "Entra pela direita");

        assertThat(buscarNaFaixaConsultada()).hasSize(2);
    }

    @Test
    void buscarAtivas_faixaDisjunta_naoIntersecta() {
        persistir(paciente, SEGUNDA, LocalTime.of(6, 0), LocalTime.of(7, 0), "Antes");
        persistir(paciente, SEGUNDA, LocalTime.of(10, 0), LocalTime.of(11, 0), "Depois");

        assertThat(buscarNaFaixaConsultada()).isEmpty();
    }

    /** Pontas abertas: quem sai às 08:00 não disputa a vaga que começa às 08:00. */
    @Test
    void buscarAtivas_faixaQueApenasEncostaNasPontas_naoIntersecta() {
        persistir(paciente, SEGUNDA, LocalTime.of(7, 0), CONSULTA_INICIO, "Termina onde a consulta começa");
        persistir(paciente, SEGUNDA, CONSULTA_FIM, LocalTime.of(10, 0), "Começa onde a consulta termina");

        assertThat(buscarNaFaixaConsultada()).isEmpty();
    }

    @Test
    void buscarAtivas_ordenaPorDataEntradaMesmoComEntradasCriadasForaDeOrdem() {
        persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Terceiro", LocalDateTime.of(2026, 8, 3, 9, 0));
        persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Primeiro", LocalDateTime.of(2026, 8, 1, 9, 0));
        persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Segundo", LocalDateTime.of(2026, 8, 2, 9, 0));

        assertThat(repository.buscarAtivas(null, null, null, null))
                .extracting(ListaEspera::getObservacao)
                .containsExactly("Primeiro", "Segundo", "Terceiro");
    }

    @Test
    void buscarAtivas_filtraPorDiaSemana() {
        persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Segunda-feira");
        persistir(paciente, DayOfWeek.WEDNESDAY, CONSULTA_INICIO, CONSULTA_FIM, "Quarta-feira");

        assertThat(repository.buscarAtivas(SEGUNDA, null, null, null))
                .extracting(ListaEspera::getObservacao)
                .containsExactly("Segunda-feira");
    }

    @Test
    void buscarAtivas_filtraPorPaciente() {
        Paciente outro = persistirPaciente("outro.fila@email.com", true);
        persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Do paciente filtrado");
        persistir(outro, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "De outro paciente");

        assertThat(repository.buscarAtivas(null, null, null, paciente.getId()))
                .extracting(ListaEspera::getObservacao)
                .containsExactly("Do paciente filtrado");
    }

    @Test
    void buscarAtivas_semFiltros_retornaTodaAFilaAtiva() {
        persistir(paciente, SEGUNDA, LocalTime.of(7, 0), LocalTime.of(8, 0), "Manhã cedo");
        persistir(paciente, DayOfWeek.FRIDAY, LocalTime.of(19, 0), LocalTime.of(20, 0), "Sexta à noite");

        assertThat(repository.buscarAtivas(null, null, null, null)).hasSize(2);
    }

    @Test
    void buscarAtivas_ignoraEntradaRemovidaLogicamente() {
        ListaEspera removida = persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Removida");
        removida.setAtivo(false);
        entityManager.flush();

        assertThat(repository.buscarAtivas(null, null, null, null)).isEmpty();
    }

    /** Paciente inativado é ex-aluno e não deve ser chamado para uma vaga — mesmo recorte das consultas de aula. */
    @Test
    void buscarAtivas_ignoraEntradaDePacienteInativo() {
        Paciente inativo = persistirPaciente("inativo.fila@email.com", false);
        persistir(inativo, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "De paciente inativo");

        assertThat(repository.buscarAtivas(null, null, null, null)).isEmpty();
    }

    @Test
    void persistir_semDataEntrada_preencheOrdemDeChegadaPeloPrePersist() {
        ListaEspera entrada = nova(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Sem data");
        entityManager.persist(entrada);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.buscarAtivas(null, null, null, null))
                .singleElement()
                .satisfies(salva -> {
                    assertThat(salva.getDataEntrada()).isNotNull();
                    assertThat(salva.isAtivo()).isTrue();
                });
    }

    @Test
    void findAtivaById_removidaLogicamente_naoRetorna() {
        ListaEspera entrada = persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Removida");
        entrada.setAtivo(false);
        entityManager.flush();

        assertThat(repository.findAtivaById(entrada.getId())).isEmpty();
    }

    /** Espelho de {@code buscarAtivas}: aqui o paciente inativo <em>não</em> some, senão a linha ficaria insolúvel. */
    @Test
    void findAtivaById_dePacienteInativo_continuaRemovivel() {
        Paciente inativo = persistirPaciente("removivel.fila@email.com", false);
        ListaEspera entrada = persistir(inativo, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "De paciente inativo");

        assertThat(repository.findAtivaById(entrada.getId())).isPresent();
    }

    @Test
    void existeAtivaParaPacienteNaFaixa_mesmaFaixaExata_encontra() {
        persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Original");

        assertThat(repository.existeAtivaParaPacienteNaFaixa(paciente.getId(), SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM))
                .isTrue();
    }

    @Test
    void existeAtivaParaPacienteNaFaixa_faixaApenasSobreposta_naoEhDuplicidade() {
        persistir(paciente, SEGUNDA, LocalTime.of(8, 30), LocalTime.of(9, 30), "Sobreposta");

        assertThat(repository.existeAtivaParaPacienteNaFaixa(paciente.getId(), SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM))
                .isFalse();
    }

    @Test
    void existeAtivaParaPacienteNaFaixa_entradaRemovida_liberaNovaInscricao() {
        ListaEspera entrada = persistir(paciente, SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, "Removida");
        entrada.setAtivo(false);
        entityManager.flush();

        assertThat(repository.existeAtivaParaPacienteNaFaixa(paciente.getId(), SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM))
                .isFalse();
    }

    private List<ListaEspera> buscarNaFaixaConsultada() {
        return repository.buscarAtivas(SEGUNDA, CONSULTA_INICIO, CONSULTA_FIM, null);
    }

    private ListaEspera persistir(
            Paciente paciente, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFim, String observacao) {
        return persistir(paciente, diaSemana, horaInicio, horaFim, observacao, LocalDateTime.of(2026, 8, 10, 9, 0));
    }

    private ListaEspera persistir(
            Paciente paciente,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFim,
            String observacao,
            LocalDateTime dataEntrada) {
        ListaEspera entrada = nova(paciente, diaSemana, horaInicio, horaFim, observacao);
        entrada.setDataEntrada(dataEntrada);
        entityManager.persist(entrada);
        entityManager.flush();
        return entrada;
    }

    private ListaEspera nova(
            Paciente paciente, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFim, String observacao) {
        ListaEspera entrada = new ListaEspera();
        entrada.setPaciente(paciente);
        entrada.setDiaSemana(diaSemana);
        entrada.setHoraInicio(horaInicio);
        entrada.setHoraFim(horaFim);
        entrada.setObservacao(observacao);
        return entrada;
    }

    private Paciente persistirPaciente(String email, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente da fila");
        paciente.setEmail(email);
        paciente.setAtivo(ativo);
        entityManager.persist(paciente);
        entityManager.flush();
        return paciente;
    }
}
