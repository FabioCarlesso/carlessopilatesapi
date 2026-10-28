package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.carlesso.pilatesapi.service.RelatorioFinanceiroExporterService;
import com.carlesso.pilatesapi.service.RelatorioFinanceiroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Relatórios", description = "Relatórios financeiros e fiscais")
@RestController
@RequestMapping("/api/relatorios/financeiro")
public class RelatorioFinanceiroController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RelatorioFinanceiroService service;
    private final RelatorioFinanceiroExporterService exporter;

    public RelatorioFinanceiroController(
            RelatorioFinanceiroService service, RelatorioFinanceiroExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Gerar relatório financeiro consolidado do estúdio",
            description = "Receita (pagamentos PAGO por dataPagamento), repasses aos profissionais (soma dos relatórios"
                    + " de pagamento por profissional), inadimplência (em aberto com vencimento no período) e"
                    + " resultado líquido. Período de até 366 dias, datas inclusivas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Período ausente, inválido ou acima de 366 dias")
    })
    @GetMapping
    public ResponseEntity<RelatorioFinanceiroDTO> gerar(
            @Parameter(description = "Data inicial do relatório", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final do relatório", required = true) @RequestParam LocalDate fim) {
        return ResponseEntity.ok(service.gerar(inicio, fim));
    }

    @Operation(
            summary = "Exportar relatório financeiro em PDF",
            description = "Mesmo cálculo do endpoint JSON, em PDF para download.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PDF gerado com sucesso",
                content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(responseCode = "400", description = "Período ausente, inválido ou acima de 366 dias")
    })
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarPdf(
            @Parameter(description = "Data inicial do relatório", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final do relatório", required = true) @RequestParam LocalDate fim) {
        byte[] pdf = exporter.exportarPdf(service.gerar(inicio, fim));
        return arquivo(pdf, MediaType.APPLICATION_PDF, nomeArquivo(inicio, fim, "pdf"));
    }

    @Operation(
            summary = "Exportar relatório financeiro em Excel",
            description = "Mesmo cálculo do endpoint JSON, em XLSX com abas Resumo e Repasses.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "XLSX gerado com sucesso",
                content = @Content(mediaType = XLSX)),
        @ApiResponse(responseCode = "400", description = "Período ausente, inválido ou acima de 366 dias")
    })
    @GetMapping(value = "/xlsx", produces = XLSX)
    public ResponseEntity<byte[]> exportarXlsx(
            @Parameter(description = "Data inicial do relatório", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final do relatório", required = true) @RequestParam LocalDate fim) {
        byte[] xlsx = exporter.exportarXlsx(service.gerar(inicio, fim));
        return arquivo(xlsx, MediaType.parseMediaType(XLSX), nomeArquivo(inicio, fim, "xlsx"));
    }

    private ResponseEntity<byte[]> arquivo(byte[] conteudo, MediaType mediaType, String nome) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome).build().toString())
                .contentLength(conteudo.length)
                .body(conteudo);
    }

    private String nomeArquivo(LocalDate inicio, LocalDate fim, String extensao) {
        return "relatorio-financeiro-" + inicio + "-" + fim + "." + extensao;
    }
}
