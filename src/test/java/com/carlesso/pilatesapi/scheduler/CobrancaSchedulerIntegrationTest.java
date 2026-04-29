package com.carlesso.pilatesapi.scheduler;

import com.carlesso.pilatesapi.config.AppProperties;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import com.carlesso.pilatesapi.service.AulaService;
import com.carlesso.pilatesapi.service.PagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@Import({PagamentoService.class, AulaService.class, CobrancaScheduler.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.cobranca.cron-vencidos=0 0 6 * * *",
        "app.cobranca.cron-cobrancas-futuras=0 0 7 * * *",
        "app.cobranca.vencimento-dias=10"
})
class CobrancaSchedulerIntegrationTest {

    @Autowired
    private CobrancaScheduler scheduler;

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void limpar() {
        pagamentoRepository.deleteAll();
        planoRepository.deleteAll();
        pacienteRepository.deleteAll();
        entityManager.flush();
    }

    // ── atualizarPagamentosVencidos ──────────────────────────────────────

    @Test
    void atualizarVencidos_marcaVencidoPagamentoPendenteSuperado() {
        Paciente paciente = pacienteRepository.save(paciente("Ana", "ana@email.com", "11122233344", true));
        Plano plano = planoRepository.save(plano(paciente));
        Pagamento pendente = pagamentoRepository.save(
                pagamento(paciente, plano, StatusPagamento.PENDENTE, LocalDate.now().minusDays(1)));

        scheduler.atualizarPagamentosVencidos();

        Pagamento atualizado = pagamentoRepository.findById(pendente.getId()).orElseThrow();
        assertThat(atualizado.getStatus()).isEqualTo(StatusPagamento.VENCIDO);
    }

    @Test
    void atualizarVencidos_naoAlteraPendenteDentroDoVencimento() {
        Paciente paciente = pacienteRepository.save(paciente("Ana", "ana@email.com", "11122233344", true));
        Plano plano = planoRepository.save(plano(paciente));
        Pagamento pendente = pagamentoRepository.save(
                pagamento(paciente, plano, StatusPagamento.PENDENTE, LocalDate.now()));

        scheduler.atualizarPagamentosVencidos();

        Pagamento resultado = pagamentoRepository.findById(pendente.getId()).orElseThrow();
        assertThat(resultado.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
    }

    @Test
    void atualizarVencidos_naoAlteraPagamentoJaConfirmado() {
        Paciente paciente = pacienteRepository.save(paciente("Ana", "ana@email.com", "11122233344", true));
        Plano plano = planoRepository.save(plano(paciente));
        Pagamento pago = pagamentoRepository.save(
                pagamento(paciente, plano, StatusPagamento.PAGO, LocalDate.now().minusDays(5)));

        scheduler.atualizarPagamentosVencidos();

        Pagamento resultado = pagamentoRepository.findById(pago.getId()).orElseThrow();
        assertThat(resultado.getStatus()).isEqualTo(StatusPagamento.PAGO);
    }

    @Test
    void atualizarVencidos_retornaContagemCorreta() {
        Paciente paciente = pacienteRepository.save(paciente("Ana", "ana@email.com", "11122233344", true));
        Plano plano = planoRepository.save(plano(paciente));
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE, LocalDate.now().minusDays(3)));
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE, LocalDate.now().minusDays(2),
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(31)));
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2), LocalDate.now().plusDays(32)));

        assertThat(pagamentoService.atualizarVencidos()).isEqualTo(2);
    }

    // ── gerarCobrancasFuturas ────────────────────────────────────────────

    @Test
    void gerarCobrancasFuturas_geraPagamentoParaPlanSemCobrancaExistente() {
        Paciente paciente = pacienteRepository.save(paciente("Bia", "bia@email.com", "22233344455", true));
        planoRepository.save(plano(paciente));

        scheduler.gerarCobrancasFuturas();

        List<Pagamento> pagamentos = pagamentoRepository.findByPacienteId(paciente.getId());
        assertThat(pagamentos).hasSize(1);
        assertThat(pagamentos.get(0).getStatus()).isEqualTo(StatusPagamento.PENDENTE);
    }

    @Test
    void gerarCobrancasFuturas_geraPagamentoQuandoFaltam7DiasOuMenosParaFimDoPeriodo() {
        Paciente paciente = pacienteRepository.save(paciente("Bia", "bia@email.com", "22233344455", true));
        Plano plano = planoRepository.save(plano(paciente));
        LocalDate periodoFim = LocalDate.now().plusDays(6);
        LocalDate periodoInicio = periodoFim.minusMonths(1).plusDays(1);
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE,
                LocalDate.now().plusDays(10), periodoInicio, periodoFim));

        scheduler.gerarCobrancasFuturas();

        List<Pagamento> pagamentos = pagamentoRepository.findByPacienteId(paciente.getId());
        assertThat(pagamentos).hasSize(2);
    }

    @Test
    void gerarCobrancasFuturas_naoGeraPagamentoQuandoPeriodoAindaEstaLonge() {
        Paciente paciente = pacienteRepository.save(paciente("Bia", "bia@email.com", "22233344455", true));
        Plano plano = planoRepository.save(plano(paciente));
        LocalDate periodoFim = LocalDate.now().plusDays(15);
        LocalDate periodoInicio = periodoFim.minusMonths(1).plusDays(1);
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE,
                LocalDate.now().plusDays(10), periodoInicio, periodoFim));

        scheduler.gerarCobrancasFuturas();

        List<Pagamento> pagamentos = pagamentoRepository.findByPacienteId(paciente.getId());
        assertThat(pagamentos).hasSize(1);
    }

    @Test
    void gerarCobrancasFuturas_ignoraPlanoDePacienteInativo() {
        Paciente pacienteInativo = pacienteRepository.save(paciente("Carlos", "carlos@email.com", "33344455566", false));
        planoRepository.save(plano(pacienteInativo));

        scheduler.gerarCobrancasFuturas();

        assertThat(pagamentoRepository.findByPacienteId(pacienteInativo.getId())).isEmpty();
    }

    @Test
    void gerarCobrancasFuturas_ignoraPlanoInativo() {
        Paciente paciente = pacienteRepository.save(paciente("Dani", "dani@email.com", "44455566677", true));
        Plano plano = plano(paciente);
        plano.setAtivo(false);
        planoRepository.save(plano);

        scheduler.gerarCobrancasFuturas();

        assertThat(pagamentoRepository.findByPacienteId(paciente.getId())).isEmpty();
    }

    @Test
    void gerarCobrancasFuturas_naoGeraCobrancaDuplicadaParaProximoPeriodo() {
        Paciente paciente = pacienteRepository.save(paciente("Eva", "eva@email.com", "55566677788", true));
        Plano plano = planoRepository.save(plano(paciente));
        LocalDate periodoFim = LocalDate.now().plusDays(5);
        LocalDate periodoInicio = periodoFim.minusMonths(1).plusDays(1);
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE,
                LocalDate.now().plusDays(10), periodoInicio, periodoFim));
        LocalDate proximoPeriodoInicio = periodoFim.plusDays(1);
        LocalDate proximoPeriodoFim = proximoPeriodoInicio.plusMonths(1).minusDays(1);
        pagamentoRepository.save(pagamento(paciente, plano, StatusPagamento.PENDENTE,
                proximoPeriodoInicio.plusDays(10), proximoPeriodoInicio, proximoPeriodoFim));

        int gerados = pagamentoService.gerarCobrancasFuturas();

        assertThat(gerados).isEqualTo(0);
    }

    @Test
    void gerarCobrancasFuturas_vencimentoUsaConfiguracao() {
        Paciente paciente = pacienteRepository.save(paciente("Fia", "fia@email.com", "66677788899", true));
        planoRepository.save(plano(paciente));

        scheduler.gerarCobrancasFuturas();

        Pagamento gerado = pagamentoRepository.findByPacienteId(paciente.getId()).get(0);
        assertThat(gerado.getDataVencimento())
                .isEqualTo(gerado.getPeriodoInicio().plusDays(10));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Paciente paciente(String nome, String email, String cpf, boolean ativo) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setEmail(email);
        p.setCpf(cpf);
        p.setAtivo(ativo);
        return p;
    }

    private Plano plano(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("250.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2025, 1, 1));
        return plano;
    }

    private Pagamento pagamento(Paciente paciente, Plano plano, StatusPagamento status, LocalDate dataVencimento) {
        LocalDate periodoInicio = LocalDate.now().withDayOfMonth(1);
        LocalDate periodoFim = periodoInicio.plusMonths(1).minusDays(1);
        return pagamento(paciente, plano, status, dataVencimento, periodoInicio, periodoFim);
    }

    private Pagamento pagamento(Paciente paciente, Plano plano, StatusPagamento status,
                                LocalDate dataVencimento, LocalDate periodoInicio, LocalDate periodoFim) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("250.00"));
        pagamento.setStatus(status);
        pagamento.setDataVencimento(dataVencimento);
        pagamento.setPeriodoInicio(periodoInicio);
        pagamento.setPeriodoFim(periodoFim);
        return pagamento;
    }
}
