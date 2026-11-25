package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RelatorioAulasSemEvolucaoExporterServiceTest {

    private final RelatorioAulasSemEvolucaoExporterService exporter = new RelatorioAulasSemEvolucaoExporterService();

    @Test
    void exportarXlsx_trazResumoPorProfissionalEAsMesmasLinhasDoJson() throws Exception {
        byte[] xlsx = exporter.exportarXlsx(relatorio());

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Resumo");
            assertThat(workbook.getSheetName(1)).isEqualTo("Aulas");

            Sheet resumo = workbook.getSheet("Resumo");
            assertThat(texto(resumo, 2, 1)).isEqualTo("01/10/2026 até 30/11/2026");
            assertThat(texto(resumo, 3, 1)).isEqualTo("todos");
            assertThat(texto(resumo, 5, 1)).isEqualTo("2");
            assertThat(texto(resumo, 7, 0)).isEqualTo("Profissional");
            assertThat(texto(resumo, 8, 0)).isEqualTo("Camila");
            assertThat(resumo.getRow(8).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(texto(resumo, 9, 0)).isEqualTo("Sem profissional");

            Sheet aulas = workbook.getSheet("Aulas");
            assertThat(texto(aulas, 0, 0)).isEqualTo("Data");
            assertThat(texto(aulas, 1, 0)).isEqualTo("05/10/2026");
            assertThat(texto(aulas, 1, 1)).isEqualTo("08:00");
            assertThat(texto(aulas, 1, 2)).isEqualTo("Ana Souza");
            assertThat(texto(aulas, 1, 3)).isEqualTo("Camila");
            assertThat(texto(aulas, 2, 1)).isEqualTo("-");
            assertThat(texto(aulas, 2, 3)).isEqualTo("Sem profissional");
            assertThat(texto(aulas, 3, 0)).isEqualTo("Total (2 aulas)");
            assertThat(aulas.getLastRowNum()).isEqualTo(3);
        }
    }

    @Test
    void nomeArquivo_levaOPeriodo() {
        assertThat(exporter.nomeArquivo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)))
                .isEqualTo("relatorio-aulas-sem-evolucao-2026-10-01-2026-11-30.xlsx");
    }

    private static String texto(Sheet sheet, int linha, int coluna) {
        return sheet.getRow(linha).getCell(coluna).getStringCellValue();
    }

    private static RelatorioAulasSemEvolucaoDTO relatorio() {
        return new RelatorioAulasSemEvolucaoDTO(
                new PeriodoDTO(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30)),
                new RelatorioAulasSemEvolucaoDTO.Filtros(null, null),
                new RelatorioAulasSemEvolucaoDTO.Resumo(
                        2,
                        List.of(
                                new RelatorioAulasSemEvolucaoDTO.TotalPorProfissional(5L, "Camila", 1),
                                new RelatorioAulasSemEvolucaoDTO.TotalPorProfissional(null, "Sem profissional", 1))),
                List.of(
                        new RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao(
                                11L, LocalDate.of(2026, 10, 5), LocalTime.of(8, 0), 7L, "Ana Souza", 5L, "Camila"),
                        new RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao(
                                12L, LocalDate.of(2026, 10, 6), null, 8L, "Bruno Lima", null, null)),
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
