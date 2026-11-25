package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RelatorioFrequenciaExporterServiceTest {

    private final RelatorioFrequenciaExporterService exporter = new RelatorioFrequenciaExporterService();

    @Test
    void exportarXlsx_trazAsMesmasLinhasETotaisDoJson() throws Exception {
        RelatorioFrequenciaDTO relatorio = relatorio();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exporter.exportarXlsx(relatorio)))) {
            Sheet resumo = workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(RelatorioFrequenciaExporterService.TITULO);
            assertThat(valorDaChave(resumo, "Considerado até")).isEqualTo("24/11/2026");
            assertThat(valorDaChave(resumo, "Filtros")).isEqualTo("profissional Paula; presença abaixo de 80%");
            assertThat(valorDaChave(resumo, "Faltas")).isEqualTo("3");
            assertThat(valorDaChave(resumo, "Presença")).isEqualTo("72,7%");

            Sheet frequencia = workbook.getSheet("Frequência");
            assertThat(frequencia.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Presença (%)");
            Row bruno = frequencia.getRow(1);
            assertThat(bruno.getCell(0).getStringCellValue()).isEqualTo("Bruno");
            assertThat(bruno.getCell(1).getStringCellValue()).isEqualTo("Inativo");
            assertThat(bruno.getCell(4).getNumericCellValue()).isEqualTo(1);
            assertThat(bruno.getCell(5).getNumericCellValue()).isEqualTo(66.7);
            assertThat(bruno.getCell(6).getStringCellValue()).isEqualTo("-");
            Row ana = frequencia.getRow(2);
            assertThat(ana.getCell(0).getStringCellValue()).isEqualTo("Ana");
            assertThat(ana.getCell(5).getNumericCellValue()).isEqualTo(75.0);
            assertThat(ana.getCell(6).getStringCellValue()).isEqualTo("20/11/2026");
            Row total = frequencia.getRow(3);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total (2 pacientes)");
            assertThat(total.getCell(2).getNumericCellValue()).isEqualTo(11);
            assertThat(total.getCell(4).getNumericCellValue()).isEqualTo(3);
            assertThat(total.getCell(5).getNumericCellValue()).isEqualTo(72.7);
            assertThat(frequencia.getLastRowNum()).isEqualTo(3);
        }
    }

    @Test
    void exportarXlsx_semLinhas_geraPlanilhaComTotalZerado() throws Exception {
        RelatorioFrequenciaDTO vazio = new RelatorioFrequenciaDTO(
                new PeriodoDTO(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)),
                LocalDate.of(2026, 11, 24),
                new RelatorioFrequenciaDTO.Filtros(null, null, null, null),
                new RelatorioFrequenciaDTO.Totais(0, 0, 0, 0, null),
                List.of(),
                LocalDateTime.of(2026, 11, 25, 10, 0));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exporter.exportarXlsx(vazio)))) {
            assertThat(valorDaChave(workbook.getSheet("Resumo"), "Filtros")).isEqualTo("nenhum");
            assertThat(valorDaChave(workbook.getSheet("Resumo"), "Presença")).isEqualTo("-");
            Row total = workbook.getSheet("Frequência").getRow(1);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total (0 pacientes)");
            assertThat(total.getCell(5)).isNull();
        }
    }

    private static String valorDaChave(Sheet sheet, String chave) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && chave.equals(row.getCell(0).getStringCellValue())) {
                return row.getCell(1).getStringCellValue();
            }
        }
        throw new AssertionError("Chave não encontrada: " + chave);
    }

    private static RelatorioFrequenciaDTO relatorio() {
        return new RelatorioFrequenciaDTO(
                new PeriodoDTO(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                LocalDate.of(2026, 11, 24),
                new RelatorioFrequenciaDTO.Filtros(5L, "Paula", null, new BigDecimal("80")),
                new RelatorioFrequenciaDTO.Totais(2, 11, 8, 3, new BigDecimal("72.7")),
                List.of(
                        new RelatorioFrequenciaDTO.PacienteFrequencia(
                                2L, "Bruno", false, 3, 2, 1, new BigDecimal("66.7"), null),
                        new RelatorioFrequenciaDTO.PacienteFrequencia(
                                1L, "Ana", true, 8, 6, 2, new BigDecimal("75.0"), LocalDate.of(2026, 11, 20))),
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
