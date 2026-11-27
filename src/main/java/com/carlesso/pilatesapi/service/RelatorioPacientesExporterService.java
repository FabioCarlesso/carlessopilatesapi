package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PendenciaCadastroPaciente;
import com.carlesso.pilatesapi.dto.RelatorioPacientesDTO;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * PDF (OpenPDF) e XLSX (Apache POI) do relatório de pacientes (#230): as mesmas linhas e totais do JSON, no layout do
 * relatório financeiro.
 */
@Service
public class RelatorioPacientesExporterService {

    static final String TITULO = "Relatório de Pacientes";

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color TEXTO = new Color(33, 37, 41);

    private static final String[] CABECALHO_PDF = {
        "Nome",
        "CPF",
        "Telefone",
        "E-mail",
        "Nascimento",
        "Situação",
        "Cadastro",
        "Inativação",
        "Plano",
        "Profissional",
        "Pendências"
    };
    private static final String[] CABECALHO_XLSX = {
        "Nome",
        "CPF",
        "Telefone",
        "E-mail",
        "Data de nascimento",
        "Idade",
        "Situação",
        "Data de cadastro",
        "Data de inativação",
        "Frequência",
        "Dias",
        "Horário",
        "Valor do plano",
        "Profissional",
        "Pendências"
    };

    public byte[] exportarPdf(RelatorioPacientesDTO relatorio) {
        Document document = new Document(PageSize.A4.rotate(), 24, 24, 36, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TEXTO);
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 10, TEXTO);

            Paragraph titulo = new Paragraph(TITULO, tituloFont);
            titulo.setSpacingAfter(12f);
            document.add(titulo);
            document.add(linha("Filtros: ", descreverFiltros(relatorio.filtros()), textoFont));
            document.add(linha("Gerado em: ", relatorio.geradoEm().format(DATE_TIME), textoFont));
            Paragraph totais = linha("Totais: ", descreverTotais(relatorio.totais()), textoFont);
            totais.setSpacingAfter(10f);
            document.add(totais);

            if (relatorio.pacientes().isEmpty()) {
                document.add(new Paragraph("Nenhum paciente encontrado com os filtros informados.", textoFont));
            } else {
                document.add(montarTabela(relatorio.pacientes()));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar PDF do relatório de pacientes", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    public byte[] exportarXlsx(RelatorioPacientesDTO relatorio) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            preencherResumo(workbook.createSheet("Resumo"), relatorio, headerStyle);
            preencherPacientes(workbook.createSheet("Pacientes"), relatorio.pacientes(), headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX do relatório de pacientes", e);
        }
    }

    private PdfPTable montarTabela(List<RelatorioPacientesDTO.PacienteItem> pacientes) {
        PdfPTable table = new PdfPTable(new float[] {3f, 2f, 2f, 3f, 1.8f, 1.3f, 1.5f, 1.5f, 3f, 2f, 2.6f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String titulo : CABECALHO_PDF) {
            PdfPCell cell = new PdfPCell(new Phrase(titulo, headerFont));
            cell.setBackgroundColor(new Color(73, 80, 87));
            cell.setPadding(4f);
            table.addCell(cell);
        }
        for (RelatorioPacientesDTO.PacienteItem item : pacientes) {
            adicionarCelula(table, item.nome());
            adicionarCelula(table, item.cpf());
            adicionarCelula(table, item.telefone());
            adicionarCelula(table, item.email());
            adicionarCelula(table, nascimento(item));
            adicionarCelula(table, situacao(item));
            adicionarCelula(table, data(item.dataCadastro()));
            adicionarCelula(table, data(item.dataInativacao()));
            adicionarCelula(table, plano(item.planoAtivo()));
            adicionarCelula(table, profissional(item));
            adicionarCelula(table, pendencias(item.pendencias()));
        }
        return table;
    }

    private void adicionarCelula(PdfPTable table, String valor) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXTO);
        PdfPCell cell = new PdfPCell(new Phrase(valor == null || valor.isBlank() ? "-" : valor, font));
        cell.setPadding(3f);
        table.addCell(cell);
    }

    private Paragraph linha(String rotulo, String valor, Font font) {
        Phrase phrase = new Phrase();
        Font negrito = FontFactory.getFont(FontFactory.HELVETICA_BOLD, font.getSize(), font.getColor());
        phrase.add(new Chunk(rotulo, negrito));
        phrase.add(new Chunk(valor, font));
        return new Paragraph(phrase);
    }

    private void preencherResumo(Sheet sheet, RelatorioPacientesDTO relatorio, CellStyle headerStyle) {
        int rowIndex = 0;
        Cell tituloCell = sheet.createRow(rowIndex++).createCell(0);
        tituloCell.setCellValue(TITULO);
        tituloCell.setCellStyle(headerStyle);
        rowIndex++;

        rowIndex = chaveValor(sheet, rowIndex, "Filtros", descreverFiltros(relatorio.filtros()));
        rowIndex = chaveValor(sheet, rowIndex, "Gerado em", relatorio.geradoEm().format(DATE_TIME));
        rowIndex++;
        RelatorioPacientesDTO.Totais totais = relatorio.totais();
        rowIndex = chaveValor(sheet, rowIndex, "Pacientes", String.valueOf(totais.total()));
        rowIndex = chaveValor(sheet, rowIndex, "Ativos", String.valueOf(totais.ativos()));
        rowIndex = chaveValor(sheet, rowIndex, "Inativos", String.valueOf(totais.inativos()));
        chaveValor(sheet, rowIndex, "Com pendência", String.valueOf(totais.comPendencia()));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void preencherPacientes(
            Sheet sheet, List<RelatorioPacientesDTO.PacienteItem> pacientes, CellStyle headerStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < CABECALHO_XLSX.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(CABECALHO_XLSX[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        for (RelatorioPacientesDTO.PacienteItem item : pacientes) {
            Row row = sheet.createRow(rowIndex++);
            RelatorioPacientesDTO.PlanoAtivo plano = item.planoAtivo();
            row.createCell(0).setCellValue(item.nome());
            row.createCell(1).setCellValue(texto(item.cpf()));
            row.createCell(2).setCellValue(texto(item.telefone()));
            row.createCell(3).setCellValue(texto(item.email()));
            row.createCell(4).setCellValue(data(item.dataNascimento()));
            if (item.idade() != null) {
                row.createCell(5).setCellValue(item.idade());
            }
            row.createCell(6).setCellValue(situacao(item));
            row.createCell(7).setCellValue(data(item.dataCadastro()));
            row.createCell(8).setCellValue(data(item.dataInativacao()));
            if (plano != null) {
                row.createCell(9).setCellValue(frequencia(plano.frequenciaSemanal()));
                row.createCell(10).setCellValue(dias(plano.diasSemana()));
                row.createCell(11)
                        .setCellValue(
                                plano.horario() == null ? "" : plano.horario().format(HORA));
                if (plano.valor() != null) {
                    row.createCell(12).setCellValue(plano.valor().doubleValue());
                }
            }
            String profissional = profissional(item);
            row.createCell(13).setCellValue(profissional == null ? "" : profissional);
            row.createCell(14).setCellValue(pendencias(item.pendencias()));
        }
        for (int i = 0; i < CABECALHO_XLSX.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int chaveValor(Sheet sheet, int rowIndex, String chave, String valor) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(chave);
        row.createCell(1).setCellValue(valor);
        return rowIndex + 1;
    }

    String descreverFiltros(RelatorioPacientesDTO.Filtros filtros) {
        StringBuilder texto = new StringBuilder(
                switch (filtros.situacao()) {
                    case ATIVOS -> "Ativos";
                    case INATIVOS -> "Inativos";
                    case TODOS -> "Ativos e inativos";
                });
        if (filtros.semPlanoAtivo()) {
            texto.append("; sem plano ativo");
        }
        if (filtros.cadastroIncompleto()) {
            texto.append("; com pendência");
        }
        if (filtros.mesNascimento() != null) {
            texto.append("; aniversariantes de ")
                    .append(Month.of(filtros.mesNascimento()).getDisplayName(TextStyle.FULL, PT_BR));
        }
        if (filtros.profissionalId() != null) {
            texto.append("; profissional ")
                    .append(
                            filtros.profissionalNome() == null
                                    ? "#" + filtros.profissionalId()
                                    : filtros.profissionalNome());
        }
        return texto.toString();
    }

    private String descreverTotais(RelatorioPacientesDTO.Totais totais) {
        return totais.total() + " pacientes (" + totais.ativos() + " ativos, " + totais.inativos() + " inativos), "
                + totais.comPendencia() + " com pendência";
    }

    private String nascimento(RelatorioPacientesDTO.PacienteItem item) {
        if (item.dataNascimento() == null) {
            return null;
        }
        return item.dataNascimento().format(DATE) + " (" + item.idade() + ")";
    }

    private String situacao(RelatorioPacientesDTO.PacienteItem item) {
        return item.ativo() ? "Ativo" : "Inativo";
    }

    private String plano(RelatorioPacientesDTO.PlanoAtivo plano) {
        if (plano == null) {
            return null;
        }
        StringBuilder texto = new StringBuilder(frequencia(plano.frequenciaSemanal()));
        String dias = dias(plano.diasSemana());
        if (!dias.isEmpty()) {
            texto.append(" - ").append(dias);
        }
        if (plano.horario() != null) {
            texto.append(" ").append(plano.horario().format(HORA));
        }
        if (plano.valor() != null) {
            texto.append(" - ").append(moeda(plano.valor()));
        }
        return texto.toString();
    }

    private String frequencia(FrequenciaSemanal frequencia) {
        return frequencia == null ? "" : frequencia.getVezesPorSemana() + "x por semana";
    }

    private String dias(List<DayOfWeek> dias) {
        if (dias == null) {
            return "";
        }
        return dias.stream()
                .map(dia -> dia.getDisplayName(TextStyle.SHORT, PT_BR))
                .collect(Collectors.joining(", "));
    }

    /**
     * Coluna Profissional, igual à tela: o fisioterapeuta padrão (#245) e, quando o das próximas aulas é outro, também
     * esse. O filtro de profissional casa pelos dois, então a linha exportada sempre mostra o profissional filtrado.
     */
    static String profissional(RelatorioPacientesDTO.PacienteItem item) {
        var padrao = item.profissionalPadrao();
        var proximas = item.profissional();
        if (padrao != null) {
            return proximas == null || Objects.equals(proximas.id(), padrao.id())
                    ? padrao.nome()
                    : "%s (próximas aulas: %s)".formatted(padrao.nome(), proximas.nome());
        }
        return proximas == null ? null : "%s (próximas aulas, sem padrão)".formatted(proximas.nome());
    }

    private String pendencias(List<PendenciaCadastroPaciente> pendencias) {
        return pendencias.stream().map(PendenciaCadastroPaciente::getDescricao).collect(Collectors.joining(", "));
    }

    private String data(LocalDate data) {
        return data == null ? "" : data.format(DATE);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private String moeda(BigDecimal valor) {
        return "R$ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
