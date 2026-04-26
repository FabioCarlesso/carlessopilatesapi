package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.service.AulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Aulas", description = "Consulta e controle de presença nas aulas")
@RestController
@RequestMapping("/aulas")
public class AulaController {

    private final AulaService service;

    public AulaController(AulaService service) {
        this.service = service;
    }

    @Operation(summary = "Buscar aula por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aula encontrada"),
            @ApiResponse(responseCode = "404", description = "Aula não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> buscar(
            @Parameter(description = "ID da aula") @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar aulas do paciente", description = "Retorna todas as aulas de um paciente ordenadas por data.")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<AulaResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente") @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.buscarPorPaciente(pacienteId));
    }

    @Operation(summary = "Listar aulas de um pagamento")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping("/pagamento/{pagamentoId}")
    public ResponseEntity<List<AulaResponseDTO>> listarPorPagamento(
            @Parameter(description = "ID do pagamento") @PathVariable Long pagamentoId) {
        return ResponseEntity.ok(service.buscarPorPagamento(pagamentoId));
    }

    @Operation(summary = "Marcar aula como realizada")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aula marcada como realizada"),
            @ApiResponse(responseCode = "404", description = "Aula não encontrada"),
            @ApiResponse(responseCode = "409", description = "Aula já marcada como realizada")
    })
    @PatchMapping("/{id}/realizar")
    public ResponseEntity<AulaResponseDTO> realizar(
            @Parameter(description = "ID da aula") @PathVariable Long id,
            @Parameter(description = "ID do profissional que ministrou a aula")
            @RequestParam(required = false) Long profissionalId) {
        return ResponseEntity.ok(service.realizarAula(id, profissionalId));
    }
}
