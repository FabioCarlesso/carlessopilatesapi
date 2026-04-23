package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.service.ProfissionalService;
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

@Tag(name = "Profissionais", description = "Gerenciamento de profissionais do estúdio")
@RestController
@RequestMapping("/profissionais")
public class ProfissionalController {

    private final ProfissionalService service;

    public ProfissionalController(ProfissionalService service) {
        this.service = service;
    }

    @Operation(summary = "Cadastrar profissional", description = "Registra um novo profissional no sistema. Retorna 201 com o header Location apontando para o recurso criado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profissional cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou campos obrigatórios ausentes"),
            @ApiResponse(responseCode = "409", description = "E-mail ou CPF já cadastrado")
    })
    @PostMapping
    public ResponseEntity<ProfissionalResponseDTO> cadastrar(@RequestBody @Valid ProfissionalRequestDTO dto,
                                                             UriComponentsBuilder uriBuilder) {
        ProfissionalResponseDTO response = service.cadastrar(dto);
        var uri = uriBuilder.path("/profissionais/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Listar profissionais ativos", description = "Retorna uma página de profissionais com status ativo. Suporta paginação e ordenação via query params.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    @GetMapping
    public ResponseEntity<Page<ProfissionalResponseDTO>> listar(
            @ParameterObject @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(service.listar(pageable));
    }

    @Operation(summary = "Buscar profissional por ID", description = "Retorna os dados completos de um profissional pelo seu identificador único.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissional encontrado"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProfissionalResponseDTO> buscar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Atualizar profissional", description = "Atualiza os dados de um profissional. Apenas os campos enviados serão alterados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissional atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProfissionalResponseDTO> atualizar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id,
            @RequestBody @Valid ProfissionalUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(summary = "Ativar profissional", description = "Reativa um profissional previamente inativado, definindo ativo = true.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profissional ativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id) {
        service.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Inativar profissional", description = "Realiza soft delete do profissional, marcando-o como inativo. O registro não é removido do banco.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profissional inativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado")
    })
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(
            @Parameter(description = "ID do profissional", required = true) @PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
