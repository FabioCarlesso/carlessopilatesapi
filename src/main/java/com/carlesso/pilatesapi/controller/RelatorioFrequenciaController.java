package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO;
import com.carlesso.pilatesapi.service.RelatorioFrequenciaExporterService;
import com.carlesso.pilatesapi.service.RelatorioFrequenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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
@RequestMapping("/api/relatorios/frequencia")
public class RelatorioFrequenciaController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String REQUISICAO_INVALIDA = "Período ausente, invertido ou acima de 366 dias, taxaMaxima fora"
            + " de 0–100 ou mais registros que o teto";

    private final RelatorioFrequenciaService service;
    private final RelatorioFrequenciaExporterService exporter;

    public RelatorioFrequenciaController(
            RelatorioFrequenciaService service, RelatorioFrequenciaExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Frequência e faltas por paciente",
            description = "Por paciente: aulas previstas, realizadas, faltas (aulas passadas não realizadas) e taxa de"
                    + " presença. Só contam aulas com data anterior a hoje (fuso do estúdio). Período de até 366"
                    + " dias, datas inclusivas; ordenado pela menor presença.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
        @ApiResponse(responseCode = "400", description = REQUISICAO_INVALIDA)
    })
    @GetMapping
    public ResponseEntity<RelatorioFrequenciaDTO> gerar(
            @Parameter(description = "Data inicial (data da aula)", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final (data da aula)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Só as aulas deste profissional") @RequestParam(required = false)
                    Long profissionalId,
            @Parameter(description = "Paciente") @RequestParam(required = false) Long pacienteId,
            @Parameter(description = "Só pacientes com presença abaixo deste percentual (0–100), por exemplo 75")
                    @RequestParam(required = false)
                    BigDecimal taxaMaxima) {
        return ResponseEntity.ok(service.gerar(inicio, fim, profissionalId, pacienteId, taxaMaxima));
    }

    @Operation(
            summary = "Exportar a frequência por paciente em Excel",
            description = "Mesmas linhas e totais do JSON, em XLSX com abas Resumo e Frequência.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "XLSX gerado com sucesso",
                content = @Content(mediaType = XLSX)),
        @ApiResponse(responseCode = "400", description = REQUISICAO_INVALIDA)
    })
    @GetMapping(value = "/xlsx", produces = XLSX)
    public ResponseEntity<byte[]> exportarXlsx(
            @Parameter(description = "Data inicial (data da aula)", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final (data da aula)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Só as aulas deste profissional") @RequestParam(required = false)
                    Long profissionalId,
            @Parameter(description = "Paciente") @RequestParam(required = false) Long pacienteId,
            @Parameter(description = "Só pacientes com presença abaixo deste percentual (0–100), por exemplo 75")
                    @RequestParam(required = false)
                    BigDecimal taxaMaxima) {
        byte[] xlsx = exporter.exportarXlsx(service.gerar(inicio, fim, profissionalId, pacienteId, taxaMaxima));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("relatorio-frequencia-" + inicio + "-" + fim + ".xlsx")
                                .build()
                                .toString())
                .contentLength(xlsx.length)
                .body(xlsx);
    }
}
