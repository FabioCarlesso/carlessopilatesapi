package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import com.carlesso.pilatesapi.service.RelatorioNfseExporterService;
import com.carlesso.pilatesapi.service.RelatorioNfseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
@RequestMapping("/api/relatorios/nfse")
public class RelatorioNfseController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv");
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RelatorioNfseService service;
    private final RelatorioNfseExporterService exporter;

    public RelatorioNfseController(RelatorioNfseService service, RelatorioNfseExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Gerar relatório de emissão de NFSEs",
            description =
                    "Retorna os pagamentos confirmados na competência informada, de pacientes ativos ou inativos, com dados"
                            + " para emissão manual de NFSE. CPF/CNPJ só sai para quem deseja CPF na nota; quem deseja e"
                            + " não tem CPF cadastrado sai com `pendencia`, sem derrubar o relatório.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Competência ou formato inválido"),
        @ApiResponse(responseCode = "422", description = "Pagamento sem valor ou sem data de pagamento")
    })
    @GetMapping
    public ResponseEntity<?> gerar(
            @Parameter(description = "Competência no formato MM/AAAA", required = true) @RequestParam
                    String competencia,
            @Parameter(description = "Filtra registros com ou sem nota anterior emitida")
                    @RequestParam(required = false)
                    Boolean notaAnteriorEmitida,
            @Parameter(description = "Filtra pacientes que desejam (true) ou não (false) CPF/CNPJ na nota")
                    @RequestParam(required = false)
                    Boolean cpfNaNota,
            @Parameter(description = "Formato de saída: JSON, CSV ou XLSX") @RequestParam(defaultValue = "JSON")
                    String formato) {
        List<RelatorioNfseResponseDTO> relatorio = service.gerar(competencia, notaAnteriorEmitida, cpfNaNota);
        String formatoNormalizado = formato.toUpperCase(Locale.ROOT);

        return switch (formatoNormalizado) {
            case "JSON" -> ResponseEntity.ok(relatorio);
            case "CSV" -> arquivo(exporter.exportarCsv(relatorio), CSV_MEDIA_TYPE, nomeArquivo(competencia, "csv"));
            case "XLSX" -> arquivo(exporter.exportarXlsx(relatorio), XLSX_MEDIA_TYPE, nomeArquivo(competencia, "xlsx"));
            default -> throw new IllegalArgumentException("formato deve ser JSON, CSV ou XLSX");
        };
    }

    private ResponseEntity<byte[]> arquivo(byte[] content, MediaType mediaType, String filename) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename)
                                .build()
                                .toString())
                .body(content);
    }

    private String nomeArquivo(String competencia, String extensao) {
        return "relatorio-nfse-" + competencia.replace("/", "-") + "." + extensao;
    }
}
