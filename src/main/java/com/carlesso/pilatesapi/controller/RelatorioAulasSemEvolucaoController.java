package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO;
import com.carlesso.pilatesapi.service.RelatorioAulasSemEvolucaoExporterService;
import com.carlesso.pilatesapi.service.RelatorioAulasSemEvolucaoService;
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
@RequestMapping("/api/relatorios/aulas-sem-evolucao")
public class RelatorioAulasSemEvolucaoController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PERIODO_INVALIDO =
            "Período ausente, inválido, acima de 366 dias ou com mais registros que o teto";

    private final RelatorioAulasSemEvolucaoService service;
    private final RelatorioAulasSemEvolucaoExporterService exporter;

    public RelatorioAulasSemEvolucaoController(
            RelatorioAulasSemEvolucaoService service, RelatorioAulasSemEvolucaoExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Listar as aulas realizadas sem evolução",
            description = "Aulas realizadas no período sem evolução registrada (EvolucaoSessao) em sessão do mesmo"
                    + " paciente na mesma data, com a quantidade por profissional. Inclui paciente inativo e aula sem"
                    + " profissional. Período de até 366 dias, datas inclusivas. Restrito a ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
        @ApiResponse(responseCode = "400", description = PERIODO_INVALIDO)
    })
    @GetMapping
    public ResponseEntity<RelatorioAulasSemEvolucaoDTO> gerar(
            @Parameter(description = "Data inicial (data da aula)", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final (data da aula)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Profissional da aula") @RequestParam(required = false) Long profissionalId) {
        return ResponseEntity.ok(service.gerar(inicio, fim, profissionalId));
    }

    @Operation(
            summary = "Exportar as aulas realizadas sem evolução em Excel",
            description = "Mesmas linhas e totais do JSON, em XLSX com abas Resumo e Aulas.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "XLSX gerado com sucesso",
                content = @Content(mediaType = XLSX)),
        @ApiResponse(responseCode = "400", description = PERIODO_INVALIDO)
    })
    @GetMapping(value = "/xlsx", produces = XLSX)
    public ResponseEntity<byte[]> exportarXlsx(
            @Parameter(description = "Data inicial (data da aula)", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final (data da aula)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Profissional da aula") @RequestParam(required = false) Long profissionalId) {
        byte[] xlsx = exporter.exportarXlsx(service.gerar(inicio, fim, profissionalId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(exporter.nomeArquivo(inicio, fim))
                                .build()
                                .toString())
                .contentLength(xlsx.length)
                .body(xlsx);
    }
}
