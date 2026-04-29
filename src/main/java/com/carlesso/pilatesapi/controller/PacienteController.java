package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.dto.PacienteResponseDTO;
import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.service.PacienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Pacientes", description = "Gerenciamento de pacientes do estúdio")
@RestController
@RequestMapping("/pacientes")
public class PacienteController {

    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    @Operation(
            summary = "Cadastrar paciente",
            description = "Registra um novo paciente no sistema. Retorna 201 com o header Location apontando para o recurso criado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paciente cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
            @ApiResponse(responseCode = "409", description = "E-mail ou CPF já cadastrado")
    })
    @PostMapping
    public ResponseEntity<PacienteResponseDTO> cadastrar(
            @RequestBody @Valid PacienteRequestDTO dto,
            UriComponentsBuilder uriBuilder) {
        PacienteResponseDTO response = service.cadastrar(dto);
        var uri = uriBuilder.path("/pacientes/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Listar e filtrar pacientes",
            description = "Retorna uma página de pacientes filtrando por nome, e-mail, CPF, telefone e status ativo/inativo. Por padrão retorna pacientes ativos. Suporta paginação e ordenação via query params (page, size, sort)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    @GetMapping
    public ResponseEntity<Page<PacienteResponseDTO>> listar(
            @Parameter(description = "Filtro parcial por nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtro parcial por e-mail") @RequestParam(required = false) String email,
            @Parameter(description = "Filtro parcial por CPF") @RequestParam(required = false) String cpf,
            @Parameter(description = "Filtro parcial por telefone") @RequestParam(required = false) String telefone,
            @Parameter(description = "Filtra por status. Quando omitido, retorna apenas ativos.") @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(service.listar(nome, email, cpf, telefone, ativo, pageable));
    }

    @Operation(
            summary = "Buscar paciente por ID",
            description = "Retorna os dados completos de um paciente pelo seu identificador único."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente encontrado"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PacienteResponseDTO> buscar(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Atualizar paciente",
            description = "Atualiza os dados de um paciente. Apenas os campos enviados no body serão alterados (atualização parcial)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PacienteResponseDTO> atualizar(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long id,
            @RequestBody @Valid PacienteUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(
            summary = "Ativar paciente",
            description = "Reativa um paciente previamente inativado, definindo ativo = true."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Paciente ativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long id) {
        service.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Inativar paciente",
            description = "Realiza soft delete do paciente, marcando-o como inativo. O registro não é removido do banco de dados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Paciente inativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado")
    })
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(
            @Parameter(description = "ID do paciente", required = true) @PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
