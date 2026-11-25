package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO.FormaRecebimento;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RelatorioRecebimentosExporterServiceTest {

    private final RelatorioRecebimentosExporterService service = new RelatorioRecebimentosExporterService();

    @Test
    void exportarPdf_trazResumoLinhasETotal() throws Exception {
        byte[] pdf = service.exportarPdf(relatorio(List.of(linhaAna(), linhaBruno()), null));

        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        String texto = textoDoPdf(pdf);
        assertThat(texto)
                .contains(RelatorioRecebimentosExporterService.TITULO)
                .contains("01/11/2026 até 30/11/2026")
                .contains("Filtros: nenhum")
                .contains("Total recebido: R$ 550,00")
                .contains("PIX: R$ 300,00 (1)")
                .contains("Não informada: R$ 250,00 (1)")
                .contains("Ana Souza")
                .contains("2x/semana");
    }

    @Test
    void exportarPdf_semRecebimentos_avisaEmVezDeTabelaVazia() throws Exception {
        String texto = textoDoPdf(service.exportarPdf(relatorio(List.of(), FormaRecebimento.PIX)));

        assertThat(texto).contains("Nenhum recebimento no período.").contains("forma de pagamento PIX");
    }

    @Test
    void exportarXlsx_trazAbasResumoERecebimentosComTotal() throws Exception {
        byte[] xlsx = service.exportarXlsx(relatorio(List.of(linhaAna(), linhaBruno()), null));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet resumo = workbook.getSheet("Resumo");
            assertThat(resumo.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(RelatorioRecebimentosExporterService.TITULO);
            assertThat(resumo.getRow(2).getCell(1).getStringCellValue()).isEqualTo("01/11/2026 até 30/11/2026");
            assertThat(resumo.getRow(7).getCell(0).getStringCellValue()).isEqualTo("Total recebido");
            assertThat(resumo.getRow(7).getCell(1).getStringCellValue()).isEqualTo("R$ 550,00");

            Sheet linhas = workbook.getSheet("Recebimentos");
            assertThat(linhas.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Paciente");
            assertThat(linhas.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Ana Souza");
            assertThat(linhas.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(300.0);
            assertThat(linhas.getRow(1).getCell(3).getStringCellValue()).isEqualTo("PIX");
            assertThat(linhas.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Não informada");
            assertThat(linhas.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(linhas.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(550.0);
        }
    }

    private RelatorioRecebimentosDTO.Recebimento linhaAna() {
        return linha(1L, "Ana Souza", "300.00", FormaRecebimento.PIX);
    }

    private RelatorioRecebimentosDTO.Recebimento linhaBruno() {
        return linha(2L, "Bruno Lima", "250.00", FormaRecebimento.NAO_INFORMADA);
    }

    private RelatorioRecebimentosDTO.Recebimento linha(Long id, String nome, String valor, FormaRecebimento forma) {
        return new RelatorioRecebimentosDTO.Recebimento(
                id,
                id,
                nome,
                new BigDecimal(valor),
                LocalDate.of(2026, 11, 5),
                forma,
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                id,
                FrequenciaSemanal.DUAS_VEZES);
    }

    private RelatorioRecebimentosDTO relatorio(
            List<RelatorioRecebimentosDTO.Recebimento> linhas, FormaRecebimento filtro) {
        var porForma = Arrays.stream(FormaRecebimento.values())
                .map(f -> {
                    var daForma =
                            linhas.stream().filter(l -> l.formaPagamento() == f).toList();
                    return new RelatorioRecebimentosDTO.TotalPorForma(
                            f,
                            f.rotulo(),
                            daForma.size(),
                            daForma.stream()
                                    .map(RelatorioRecebimentosDTO.Recebimento::valor)
                                    .reduce(new BigDecimal("0.00"), BigDecimal::add));
                })
                .toList();
        BigDecimal total = linhas.stream()
                .map(RelatorioRecebimentosDTO.Recebimento::valor)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new RelatorioRecebimentosDTO(
                new PeriodoDTO(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                new RelatorioRecebimentosDTO.Filtros(filtro, null),
                new RelatorioRecebimentosDTO.Resumo(linhas.size(), total, porForma),
                linhas,
                LocalDateTime.of(2026, 12, 1, 9, 0));
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
