package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoFisioterapeuticaUpdateDTO;
import com.carlesso.pilatesapi.service.AvaliacaoFisioterapeuticaService;
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

@Tag(name = "Avaliações Fisioterapêuticas", description = "Gerenciamento de avaliações fisioterapêuticas de pacientes")
@RestController
@RequestMapping("/avaliacoes-fisioterapeuticas")
public class AvaliacaoFisioterapeuticaController {

    private final AvaliacaoFisioterapeuticaService service;

    public AvaliacaoFisioterapeuticaController(AvaliacaoFisioterapeuticaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar avaliação fisioterapêutica",
            description =
                    "Registra uma avaliação fisioterapêutica para um paciente. O paciente pode possuir múltiplas avaliações.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Avaliação criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @PostMapping
    public ResponseEntity<AvaliacaoFisioterapeuticaResponseDTO> criar(
            @RequestBody @Valid AvaliacaoFisioterapeuticaRequestDTO dto, UriComponentsBuilder uriBuilder) {
        AvaliacaoFisioterapeuticaResponseDTO response = service.criar(dto);
        var uri = uriBuilder
                .path("/avaliacoes-fisioterapeuticas/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar avaliação fisioterapêutica por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avaliação encontrada"),
        @ApiResponse(responseCode = "404", description = "Avaliação não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AvaliacaoFisioterapeuticaResponseDTO> buscarPorId(
            @Parameter(description = "ID da avaliação fisioterapêutica", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar avaliações fisioterapêuticas por paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avaliações encontradas"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<AvaliacaoFisioterapeuticaResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Atualizar avaliação fisioterapêutica",
            description = "Atualiza uma avaliação fisioterapêutica. Apenas os campos enviados serão alterados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avaliação atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Avaliação não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AvaliacaoFisioterapeuticaResponseDTO> atualizar(
            @Parameter(description = "ID da avaliação fisioterapêutica", required = true) @PathVariable Long id,
            @RequestBody @Valid AvaliacaoFisioterapeuticaUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }
}
