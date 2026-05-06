package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.dto.UserRequestDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(
        name = "Usuários",
        description = "Dados do usuário autenticado e gestão administrativa de usuários. O CRUD exige role ADMIN."
)
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @Operation(
            summary = "Usuário autenticado",
            description = "Requer autenticação JWT. Perfis USER e ADMIN podem acessar. Retorna dados seguros do usuário logado."
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(Authentication authentication) {
        return ResponseEntity.ok(service.buscarPorEmail(authentication.getName()));
    }

    @Operation(
            summary = "Criar usuário",
            description = "Requer role ADMIN. Cria um usuário com perfil USER ou ADMIN e nunca retorna a senha."
    )
    @PostMapping
    public ResponseEntity<UserResponseDTO> criar(
            @RequestBody @Valid UserRequestDTO dto,
            UriComponentsBuilder uriBuilder) {
        UserResponseDTO response = service.criar(dto);
        var uri = uriBuilder.path("/users/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED).location(uri).body(response);
    }

    @Operation(
            summary = "Listar usuários",
            description = "Requer role ADMIN. Lista usuários cadastrados com paginação, sem expor senhas."
    )
    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> listar(
            @ParameterObject @PageableDefault(sort = "name") Pageable pageable) {
        return ResponseEntity.ok(service.listar(pageable));
    }

    @Operation(
            summary = "Buscar usuário por ID",
            description = "Requer role ADMIN. Busca um usuário cadastrado sem expor senha."
    )
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> buscar(
            @Parameter(description = "ID do usuário", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(
            summary = "Atualizar usuário",
            description = "Requer role ADMIN. Atualiza nome, e-mail, senha e perfil de acesso USER ou ADMIN. Admin não pode alterar o próprio role nem rebaixar o último administrador ativo."
    )
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> atualizar(
            @Parameter(description = "ID do usuário", required = true) @PathVariable Long id,
            @RequestBody @Valid UserUpdateDTO dto,
            Authentication authentication) {
        return ResponseEntity.ok(service.atualizar(id, dto, authentication.getName()));
    }

    @Operation(
            summary = "Inativar usuário",
            description = "Requer role ADMIN. Inativa (soft delete) um usuário cadastrado. Admin não pode inativar a própria conta nem o último administrador ativo."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(
            @Parameter(description = "ID do usuário", required = true) @PathVariable Long id,
            Authentication authentication) {
        service.inativar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
