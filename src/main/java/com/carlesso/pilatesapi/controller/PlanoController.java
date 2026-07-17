package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PlanoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoResponseDTO;
import com.carlesso.pilatesapi.service.PlanoService;
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

@Tag(name = "Planos", description = "Gerenciamento de planos de pagamento dos pacientes")
@RestController
@RequestMapping("/planos")
public class PlanoController {

    private final PlanoService planoService;

    public PlanoController(PlanoService planoService) {
        this.planoService = planoService;
    }

    @Operation(
            summary = "Criar plano",
            description =
                    "Cria um novo plano de pagamento para um paciente. Se já existir um plano ativo, ele será inativado automaticamente.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Plano criado com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description = "Dados inválidos ou frequência incompatível com os dias informados"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado"),
        @ApiResponse(responseCode = "422", description = "Paciente inativo")
    })
    @PostMapping
    public ResponseEntity<PlanoResponseDTO> criar(
            @RequestBody @Valid PlanoRequestDTO dto, UriComponentsBuilder uriBuilder) {
        PlanoResponseDTO response = planoService.criar(dto);
        var uri = uriBuilder.path("/planos/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar plano por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plano encontrado"),
        @ApiResponse(responseCode = "404", description = "Plano não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PlanoResponseDTO> buscar(@Parameter(description = "ID do plano") @PathVariable Long id) {
        return ResponseEntity.ok(planoService.buscarPorId(id));
    }

    @Operation(summary = "Listar planos do paciente")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<PlanoResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente") @PathVariable Long pacienteId) {
        return ResponseEntity.ok(planoService.listarPorPaciente(pacienteId));
    }

    @Operation(summary = "Buscar plano ativo do paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plano ativo encontrado"),
        @ApiResponse(responseCode = "204", description = "Paciente não possui plano ativo")
    })
    @GetMapping("/paciente/{pacienteId}/ativo")
    public ResponseEntity<PlanoResponseDTO> buscarAtivoPorPaciente(
            @Parameter(description = "ID do paciente") @PathVariable Long pacienteId) {
        return planoService
                .buscarAtivoPorPaciente(pacienteId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @Operation(
            summary = "Inativar plano",
            description = "Inativa o plano especificado. Não afeta pagamentos ou aulas já gerados.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Plano inativado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Plano não encontrado"),
        @ApiResponse(responseCode = "409", description = "Plano já está inativo")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@Parameter(description = "ID do plano") @PathVariable Long id) {
        planoService.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
