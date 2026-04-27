package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PagamentoResumoDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoAulaDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class RelatorioPagamentoExporterService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] exportarPdf(ProfissionalPagamentoRelatorioDTO relatorio) {
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(33, 37, 41));
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(33, 37, 41));
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(33, 37, 41));

            Paragraph titulo = new Paragraph("Relatório de Pagamento do Profissional", tituloFont);
            titulo.setSpacingAfter(12f);
            document.add(titulo);

            document.add(linha("Profissional: ", relatorio.profissional().nome(), textoFont));
            document.add(linha("CPF: ", relatorio.profissional().cpf(), textoFont));
            document.add(linha("Tipo de contrato: ", relatorio.profissional().tipoContrato().name(), textoFont));
            document.add(linha("Percentual por aula: ", relatorio.profissional().percentualPagamentoAula() + "%", textoFont));
            document.add(linha(
                    "Período: ",
                    relatorio.periodo().inicio().format(DATE) + " até " + relatorio.periodo().fim().format(DATE),
                    textoFont));
            document.add(linha("Gerado em: ", relatorio.geradoEm().format(DATE_TIME), textoFont));

            Paragraph resumoTitulo = new Paragraph("Resumo financeiro", subtituloFont);
            resumoTitulo.setSpacingBefore(12f);
            resumoTitulo.setSpacingAfter(6f);
            document.add(resumoTitulo);

            document.add(linha("Total de aulas realizadas: ",
                    String.valueOf(relatorio.resumo().totalAulas()), textoFont));
            document.add(linha("Quantidade de pagamentos: ",
                    String.valueOf(relatorio.resumo().quantidadePagamentos()), textoFont));
            document.add(linha("Total bruto dos pagamentos: ",
                    formatarMoeda(relatorio.resumo().totalPagamentosBruto()), textoFont));
            document.add(linha("Total devido ao profissional: ",
                    formatarMoeda(relatorio.resumo().totalProfissional()), textoFont));

            if (!relatorio.pagamentos().isEmpty()) {
                Paragraph pagTitulo = new Paragraph("Pagamentos", subtituloFont);
                pagTitulo.setSpacingBefore(12f);
                pagTitulo.setSpacingAfter(6f);
                document.add(pagTitulo);
                document.add(montarTabelaPagamentos(relatorio));
            }

            Paragraph aulasTitulo = new Paragraph("Aulas realizadas", subtituloFont);
            aulasTitulo.setSpacingBefore(12f);
            aulasTitulo.setSpacingAfter(6f);
            document.add(aulasTitulo);

            if (relatorio.aulas().isEmpty()) {
                document.add(new Paragraph("Nenhuma aula realizada no período.", textoFont));
            } else {
                document.add(montarTabelaAulas(relatorio));
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

    public byte[] exportarXlsx(ProfissionalPagamentoRelatorioDTO relatorio) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            preencherResumo(workbook.createSheet("Resumo"), relatorio, headerStyle);
            preencherPagamentos(workbook.createSheet("Pagamentos"), relatorio, headerStyle);
            preencherAulas(workbook.createSheet("Aulas"), relatorio, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX do relatório", e);
        }
    }

    private Paragraph linha(String rotulo, String valor, Font font) {
        Phrase phrase = new Phrase();
        Font negrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, font.getSize(), font.getColor());
        phrase.add(new com.lowagie.text.Chunk(rotulo, negrito));
        phrase.add(new com.lowagie.text.Chunk(valor == null ? "-" : valor, font));
        return new Paragraph(phrase);
    }

    private PdfPTable montarTabelaPagamentos(ProfissionalPagamentoRelatorioDTO relatorio) {
        PdfPTable table = new PdfPTable(new float[]{1.5f, 2f, 2f, 2f, 2f, 2f});
        table.setWidthPercentage(100);
        adicionarCabecalho(table, "Pagamento", "Valor", "Aulas total", "Aulas no período", "Valor base aula", "Total profissional");
        for (PagamentoResumoDTO pagamento : relatorio.pagamentos()) {
            adicionarCelula(table, String.valueOf(pagamento.pagamentoId()));
            adicionarCelula(table, formatarMoeda(pagamento.valorPagamento()));
            adicionarCelula(table, String.valueOf(pagamento.quantidadeAulasPagamento()));
            adicionarCelula(table, String.valueOf(pagamento.quantidadeAulasNoPeriodo()));
            adicionarCelula(table, formatarMoeda(pagamento.valorBaseAula()));
            adicionarCelula(table, formatarMoeda(pagamento.totalProfissional()));
        }
        return table;
    }

    private PdfPTable montarTabelaAulas(ProfissionalPagamentoRelatorioDTO relatorio) {
        PdfPTable table = new PdfPTable(new float[]{1.5f, 2f, 3f, 2f, 2f});
        table.setWidthPercentage(100);
        adicionarCabecalho(table, "Aula", "Data", "Paciente", "Pagamento", "Valor profissional");
        for (ProfissionalPagamentoAulaDTO aula : relatorio.aulas()) {
            adicionarCelula(table, String.valueOf(aula.aulaId()));
            adicionarCelula(table, aula.data().format(DATE));
            adicionarCelula(table, aula.pacienteNome());
            adicionarCelula(table, String.valueOf(aula.pagamentoId()));
            adicionarCelula(table, formatarMoeda(aula.valorProfissional()));
        }
        return table;
    }

    private void adicionarCabecalho(PdfPTable table, String... titulos) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        for (String titulo : titulos) {
            PdfPCell cell = new PdfPCell(new Phrase(titulo, headerFont));
            cell.setBackgroundColor(new Color(73, 80, 87));
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
    }

    private void adicionarCelula(PdfPTable table, String valor) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(33, 37, 41));
        PdfPCell cell = new PdfPCell(new Phrase(valor == null ? "-" : valor, font));
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void preencherResumo(Sheet sheet, ProfissionalPagamentoRelatorioDTO relatorio, CellStyle headerStyle) {
        int rowIndex = 0;
        Row tituloRow = sheet.createRow(rowIndex++);
        Cell tituloCell = tituloRow.createCell(0);
        tituloCell.setCellValue("Relatório de Pagamento do Profissional");
        tituloCell.setCellStyle(headerStyle);
        rowIndex++;

        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Profissional", relatorio.profissional().nome());
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "CPF", relatorio.profissional().cpf());
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Tipo de contrato", relatorio.profissional().tipoContrato().name());
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Percentual por aula",
                relatorio.profissional().percentualPagamentoAula() + "%");
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Período",
                relatorio.periodo().inicio().format(DATE) + " até " + relatorio.periodo().fim().format(DATE));
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Gerado em", relatorio.geradoEm().format(DATE_TIME));
        rowIndex++;

        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Total de aulas realizadas",
                String.valueOf(relatorio.resumo().totalAulas()));
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Quantidade de pagamentos",
                String.valueOf(relatorio.resumo().quantidadePagamentos()));
        rowIndex = adicionarLinhaChaveValor(sheet, rowIndex, "Total bruto dos pagamentos",
                formatarMoeda(relatorio.resumo().totalPagamentosBruto()));
        adicionarLinhaChaveValor(sheet, rowIndex, "Total devido ao profissional",
                formatarMoeda(relatorio.resumo().totalProfissional()));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void preencherPagamentos(Sheet sheet, ProfissionalPagamentoRelatorioDTO relatorio, CellStyle headerStyle) {
        String[] headers = {"Pagamento", "Valor", "Aulas total", "Aulas no período", "Valor base aula", "Total profissional"};
        criarHeader(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (PagamentoResumoDTO pagamento : relatorio.pagamentos()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(pagamento.pagamentoId());
            row.createCell(1).setCellValue(pagamento.valorPagamento().doubleValue());
            row.createCell(2).setCellValue(pagamento.quantidadeAulasPagamento());
            row.createCell(3).setCellValue(pagamento.quantidadeAulasNoPeriodo());
            row.createCell(4).setCellValue(pagamento.valorBaseAula().doubleValue());
            row.createCell(5).setCellValue(pagamento.totalProfissional().doubleValue());
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void preencherAulas(Sheet sheet, ProfissionalPagamentoRelatorioDTO relatorio, CellStyle headerStyle) {
        String[] headers = {"Aula", "Data", "Paciente", "Pagamento", "Valor profissional"};
        criarHeader(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (ProfissionalPagamentoAulaDTO aula : relatorio.aulas()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(aula.aulaId());
            row.createCell(1).setCellValue(aula.data().format(DATE));
            row.createCell(2).setCellValue(aula.pacienteNome());
            row.createCell(3).setCellValue(aula.pagamentoId());
            row.createCell(4).setCellValue(aula.valorProfissional().doubleValue());
        }
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void criarHeader(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private int adicionarLinhaChaveValor(Sheet sheet, int rowIndex, String chave, String valor) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(chave);
        row.createCell(1).setCellValue(valor == null ? "-" : valor);
        return rowIndex + 1;
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        return "R$ " + valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
