package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Agregações mensais e ranking do dashboard (issue #202) contra o PostgreSQL: o {@code extract} e o {@code group by}
 * por expressão são exatamente o que o H2 poderia aceitar com semântica diferente.
 */
@PostgresDataJpaTest
class IndicadoresDashboardRepositoryTest extends PostgresTestcontainerSupport {

    private static final LocalDate INICIO = LocalDate.of(2025, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 2, 28);

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    TestEntityManager entityManager;

    private int sequencial;

    @Test
    void sumValorPorMes_somaSoOsPagosPeloMesDoPagamento() {
        Paciente paciente = entityManager.persist(paciente(true));
        persistirPagamento(paciente, StatusPagamento.PAGO, "200.00", LocalDate.of(2025, 12, 5));
        persistirPagamento(paciente, StatusPagamento.PAGO, "150.50", LocalDate.of(2025, 12, 31));
        persistirPagamento(paciente, StatusPagamento.PAGO, "300.00", LocalDate.of(2026, 1, 2));
        persistirPagamento(paciente, StatusPagamento.PENDENTE, "999.00", LocalDate.of(2026, 1, 10));
        persistirPagamento(paciente, StatusPagamento.VENCIDO, "999.00", LocalDate.of(2026, 1, 11));
        persistirPagamento(paciente, StatusPagamento.PAGO, "999.00", LocalDate.of(2025, 10, 31));
        // Paciente inativo não sai da receita histórica: o dinheiro entrou.
        Paciente inativo = entityManager.persist(paciente(false));
        persistirPagamento(inativo, StatusPagamento.PAGO, "50.00", LocalDate.of(2026, 2, 28));
        entityManager.flush();

        var linhas = pagamentoRepository.sumValorPorMes(StatusPagamento.PAGO, INICIO, FIM);

        assertThat(linhas)
                .extracting(
                        l -> l.getAno() + "-" + l.getMes(), l -> l.getTotal().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple("2025-12", "350.50"), tuple("2026-1", "300.00"), tuple("2026-2", "50.00"));
    }

    @Test
    void countPorMes_separaRealizadasDasNaoRealizadasSemFiltrarPacienteInativo() {
        Profissional profissional = entityManager.persist(profissional("Paula", true));
        Paciente ativo = entityManager.persist(paciente(true));
        Paciente inativo = entityManager.persist(paciente(false));
        Pagamento pagamentoAtivo = persistirPagamento(ativo, StatusPagamento.PAGO, "200.00", LocalDate.of(2025, 12, 1));
        Pagamento pagamentoInativo =
                persistirPagamento(inativo, StatusPagamento.PAGO, "200.00", LocalDate.of(2025, 12, 1));
        entityManager.persist(aula(ativo, pagamentoAtivo, profissional, LocalDate.of(2025, 12, 1), true));
        entityManager.persist(aula(ativo, pagamentoAtivo, profissional, LocalDate.of(2025, 12, 8), true));
        entityManager.persist(aula(ativo, pagamentoAtivo, null, LocalDate.of(2025, 12, 15), false));
        entityManager.persist(aula(inativo, pagamentoInativo, profissional, LocalDate.of(2025, 12, 2), true));
        entityManager.persist(aula(ativo, pagamentoAtivo, profissional, LocalDate.of(2026, 2, 2), false));
        entityManager.persist(aula(ativo, pagamentoAtivo, profissional, LocalDate.of(2026, 3, 2), true));
        entityManager.flush();

        var linhas = aulaRepository.countPorMes(INICIO, FIM);

        assertThat(linhas)
                .extracting(
                        l -> l.getAno() + "-" + l.getMes(),
                        AulaRepository.AulasMensalProjection::getRealizadas,
                        AulaRepository.AulasMensalProjection::getAgendadas)
                .containsExactlyInAnyOrder(tuple("2025-12", 3L, 1L), tuple("2026-2", 0L, 1L));
    }

    @Test
    void countCadastradosEInativadosPorMes_ignoramPacientesSemData() {
        Paciente novoDezembro = paciente(true);
        novoDezembro.setDataCadastro(LocalDate.of(2025, 12, 10));
        entityManager.persist(novoDezembro);
        Paciente novoESaiu = paciente(false);
        novoESaiu.setDataCadastro(LocalDate.of(2025, 12, 20));
        novoESaiu.setDataInativacao(LocalDate.of(2026, 1, 5));
        entityManager.persist(novoESaiu);
        Paciente antigoQueSaiu = paciente(false);
        antigoQueSaiu.setDataInativacao(LocalDate.of(2026, 1, 31));
        entityManager.persist(antigoQueSaiu);
        // Anterior à V41: sem nenhuma das datas, não entra em série alguma.
        entityManager.persist(paciente(false));
        Paciente foraDaJanela = paciente(true);
        foraDaJanela.setDataCadastro(LocalDate.of(2026, 3, 1));
        entityManager.persist(foraDaJanela);
        entityManager.flush();

        assertThat(pacienteRepository.countCadastradosPorMes(INICIO, FIM))
                .extracting(l -> l.getAno() + "-" + l.getMes(), ContagemMensalProjection::getTotal)
                .containsExactly(tuple("2025-12", 2L));
        assertThat(pacienteRepository.countInativadosPorMes(INICIO, FIM))
                .extracting(l -> l.getAno() + "-" + l.getMes(), ContagemMensalProjection::getTotal)
                .containsExactly(tuple("2026-1", 2L));
    }

    @Test
    void countRealizadasPorProfissional_ordenaPorTotalENomeEIgnoraAulaSemProfissional() {
        Profissional paula = entityManager.persist(profissional("Paula", true));
        Profissional ana = entityManager.persist(profissional("Ana", false));
        Profissional bruno = entityManager.persist(profissional("Bruno", true));
        Profissional semAulas = entityManager.persist(profissional("Carla", true));
        Paciente paciente = entityManager.persist(paciente(true));
        Pagamento pagamento = persistirPagamento(paciente, StatusPagamento.PAGO, "200.00", LocalDate.of(2026, 1, 1));
        LocalDate dia = LocalDate.of(2026, 1, 1);
        entityManager.persist(aula(paciente, pagamento, paula, dia, true));
        entityManager.persist(aula(paciente, pagamento, paula, dia.plusDays(1), true));
        entityManager.persist(aula(paciente, pagamento, paula, dia.plusDays(2), true));
        entityManager.persist(aula(paciente, pagamento, bruno, INICIO, true));
        entityManager.persist(aula(paciente, pagamento, ana, FIM, true));
        entityManager.persist(aula(paciente, pagamento, ana, dia.plusDays(3), false));
        entityManager.persist(aula(paciente, pagamento, bruno, FIM.plusDays(1), true));
        entityManager.persist(aula(paciente, pagamento, null, dia.plusDays(4), true));
        entityManager.flush();

        List<AulasPorProfissionalDTO> ranking = aulaRepository.countRealizadasPorProfissional(INICIO, FIM);

        assertThat(ranking)
                .containsExactly(
                        new AulasPorProfissionalDTO(paula.getId(), "Paula", 3),
                        new AulasPorProfissionalDTO(ana.getId(), "Ana", 1),
                        new AulasPorProfissionalDTO(bruno.getId(), "Bruno", 1));
        assertThat(ranking).extracting(AulasPorProfissionalDTO::profissionalId).doesNotContain(semAulas.getId());
    }

    private Paciente paciente(boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente " + sequencial);
        paciente.setEmail("indicadores" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        paciente.setAtivo(ativo);
        return paciente;
    }

    private Profissional profissional(String nome, boolean ativo) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail("prof" + sequencial + "@email.com");
        profissional.setCpf(String.format("9%010d", sequencial));
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        profissional.setAtivo(ativo);
        return profissional;
    }

    /** Um plano por pagamento: a constraint UNIQUE (plano_id, periodo_inicio) recusaria dois no mesmo mês. */
    private Pagamento persistirPagamento(Paciente paciente, StatusPagamento status, String valor, LocalDate data) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal(valor));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2025, 1, 1));
        entityManager.persist(plano);

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal(valor));
        pagamento.setStatus(status);
        pagamento.setDataPagamento(data);
        pagamento.setDataVencimento(data.plusDays(5));
        pagamento.setPeriodoInicio(data.withDayOfMonth(1));
        pagamento.setPeriodoFim(data.withDayOfMonth(data.lengthOfMonth()));
        return entityManager.persist(pagamento);
    }

    private Aula aula(
            Paciente paciente, Pagamento pagamento, Profissional profissional, LocalDate data, boolean realizada) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setRealizada(realizada);
        return aula;
    }
}
