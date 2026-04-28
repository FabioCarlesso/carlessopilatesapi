package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class PagamentoRepositoryTest {

    @Autowired
    PagamentoRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findPagamentosConfirmadosParaRelatorioNfse_retornaApenasPagosAtivosNaCompetencia() {
        Paciente pacienteAtivo = entityManager.persist(paciente("Ana Ativa", "ana@email.com", "11122233344", true));
        Paciente pacienteInativo = entityManager.persist(paciente("Bia Inativa", "bia@email.com", "55566677788", false));
        Plano planoAtivo = entityManager.persist(plano(pacienteAtivo));
        Plano planoInativo = entityManager.persist(plano(pacienteInativo));

        Pagamento pagoNaCompetencia = entityManager.persist(pagamento(pacienteAtivo, planoAtivo,
                StatusPagamento.PAGO, LocalDate.of(2026, 4, 10)));
        entityManager.persist(pagamento(pacienteAtivo, planoAtivo, StatusPagamento.PENDENTE,
                LocalDate.of(2026, 4, 12)));
        entityManager.persist(pagamento(pacienteAtivo, planoAtivo, StatusPagamento.PAGO,
                LocalDate.of(2026, 3, 31)));
        entityManager.persist(pagamento(pacienteInativo, planoInativo, StatusPagamento.PAGO,
                LocalDate.of(2026, 4, 15)));
        entityManager.flush();
        entityManager.clear();

        var pagamentos = repository.findPagamentosConfirmadosParaRelatorioNfse(
                StatusPagamento.PAGO,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertThat(pagamentos)
                .extracting(Pagamento::getId)
                .containsExactly(pagoNaCompetencia.getId());
    }

    @Test
    void findPacienteIdsComPagamentoConfirmadoAntes_retornaPacientesComPagamentoAnterior() {
        Paciente paciente = entityManager.persist(paciente("Ana Ativa", "ana@email.com", "11122233344", true));
        Paciente pacienteSemAnterior = entityManager.persist(paciente("Bia Ativa", "bia@email.com", "55566677788", true));
        Plano plano = entityManager.persist(plano(paciente));
        Plano planoSemAnterior = entityManager.persist(plano(pacienteSemAnterior));
        entityManager.persist(pagamento(paciente, plano, StatusPagamento.PAGO, LocalDate.of(2026, 3, 10)));
        entityManager.persist(pagamento(pacienteSemAnterior, planoSemAnterior, StatusPagamento.PAGO,
                LocalDate.of(2026, 4, 10)));
        entityManager.flush();

        var pacienteIds = repository.findPacienteIdsComPagamentoConfirmadoAntes(
                List.of(paciente.getId(), pacienteSemAnterior.getId()),
                StatusPagamento.PAGO,
                LocalDate.of(2026, 4, 1));

        assertThat(pacienteIds).containsExactly(paciente.getId());
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
