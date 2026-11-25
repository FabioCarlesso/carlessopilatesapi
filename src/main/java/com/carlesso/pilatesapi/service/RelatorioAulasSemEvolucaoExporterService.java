package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
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

/** Exportação em XLSX do relatório de aulas realizadas sem evolução (#234): abas Resumo e Aulas. */
@Service
public class RelatorioAulasSemEvolucaoExporterService {

    private static final String TITULO = "Aulas realizadas sem evolução";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] CABECALHO = {"Data", "Horário", "Paciente", "Profissional"};

    public String nomeArquivo(LocalDate inicio, LocalDate fim) {
        return "relatorio-aulas-sem-evolucao-" + inicio + "-" + fim + ".xlsx";
    }

    public byte[] exportarXlsx(RelatorioAulasSemEvolucaoDTO relatorio) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            preencherResumo(workbook.createSheet("Resumo"), relatorio, headerStyle);
            preencherAulas(workbook.createSheet("Aulas"), relatorio, headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX do relatório", e);
        }
    }

    private void preencherResumo(Sheet sheet, RelatorioAulasSemEvolucaoDTO relatorio, CellStyle headerStyle) {
        int rowIndex = 0;
        Cell tituloCell = sheet.createRow(rowIndex++).createCell(0);
        tituloCell.setCellValue(TITULO);
        tituloCell.setCellStyle(headerStyle);
        rowIndex++;

        rowIndex = chaveValor(
                sheet,
                rowIndex,
                "Período",
                relatorio.periodo().inicio().format(DATE) + " até "
                        + relatorio.periodo().fim().format(DATE));
        rowIndex = chaveValor(sheet, rowIndex, "Profissional", filtroProfissional(relatorio));
        rowIndex = chaveValor(sheet, rowIndex, "Gerado em", relatorio.geradoEm().format(DATE_TIME));
        rowIndex = chaveValor(
                sheet,
                rowIndex,
                "Aulas sem evolução",
                String.valueOf(relatorio.resumo().totalAulas()));
        rowIndex++;

        Row header = sheet.createRow(rowIndex++);
        Cell profissional = header.createCell(0);
        profissional.setCellValue("Profissional");
        profissional.setCellStyle(headerStyle);
        Cell quantidade = header.createCell(1);
        quantidade.setCellValue("Aulas");
        quantidade.setCellStyle(headerStyle);
        for (RelatorioAulasSemEvolucaoDTO.TotalPorProfissional total :
                relatorio.resumo().porProfissional()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(total.profissionalNome());
            row.createCell(1).setCellValue(total.quantidade());
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void preencherAulas(Sheet sheet, RelatorioAulasSemEvolucaoDTO relatorio, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < CABECALHO.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(CABECALHO[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao aula : relatorio.aulas()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(aula.data().format(DATE));
            row.createCell(1).setCellValue(horario(aula.horario()));
            row.createCell(2).setCellValue(aula.pacienteNome());
            row.createCell(3)
                    .setCellValue(
                            aula.profissionalNome() == null
                                    ? RelatorioAulasSemEvolucaoService.SEM_PROFISSIONAL
                                    : aula.profissionalNome());
        }
        long quantidade = relatorio.resumo().totalAulas();
        Cell rotulo = sheet.createRow(rowIndex).createCell(0);
        rotulo.setCellValue("Total (" + quantidade + (quantidade == 1 ? " aula)" : " aulas)"));
        rotulo.setCellStyle(headerStyle);

        for (int i = 0; i < CABECALHO.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static int chaveValor(Sheet sheet, int rowIndex, String chave, String valor) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(chave);
        row.createCell(1).setCellValue(valor);
        return rowIndex + 1;
    }

    private static String filtroProfissional(RelatorioAulasSemEvolucaoDTO relatorio) {
        var filtros = relatorio.filtros();
        if (filtros.profissionalId() == null) {
            return "todos";
        }
        return filtros.profissionalNome() == null ? "#" + filtros.profissionalId() : filtros.profissionalNome();
    }

    private static String horario(LocalTime horario) {
        return horario == null ? "-" : horario.format(TIME);
    }
}
