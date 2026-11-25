package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** XLSX (Apache POI) do relatório de frequência (#233): abas Resumo e Frequência, com as mesmas linhas do JSON. */
@Service
public class RelatorioFrequenciaExporterService {

    static final String TITULO = "Relatório de Frequência";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] CABECALHO = {
        "Paciente", "Situação", "Previstas", "Realizadas", "Faltas", "Presença (%)", "Última aula realizada"
    };

    public byte[] exportarXlsx(RelatorioFrequenciaDTO relatorio) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = estiloCabecalho(workbook);
            preencherResumo(workbook.createSheet("Resumo"), relatorio, headerStyle);
            preencherFrequencia(workbook.createSheet("Frequência"), relatorio, headerStyle);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Erro ao gerar o XLSX do relatório de frequência", e);
        }
    }

    private void preencherResumo(Sheet sheet, RelatorioFrequenciaDTO relatorio, CellStyle headerStyle) {
        int rowIndex = 0;
        Cell tituloCell = sheet.createRow(rowIndex++).createCell(0);
        tituloCell.setCellValue(TITULO);
        tituloCell.setCellStyle(headerStyle);
        rowIndex++;

        var totais = relatorio.totais();
        rowIndex = chaveValor(sheet, rowIndex, "Período", periodo(relatorio));
        rowIndex = chaveValor(sheet, rowIndex, "Considerado até", data(relatorio.consideradoAte()));
        rowIndex = chaveValor(sheet, rowIndex, "Filtros", filtros(relatorio));
        rowIndex = chaveValor(sheet, rowIndex, "Gerado em", relatorio.geradoEm().format(DATE_TIME));
        rowIndex++;
        rowIndex = chaveValor(sheet, rowIndex, "Pacientes", String.valueOf(totais.pacientes()));
        rowIndex = chaveValor(sheet, rowIndex, "Aulas previstas", String.valueOf(totais.aulasPrevistas()));
        rowIndex = chaveValor(sheet, rowIndex, "Aulas realizadas", String.valueOf(totais.aulasRealizadas()));
        rowIndex = chaveValor(sheet, rowIndex, "Faltas", String.valueOf(totais.faltas()));
        chaveValor(sheet, rowIndex, "Presença", percentual(totais.taxaPresenca()));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void preencherFrequencia(Sheet sheet, RelatorioFrequenciaDTO relatorio, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < CABECALHO.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(CABECALHO[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (RelatorioFrequenciaDTO.PacienteFrequencia p : relatorio.pacientes()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(p.pacienteNome());
            row.createCell(1).setCellValue(p.ativo() ? "Ativo" : "Inativo");
            row.createCell(2).setCellValue(p.aulasPrevistas());
            row.createCell(3).setCellValue(p.aulasRealizadas());
            row.createCell(4).setCellValue(p.faltas());
            // Mesmo valor do JSON (1 casa, HALF_UP), como número para a planilha ordenar e filtrar.
            row.createCell(5).setCellValue(p.taxaPresenca().doubleValue());
            row.createCell(6).setCellValue(data(p.ultimaAulaRealizada()));
        }
        var totais = relatorio.totais();
        Row total = sheet.createRow(rowIndex);
        Cell rotulo = total.createCell(0);
        rotulo.setCellValue("Total (" + totais.pacientes() + (totais.pacientes() == 1 ? " paciente)" : " pacientes)"));
        rotulo.setCellStyle(headerStyle);
        total.createCell(2).setCellValue(totais.aulasPrevistas());
        total.createCell(3).setCellValue(totais.aulasRealizadas());
        total.createCell(4).setCellValue(totais.faltas());
        if (totais.taxaPresenca() != null) {
            total.createCell(5).setCellValue(totais.taxaPresenca().doubleValue());
        }

        for (int i = 0; i < CABECALHO.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static CellStyle estiloCabecalho(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static int chaveValor(Sheet sheet, int rowIndex, String chave, String valor) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(chave);
        row.createCell(1).setCellValue(valor);
        return rowIndex + 1;
    }

    private static String periodo(RelatorioFrequenciaDTO relatorio) {
        return relatorio.periodo().inicio().format(DATE) + " até "
                + relatorio.periodo().fim().format(DATE);
    }

    private static String filtros(RelatorioFrequenciaDTO relatorio) {
        var filtros = relatorio.filtros();
        StringBuilder texto = new StringBuilder();
        if (filtros.profissionalId() != null) {
            texto.append("profissional ")
                    .append(
                            filtros.profissionalNome() == null
                                    ? "#" + filtros.profissionalId()
                                    : filtros.profissionalNome());
        }
        if (filtros.pacienteId() != null) {
            separar(texto).append("paciente #").append(filtros.pacienteId());
        }
        if (filtros.taxaMaxima() != null) {
            separar(texto)
                    .append("presença abaixo de ")
                    .append(filtros.taxaMaxima().stripTrailingZeros().toPlainString())
                    .append('%');
        }
        return texto.isEmpty() ? "nenhum" : texto.toString();
    }

    private static StringBuilder separar(StringBuilder texto) {
        return texto.isEmpty() ? texto : texto.append("; ");
    }

    private static String percentual(BigDecimal taxa) {
        return taxa == null ? "-" : taxa.toPlainString().replace('.', ',') + "%";
    }

    private static String data(LocalDate data) {
        return data == null ? "-" : data.format(DATE);
    }
}
