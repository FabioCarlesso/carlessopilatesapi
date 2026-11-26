package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.PendenciaCadastroPaciente;
import com.carlesso.pilatesapi.dto.RelatorioPacientesDTO;
import com.carlesso.pilatesapi.dto.SituacaoPacienteRelatorio;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RelatorioPacientesExporterServiceTest {

    private final RelatorioPacientesExporterService service = new RelatorioPacientesExporterService();

    @Test
    void exportarPdf_trazFiltrosTotaisELinhas() throws Exception {
        byte[] pdf = service.exportarPdf(relatorio(List.of(bruna(), carlos())));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        String texto = textoDoPdf(pdf);
        assertThat(texto)
                .contains(RelatorioPacientesExporterService.TITULO)
                .contains("Ativos e inativos; aniversariantes de dezembro")
                .contains("2 pacientes (1 ativos, 1 inativos), 1 com pendência")
                .contains("Bruna Souza")
                .contains("Carlos Lima")
                .contains("Sem CPF, Sem e-mail");
    }

    @Test
    void exportarPdf_semPacientes_avisaEmVezDeTabelaVazia() throws Exception {
        String texto = textoDoPdf(service.exportarPdf(relatorio(List.of())));

        assertThat(texto).contains("Nenhum paciente encontrado com os filtros informados.");
    }

    @Test
    void exportarXlsx_trazResumoEAsMesmasLinhas() throws Exception {
        byte[] xlsx = service.exportarXlsx(relatorio(List.of(bruna(), carlos())));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet resumo = workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(RelatorioPacientesExporterService.TITULO);
            assertThat(resumo.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Pacientes");
            assertThat(resumo.getRow(5).getCell(1).getStringCellValue()).isEqualTo("2");
            assertThat(resumo.getRow(8).getCell(1).getStringCellValue()).isEqualTo("1");

            Sheet pacientes = workbook.getSheet("Pacientes");
            assertThat(pacientes.getLastRowNum()).isEqualTo(2);
            Row bruna = pacientes.getRow(1);
            assertThat(bruna.getCell(0).getStringCellValue()).isEqualTo("Bruna Souza");
            assertThat(bruna.getCell(4).getStringCellValue()).isEqualTo("03/12/1989");
            assertThat(bruna.getCell(5).getNumericCellValue()).isEqualTo(36);
            assertThat(bruna.getCell(6).getStringCellValue()).isEqualTo("Ativo");
            assertThat(bruna.getCell(9).getStringCellValue()).isEqualTo("2x por semana");
            assertThat(bruna.getCell(11).getStringCellValue()).isEqualTo("08:00");
            assertThat(bruna.getCell(12).getNumericCellValue()).isEqualTo(300.0);
            assertThat(bruna.getCell(13).getStringCellValue()).isEqualTo("Paula Mendes");
            Row carlos = pacientes.getRow(2);
            assertThat(carlos.getCell(6).getStringCellValue()).isEqualTo("Inativo");
            assertThat(carlos.getCell(14).getStringCellValue()).isEqualTo("Sem CPF, Sem e-mail");
        }
    }

    private RelatorioPacientesDTO relatorio(List<RelatorioPacientesDTO.PacienteItem> pacientes) {
        long ativos = pacientes.stream()
                .filter(RelatorioPacientesDTO.PacienteItem::ativo)
                .count();
        long comPendencia =
                pacientes.stream().filter(p -> !p.pendencias().isEmpty()).count();
        return new RelatorioPacientesDTO(
                new RelatorioPacientesDTO.Filtros(SituacaoPacienteRelatorio.TODOS, false, false, 12, null, null),
                new RelatorioPacientesDTO.Totais(pacientes.size(), ativos, pacientes.size() - ativos, comPendencia),
                pacientes,
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }

    private RelatorioPacientesDTO.PacienteItem bruna() {
        return new RelatorioPacientesDTO.PacienteItem(
                1L,
                "Bruna Souza",
                "12345678901",
                "45999990001",
                "bruna@email.com",
                LocalDate.of(1989, 12, 3),
                36,
                true,
                LocalDate.of(2026, 1, 10),
                null,
                new RelatorioPacientesDTO.PlanoAtivo(
                        10L,
                        TipoPagamento.MENSAL,
                        FrequenciaSemanal.DUAS_VEZES,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        LocalTime.of(8, 0),
                        new BigDecimal("300.00")),
                new RelatorioPacientesDTO.ProfissionalResumo(5L, "Paula Mendes"),
                null,
                List.of());
    }

    private RelatorioPacientesDTO.PacienteItem carlos() {
        return new RelatorioPacientesDTO.PacienteItem(
                2L,
                "Carlos Lima",
                null,
                "45999990002",
                null,
                LocalDate.of(1975, 12, 20),
                50,
                false,
                null,
                LocalDate.of(2026, 10, 1),
                null,
                null,
                null,
                List.of(PendenciaCadastroPaciente.SEM_CPF, PendenciaCadastroPaciente.SEM_EMAIL));
    }

    private String textoDoPdf(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }
}
