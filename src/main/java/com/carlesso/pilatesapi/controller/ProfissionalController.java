package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.service.ProfissionalService;
import com.carlesso.pilatesapi.service.RelatorioPagamentoExporterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Tag(name = "Profissionais", description = "Gerenciamento de profissionais do estúdio")
@RestController
@RequestMapping("/profissionais")
public class ProfissionalController {

    private static final MediaType APPLICATION_XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProfissionalService service;
    private final RelatorioPagamentoExporterService exporter;

    public ProfissionalController(ProfissionalService service, RelatorioPagamentoExporterService exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(summary = "Cadastrar profissional", description = "Registra um novo profissional no sistema. Retorna 201 com o header Location apontando para o recurso criado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profissional cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
            @ApiResponse(responseCode = "409", description = "E-mail ou CPF já cadastrado")
    })
    @PostMapping
    public ResponseEntity<ProfissionalResponseDTO> cadastrar(@RequestBody @Valid ProfissionalRequestDTO dto,
                                                             UriComponentsBuilder uriBuilder) {
        ProfissionalResponseDTO response = service.cadastrar(dto);
        var uri = uriBuilder.path("/profissionais/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Listar e filtrar profissionais", description = "Retorna uma página de profissionais filtrando por nome, e-mail, tipo de contrato, percentual por aula e status ativo/inativo. Por padrão retorna profissionais ativos. Suporta paginação e ordenação via query params.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    @GetMapping
    public ResponseEntity<Page<ProfissionalResponseDTO>> listar(
            @Parameter(description = "Filtro parcial por nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtro parcial por e-mail") @RequestParam(required = false) String email,
            @Parameter(description = "Filtro por tipo de contrato") @RequestParam(required = false) TipoContrato tipoContrato,
            @Parameter(description = "Filtro por percentual de pagamento por aula") @RequestParam(required = false) BigDecimal percentualPagamentoAula,
            @Parameter(description = "Filtra por status. Quando omitido, retorna apenas ativos.") @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(service.listar(nome, email, tipoContrato, percentualPagamentoAula, ativo, pageable));
    }

    @Operation(summary = "Buscar profissional por ID", description = "Retorna os dados completos de um profissional pelo seu identificador único.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissional encontrado"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProfissionalResponseDTO> buscar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Atualizar profissional", description = "Atualiza os dados de um profissional. Apenas os campos enviados serão alterados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissional atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProfissionalResponseDTO> atualizar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id,
            @RequestBody @Valid ProfissionalUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(summary = "Ativar profissional", description = "Reativa um profissional previamente inativado, definindo ativo = true.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profissional ativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id) {
        service.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Inativar profissional", description = "Realiza soft delete do profissional, marcando-o como inativo. O registro não é removido do banco.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profissional inativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gerar relatório de pagamento do profissional",
            description = "Retorna o relatório financeiro estruturado em sub-objetos (profissional, periodo, resumo, pagamentos, aulas), pronto para consumo no Angular.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatório gerado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Período inválido"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping("/{id}/relatorio-pagamento")
    public ResponseEntity<ProfissionalPagamentoRelatorioDTO> gerarRelatorioPagamento(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id,
            @Parameter(description = "Data inicial do relatório", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final do relatório", required = true) @RequestParam LocalDate fim) {
        return ResponseEntity.ok(service.gerarRelatorioPagamento(id, inicio, fim));
    }

    @Operation(summary = "Exportar relatório de pagamento em PDF",
            description = "Gera o relatório de pagamento do profissional em PDF para download. Usa o mesmo cálculo do endpoint JSON.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF gerado com sucesso", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "400", description = "Período inválido"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping(value = "/{id}/relatorio-pagamento/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarRelatorioPagamentoPdf(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id,
            @Parameter(description = "Data inicial do relatório", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final do relatório", required = true) @RequestParam LocalDate fim) {
        ProfissionalPagamentoRelatorioDTO relatorio = service.gerarRelatorioPagamento(id, inicio, fim);
        byte[] pdf = exporter.exportarPdf(relatorio);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomeArquivo(id, inicio, fim) + ".pdf\"")
                .contentLength(pdf.length)
                .body(pdf);
    }

    @Operation(summary = "Exportar relatório de pagamento em Excel",
            description = "Gera o relatório de pagamento do profissional em XLSX para download, com abas para resumo, pagamentos e aulas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XLSX gerado com sucesso", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "400", description = "Período inválido"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping(value = "/{id}/relatorio-pagamento/xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportarRelatorioPagamentoXlsx(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id,
            @Parameter(description = "Data inicial do relatório", required = true) @RequestParam LocalDate inicio,
            @Parameter(description = "Data final do relatório", required = true) @RequestParam LocalDate fim) {
        ProfissionalPagamentoRelatorioDTO relatorio = service.gerarRelatorioPagamento(id, inicio, fim);
        byte[] xlsx = exporter.exportarXlsx(relatorio);
        return ResponseEntity.ok()
                .contentType(APPLICATION_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomeArquivo(id, inicio, fim) + ".xlsx\"")
                .contentLength(xlsx.length)
                .body(xlsx);
    }

    private String nomeArquivo(Long id, LocalDate inicio, LocalDate fim) {
        return "relatorio-pagamento-profissional-" + id + "-" + inicio + "-" + fim;
    }
}
