package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.VerificacaoDisponibilidadeDTO;
import com.carlesso.pilatesapi.service.DisponibilidadeProfissionalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Disponibilidade dos profissionais",
        description = "Faixas semanais de atendimento de cada profissional. A disponibilidade é informativa: "
                + "alimenta o aviso de horário fora do expediente, não impede gerar, atribuir ou remarcar aula")
@RestController
public class DisponibilidadeProfissionalController {

    private final DisponibilidadeProfissionalService service;

    public DisponibilidadeProfissionalController(DisponibilidadeProfissionalService service) {
        this.service = service;
    }

    @Operation(
            summary = "Listar disponibilidade do profissional",
            description = "Faixas ordenadas por dia da semana (segunda a domingo) e hora de início. Profissional "
                    + "sem faixas retorna lista vazia.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping("/profissionais/{id}/disponibilidades")
    public ResponseEntity<List<DisponibilidadeProfissionalResponseDTO>> listar(
            @Parameter(description = "ID do profissional") @PathVariable Long id) {
        return ResponseEntity.ok(service.listar(id));
    }

    @Operation(
            summary = "Cadastrar faixa de disponibilidade",
            description =
                    """
                    Registra uma faixa semanal `[horaInicio, horaFim)` em que o profissional atende. Faixas do mesmo
                    dia não podem se sobrepor, mas podem encostar (12:00–14:00 e 14:00–19:00). Exige perfil `ADMIN`.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Faixa criada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente ou em formato inválido"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN"),
        @ApiResponse(responseCode = "404", description = "Profissional não encontrado"),
        @ApiResponse(responseCode = "409", description = "Faixa sobreposta a outra do mesmo dia"),
        @ApiResponse(responseCode = "422", description = "horaInicio não anterior a horaFim, ou profissional inativo")
    })
    @PostMapping("/profissionais/{id}/disponibilidades")
    public ResponseEntity<DisponibilidadeProfissionalResponseDTO> criar(
            @Parameter(description = "ID do profissional") @PathVariable Long id,
            @RequestBody @Valid DisponibilidadeProfissionalRequestDTO dto,
            UriComponentsBuilder uriBuilder) {
        DisponibilidadeProfissionalResponseDTO response = service.criar(id, dto);
        var uri = uriBuilder
                .path("/disponibilidades/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Verificar se um horário está na disponibilidade",
            description =
                    """
                    Responde se `horario`, no dia da semana de `data`, cai dentro de alguma faixa do profissional —
                    início inclusivo, fim exclusivo. Profissional sem nenhuma faixa responde `true`: sem configuração,
                    não há aviso. Horários são locais do estúdio, sem conversão de fuso.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Verificação feita"),
        @ApiResponse(responseCode = "400", description = "data ou horario ausente ou em formato inválido"),
        @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping("/profissionais/{id}/disponibilidades/verificar")
    public ResponseEntity<VerificacaoDisponibilidadeDTO> verificar(
            @Parameter(description = "ID do profissional") @PathVariable Long id,
            @Parameter(description = "Data consultada (`AAAA-MM-DD`)", required = true)
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate data,
            @Parameter(description = "Horário consultado (`HH:mm`)", required = true)
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
                    LocalTime horario) {
        return ResponseEntity.ok(service.verificar(id, data, horario));
    }

    @Operation(
            summary = "Atualizar faixa de disponibilidade",
            description = "Substituição completa da faixa. A própria faixa não conta como conflito. Exige perfil "
                    + "`ADMIN`.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Faixa atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente ou em formato inválido"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN"),
        @ApiResponse(responseCode = "404", description = "Faixa não encontrada"),
        @ApiResponse(
                responseCode = "409",
                description = "Faixa sobreposta a outra do mesmo dia, ou version divergente (concurrent-modification)"),
        @ApiResponse(responseCode = "422", description = "horaInicio não anterior a horaFim, ou profissional inativo")
    })
    @PutMapping("/disponibilidades/{id}")
    public ResponseEntity<DisponibilidadeProfissionalResponseDTO> atualizar(
            @Parameter(description = "ID da faixa") @PathVariable Long id,
            @RequestBody @Valid DisponibilidadeProfissionalRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Excluir faixa de disponibilidade",
            description = "Remove a faixa permanentemente: é configuração, não histórico. Exige perfil `ADMIN`.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Faixa excluída com sucesso"),
        @ApiResponse(responseCode = "403", description = "Usuário sem perfil ADMIN"),
        @ApiResponse(responseCode = "404", description = "Faixa não encontrada")
    })
    @DeleteMapping("/disponibilidades/{id}")
    public ResponseEntity<Void> excluir(@Parameter(description = "ID da faixa") @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
