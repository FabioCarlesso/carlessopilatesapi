package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioPacientesDTO;
import com.carlesso.pilatesapi.dto.SituacaoPacienteRelatorio;
import com.carlesso.pilatesapi.service.RelatorioPacientesExporterService;
import com.carlesso.pilatesapi.service.RelatorioPacientesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
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
@RequestMapping("/api/relatorios/pacientes")
public class RelatorioPacientesController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String DESCRICAO_400 =
            "mesNascimento fora de 1–12, situacao inválida ou resultado acima do" + " teto de registros";

    private final RelatorioPacientesService service;
    private final RelatorioPacientesExporterService exporter;

    public RelatorioPacientesController(RelatorioPacientesService service, RelatorioPacientesExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Gerar relatório de pacientes",
            description = "Pacientes por nome, com contato, situação, plano ativo, profissional das próximas aulas e"
                    + " pendências de cadastro. Filtros booleanos só restringem quando true.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
        @ApiResponse(responseCode = "400", description = DESCRICAO_400)
    })
    @GetMapping
    public ResponseEntity<RelatorioPacientesDTO> gerar(
            @Parameter(description = "Situação dos pacientes (padrão ATIVOS)") @RequestParam(defaultValue = "ATIVOS")
                    SituacaoPacienteRelatorio situacao,
            @Parameter(description = "Só pacientes ativos sem plano ativo") @RequestParam(required = false)
                    Boolean semPlanoAtivo,
            @Parameter(description = "Só pacientes com ao menos uma pendência") @RequestParam(required = false)
                    Boolean cadastroIncompleto,
            @Parameter(description = "Mês de nascimento (1–12), para aniversariantes") @RequestParam(required = false)
                    Integer mesNascimento,
            @Parameter(description = "Profissional das próximas aulas do paciente") @RequestParam(required = false)
                    Long profissionalId) {
        return ResponseEntity.ok(
                service.gerar(situacao, semPlanoAtivo, cadastroIncompleto, mesNascimento, profissionalId));
    }

    @Operation(summary = "Exportar relatório de pacientes em PDF", description = "Mesmas linhas e totais do JSON.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PDF gerado com sucesso",
                content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
        @ApiResponse(responseCode = "400", description = DESCRICAO_400)
    })
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(defaultValue = "ATIVOS") SituacaoPacienteRelatorio situacao,
            @RequestParam(required = false) Boolean semPlanoAtivo,
            @RequestParam(required = false) Boolean cadastroIncompleto,
            @RequestParam(required = false) Integer mesNascimento,
            @RequestParam(required = false) Long profissionalId) {
        RelatorioPacientesDTO relatorio =
                service.gerar(situacao, semPlanoAtivo, cadastroIncompleto, mesNascimento, profissionalId);
        return arquivo(exporter.exportarPdf(relatorio), MediaType.APPLICATION_PDF, nomeArquivo(relatorio, "pdf"));
    }

    @Operation(
            summary = "Exportar relatório de pacientes em Excel",
            description = "Mesmas linhas e totais do JSON, em XLSX com abas Resumo e Pacientes.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "XLSX gerado com sucesso",
                content = @Content(mediaType = XLSX)),
        @ApiResponse(responseCode = "400", description = DESCRICAO_400)
    })
    @GetMapping(value = "/xlsx", produces = XLSX)
    public ResponseEntity<byte[]> exportarXlsx(
            @RequestParam(defaultValue = "ATIVOS") SituacaoPacienteRelatorio situacao,
            @RequestParam(required = false) Boolean semPlanoAtivo,
            @RequestParam(required = false) Boolean cadastroIncompleto,
            @RequestParam(required = false) Integer mesNascimento,
            @RequestParam(required = false) Long profissionalId) {
        RelatorioPacientesDTO relatorio =
                service.gerar(situacao, semPlanoAtivo, cadastroIncompleto, mesNascimento, profissionalId);
        return arquivo(
                exporter.exportarXlsx(relatorio), MediaType.parseMediaType(XLSX), nomeArquivo(relatorio, "xlsx"));
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

    private String nomeArquivo(RelatorioPacientesDTO relatorio, String extensao) {
        return "relatorio-pacientes-"
                + relatorio.filtros().situacao().name().toLowerCase(Locale.ROOT)
                + "-"
                + relatorio.geradoEm().toLocalDate()
                + "."
                + extensao;
    }
}
