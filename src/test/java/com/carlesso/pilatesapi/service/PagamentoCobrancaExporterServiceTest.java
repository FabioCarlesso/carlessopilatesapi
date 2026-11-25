package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository.ResumoContatosProjection;
import com.carlesso.pilatesapi.service.PagamentoCobrancaExporterService.LinhaCobranca;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PagamentoCobrancaExporterServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);

    private ContatoCobrancaRepository contatoRepository;
    private PagamentoCobrancaExporterService exporter;

    @BeforeEach
    void setUp() {
        contatoRepository = mock(ContatoCobrancaRepository.class);
        Clock relogio = Clock.fixed(ZonedDateTime.of(HOJE.atTime(10, 0), FUSO).toInstant(), FUSO);
        exporter = new PagamentoCobrancaExporterService(contatoRepository, relogio);
    }

    @Test
    void montarLinhas_calculaDiasEmAtrasoPeloVencimento() {
        var vencido = pagamento(1L, StatusPagamento.VENCIDO, HOJE.minusDays(12), "250.00");
        // Ainda PENDENTE porque o scheduler não rodou, mas já venceu: conta atraso.
        var pendenteJaVencido = pagamento(2L, StatusPagamento.PENDENTE, HOJE.minusDays(1), "250.00");
        var venceHoje = pagamento(3L, StatusPagamento.PENDENTE, HOJE, "250.00");
        var aVencer = pagamento(4L, StatusPagamento.PENDENTE, HOJE.plusDays(5), "250.00");
        var pagoAtrasado = pagamento(5L, StatusPagamento.PAGO, HOJE.minusDays(30), "250.00");
        when(contatoRepository.resumirPorPagamentos(any())).thenReturn(List.of());

        List<LinhaCobranca> linhas =
                exporter.montarLinhas(List.of(vencido, pendenteJaVencido, venceHoje, aVencer, pagoAtrasado));

        assertThat(linhas).extracting(LinhaCobranca::diasEmAtraso).containsExactly(12L, 1L, 0L, 0L, 0L);
    }

    @Test
    void montarLinhas_buscaOsContatosDeTodosOsPagamentosNumaConsultaSo() {
        var comContatos = pagamento(1L, StatusPagamento.VENCIDO, HOJE.minusDays(3), "250.00");
        var semContatos = pagamento(2L, StatusPagamento.PENDENTE, HOJE.plusDays(3), "300.00");
        when(contatoRepository.resumirPorPagamentos(List.of(1L, 2L)))
                .thenReturn(List.of(resumo(1L, 3L, HOJE.minusDays(1))));

        List<LinhaCobranca> linhas = exporter.montarLinhas(List.of(comContatos, semContatos));

        verify(contatoRepository).resumirPorPagamentos(List.of(1L, 2L));
        assertThat(linhas.get(0).quantidadeContatos()).isEqualTo(3);
        assertThat(linhas.get(0).ultimoContato()).isEqualTo(HOJE.minusDays(1));
        assertThat(linhas.get(1).quantidadeContatos()).isZero();
        assertThat(linhas.get(1).ultimoContato()).isNull();
        assertThat(linhas.get(0).paciente()).isEqualTo("Paciente 1");
        assertThat(linhas.get(0).telefone()).isEqualTo("45999990001");
    }

    @Test
    void exportarXlsx_semPagamentos_naoConsultaContatosEGeraSoCabecalhoETotal() throws Exception {
        byte[] xlsx = exporter.exportarXlsx(List.of());

        verifyNoInteractions(contatoRepository);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Paciente");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Total (0 cobranças)");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isZero();
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
        }
    }

    @Test
    void exportarXlsx_geraUmaLinhaPorPagamentoNaOrdemRecebidaComLinhaDeTotal() throws Exception {
        var primeiro = pagamento(1L, StatusPagamento.VENCIDO, HOJE.minusDays(10), "250.00");
        var segundo = pagamento(2L, StatusPagamento.PENDENTE, HOJE.plusDays(2), "300.50");
        segundo.getPaciente().setTelefone(null);
        when(contatoRepository.resumirPorPagamentos(any())).thenReturn(List.of(resumo(1L, 2L, HOJE.minusDays(4))));

        byte[] xlsx = exporter.exportarXlsx(List.of(primeiro, segundo));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Cobranças");
            Row cabecalho = sheet.getRow(0);
            for (int i = 0; i < PagamentoCobrancaExporterService.CABECALHO.size(); i++) {
                assertThat(cabecalho.getCell(i).getStringCellValue())
                        .isEqualTo(PagamentoCobrancaExporterService.CABECALHO.get(i));
            }

            Row linha = sheet.getRow(1);
            assertThat(linha.getCell(0).getStringCellValue()).isEqualTo("Paciente 1");
            assertThat(linha.getCell(1).getStringCellValue()).isEqualTo("45999990001");
            assertThat(linha.getCell(2).getNumericCellValue()).isEqualTo(250.00);
            assertThat(DateUtil.getLocalDateTime(linha.getCell(3).getNumericCellValue())
                            .toLocalDate())
                    .isEqualTo(HOJE.minusDays(10));
            assertThat(linha.getCell(4).getNumericCellValue()).isEqualTo(10);
            assertThat(linha.getCell(5).getStringCellValue()).isEqualTo("VENCIDO");
            assertThat(linha.getCell(6).getStringCellValue()).isEqualTo("01/11/2026 a 30/11/2026");
            assertThat(linha.getCell(7).getNumericCellValue()).isEqualTo(2);
            assertThat(DateUtil.getLocalDateTime(linha.getCell(8).getNumericCellValue())
                            .toLocalDate())
                    .isEqualTo(HOJE.minusDays(4));

            Row semContato = sheet.getRow(2);
            assertThat(semContato.getCell(1).getStringCellValue()).isEqualTo("-");
            assertThat(semContato.getCell(4).getNumericCellValue()).isZero();
            assertThat(semContato.getCell(7).getNumericCellValue()).isZero();
            assertThat(semContato.getCell(8).getStringCellValue()).isEqualTo("-");

            Row total = sheet.getRow(3);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total (2 cobranças)");
            assertThat(total.getCell(2).getNumericCellValue()).isEqualTo(550.50);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }
    }

    @Test
    void nomeArquivo_usaADataDeHojeNoFusoDoEstudio() {
        assertThat(exporter.nomeArquivo()).isEqualTo("cobrancas-2026-11-25.xlsx");
    }

    private Pagamento pagamento(Long id, StatusPagamento status, LocalDate vencimento, String valor) {
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente " + id);
        paciente.setTelefone("4599999000" + id);
        Pagamento pagamento = new Pagamento();
        ReflectionTestUtils.setField(pagamento, "id", id);
        pagamento.setPaciente(paciente);
        pagamento.setValor(new BigDecimal(valor));
        pagamento.setStatus(status);
        pagamento.setDataVencimento(vencimento);
        pagamento.setPeriodoInicio(LocalDate.of(2026, 11, 1));
        pagamento.setPeriodoFim(LocalDate.of(2026, 11, 30));
        return pagamento;
    }

    private ResumoContatosProjection resumo(Long pagamentoId, Long quantidade, LocalDate ultimoContato) {
        return new ResumoContatosProjection() {
            @Override
            public Long getPagamentoId() {
                return pagamentoId;
            }

            @Override
            public Long getQuantidade() {
                return quantidade;
            }

            @Override
            public LocalDate getUltimoContato() {
                return ultimoContato;
            }
        };
    }
}
