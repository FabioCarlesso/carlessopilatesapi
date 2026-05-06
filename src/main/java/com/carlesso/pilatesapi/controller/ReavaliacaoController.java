package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ReavaliacaoRequestDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoResponseDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoUpdateDTO;
import com.carlesso.pilatesapi.service.ReavaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Tag(name = "Reavaliações", description = "Gerenciamento de reavaliações periódicas de pacientes")
@RestController
@RequestMapping("/reavaliacoes")
public class ReavaliacaoController {

    private final ReavaliacaoService service;

    public ReavaliacaoController(ReavaliacaoService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar reavaliação",
            description = "Registra uma reavaliação para um paciente. O paciente pode possuir múltiplas reavaliações."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reavaliação criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
            @ApiResponse(responseCode = "404", description = "Paciente, avaliação fisioterapêutica ou plano de tratamento não encontrado")
    })
    @PostMapping
    public ResponseEntity<ReavaliacaoResponseDTO> criar(
            @RequestBody @Valid ReavaliacaoRequestDTO dto,
            UriComponentsBuilder uriBuilder) {
        ReavaliacaoResponseDTO response = service.criar(dto);
        var uri = uriBuilder.path("/reavaliacoes/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar reavaliação por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reavaliação encontrada"),
            @ApiResponse(responseCode = "404", description = "Reavaliação não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReavaliacaoResponseDTO> buscarPorId(
            @Parameter(description = "ID da reavaliação", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar reavaliações por paciente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reavaliações encontradas"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<ReavaliacaoResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Atualizar reavaliação",
            description = "Atualiza uma reavaliação. Apenas os campos enviados serão alterados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reavaliação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Reavaliação não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ReavaliacaoResponseDTO> atualizar(
            @Parameter(description = "ID da reavaliação", required = true) @PathVariable Long id,
            @RequestBody @Valid ReavaliacaoUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }
}
