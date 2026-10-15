package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.NotaFiscalEmitidaRequestDTO;
import com.carlesso.pilatesapi.dto.NotaFiscalEmitidaResponseDTO;
import com.carlesso.pilatesapi.service.NotaFiscalEmitidaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "NFSE", description = "Registro de notas fiscais de serviço emitidas por paciente/competência")
@RestController
@RequestMapping("/api/nfse-emitidas")
public class NotaFiscalEmitidaController {

    private final NotaFiscalEmitidaService service;

    public NotaFiscalEmitidaController(NotaFiscalEmitidaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Registrar ou atualizar NFSE emitida",
            description = "Registra a última NFSE emitida para um paciente em uma competência. "
                    + "Se já existir nota para o paciente na competência informada, ela é atualizada.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "NFSE registrada ou atualizada com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou competência fora do formato"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado"),
        @ApiResponse(
                responseCode = "422",
                description = "Valor informado inválido ou paciente inativo não aceita novos registros clínicos")
    })
    @PostMapping
    public ResponseEntity<NotaFiscalEmitidaResponseDTO> registrar(@RequestBody @Valid NotaFiscalEmitidaRequestDTO dto) {
        return ResponseEntity.ok(service.registrar(dto));
    }

    @Operation(
            summary = "Listar NFSEs emitidas de um paciente",
            description =
                    "Retorna uma página das notas fiscais emitidas registradas para o paciente. Ordenação padrão: `competencia` desc; aceita `competencia`, `dataEmissao` e `valor`, sempre com `id` como desempate.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<Page<NotaFiscalEmitidaResponseDTO>> listarPorPaciente(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long pacienteId,
            @ParameterObject @SortDefault(sort = "competencia", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.listarPorPaciente(pacienteId, pageable));
    }
}
