package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.OpcoesAvaliacaoResponseDTO;
import com.carlesso.pilatesapi.service.OpcoesAvaliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Opções de avaliação",
        description = "Catálogo das opções pré-definidas de anamnese, avaliação e reavaliação")
@RestController
@RequestMapping("/avaliacoes")
public class OpcoesAvaliacaoController {

    private final OpcoesAvaliacaoService service;

    public OpcoesAvaliacaoController(OpcoesAvaliacaoService service) {
        this.service = service;
    }

    @Operation(
            summary = "Catálogo de opções",
            description = "Campos estruturados da anamnese, da avaliação fisioterapêutica e da reavaliação, com as "
                    + "opções de cada um. `campo` é o nome da propriedade nos DTOs e `campoComplemento` o do texto "
                    + "livre que o acompanha. Opção com `ativa: false` está aposentada: exibir nos registros que a "
                    + "usam, mas não oferecer para seleção nova.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Catálogo"),
        @ApiResponse(responseCode = "403", description = "Papel sem acesso ao prontuário")
    })
    @GetMapping("/opcoes")
    public ResponseEntity<OpcoesAvaliacaoResponseDTO> catalogo() {
        return ResponseEntity.ok(service.catalogo());
    }
}
