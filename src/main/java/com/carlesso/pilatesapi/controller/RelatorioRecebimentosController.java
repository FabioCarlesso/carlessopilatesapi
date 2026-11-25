package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO.FormaRecebimento;
import com.carlesso.pilatesapi.service.RelatorioRecebimentosExporterService;
import com.carlesso.pilatesapi.service.RelatorioRecebimentosService;
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
@RequestMapping("/api/relatorios/recebimentos")
public class RelatorioRecebimentosController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PERIODO_INVALIDO =
            "Período ausente, inválido, acima de 366 dias ou com mais registros que o teto";

    private final RelatorioRecebimentosService service;
    private final RelatorioRecebimentosExporterService exporter;

    public RelatorioRecebimentosController(
            RelatorioRecebimentosService service, RelatorioRecebimentosExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Listar os recebimentos do período",
            description = "Pagamentos PAGO por dataPagamento (inclusive de paciente inativo), com totais por forma de"
                    + " pagamento. Sem filtros, o total é a receita do relatório financeiro no mesmo período. Período"
                    + " de até 366 dias, datas inclusivas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
        @ApiResponse(responseCode = "400", description = PERIODO_INVALIDO)
    })
    @GetMapping
    public ResponseEntity<RelatorioRecebimentosDTO> gerar(
            @Parameter(description = "Data inicial (data de pagamento)", required = true) @RequestParam
                    LocalDate inicio,
            @Parameter(description = "Data final (data de pagamento)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Forma de pagamento; NAO_INFORMADA traz os confirmados sem forma registrada")
                    @RequestParam(required = false)
                    FormaRecebimento formaPagamento,
            @Parameter(description = "Paciente") @RequestParam(required = false) Long pacienteId) {
        return ResponseEntity.ok(service.gerar(inicio, fim, formaPagamento, pacienteId));
    }

    @Operation(summary = "Exportar os recebimentos do período em PDF", description = "Mesmas linhas e totais do JSON.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PDF gerado com sucesso",
                content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(responseCode = "400", description = PERIODO_INVALIDO)
    })
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarPdf(
            @Parameter(description = "Data inicial (data de pagamento)", required = true) @RequestParam
                    LocalDate inicio,
            @Parameter(description = "Data final (data de pagamento)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Forma de pagamento") @RequestParam(required = false)
                    FormaRecebimento formaPagamento,
            @Parameter(description = "Paciente") @RequestParam(required = false) Long pacienteId) {
        byte[] pdf = exporter.exportarPdf(service.gerar(inicio, fim, formaPagamento, pacienteId));
        return arquivo(pdf, MediaType.APPLICATION_PDF, nomeArquivo(inicio, fim, "pdf"));
    }

    @Operation(
            summary = "Exportar os recebimentos do período em Excel",
            description = "Mesmas linhas e totais do JSON, em XLSX com abas Resumo e Recebimentos.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "XLSX gerado com sucesso",
                content = @Content(mediaType = XLSX)),
        @ApiResponse(responseCode = "400", description = PERIODO_INVALIDO)
    })
    @GetMapping(value = "/xlsx", produces = XLSX)
    public ResponseEntity<byte[]> exportarXlsx(
            @Parameter(description = "Data inicial (data de pagamento)", required = true) @RequestParam
                    LocalDate inicio,
            @Parameter(description = "Data final (data de pagamento)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Forma de pagamento") @RequestParam(required = false)
                    FormaRecebimento formaPagamento,
            @Parameter(description = "Paciente") @RequestParam(required = false) Long pacienteId) {
        byte[] xlsx = exporter.exportarXlsx(service.gerar(inicio, fim, formaPagamento, pacienteId));
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
        return "relatorio-recebimentos-" + inicio + "-" + fim + "." + extensao;
    }
}
