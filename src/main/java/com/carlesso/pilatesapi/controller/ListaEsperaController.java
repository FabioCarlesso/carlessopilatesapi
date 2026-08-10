package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ListaEsperaRequestDTO;
import com.carlesso.pilatesapi.dto.ListaEsperaResponseDTO;
import com.carlesso.pilatesapi.service.ListaEsperaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Lista de espera",
        description = "Interessados em entrar num dia da semana e faixa de horário lotados, na ordem de chegada. "
                + "A recepção consulta a fila quando um cancelamento abre vaga")
@RestController
@RequestMapping("/lista-espera")
public class ListaEsperaController {

    private final ListaEsperaService service;

    public ListaEsperaController(ListaEsperaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Inscrever paciente na lista de espera",
            description =
                    """
                    Registra o interesse do paciente num dia da semana e faixa de horário. A posição na fila é a
                    ordem de chegada: `dataEntrada` é sempre definida pelo servidor e um valor enviado no corpo é
                    ignorado.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Entrada criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Campos obrigatórios ausentes ou faixa de horário inválida"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado"),
        @ApiResponse(responseCode = "409", description = "Paciente já inscrito nesse mesmo dia e faixa de horário"),
        @ApiResponse(responseCode = "422", description = "Paciente inativo")
    })
    @PostMapping
    public ResponseEntity<ListaEsperaResponseDTO> criar(
            @RequestBody @Valid ListaEsperaRequestDTO dto, UriComponentsBuilder uriBuilder) {
        ListaEsperaResponseDTO response = service.criar(dto);
        var uri = uriBuilder
                .path("/lista-espera/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Listar interessados",
            description =
                    """
                    Retorna a fila ativa ordenada por `dataEntrada` crescente — a ordem de chegada. Todos os filtros
                    são opcionais; a faixa horária casa por **interseção**, então uma busca por 08:00–09:00 encontra
                    quem se inscreveu para 07:30–09:30. `horaInicio` e `horaFim` só valem juntas.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Faixa de horário informada pela metade ou invertida")
    })
    @GetMapping
    public ResponseEntity<List<ListaEsperaResponseDTO>> listar(
            @Parameter(description = "Dia da semana, em inglês maiúsculo (ex.: `MONDAY`)")
                    @RequestParam(required = false)
                    DayOfWeek diaSemana,
            @Parameter(description = "Início da faixa consultada (`HH:mm`)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
                    LocalTime horaInicio,
            @Parameter(description = "Fim da faixa consultada (`HH:mm`)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
                    LocalTime horaFim,
            @Parameter(description = "ID do paciente") @RequestParam(required = false) Long pacienteId) {
        return ResponseEntity.ok(service.listar(diaSemana, horaInicio, horaFim, pacienteId));
    }

    @Operation(
            summary = "Buscar entrada por ID",
            description = "Recupera a entrada apontada pelo `Location` devolvido na criação. Entrada já removida "
                    + "responde `404`, como na listagem.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entrada encontrada"),
        @ApiResponse(responseCode = "404", description = "Entrada não encontrada ou já removida")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ListaEsperaResponseDTO> buscar(
            @Parameter(description = "ID da entrada") @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Remover da lista de espera",
            description = "Tira a entrada da fila. A remoção é lógica: a entrada some da listagem, mas o registro de "
                    + "quem esperou é preservado para histórico.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Entrada removida com sucesso"),
        @ApiResponse(responseCode = "404", description = "Entrada não encontrada ou já removida")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@Parameter(description = "ID da entrada") @PathVariable Long id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
