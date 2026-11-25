package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.TomadorPacienteDTO;
import com.carlesso.pilatesapi.dto.TomadorRequestDTO;
import com.carlesso.pilatesapi.dto.TomadorResponseDTO;
import com.carlesso.pilatesapi.dto.TomadorUpdateDTO;
import com.carlesso.pilatesapi.service.TomadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Tomadores",
        description =
                "Quem recebe a NFSE quando não é o próprio paciente (responsável, empresa); atende vários pacientes")
@RestController
@RequestMapping("/tomadores")
public class TomadorController {

    private final TomadorService service;

    public TomadorController(TomadorService service) {
        this.service = service;
    }

    @Operation(
            summary = "Cadastrar tomador",
            description =
                    "CPF/CNPJ aceito com ou sem máscara e gravado só com dígitos (11 ou 14). Retorna 201 com Location.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tomador cadastrado"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos ou CPF/CNPJ sem 11 ou 14 dígitos"),
        @ApiResponse(responseCode = "409", description = "CPF/CNPJ já cadastrado para outro tomador")
    })
    @PostMapping
    public ResponseEntity<TomadorResponseDTO> cadastrar(
            @RequestBody @Valid TomadorRequestDTO dto, UriComponentsBuilder uriBuilder) {
        TomadorResponseDTO response = service.cadastrar(dto);
        var uri =
                uriBuilder.path("/tomadores/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(
            summary = "Listar tomadores",
            description =
                    "Filtra por nome (parcial), CPF/CNPJ (parcial, com ou sem máscara) e situação. Sem `ativo`, só"
                            + " os ativos. Cada item traz a quantidade de pacientes vinculados.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Página de tomadores")})
    @GetMapping
    public ResponseEntity<PagedModel<TomadorResponseDTO>> listar(
            @Parameter(description = "Filtro parcial por nome") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtro parcial por CPF/CNPJ") @RequestParam(required = false) String cpfCnpj,
            @Parameter(description = "Situação. Omitido: apenas ativos") @RequestParam(required = false) Boolean ativo,
            @ParameterObject @SortDefault(sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(service.listar(nome, cpfCnpj, ativo, pageable)));
    }

    @Operation(summary = "Buscar tomador por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tomador encontrado"),
        @ApiResponse(responseCode = "404", description = "Tomador não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TomadorResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Pacientes vinculados ao tomador",
            description = "Pacientes, ativos ou não, cujas notas saem em nome deste tomador, por nome.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pacientes"),
        @ApiResponse(responseCode = "404", description = "Tomador não encontrado")
    })
    @GetMapping("/{id}/pacientes")
    public ResponseEntity<List<TomadorPacienteDTO>> listarPacientes(@PathVariable Long id) {
        return ResponseEntity.ok(service.listarPacientes(id));
    }

    @Operation(
            summary = "Atualizar tomador",
            description = "Atualização parcial: ausente mantém; nos campos opcionais, vazio apaga.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tomador atualizado"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "404", description = "Tomador não encontrado"),
        @ApiResponse(
                responseCode = "409",
                description =
                        "CPF/CNPJ de outro tomador, ou registro alterado por outra operação (concurrent-modification)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<TomadorResponseDTO> atualizar(
            @PathVariable Long id, @RequestBody @Valid TomadorUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @Operation(summary = "Ativar tomador")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Tomador ativado"),
        @ApiResponse(responseCode = "404", description = "Tomador não encontrado")
    })
    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> ativar(@PathVariable Long id) {
        service.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Inativar tomador",
            description = "Soft delete. Os pacientes já vinculados continuam com o tomador; só novos vínculos são"
                    + " recusados.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Tomador inativado"),
        @ApiResponse(responseCode = "404", description = "Tomador não encontrado")
    })
    @PatchMapping("/{id}/inativar")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
