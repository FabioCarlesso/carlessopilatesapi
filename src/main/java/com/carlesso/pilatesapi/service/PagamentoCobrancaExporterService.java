package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository.ResumoContatosProjection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Planilha das cobranças da tela de inadimplência (#232): uma linha por pagamento da listagem global, com os dias em
 * atraso e o resumo dos contatos de cobrança, e uma linha de total no fim.
 */
@Service
public class PagamentoCobrancaExporterService {

    static final List<String> CABECALHO = List.of(
            "Paciente",
            "Telefone",
            "Valor",
            "Vencimento",
            "Dias em atraso",
            "Status",
            "Período",
            "Contatos de cobrança",
            "Último contato");

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContatoCobrancaRepository contatoCobrancaRepository;
    private final Clock relogio;

    @Autowired
    public PagamentoCobrancaExporterService(
            ContatoCobrancaRepository contatoCobrancaRepository,
            @Value("${app.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this(contatoCobrancaRepository, Clock.system(ZoneId.of(fusoHorario)));
    }

    PagamentoCobrancaExporterService(ContatoCobrancaRepository contatoCobrancaRepository, Clock relogio) {
        this.contatoCobrancaRepository = contatoCobrancaRepository;
        this.relogio = relogio;
    }

    /** Linha da planilha, já com os campos derivados; separada da escrita do XLSX para ser testável sem o POI. */
    record LinhaCobranca(
            String paciente,
            String telefone,
            BigDecimal valor,
            LocalDate vencimento,
            long diasEmAtraso,
            StatusPagamento status,
            LocalDate periodoInicio,
            LocalDate periodoFim,
            long quantidadeContatos,
            LocalDate ultimoContato) {}

    /** {@code cobrancas-<data de hoje no fuso do estúdio>.xlsx}. */
    public String nomeArquivo() {
        return "cobrancas-" + LocalDate.now(relogio) + ".xlsx";
    }

    public byte[] exportarXlsx(List<Pagamento> pagamentos) {
        return escrever(montarLinhas(pagamentos));
    }

    List<LinhaCobranca> montarLinhas(List<Pagamento> pagamentos) {
        LocalDate hoje = LocalDate.now(relogio);
        Map<Long, ResumoContatosProjection> contatos = pagamentos.isEmpty()
                ? Map.of()
                : contatoCobrancaRepository
                        .resumirPorPagamentos(
                                pagamentos.stream().map(Pagamento::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(ResumoContatosProjection::getPagamentoId, Function.identity()));

        return pagamentos.stream()
                .map(pagamento -> {
                    ResumoContatosProjection resumo = contatos.get(pagamento.getId());
                    return new LinhaCobranca(
                            pagamento.getPaciente().getNome(),
                            pagamento.getPaciente().getTelefone(),
                            pagamento.getValor(),
                            pagamento.getDataVencimento(),
                            diasEmAtraso(pagamento, hoje),
                            pagamento.getStatus(),
                            pagamento.getPeriodoInicio(),
                            pagamento.getPeriodoFim(),
                            resumo == null ? 0 : resumo.getQuantidade(),
                            resumo == null ? null : resumo.getUltimoContato());
                })
                .toList();
    }

    /**
     * Dias entre o vencimento e hoje, derivado só da data: um {@code PENDENTE} que já venceu conta atraso mesmo antes
     * do scheduler das 06:00 marcá-lo como {@code VENCIDO}. A vencer e pagos ficam com 0.
     */
    static long diasEmAtraso(Pagamento pagamento, LocalDate hoje) {
        if (pagamento.getStatus() == StatusPagamento.PAGO) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(pagamento.getDataVencimento(), hoje));
    }

    private byte[] escrever(List<LinhaCobranca> linhas) {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle cabecalho = workbook.createCellStyle();
            Font negrito = workbook.createFont();
            negrito.setBold(true);
            cabecalho.setFont(negrito);
            cabecalho.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moeda = workbook.createCellStyle();
            moeda.setDataFormat(workbook.createDataFormat().getFormat("\"R$\" #,##0.00"));
            CellStyle moedaTotal = workbook.createCellStyle();
            moedaTotal.cloneStyleFrom(moeda);
            moedaTotal.setFont(negrito);
            CellStyle data = workbook.createCellStyle();
            data.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

            Sheet sheet = workbook.createSheet("Cobranças");
            Row header = sheet.createRow(0);
            for (int i = 0; i < CABECALHO.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(CABECALHO.get(i));
                cell.setCellStyle(cabecalho);
            }

            int rowIndex = 1;
            BigDecimal total = BigDecimal.ZERO;
            for (LinhaCobranca linha : linhas) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(linha.paciente());
                texto(row, 1, linha.telefone());
                Cell valor = row.createCell(2);
                valor.setCellValue(linha.valor().doubleValue());
                valor.setCellStyle(moeda);
                data(row, 3, linha.vencimento(), data);
                row.createCell(4).setCellValue(linha.diasEmAtraso());
                row.createCell(5).setCellValue(linha.status().name());
                row.createCell(6)
                        .setCellValue(linha.periodoInicio().format(DATA) + " a "
                                + linha.periodoFim().format(DATA));
                row.createCell(7).setCellValue(linha.quantidadeContatos());
                data(row, 8, linha.ultimoContato(), data);
                total = total.add(linha.valor());
            }

            Row totalRow = sheet.createRow(rowIndex);
            Cell rotulo = totalRow.createCell(0);
            rotulo.setCellValue("Total (" + linhas.size() + (linhas.size() == 1 ? " cobrança)" : " cobranças)"));
            rotulo.setCellStyle(cabecalho);
            Cell valorTotal = totalRow.createCell(2);
            valorTotal.setCellValue(total.doubleValue());
            valorTotal.setCellStyle(moedaTotal);

            for (int i = 0; i < CABECALHO.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar XLSX das cobranças", e);
        }
    }

    private static void texto(Row row, int coluna, String valor) {
        row.createCell(coluna).setCellValue(valor == null || valor.isBlank() ? "-" : valor);
    }

    private static void data(Row row, int coluna, LocalDate valor, CellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (valor == null) {
            cell.setCellValue("-");
            return;
        }
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }
}
