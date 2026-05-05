package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.EvolucaoSessaoRequestDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoResponseDTO;
import com.carlesso.pilatesapi.dto.EvolucaoSessaoUpdateDTO;
import com.carlesso.pilatesapi.service.EvolucaoSessaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Evoluções de Sessão", description = "Registro e consulta de evoluções clínicas das sessões")
@RestController
@RequestMapping("/evolucoes-sessao")
public class EvolucaoSessaoController {

    private final EvolucaoSessaoService service;

    public EvolucaoSessaoController(EvolucaoSessaoService service) {
        this.service = service;
    }

    @Operation(
            summary = "Registrar evolução de sessão",
            description = "Registra a evolução clínica de uma sessão de Pilates ou Fisioterapia. Cada sessão admite apenas uma evolução."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evolução registrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada"),
            @ApiResponse(responseCode = "409", description = "Sessão já possui evolução registrada")
    })
    @PostMapping
    public ResponseEntity<EvolucaoSessaoResponseDTO> criar(
            @RequestBody @Valid EvolucaoSessaoRequestDTO dto,
            UriComponentsBuilder uriBuilder) {
        EvolucaoSessaoResponseDTO response = service.criar(dto);
        var uri = uriBuilder.path("/evolucoes-sessao/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar evolução por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evolução encontrada"),
            @ApiResponse(responseCode = "404", description = "Evolução não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EvolucaoSessaoResponseDTO> buscarPorId(
            @Parameter(description = "ID da evolução", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Buscar evolução por sessão")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evolução encontrada"),
            @ApiResponse(responseCode = "404", description = "Sessão ou evolução não encontrada")
    })
    @GetMapping("/sessao/{sessaoId}")
    public ResponseEntity<EvolucaoSessaoResponseDTO> buscarPorSessao(
            @Parameter(description = "ID da sessão", required = true) @PathVariable Long sessaoId) {
        return ResponseEntity.ok(service.buscarPorSessao(sessaoId));
    }

    @Operation(
            summary = "Atualizar evolução",
            description = "Atualiza os dados da evolução clínica. Apenas os campos enviados serão alterados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evolução atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Evolução não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EvolucaoSessaoResponseDTO> atualizar(
            @Parameter(description = "ID da evolução", required = true) @PathVariable Long id,
            @RequestBody @Valid EvolucaoSessaoUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }
}
