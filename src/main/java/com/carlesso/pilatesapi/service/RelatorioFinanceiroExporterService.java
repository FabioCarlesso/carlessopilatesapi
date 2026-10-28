package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** PDF (OpenPDF) e XLSX (Apache POI) do relatório financeiro, no mesmo layout do relatório de pagamento. */
@Service
public class RelatorioFinanceiroExporterService {

    static final String TITULO = "Relatório Financeiro do Estúdio";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color TEXTO = new Color(33, 37, 41);
    private static final String[] CABECALHO_REPASSES = {
        "Profissional", "Percentual", "Aulas", "Pagamentos", "Total bruto", "Repasse"
    };

    public byte[] exportarPdf(RelatorioFinanceiroDTO relatorio) {
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEXTO);
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TEXTO);
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXTO);

            Paragraph titulo = new Paragraph(TITULO, tituloFont);
            titulo.setSpacingAfter(12f);
            document.add(titulo);
            document.add(linha("Período: ", periodo(relatorio), textoFont));
            document.add(linha("Gerado em: ", relatorio.geradoEm().format(DATE_TIME), textoFont));

            document.add(subtitulo("Resumo financeiro", subtituloFont));
            for (String[] item : resumo(relatorio)) {
                document.add(linha(item[0] + ": ", item[1], textoFont));
            }

            document.add(subtitulo("Repasses por profissional", subtituloFont));
            if (relatorio.repasses().profissionais().isEmpty()) {
                document.add(new Paragraph("Nenhum repasse no período.", textoFont));
            } else {
                document.add(montarTabelaRepasses(relatorio));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar PDF do relatório", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    public byte[] exportarXlsx(RelatorioFinanceiroDTO relatorio) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            preencherResumo(workbook.createSheet("Resumo"), relatorio, headerStyle);
            preencherRepasses(workbook.createSheet("Repasses"), relatorio, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX do relatório", e);
        }
    }

    /** Pares rótulo/valor do resumo, na mesma ordem nos dois formatos. */
    private String[][] resumo(RelatorioFinanceiroDTO relatorio) {
        return new String[][] {
            {"Pagamentos recebidos", String.valueOf(relatorio.receita().quantidadePagamentos())},
            {"Receita", formatarMoeda(relatorio.receita().total())},
            {"Aulas com repasse", String.valueOf(relatorio.repasses().totalAulas())},
            {"Total de repasses", formatarMoeda(relatorio.repasses().total())},
            {"Resultado líquido", formatarMoeda(relatorio.resultadoLiquido())},
            {
                "Pagamentos vencidos em aberto",
                String.valueOf(relatorio.inadimplencia().quantidadePagamentos())
            },
            {"Inadimplência", formatarMoeda(relatorio.inadimplencia().total())}
        };
    }

    private Paragraph subtitulo(String texto, Font font) {
        Paragraph paragraph = new Paragraph(texto, font);
        paragraph.setSpacingBefore(12f);
        paragraph.setSpacingAfter(6f);
        return paragraph;
    }

    private Paragraph linha(String rotulo, String valor, Font font) {
        Phrase phrase = new Phrase();
        Font negrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, font.getSize(), font.getColor());
        phrase.add(new Chunk(rotulo, negrito));
        phrase.add(new Chunk(valor == null ? "-" : valor, font));
        return new Paragraph(phrase);
    }

    private PdfPTable montarTabelaRepasses(RelatorioFinanceiroDTO relatorio) {
        PdfPTable table = new PdfPTable(new float[] {3f, 1.5f, 1.5f, 1.5f, 2f, 2f});
        table.setWidthPercentage(100);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        for (String titulo : CABECALHO_REPASSES) {
            PdfPCell cell = new PdfPCell(new Phrase(titulo, headerFont));
            cell.setBackgroundColor(new Color(73, 80, 87));
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        for (RelatorioFinanceiroDTO.RepasseProfissional repasse :
                relatorio.repasses().profissionais()) {
            adicionarCelula(table, repasse.nome());
            adicionarCelula(table, repasse.percentualPagamentoAula() + "%");
            adicionarCelula(table, String.valueOf(repasse.totalAulas()));
            adicionarCelula(table, String.valueOf(repasse.quantidadePagamentos()));
            adicionarCelula(table, formatarMoeda(repasse.totalPagamentosBruto()));
            adicionarCelula(table, formatarMoeda(repasse.totalProfissional()));
        }
        return table;
    }

    private void adicionarCelula(PdfPTable table, String valor) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXTO);
        PdfPCell cell = new PdfPCell(new Phrase(valor == null ? "-" : valor, font));
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void preencherResumo(Sheet sheet, RelatorioFinanceiroDTO relatorio, CellStyle headerStyle) {
        int rowIndex = 0;
        Cell tituloCell = sheet.createRow(rowIndex++).createCell(0);
        tituloCell.setCellValue(TITULO);
        tituloCell.setCellStyle(headerStyle);
        rowIndex++;

        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Período", periodo(relatorio));
        rowIndex = adicionarLinhaChaveValor(
                sheet, rowIndex, "Gerado em", relatorio.geradoEm().format(DATE_TIME));
        rowIndex++;
        for (String[] item : resumo(relatorio)) {
            rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, item[0], item[1]);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void preencherRepasses(Sheet sheet, RelatorioFinanceiroDTO relatorio, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < CABECALHO_REPASSES.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(CABECALHO_REPASSES[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (RelatorioFinanceiroDTO.RepasseProfissional repasse :
                relatorio.repasses().profissionais()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(repasse.nome());
            row.createCell(1).setCellValue(repasse.percentualPagamentoAula().doubleValue());
            row.createCell(2).setCellValue(repasse.totalAulas());
            row.createCell(3).setCellValue(repasse.quantidadePagamentos());
            row.createCell(4).setCellValue(repasse.totalPagamentosBruto().doubleValue());
            row.createCell(5).setCellValue(repasse.totalProfissional().doubleValue());
        }
        for (int i = 0; i < CABECALHO_REPASSES.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int adicionarLinhaChaveValor(Sheet sheet, int rowIndex, String chave, String valor) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(chave);
        row.createCell(1).setCellValue(valor);
        return rowIndex + 1;
    }

    private String periodo(RelatorioFinanceiroDTO relatorio) {
        return relatorio.periodo().inicio().format(DATE) + " até "
                + relatorio.periodo().fim().format(DATE);
    }

    private String formatarMoeda(BigDecimal valor) {
        return "R$ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
