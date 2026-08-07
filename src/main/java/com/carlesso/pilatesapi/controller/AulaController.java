package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.service.AulaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Aulas", description = "Consulta e controle de presença nas aulas")
@RestController
@RequestMapping("/aulas")
public class AulaController {

    private final AulaService service;

    public AulaController(AulaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Listar aulas por período",
            description =
                    """
                    Agenda do estúdio: todas as aulas de pacientes ativos entre `inicio` e `fim` (ambos inclusive),
                    ordenadas por data. O período é obrigatório e limitado a 92 dias, e a resposta a 5000 registros.
                    Os filtros opcionais são combináveis entre si. Período sem registros retorna lista vazia.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Período ausente, inválido (início posterior ao fim), acima de 92 dias, ou resultado acima de 5000 registros")
    })
    @GetMapping
    public ResponseEntity<List<AulaResponseDTO>> listarPorPeriodo(
            @Parameter(description = "Data inicial do período (inclusive)", required = true) @RequestParam
                    LocalDate inicio,
            @Parameter(description = "Data final do período (inclusive)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Filtrar pelo profissional que ministrou/ministrará a aula")
                    @RequestParam(required = false)
                    Long profissionalId,
            @Parameter(description = "Filtrar pelo paciente da aula") @RequestParam(required = false) Long pacienteId,
            @Parameter(description = "Filtrar por aulas realizadas (true) ou pendentes (false)")
                    @RequestParam(required = false)
                    Boolean realizada) {
        return ResponseEntity.ok(service.listarPorPeriodo(inicio, fim, profissionalId, pacienteId, realizada));
    }

    @Operation(summary = "Buscar aula por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aula encontrada"),
        @ApiResponse(responseCode = "404", description = "Aula não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AulaResponseDTO> buscar(@Parameter(description = "ID da aula") @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Listar aulas do paciente",
            description = "Retorna todas as aulas de um paciente ordenadas por data.")
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
        @ApiResponse(responseCode = "404", description = "Aula ou profissional não encontrado"),
        @ApiResponse(responseCode = "409", description = "Aula já marcada como realizada"),
        @ApiResponse(responseCode = "422", description = "Profissional inativo")
    })
    @PatchMapping("/{id}/realizar")
    public ResponseEntity<AulaResponseDTO> realizar(
            @Parameter(description = "ID da aula") @PathVariable Long id,
            @Parameter(description = "ID do profissional que ministrou a aula") @RequestParam(required = false)
                    Long profissionalId) {
        return ResponseEntity.ok(service.realizarAula(id, profissionalId));
    }
}
