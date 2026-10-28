package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RelatorioFinanceiroExporterServiceTest {

    private final RelatorioFinanceiroExporterService service = new RelatorioFinanceiroExporterService();

    @Test
    void exportarPdf_trazResumoERepasses() throws Exception {
        byte[] pdf = service.exportarPdf(relatorio(List.of(paula())));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        String texto = textoDoPdf(pdf);
        assertThat(texto)
                .contains(RelatorioFinanceiroExporterService.TITULO)
                .contains("01/02/2026 até 28/02/2026")
                .contains("Receita: R$ 1000,00")
                .contains("Total de repasses: R$ 90,00")
                .contains("Resultado líquido: R$ 910,00")
                .contains("Inadimplência: R$ 250,00")
                .contains("Paula Mendes");
    }

    @Test
    void exportarPdf_semRepasses_avisaEmVezDeTabelaVazia() throws Exception {
        String texto = textoDoPdf(service.exportarPdf(relatorio(List.of())));

        assertThat(texto).contains("Nenhum repasse no período.");
    }

    @Test
    void exportarXlsx_trazAbasResumoERepasses() throws Exception {
        byte[] xlsx = service.exportarXlsx(relatorio(List.of(paula())));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet resumo = workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(RelatorioFinanceiroExporterService.TITULO);
            assertThat(resumo.getRow(2).getCell(1).getStringCellValue()).isEqualTo("01/02/2026 até 28/02/2026");
            assertThat(resumo.getRow(6).getCell(0).getStringCellValue()).isEqualTo("Receita");
            assertThat(resumo.getRow(6).getCell(1).getStringCellValue()).isEqualTo("R$ 1000,00");
            assertThat(resumo.getRow(9).getCell(1).getStringCellValue()).isEqualTo("R$ 910,00");

            Sheet repasses = workbook.getSheet("Repasses");
            assertThat(repasses.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Repasse");
            assertThat(repasses.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Paula Mendes");
            assertThat(repasses.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(8);
            assertThat(repasses.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(90.0);
        }
    }

    @Test
    void exportarXlsx_semRepasses_mantemSoOCabecalho() throws Exception {
        byte[] xlsx = service.exportarXlsx(relatorio(List.of()));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getSheet("Repasses").getLastRowNum()).isZero();
        }
    }

    private static String textoDoPdf(byte[] pdf) throws Exception {
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

    private static RelatorioFinanceiroDTO.RepasseProfissional paula() {
        return new RelatorioFinanceiroDTO.RepasseProfissional(
                1L, "Paula Mendes", new BigDecimal("45.00"), 8, 2, new BigDecimal("400.00"), new BigDecimal("90.00"));
    }

    private static RelatorioFinanceiroDTO relatorio(List<RelatorioFinanceiroDTO.RepasseProfissional> profissionais) {
        BigDecimal repasse = profissionais.isEmpty() ? new BigDecimal("0.00") : new BigDecimal("90.00");
        BigDecimal receita = new BigDecimal("1000.00");
        return new RelatorioFinanceiroDTO(
                new PeriodoDTO(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
                new RelatorioFinanceiroDTO.Receita(4, receita),
                new RelatorioFinanceiroDTO.Repasses(profissionais.isEmpty() ? 0 : 8, repasse, profissionais),
                new RelatorioFinanceiroDTO.Inadimplencia(1, new BigDecimal("250.00")),
                receita.subtract(repasse),
                LocalDateTime.of(2026, 3, 1, 10, 0));
    }
}
