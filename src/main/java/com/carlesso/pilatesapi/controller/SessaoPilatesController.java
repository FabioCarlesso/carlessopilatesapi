package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.SessaoPilatesRequestDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesResponseDTO;
import com.carlesso.pilatesapi.dto.SessaoPilatesUpdateDTO;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.service.SessaoPilatesService;
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
        name = "Sessões de Pilates/Fisioterapia",
        description = "Registro e gerenciamento de sessões de Pilates e Fisioterapia")
@RestController
@RequestMapping("/sessoes")
public class SessaoPilatesController {

    private final SessaoPilatesService service;

    public SessaoPilatesController(SessaoPilatesService service) {
        this.service = service;
    }

    @Operation(
            summary = "Registrar sessão",
            description = "Registra uma nova sessão de Pilates ou Fisioterapia para um paciente.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Sessão registrada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
        @ApiResponse(
                responseCode = "404",
                description = "Paciente, profissional ou plano de tratamento não encontrado"),
        @ApiResponse(
                responseCode = "422",
                description =
                        "Paciente inativo não aceita novos registros clínicos, profissional inativo ou plano de tratamento incompatível")
    })
    @PostMapping
    public ResponseEntity<SessaoPilatesResponseDTO> criar(
            @RequestBody @Valid SessaoPilatesRequestDTO dto, UriComponentsBuilder uriBuilder) {
        SessaoPilatesResponseDTO response = service.criar(dto);
        var uri = uriBuilder.path("/sessoes/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Listar sessões por período",
            description =
                    """
                    Agenda do estúdio: todas as sessões entre `inicio` e `fim` (ambos inclusive), ordenadas por data e
                    horário — sessões sem horário vão para o fim do dia. O período é obrigatório e limitado a 92 dias,
                    e a resposta a 5000 registros. Os filtros opcionais são combináveis entre si. Por serem registro
                    clínico, sessões de pacientes inativos continuam aparecendo. Período sem registros retorna lista
                    vazia.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Período ausente, inválido (início posterior ao fim), acima de 92 dias, ou resultado acima de 5000 registros")
    })
    @GetMapping
    public ResponseEntity<List<SessaoPilatesResponseDTO>> listarPorPeriodo(
            @Parameter(description = "Data inicial do período (inclusive)", required = true) @RequestParam
                    LocalDate inicio,
            @Parameter(description = "Data final do período (inclusive)", required = true) @RequestParam LocalDate fim,
            @Parameter(description = "Filtrar pelo profissional da sessão") @RequestParam(required = false)
                    Long profissionalId,
            @Parameter(description = "Filtrar pelo paciente da sessão") @RequestParam(required = false) Long pacienteId,
            @Parameter(description = "Filtrar por tipo de sessão") @RequestParam(required = false) TipoSessao tipo,
            @Parameter(description = "Filtrar por status da sessão") @RequestParam(required = false)
                    StatusSessao status) {
        return ResponseEntity.ok(service.listarPorPeriodo(inicio, fim, profissionalId, pacienteId, tipo, status));
    }

    @Operation(summary = "Buscar sessão por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão encontrada"),
        @ApiResponse(responseCode = "404", description = "Sessão não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<SessaoPilatesResponseDTO> buscarPorId(
            @Parameter(description = "ID da sessão", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Listar sessões por paciente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessões encontradas"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<SessaoPilatesResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId));
    }

    @Operation(
            summary = "Atualizar sessão",
            description = "Atualiza os dados de uma sessão. Apenas os campos enviados serão alterados.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Sessão não encontrada")
    })
    @PutMapping("/{id}")
    public ResponseEntity<SessaoPilatesResponseDTO> atualizar(
            @Parameter(description = "ID da sessão", required = true) @PathVariable Long id,
            @RequestBody @Valid SessaoPilatesUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Marcar sessão como realizada",
            description = "Transiciona o status da sessão de AGENDADA para REALIZADA.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão marcada como realizada"),
        @ApiResponse(responseCode = "404", description = "Sessão não encontrada"),
        @ApiResponse(responseCode = "422", description = "Transição inválida (sessão não está AGENDADA)")
    })
    @PatchMapping("/{id}/realizar")
    public ResponseEntity<SessaoPilatesResponseDTO> realizar(
            @Parameter(description = "ID da sessão", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.realizar(id));
    }

    @Operation(summary = "Cancelar sessão", description = "Transiciona o status da sessão de AGENDADA para CANCELADA.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sessão cancelada"),
        @ApiResponse(responseCode = "404", description = "Sessão não encontrada"),
        @ApiResponse(responseCode = "422", description = "Transição inválida (sessão não está AGENDADA)")
    })
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<SessaoPilatesResponseDTO> cancelar(
            @Parameter(description = "ID da sessão", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.cancelar(id));
    }

    @Operation(
            summary = "Excluir sessão",
            description = "Remove permanentemente uma sessão. Use apenas para cancelamentos/erros de registro.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sessão excluída com sucesso"),
        @ApiResponse(responseCode = "404", description = "Sessão não encontrada")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "ID da sessão", required = true) @PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
