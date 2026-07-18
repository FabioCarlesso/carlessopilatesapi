package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AvaliacaoPosturalRequestDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalResponseDTO;
import com.carlesso.pilatesapi.dto.AvaliacaoPosturalUpdateDTO;
import com.carlesso.pilatesapi.service.AvaliacaoPosturalService;
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

@Tag(
        name = "Análises Posturais",
        description = "Simetrógrafo virtual: marcação de pontos anatômicos e métricas calculadas pelo sistema")
@RestController
@RequestMapping("/avaliacoes-posturais")
public class AvaliacaoPosturalController {

    private final AvaliacaoPosturalService service;

    public AvaliacaoPosturalController(AvaliacaoPosturalService service) {
        this.service = service;
    }

    @Operation(
            summary = "Criar análise postural",
            description =
                    "Cria a análise em RASCUNHO dentro de uma avaliação fisioterapêutica. Cada avaliação admite no máximo uma análise ativa por vista.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Análise criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
        @ApiResponse(responseCode = "404", description = "Avaliação fisioterapêutica não encontrada"),
        @ApiResponse(responseCode = "409", description = "Já existe análise ativa dessa vista na avaliação")
    })
    @PostMapping
    public ResponseEntity<AvaliacaoPosturalResponseDTO> criar(
            @RequestBody @Valid AvaliacaoPosturalRequestDTO dto, UriComponentsBuilder uriBuilder) {
        AvaliacaoPosturalResponseDTO response = service.criar(dto);
        var uri = uriBuilder
                .path("/avaliacoes-posturais/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Buscar análise postural por ID",
            description = "Retorna a análise com as métricas calculadas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise encontrada"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> buscarPorId(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar análises posturais ativas da avaliação fisioterapêutica")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análises encontradas"),
        @ApiResponse(responseCode = "404", description = "Avaliação fisioterapêutica não encontrada")
    })
    @GetMapping("/avaliacao-fisioterapeutica/{avaliacaoId}")
    public ResponseEntity<List<AvaliacaoPosturalResponseDTO>> listarPorAvaliacaoFisioterapeutica(
            @Parameter(description = "ID da avaliação fisioterapêutica", required = true) @PathVariable
                    Long avaliacaoId) {
        return ResponseEntity.ok(service.listarPorAvaliacaoFisioterapeutica(avaliacaoId));
    }

    @Operation(
            summary = "Atualizar marcação da análise postural",
            description =
                    "Atualiza landmarks, linha de prumo, calibração, proporção da imagem e observações de uma análise em RASCUNHO. As métricas são recalculadas a cada salvamento e nunca são aceitas no corpo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise atualizada com métricas recalculadas"),
        @ApiResponse(responseCode = "400", description = "Coordenada fora de [0,1] ou dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada"),
        @ApiResponse(
                responseCode = "422",
                description = "Análise concluída (imutável), ponto de outra vista ou ponto duplicado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> atualizar(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id,
            @RequestBody @Valid AvaliacaoPosturalUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Concluir análise postural",
            description =
                    "Fecha a análise. Exige todos os pontos obrigatórios da vista marcados e a foto enviada; depois disso a análise fica imutável.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise concluída"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou cancelada"),
        @ApiResponse(responseCode = "422", description = "Pontos obrigatórios incompletos, sem foto ou já concluída")
    })
    @PatchMapping("/{id}/concluir")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> concluir(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.concluir(id));
    }

    @Operation(
            summary = "Cancelar análise postural",
            description =
                    "Exclusão lógica: a análise sai das listagens e libera a vista para uma nova análise, preservando o histórico clínico.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Análise cancelada"),
        @ApiResponse(responseCode = "404", description = "Análise não encontrada ou já cancelada")
    })
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<AvaliacaoPosturalResponseDTO> cancelar(
            @Parameter(description = "ID da análise postural", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.cancelar(id));
    }
}
