package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.BloqueioAgendaRequestDTO;
import com.carlesso.pilatesapi.dto.BloqueioAgendaResponseDTO;
import com.carlesso.pilatesapi.service.BloqueioAgendaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Bloqueios de agenda",
        description = "Feriados, manutenções e eventos em que o estúdio não funciona. O bloqueio é informativo: "
                + "avisa a agenda, não impede a geração de aulas nem o agendamento de sessões")
@RestController
@RequestMapping("/bloqueios")
public class BloqueioAgendaController {

    private final BloqueioAgendaService service;

    public BloqueioAgendaController(BloqueioAgendaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Cadastrar bloqueio de agenda",
            description =
                    """
                    Registra um período em que o estúdio não funciona. Omitir `horaInicio` e `horaFim` bloqueia o dia
                    inteiro; informar as duas bloqueia apenas a faixa de horário, em cada dia do período.
                    Exige perfil `ADMIN`.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Bloqueio criado com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Motivo vazio, período invertido, faixa de horário inválida ou hora informada pela metade"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN")
    })
    @PostMapping
    public ResponseEntity<BloqueioAgendaResponseDTO> criar(
            @RequestBody @Valid BloqueioAgendaRequestDTO dto, UriComponentsBuilder uriBuilder) {
        BloqueioAgendaResponseDTO response = service.criar(dto);
        var uri =
                uriBuilder.path("/bloqueios/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Listar bloqueios por período",
            description =
                    """
                    Retorna os bloqueios que intersectam o período, ordenados por data de início — inclusive os que
                    começam antes de `inicio` ou terminam depois de `fim`. Mesmas regras de período de `GET /aulas`:
                    obrigatório, fechado nas duas pontas e limitado a 92 dias. Período sem bloqueios retorna lista
                    vazia.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description = "Período ausente, invertido (início posterior ao fim) ou acima de 92 dias")
    })
    @GetMapping
    public ResponseEntity<List<BloqueioAgendaResponseDTO>> listarPorPeriodo(
            @Parameter(description = "Data inicial do período (inclusive)", required = true) @RequestParam
                    LocalDate inicio,
            @Parameter(description = "Data final do período (inclusive)", required = true) @RequestParam
                    LocalDate fim) {
        return ResponseEntity.ok(service.listarPorPeriodo(inicio, fim));
    }

    @Operation(summary = "Buscar bloqueio por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bloqueio encontrado"),
        @ApiResponse(responseCode = "404", description = "Bloqueio não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<BloqueioAgendaResponseDTO> buscar(
            @Parameter(description = "ID do bloqueio") @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Atualizar bloqueio de agenda",
            description = "Substituição completa: a faixa de horário é removida se `horaInicio`/`horaFim` vierem "
                    + "ausentes, voltando o bloqueio para dia inteiro. Exige perfil `ADMIN`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bloqueio atualizado com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Motivo vazio, período invertido, faixa de horário inválida ou hora informada pela metade"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN"),
        @ApiResponse(responseCode = "404", description = "Bloqueio não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<BloqueioAgendaResponseDTO> atualizar(
            @Parameter(description = "ID do bloqueio") @PathVariable Long id,
            @RequestBody @Valid BloqueioAgendaRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Excluir bloqueio de agenda",
            description = "Remove o bloqueio permanentemente. Não afeta aulas ou sessões já registradas no período — "
                    + "o bloqueio nunca as impediu. Exige perfil `ADMIN`.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Bloqueio excluído com sucesso"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN"),
        @ApiResponse(responseCode = "404", description = "Bloqueio não encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@Parameter(description = "ID do bloqueio") @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
