package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO;
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
import java.time.LocalDate;
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

/** PDF (OpenPDF) e XLSX (Apache POI) do relatório de recebimentos, no layout do relatório financeiro. */
@Service
public class RelatorioRecebimentosExporterService {

    static final String TITULO = "Relatório de Recebimentos";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color TEXTO = new Color(33, 37, 41);
    private static final String[] CABECALHO = {
        "Paciente", "Valor", "Data de pagamento", "Forma de pagamento", "Vencimento", "Período", "Plano"
    };

    public byte[] exportarPdf(RelatorioRecebimentosDTO relatorio) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 54, 36);
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
            document.add(linha("Filtros: ", filtros(relatorio), textoFont));
            document.add(linha("Gerado em: ", relatorio.geradoEm().format(DATE_TIME), textoFont));

            document.add(subtitulo("Resumo", subtituloFont));
            for (String[] item : resumo(relatorio)) {
                document.add(linha(item[0] + ": ", item[1], textoFont));
            }

            document.add(subtitulo("Recebimentos", subtituloFont));
            if (relatorio.recebimentos().isEmpty()) {
                document.add(new Paragraph("Nenhum recebimento no período.", textoFont));
            } else {
                document.add(montarTabela(relatorio));
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

    public byte[] exportarXlsx(RelatorioRecebimentosDTO relatorio) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            preencherResumo(workbook.createSheet("Resumo"), relatorio, headerStyle);
            preencherRecebimentos(workbook.createSheet("Recebimentos"), relatorio, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX do relatório", e);
        }
    }

    /** Pares rótulo/valor do resumo, na mesma ordem nos dois formatos. */
    private String[][] resumo(RelatorioRecebimentosDTO relatorio) {
        var resumo = relatorio.resumo();
        String[][] linhas = new String[resumo.porFormaPagamento().size() + 2][];
        linhas[0] = new String[] {"Pagamentos recebidos", String.valueOf(resumo.quantidadePagamentos())};
        linhas[1] = new String[] {"Total recebido", formatarMoeda(resumo.total())};
        int i = 2;
        for (RelatorioRecebimentosDTO.TotalPorForma forma : resumo.porFormaPagamento()) {
            linhas[i++] =
                    new String[] {forma.descricao(), formatarMoeda(forma.total()) + " (" + forma.quantidade() + ")"};
        }
        return linhas;
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

    private PdfPTable montarTabela(RelatorioRecebimentosDTO relatorio) {
        PdfPTable table = new PdfPTable(new float[] {4f, 1.6f, 1.8f, 2f, 1.6f, 3f, 1.6f});
        table.setWidthPercentage(100);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        for (String titulo : CABECALHO) {
            PdfPCell cell = new PdfPCell(new Phrase(titulo, headerFont));
            cell.setBackgroundColor(new Color(73, 80, 87));
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        for (RelatorioRecebimentosDTO.Recebimento r : relatorio.recebimentos()) {
            adicionarCelula(table, r.pacienteNome());
            adicionarCelula(table, formatarMoeda(r.valor()));
            adicionarCelula(table, data(r.dataPagamento()));
            adicionarCelula(table, r.formaPagamento().rotulo());
            adicionarCelula(table, data(r.dataVencimento()));
            adicionarCelula(table, data(r.periodoInicio()) + " a " + data(r.periodoFim()));
            adicionarCelula(table, plano(r));
        }
        adicionarCelula(table, "Total");
        adicionarCelula(table, formatarMoeda(relatorio.resumo().total()));
        for (int i = 2; i < CABECALHO.length; i++) {
            adicionarCelula(table, "");
        }
        return table;
    }

    private void adicionarCelula(PdfPTable table, String valor) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXTO);
        PdfPCell cell = new PdfPCell(new Phrase(valor == null ? "-" : valor, font));
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void preencherResumo(Sheet sheet, RelatorioRecebimentosDTO relatorio, CellStyle headerStyle) {
        int rowIndex = 0;
        Cell tituloCell = sheet.createRow(rowIndex++).createCell(0);
        tituloCell.setCellValue(TITULO);
        tituloCell.setCellStyle(headerStyle);
        rowIndex++;

        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Período", periodo(relatorio));
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Filtros", filtros(relatorio));
        rowIndex = adicionarLinhaChaveValor(
                sheet, rowIndex, "Gerado em", relatorio.geradoEm().format(DATE_TIME));
        rowIndex++;
        for (String[] item : resumo(relatorio)) {
            rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, item[0], item[1]);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void preencherRecebimentos(Sheet sheet, RelatorioRecebimentosDTO relatorio, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < CABECALHO.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(CABECALHO[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (RelatorioRecebimentosDTO.Recebimento r : relatorio.recebimentos()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(r.pacienteNome());
            row.createCell(1).setCellValue(r.valor().doubleValue());
            row.createCell(2).setCellValue(data(r.dataPagamento()));
            row.createCell(3).setCellValue(r.formaPagamento().rotulo());
            row.createCell(4).setCellValue(data(r.dataVencimento()));
            row.createCell(5).setCellValue(data(r.periodoInicio()) + " a " + data(r.periodoFim()));
            row.createCell(6).setCellValue(plano(r));
        }
        Row total = sheet.createRow(rowIndex);
        Cell rotulo = total.createCell(0);
        rotulo.setCellValue("Total");
        rotulo.setCellStyle(headerStyle);
        total.createCell(1).setCellValue(relatorio.resumo().total().doubleValue());

        for (int i = 0; i < CABECALHO.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int adicionarLinhaChaveValor(Sheet sheet, int rowIndex, String chave, String valor) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(chave);
        row.createCell(1).setCellValue(valor);
        return rowIndex + 1;
    }

    private String periodo(RelatorioRecebimentosDTO relatorio) {
        return relatorio.periodo().inicio().format(DATE) + " até "
                + relatorio.periodo().fim().format(DATE);
    }

    private String filtros(RelatorioRecebimentosDTO relatorio) {
        var filtros = relatorio.filtros();
        if (filtros.formaPagamento() == null && filtros.pacienteId() == null) {
            return "nenhum";
        }
        StringBuilder texto = new StringBuilder();
        if (filtros.formaPagamento() != null) {
            texto.append("forma de pagamento ").append(filtros.formaPagamento().rotulo());
        }
        if (filtros.pacienteId() != null) {
            if (!texto.isEmpty()) {
                texto.append("; ");
            }
            texto.append("paciente #").append(filtros.pacienteId());
        }
        return texto.toString();
    }

    private String plano(RelatorioRecebimentosDTO.Recebimento recebimento) {
        if (recebimento.frequenciaSemanal() == null) {
            return "-";
        }
        return switch (recebimento.frequenciaSemanal()) {
            case UMA_VEZ -> "1x/semana";
            case DUAS_VEZES -> "2x/semana";
            case TRES_VEZES -> "3x/semana";
        };
    }

    private String data(LocalDate data) {
        return data == null ? "-" : data.format(DATE);
    }

    private String formatarMoeda(BigDecimal valor) {
        return "R$ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
