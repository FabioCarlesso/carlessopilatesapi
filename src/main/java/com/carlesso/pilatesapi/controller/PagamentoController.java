package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ContatoCobrancaRequestDTO;
import com.carlesso.pilatesapi.dto.ContatoCobrancaResponseDTO;
import com.carlesso.pilatesapi.dto.PagamentoPagarRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.service.ContatoCobrancaService;
import com.carlesso.pilatesapi.service.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Pagamentos", description = "Gerenciamento de pagamentos e cobranças")
@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService service;
    private final ContatoCobrancaService contatoCobrancaService;

    public PagamentoController(PagamentoService service, ContatoCobrancaService contatoCobrancaService) {
        this.service = service;
        this.contatoCobrancaService = contatoCobrancaService;
    }

    @Operation(
            summary = "Listar pagamentos (gestão de inadimplência)",
            description =
                    """
                    Listagem global paginada, restrita a `ADMIN`. Todos os filtros são opcionais e combináveis:
                    `status` aceita mais de um valor (`?status=PENDENTE&status=VENCIDO`) e a faixa de vencimento é
                    inclusiva nas duas pontas. A ordenação padrão é `dataVencimento` crescente — os mais atrasados
                    primeiro; `sort` aceita `dataVencimento`, `valor`, `status` e `paciente.nome`.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página retornada com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Faixa de vencimento invertida, status ou data inválidos, ou campo de ordenação não aceito"),
        @ApiResponse(responseCode = "403", description = "Usuário sem papel ADMIN")
    })
    @GetMapping
    public ResponseEntity<Page<PagamentoResponseDTO>> listar(
            @Parameter(description = "Filtra por status; pode ser repetido") @RequestParam(required = false)
                    List<StatusPagamento> status,
            @Parameter(description = "Vencimento a partir de (`yyyy-MM-dd`, inclusivo)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate vencimentoDe,
            @Parameter(description = "Vencimento até (`yyyy-MM-dd`, inclusivo)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate vencimentoAte,
            @Parameter(description = "ID do paciente") @RequestParam(required = false) Long pacienteId,
            @ParameterObject @PageableDefault(sort = "dataVencimento") Pageable pageable) {
        return ResponseEntity.ok(service.listar(status, vencimentoDe, vencimentoAte, pacienteId, pageable));
    }

    @Operation(
            summary = "Criar pagamento",
            description =
                    "Cria um novo pagamento com status PENDENTE. O período final é calculado automaticamente com base no tipo do plano.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pagamento criado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou valor abaixo do plano"),
        @ApiResponse(responseCode = "404", description = "Paciente ou plano não encontrado"),
        @ApiResponse(responseCode = "409", description = "Pagamento duplicado para o período"),
        @ApiResponse(responseCode = "422", description = "Paciente inativo")
    })
    @PostMapping
    public ResponseEntity<PagamentoResponseDTO> criar(
            @RequestBody @Valid PagamentoRequestDTO dto, UriComponentsBuilder uriBuilder) {
        PagamentoResponseDTO response = service.criar(dto);
        var uri = uriBuilder
                .path("/pagamentos/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar pagamento por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagamento encontrado"),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PagamentoResponseDTO> buscar(
            @Parameter(description = "ID do pagamento") @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar pagamentos do paciente")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<PagamentoResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente") @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Confirmar pagamento",
            description =
                    "Marca o pagamento como PAGO e gera automaticamente as aulas do período. A data de pagamento pode ser enviada no corpo da requisição.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagamento confirmado e aulas geradas"),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado"),
        @ApiResponse(responseCode = "409", description = "Pagamento já confirmado"),
        @ApiResponse(responseCode = "422", description = "Pagamento não está PAGO ou paciente está inativo")
    })
    @PatchMapping("/{id}/pagar")
    public ResponseEntity<PagamentoResponseDTO> pagar(
            @Parameter(description = "ID do pagamento") @PathVariable Long id,
            @RequestBody(required = false) PagamentoPagarRequestDTO dto) {
        return ResponseEntity.ok(service.pagar(id, dto != null ? dto.dataPagamento() : null));
    }

    @Operation(
            summary = "Registrar contato de cobrança",
            description =
                    """
                    Registra um contato de cobrança sobre o pagamento, restrito a `ADMIN`. `registradoPor` e
                    `registradoEm` são definidos pelo servidor a partir do usuário autenticado; valores enviados no
                    corpo são ignorados. Pagamento `PAGO` não aceita novos contatos.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Contato registrado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Data ausente ou futura, ou observação vazia/longa demais"),
        @ApiResponse(responseCode = "403", description = "Usuário sem papel ADMIN"),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado"),
        @ApiResponse(responseCode = "422", description = "Pagamento já confirmado")
    })
    @PostMapping("/{id}/cobrancas")
    public ResponseEntity<ContatoCobrancaResponseDTO> registrarCobranca(
            @Parameter(description = "ID do pagamento") @PathVariable Long id,
            @RequestBody @Valid ContatoCobrancaRequestDTO dto,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {
        ContatoCobrancaResponseDTO response = contatoCobrancaService.registrar(id, dto, authentication.getName());
        var uri = uriBuilder
                .path("/pagamentos/{id}/cobrancas/{contatoId}")
                .buildAndExpand(id, response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Listar contatos de cobrança do pagamento",
            description = "Histórico do pagamento, do contato mais recente para o mais antigo. Restrito a `ADMIN`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "403", description = "Usuário sem papel ADMIN"),
        @ApiResponse(responseCode = "404", description = "Pagamento não encontrado")
    })
    @GetMapping("/{id}/cobrancas")
    public ResponseEntity<List<ContatoCobrancaResponseDTO>> listarCobrancas(
            @Parameter(description = "ID do pagamento") @PathVariable Long id) {
        return ResponseEntity.ok(contatoCobrancaService.listar(id));
    }

    @Operation(
            summary = "Buscar contato de cobrança por ID",
            description = "Recupera o contato apontado pelo `Location` devolvido no registro. Restrito a `ADMIN`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contato encontrado"),
        @ApiResponse(responseCode = "403", description = "Usuário sem papel ADMIN"),
        @ApiResponse(responseCode = "404", description = "Contato não encontrado neste pagamento")
    })
    @GetMapping("/{id}/cobrancas/{contatoId}")
    public ResponseEntity<ContatoCobrancaResponseDTO> buscarCobranca(
            @Parameter(description = "ID do pagamento") @PathVariable Long id,
            @Parameter(description = "ID do contato") @PathVariable Long contatoId) {
        return ResponseEntity.ok(contatoCobrancaService.buscarPorId(id, contatoId));
    }
}
