package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.PagamentoResumoDTO;
import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoAulaDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResumoDTO;
import com.carlesso.pilatesapi.dto.ResumoFinanceiroDTO;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RelatorioPagamentoExporterServiceTest {

    private final RelatorioPagamentoExporterService service = new RelatorioPagamentoExporterService();

    private ProfissionalPagamentoRelatorioDTO relatorio() {
        var profissional =
                new ProfissionalResumoDTO(1L, "Paula Mendes", "12345678900", TipoContrato.PJ, new BigDecimal("45.00"));
        var periodo = new PeriodoDTO(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28));
        var resumo = new ResumoFinanceiroDTO(2L, 1L, new BigDecimal("200.00"), new BigDecimal("22.50"));
        var pagamento = new PagamentoResumoDTO(
                5L, new BigDecimal("200.00"), 8L, 2L, new BigDecimal("25.00"), new BigDecimal("22.50"));
        var aula1 = new ProfissionalPagamentoAulaDTO(
                10L,
                LocalDate.of(2025, 2, 3),
                2L,
                "Ana",
                5L,
                new BigDecimal("200.00"),
                8L,
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                new BigDecimal("11.25"));
        var aula2 = new ProfissionalPagamentoAulaDTO(
                11L,
                LocalDate.of(2025, 2, 5),
                2L,
                "Ana",
                5L,
                new BigDecimal("200.00"),
                8L,
                new BigDecimal("25.00"),
                new BigDecimal("45.00"),
                new BigDecimal("11.25"));
        return new ProfissionalPagamentoRelatorioDTO(
                profissional,
                periodo,
                resumo,
                List.of(pagamento),
                List.of(aula1, aula2),
                LocalDateTime.of(2025, 3, 1, 10, 0));
    }

    @Test
    void exportarPdf_deveGerarBytesComMagicNumberPdf() {
        byte[] pdf = service.exportarPdf(relatorio());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportarXlsx_deveGerarPlanilhaComAbasResumoPagamentosAulas() throws Exception {
        byte[] xlsx = service.exportarXlsx(relatorio());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("Resumo");
            assertThat(workbook.getSheetName(1)).isEqualTo("Pagamentos");
            assertThat(workbook.getSheetName(2)).isEqualTo("Aulas");

            Sheet aulas = workbook.getSheet("Aulas");
            assertThat(aulas.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Aula");
            assertThat(aulas.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Paciente");
            assertThat(aulas.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(10d);
            assertThat(aulas.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(11d);

            Sheet pagamentos = workbook.getSheet("Pagamentos");
            assertThat(pagamentos.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Total profissional");
            assertThat(pagamentos.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(5d);
            assertThat(pagamentos.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(22.50d);
        }
    }

    @Test
    void exportarPdf_quandoNaoHaAulas_deveGerarPdfComMensagem() {
        var profissional =
                new ProfissionalResumoDTO(1L, "Paula Mendes", "12345678900", TipoContrato.PJ, new BigDecimal("45.00"));
        var periodo = new PeriodoDTO(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28));
        var resumo = new ResumoFinanceiroDTO(0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
        var vazio = new ProfissionalPagamentoRelatorioDTO(
                profissional, periodo, resumo, List.of(), List.of(), LocalDateTime.of(2025, 3, 1, 10, 0));

        byte[] pdf = service.exportarPdf(vazio);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
