package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PontoDorResponseDTO;
import com.carlesso.pilatesapi.dto.PontosDorRegistroDTO;
import com.carlesso.pilatesapi.dto.PontosDorRequestDTO;
import com.carlesso.pilatesapi.service.PontoDorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pontos de dor do mapa corporal (#249). Controller próprio, em vez de campos nos DTOs da avaliação e da
 * reavaliação: o conjunto de pontos é salvo e lido pela tela do mapa, independente do formulário do registro.
 */
@Tag(name = "Pontos de dor", description = "Pontos de dor marcados sobre o mapa corporal da avaliação e da reavaliação")
@RestController
public class PontoDorController {

    private final PontoDorService service;

    public PontoDorController(PontoDorService service) {
        this.service = service;
    }

    @Operation(summary = "Listar pontos de dor da avaliação fisioterapêutica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pontos da avaliação (lista vazia se não houver)"),
        @ApiResponse(responseCode = "404", description = "Avaliação fisioterapêutica não encontrada")
    })
    @GetMapping("/avaliacoes-fisioterapeuticas/{id}/pontos-dor")
    public ResponseEntity<List<PontoDorResponseDTO>> listarDaAvaliacao(
            @Parameter(description = "ID da avaliação fisioterapêutica", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.listarDaAvaliacao(id));
    }

    @Operation(
            summary = "Substituir pontos de dor da avaliação fisioterapêutica",
            description = "Substitui o conjunto inteiro de pontos da avaliação pelo enviado; lista vazia apaga todos."
                    + " Máximo de " + PontosDorRequestDTO.MAXIMO_PONTOS + " pontos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pontos gravados"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Coordenada fora de 0–1, vista inválida, desenhoVersao menor que 1, intensidade fora de 0–10,"
                                + " observação acima de 200 caracteres ou mais de 50 pontos"),
        @ApiResponse(responseCode = "404", description = "Avaliação fisioterapêutica não encontrada")
    })
    @PutMapping("/avaliacoes-fisioterapeuticas/{id}/pontos-dor")
    public ResponseEntity<List<PontoDorResponseDTO>> substituirDaAvaliacao(
            @Parameter(description = "ID da avaliação fisioterapêutica", required = true) @PathVariable Long id,
            @RequestBody @Valid PontosDorRequestDTO dto) {
        return ResponseEntity.ok(service.substituirDaAvaliacao(id, dto));
    }

    @Operation(summary = "Listar pontos de dor da reavaliação")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pontos da reavaliação (lista vazia se não houver)"),
        @ApiResponse(responseCode = "404", description = "Reavaliação não encontrada")
    })
    @GetMapping("/reavaliacoes/{id}/pontos-dor")
    public ResponseEntity<List<PontoDorResponseDTO>> listarDaReavaliacao(
            @Parameter(description = "ID da reavaliação", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.listarDaReavaliacao(id));
    }

    @Operation(
            summary = "Substituir pontos de dor da reavaliação",
            description = "Substitui o conjunto inteiro de pontos da reavaliação pelo enviado; lista vazia apaga todos."
                    + " Máximo de " + PontosDorRequestDTO.MAXIMO_PONTOS + " pontos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pontos gravados"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Coordenada fora de 0–1, vista inválida, desenhoVersao menor que 1, intensidade fora de 0–10,"
                                + " observação acima de 200 caracteres ou mais de 50 pontos"),
        @ApiResponse(responseCode = "404", description = "Reavaliação não encontrada")
    })
    @PutMapping("/reavaliacoes/{id}/pontos-dor")
    public ResponseEntity<List<PontoDorResponseDTO>> substituirDaReavaliacao(
            @Parameter(description = "ID da reavaliação", required = true) @PathVariable Long id,
            @RequestBody @Valid PontosDorRequestDTO dto) {
        return ResponseEntity.ok(service.substituirDaReavaliacao(id, dto));
    }

    @Operation(
            summary = "Histórico de pontos de dor do paciente",
            description = "Pontos agrupados por avaliação/reavaliação, do registro mais antigo para o mais recente"
                    + " (pela data do registro). Só entram os registros que têm pontos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Histórico do paciente (lista vazia se não houver pontos)"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/pacientes/{pacienteId}/pontos-dor")
    public ResponseEntity<List<PontosDorRegistroDTO>> historicoDoPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.historicoDoPaciente(pacienteId));
    }
}
