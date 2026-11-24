package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

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
    void exportarCsv_semCpfComPendencia_deixaCpfVazioEMostraAPendencia() {
        var semCpf = new RelatorioNfseResponseDTO(
                "Bia Lima",
                null,
                new BigDecimal("300.00"),
                "04/2026",
                "Aulas de Pilates - Competência 04/2026",
                false,
                LocalDate.of(2026, 4, 12),
                "",
                2L,
                true,
                "Paciente deseja CPF/CNPJ na nota, mas o CPF não está cadastrado");

        String content = new String(service.exportarCsv(List.of(semCpf)), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(content).contains("Observacoes,CPFNaNota,Pendencia");
        assertThat(content).contains("\"Bia Lima\",,\"300.00\"");
        assertThat(content).contains("\"Sim\",\"Paciente deseja CPF/CNPJ na nota, mas o CPF não está cadastrado\"");
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
                "-observacao",
                1L,
                true,
                null);

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
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("CPFNaNota");
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("Pendencia");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("Sim");
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
                "",
                1L,
                true,
                null);
    }
}
