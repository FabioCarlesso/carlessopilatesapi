package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import com.carlesso.pilatesapi.util.PaginacaoGuard;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@PostgresDataJpaTest
class PagamentoRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    PagamentoRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findPagamentosConfirmadosParaRelatorioNfse_retornaApenasPagosAtivosNaCompetencia() {
        Paciente pacienteAtivo = entityManager.persist(paciente("Ana Ativa", "ana@email.com", "11122233344", true));
        Paciente pacienteInativo =
                entityManager.persist(paciente("Bia Inativa", "bia@email.com", "55566677788", false));
        Plano planoAtivo = entityManager.persist(plano(pacienteAtivo));
        // Segundo plano do mesmo paciente: o pagamento PENDENTE da competência
        // usa-o para não violar a constraint UNIQUE (plano_id, periodo_inicio)
        // com o pagamento PAGO — ambos teriam período 2026-04-01 no mesmo plano.
        Plano planoAtivoSecundario = entityManager.persist(plano(pacienteAtivo));
        Plano planoInativo = entityManager.persist(plano(pacienteInativo));

        Pagamento pagoNaCompetencia = entityManager.persist(
                pagamento(pacienteAtivo, planoAtivo, StatusPagamento.PAGO, LocalDate.of(2026, 4, 10)));
        entityManager.persist(
                pagamento(pacienteAtivo, planoAtivoSecundario, StatusPagamento.PENDENTE, LocalDate.of(2026, 4, 12)));
        entityManager.persist(pagamento(pacienteAtivo, planoAtivo, StatusPagamento.PAGO, LocalDate.of(2026, 3, 31)));
        entityManager.persist(
                pagamento(pacienteInativo, planoInativo, StatusPagamento.PAGO, LocalDate.of(2026, 4, 15)));
        entityManager.flush();
        entityManager.clear();

        var pagamentos = repository.findPagamentosConfirmadosParaRelatorioNfse(
                StatusPagamento.PAGO, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(pagamentos).extracting(Pagamento::getId).containsExactly(pagoNaCompetencia.getId());
    }

    /**
     * As consultas de pagamento passam pela Criteria API — a derivada por paciente e a {@code Specification} da
     * listagem global —, que não aceita precedência de nulos; por isso o serviço usa a variante sem {@code nullsLast}.
     * Este teste roda as duas contra o banco com a ordenação que o serviço monta.
     */
    @Test
    void consultasPorCriteria_aceitamAOrdenacaoDoPaginacaoGuard() {
        Paciente paciente = entityManager.persist(paciente("Ana", "ana.criteria@email.com", "99988877766", true));
        Plano plano = entityManager.persist(plano(paciente));
        Plano outroPlano = entityManager.persist(plano(paciente));
        Pagamento antigo =
                entityManager.persist(pagamento(paciente, plano, StatusPagamento.PAGO, LocalDate.of(2026, 3, 10)));
        Pagamento recente =
                entityManager.persist(pagamento(paciente, outroPlano, StatusPagamento.PAGO, LocalDate.of(2026, 4, 10)));
        entityManager.flush();
        entityManager.clear();
        Pageable pageable = PaginacaoGuard.comDesempatePorId(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dataVencimento")),
                Set.of("dataVencimento"),
                Sort.Direction.DESC);

        assertThat(repository.findByPacienteId(paciente.getId(), pageable))
                .extracting(Pagamento::getId)
                .containsExactly(recente.getId(), antigo.getId());
        assertThat(repository.findAll(
                        (root, query, cb) -> cb.equal(root.get("paciente").get("id"), paciente.getId()), pageable))
                .extracting(Pagamento::getId)
                .containsExactly(recente.getId(), antigo.getId());
    }

    private Paciente paciente(String nome, String email, String cpf, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail(email);
        paciente.setCpf(cpf);
        paciente.setAtivo(ativo);
        return paciente;
    }

    private Plano plano(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("250.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2026, 1, 1));
        return plano;
    }

    private Pagamento pagamento(Paciente paciente, Plano plano, StatusPagamento status, LocalDate dataPagamento) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("250.00"));
        pagamento.setStatus(status);
        pagamento.setDataPagamento(dataPagamento);
        pagamento.setDataVencimento(dataPagamento.plusDays(5));
        pagamento.setPeriodoInicio(dataPagamento.withDayOfMonth(1));
        pagamento.setPeriodoFim(dataPagamento.withDayOfMonth(dataPagamento.lengthOfMonth()));
        return pagamento;
    }
}
