package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AnamneseRequestDTO;
import com.carlesso.pilatesapi.dto.AnamneseResponseDTO;
import com.carlesso.pilatesapi.dto.AnamneseUpdateDTO;
import com.carlesso.pilatesapi.service.AnamneseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Anamnese", description = "Gerenciamento de anamneses de pacientes")
@RestController
@RequestMapping("/anamneses")
public class AnamneseController {

    private final AnamneseService service;

    public AnamneseController(AnamneseService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar anamnese",
            description =
                    "Registra a anamnese inicial de um paciente. Cada paciente pode ter apenas uma anamnese principal.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Anamnese criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado"),
        @ApiResponse(responseCode = "409", description = "Paciente já possui anamnese cadastrada")
    })
    @PostMapping
    public ResponseEntity<AnamneseResponseDTO> criar(
            @RequestBody @Valid AnamneseRequestDTO dto, UriComponentsBuilder uriBuilder) {
        AnamneseResponseDTO response = service.criar(dto);
        var uri =
                uriBuilder.path("/anamneses/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Buscar anamnese por ID", description = "Retorna a anamnese pelo seu identificador único.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Anamnese encontrada"),
        @ApiResponse(responseCode = "404", description = "Anamnese não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AnamneseResponseDTO> buscarPorId(
            @Parameter(description = "ID da anamnese", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Buscar anamnese por paciente",
            description = "Retorna a anamnese vinculada ao paciente informado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Anamnese encontrada"),
        @ApiResponse(responseCode = "404", description = "Paciente ou anamnese não encontrada")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<AnamneseResponseDTO> buscarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.buscarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Atualizar anamnese",
            description = "Atualiza as informações da anamnese. Apenas os campos enviados serão alterados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Anamnese atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Anamnese não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AnamneseResponseDTO> atualizar(
            @Parameter(description = "ID da anamnese", required = true) @PathVariable Long id,
            @RequestBody @Valid AnamneseUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }
}
