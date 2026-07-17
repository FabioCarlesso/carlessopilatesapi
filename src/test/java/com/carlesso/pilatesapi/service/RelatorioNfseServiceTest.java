package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.repository.NotaFiscalEmitidaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RelatorioNfseServiceTest {

    @Mock
    PagamentoRepository pagamentoRepository;

    @Mock
    NotaFiscalEmitidaRepository notaFiscalEmitidaRepository;

    @InjectMocks
    RelatorioNfseService service;

    @Test
    void gerar_deveRetornarPagamentosConfirmadosDaCompetencia() {
        Pagamento pagamento =
                pagamento("Ana Souza", "11122233344", new BigDecimal("250.00"), LocalDate.of(2026, 4, 10));

        when(pagamentoRepository.findPagamentosConfirmadosParaRelatorioNfse(
                        StatusPagamento.PAGO, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .thenReturn(List.of(pagamento));
        when(notaFiscalEmitidaRepository.findPacienteIdsComNotaEmitidaAntes(List.of(1L), LocalDate.of(2026, 4, 1)))
                .thenReturn(List.of(1L));

        var relatorio = service.gerar("04/2026", null);

        assertThat(relatorio).singleElement().satisfies(item -> {
            assertThat(item.nome()).isEqualTo("Ana Souza");
            assertThat(item.cpfCnpj()).isEqualTo("11122233344");
            assertThat(item.valorPago()).isEqualByComparingTo("250.00");
            assertThat(item.competencia()).isEqualTo("04/2026");
            assertThat(item.descricaoServico()).isEqualTo("Aulas de Pilates - Competência 04/2026");
            assertThat(item.notaAnteriorEmitida()).isTrue();
            assertThat(item.dataPagamento()).isEqualTo(LocalDate.of(2026, 4, 10));
            assertThat(item.observacoes()).isEmpty();
        });
        verify(notaFiscalEmitidaRepository).findPacienteIdsComNotaEmitidaAntes(List.of(1L), LocalDate.of(2026, 4, 1));
    }

    @Test
    void gerar_comFiltroNotaAnteriorEmitida_deveFiltrarResultado() {
        Pagamento comNotaAnterior =
                pagamento("Ana Souza", "11122233344", new BigDecimal("250.00"), LocalDate.of(2026, 4, 10));
        Pagamento semNotaAnterior =
                pagamento("Bia Lima", "55566677788", new BigDecimal("300.00"), LocalDate.of(2026, 4, 12));
        ReflectionTestUtils.setField(semNotaAnterior.getPaciente(), "id", 2L);

        when(pagamentoRepository.findPagamentosConfirmadosParaRelatorioNfse(
                        StatusPagamento.PAGO, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .thenReturn(List.of(comNotaAnterior, semNotaAnterior));
        when(notaFiscalEmitidaRepository.findPacienteIdsComNotaEmitidaAntes(List.of(1L, 2L), LocalDate.of(2026, 4, 1)))
                .thenReturn(List.of(1L));

        var relatorio = service.gerar("04/2026", false);

        assertThat(relatorio).singleElement().extracting("nome").isEqualTo("Bia Lima");
        verify(notaFiscalEmitidaRepository)
                .findPacienteIdsComNotaEmitidaAntes(List.of(1L, 2L), LocalDate.of(2026, 4, 1));
    }

    @Test
    void gerar_competenciaObrigatoria_deveLancarErro() {
        assertThatThrownBy(() -> service.gerar(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatória");
    }

    @Test
    void gerar_competenciaForaDoFormato_deveLancarErro() {
        assertThatThrownBy(() -> service.gerar("13/2026", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MM/AAAA");
    }

    @Test
    void gerar_semCpf_deveLancarErroDeNegocio() {
        Pagamento pagamento = pagamento("Ana Souza", "", new BigDecimal("250.00"), LocalDate.of(2026, 4, 10));

        when(pagamentoRepository.findPagamentosConfirmadosParaRelatorioNfse(
                        StatusPagamento.PAGO, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))
                .thenReturn(List.of(pagamento));
        when(notaFiscalEmitidaRepository.findPacienteIdsComNotaEmitidaAntes(List.of(1L), LocalDate.of(2026, 4, 1)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.gerar("04/2026", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CPF/CNPJ");
    }

    private Pagamento pagamento(String nome, String cpf, BigDecimal valor, LocalDate dataPagamento) {
        Paciente paciente = new Paciente();
        ReflectionTestUtils.setField(paciente, "id", 1L);
        paciente.setNome(nome);
        paciente.setCpf(cpf);
        paciente.setEmail(nome.toLowerCase().replace(" ", ".") + "@email.com");

        Plano plano = new Plano();
        ReflectionTestUtils.setField(plano, "id", 1L);
        plano.setPaciente(paciente);

        Pagamento pagamento = new Pagamento();
        ReflectionTestUtils.setField(pagamento, "id", 10L);
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(valor);
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataPagamento(dataPagamento);
        pagamento.setDataVencimento(dataPagamento.plusDays(5));
        pagamento.setPeriodoInicio(dataPagamento.withDayOfMonth(1));
        pagamento.setPeriodoFim(dataPagamento.withDayOfMonth(dataPagamento.lengthOfMonth()));
        return pagamento;
    }
}
