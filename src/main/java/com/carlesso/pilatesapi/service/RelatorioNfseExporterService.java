package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RelatorioNfseExporterService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] HEADERS = {
            "Nome",
            "CPF/CNPJ",
            "ValorPago",
            "Competencia",
            "DescricaoServico",
            "NotaAnteriorEmitida",
            "DataPagamento",
            "Observacoes"
    };

    public byte[] exportarCsv(List<RelatorioNfseResponseDTO> itens) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADERS)).append("\n");

        for (RelatorioNfseResponseDTO item : itens) {
            csv.append(Stream.of(
                            item.nome(),
                            item.cpfCnpj(),
                            item.valorPago().toPlainString(),
                            item.competencia(),
                            item.descricaoServico(),
                            item.notaAnteriorEmitida().toString(),
                            item.dataPagamento().format(DATE),
                            item.observacoes())
                    .map(this::csv)
                    .collect(Collectors.joining(",")));
            csv.append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportarXlsx(List<RelatorioNfseResponseDTO> itens) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("NFSE");
            CellStyle headerStyle = criarHeaderStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
                header.getCell(i).setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (RelatorioNfseResponseDTO item : itens) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.nome());
                row.createCell(1).setCellValue(item.cpfCnpj());
                row.createCell(2).setCellValue(item.valorPago().doubleValue());
                row.createCell(3).setCellValue(item.competencia());
                row.createCell(4).setCellValue(item.descricaoServico());
                row.createCell(5).setCellValue(item.notaAnteriorEmitida());
                row.createCell(6).setCellValue(item.dataPagamento().format(DATE));
                row.createCell(7).setCellValue(item.observacoes());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar XLSX do relatório de NFSE", e);
        }
    }

    private CellStyle criarHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
