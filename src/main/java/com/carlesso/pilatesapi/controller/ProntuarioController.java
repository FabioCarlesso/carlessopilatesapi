package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.service.ProntuarioService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prontuário em PDF para a fiscalização do CREFITO (#250, COFFITO 414/2012).
 *
 * <p>Controller próprio, e não rotas no de pacientes e no de evoluções: as duas rotas são da área clínica (ADMIN e
 * PROFISSIONAL), enquanto {@code /pacientes/**} é da equipe toda — a regra de acesso fica no {@code SecurityConfig},
 * antes da de pacientes.
 */
@Tag(name = "Prontuário", description = "Exportação do prontuário do paciente em PDF")
@RestController
public class ProntuarioController {

    private final ProntuarioService service;

    public ProntuarioController(ProntuarioService service) {
        this.service = service;
    }

    @Operation(
            summary = "Exportar o prontuário do paciente em PDF",
            description = "Identificação do paciente, anamnese, avaliações fisioterapêuticas (com diagnóstico) e planos"
                    + " de tratamento vigentes no período, reavaliações e evoluções do período em ordem cronológica,"
                    + " cada evolução com o nome e o CREFITO do fisioterapeuta. Período opcional e inclusivo; sem ele,"
                    + " o prontuário completo. Evoluções são filtradas pela data da sessão.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PDF gerado",
                content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(responseCode = "400", description = "inicio posterior a fim ou data malformada"),
        @ApiResponse(responseCode = "403", description = "Papel sem acesso clínico"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping(value = "/pacientes/{id}/prontuario/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarProntuario(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long id,
            @Parameter(description = "Início do período (yyyy-MM-dd), inclusivo; ausente = desde o primeiro registro")
                    @RequestParam(required = false)
                    LocalDate inicio,
            @Parameter(description = "Fim do período (yyyy-MM-dd), inclusivo; ausente = até hoje")
                    @RequestParam(required = false)
                    LocalDate fim) {
        return pdf(service.exportarProntuario(id, inicio, fim));
    }

    @Operation(
            summary = "Exportar uma evolução de sessão em PDF",
            description = "Identificação do paciente e a evolução, com o nome e o CREFITO do fisioterapeuta.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PDF gerado",
                content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(responseCode = "403", description = "Papel sem acesso clínico"),
        @ApiResponse(responseCode = "404", description = "Evolução não encontrada")
    })
    @GetMapping(value = "/evolucoes-sessao/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarEvolucao(
            @Parameter(description = "ID da evolução", required = true) @PathVariable Long id) {
        return pdf(service.exportarEvolucao(id));
    }

    private ResponseEntity<byte[]> pdf(ProntuarioService.ArquivoPdf arquivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(arquivo.nomeArquivo())
                                .build()
                                .toString())
                .contentLength(arquivo.conteudo().length)
                .body(arquivo.conteudo());
    }
}
