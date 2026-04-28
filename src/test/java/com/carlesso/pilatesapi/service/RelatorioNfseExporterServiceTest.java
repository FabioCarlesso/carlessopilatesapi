package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioNfseExporterServiceTest {

    private final RelatorioNfseExporterService service = new RelatorioNfseExporterService();

    @Test
    void exportarCsv_deveGerarCabecalhoEConteudo() {
        byte[] csv = service.exportarCsv(List.of(item()));

        String content = new String(csv);
        assertThat(content).contains("Nome,CPF/CNPJ,ValorPago,Competencia");
        assertThat(content).contains("\"Ana Souza\",\"11122233344\",\"250.00\",\"04/2026\"");
    }

    @Test
    void exportarCsv_deveNeutralizarFormulaInjection() {
        var itemMalicioso = new RelatorioNfseResponseDTO(
                "=IMPORTDATA(\"https://example.com\")",
                "+5511999999999",
                new BigDecimal("250.00"),
                "04/2026",
                "@SUM(1,1)",
                false,
                LocalDate.of(2026, 4, 10),
                "-observacao"
        );

        String content = new String(service.exportarCsv(List.of(itemMalicioso)));

        assertThat(content).contains("\"'=IMPORTDATA(\"\"https://example.com\"\")\"");
        assertThat(content).contains("\"'+5511999999999\"");
        assertThat(content).contains("\"'@SUM(1,1)\"");
        assertThat(content).contains("\"'-observacao\"");
    }

    @Test
    void exportarXlsx_deveGerarPlanilhaNfse() throws Exception {
        byte[] xlsx = service.exportarXlsx(List.of(item()));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetName(0)).isEqualTo("NFSE");
            var sheet = workbook.getSheet("NFSE");

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Nome");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("NotaAnteriorEmitida");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Ana Souza");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(250.00d);
            assertThat(sheet.getRow(1).getCell(5).getBooleanCellValue()).isFalse();
        }
    }

    private RelatorioNfseResponseDTO item() {
        return new RelatorioNfseResponseDTO(
                "Ana Souza",
                "11122233344",
                new BigDecimal("250.00"),
                "04/2026",
                "Aulas de Pilates - Competência 04/2026",
                false,
                LocalDate.of(2026, 4, 10),
                ""
        );
    }
}
