package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PlanoTratamentoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoResponseDTO;
import com.carlesso.pilatesapi.dto.PlanoTratamentoUpdateDTO;
import com.carlesso.pilatesapi.service.PlanoTratamentoService;
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

@Tag(name = "Planos de Tratamento", description = "Gerenciamento de planos de tratamento de pacientes")
@RestController
@RequestMapping("/planos-tratamento")
public class PlanoTratamentoController {

    private final PlanoTratamentoService service;

    public PlanoTratamentoController(PlanoTratamentoService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar plano de tratamento",
            description = "Registra um plano de tratamento para um paciente. O paciente pode possuir múltiplos planos ao longo do tempo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plano de tratamento criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @PostMapping
    public ResponseEntity<PlanoTratamentoResponseDTO> criar(
            @RequestBody @Valid PlanoTratamentoRequestDTO dto,
            UriComponentsBuilder uriBuilder) {
        PlanoTratamentoResponseDTO response = service.criar(dto);
        var uri = uriBuilder.path("/planos-tratamento/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar plano de tratamento por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano de tratamento encontrado"),
            @ApiResponse(responseCode = "404", description = "Plano de tratamento não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PlanoTratamentoResponseDTO> buscarPorId(
            @Parameter(description = "ID do plano de tratamento", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar planos de tratamento por paciente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planos de tratamento encontrados"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<PlanoTratamentoResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Atualizar plano de tratamento",
            description = "Atualiza um plano de tratamento. Apenas os campos enviados serão alterados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano de tratamento atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Plano de tratamento não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PlanoTratamentoResponseDTO> atualizar(
            @Parameter(description = "ID do plano de tratamento", required = true) @PathVariable Long id,
            @RequestBody @Valid PlanoTratamentoUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }
}
