package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PagamentoPagarRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.service.PagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Pagamentos", description = "Gerenciamento de pagamentos e cobranças")
@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService service;

    public PagamentoController(PagamentoService service) {
        this.service = service;
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
}
