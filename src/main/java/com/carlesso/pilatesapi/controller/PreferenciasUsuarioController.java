package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.PreferenciasUsuarioRequestDTO;
import com.carlesso.pilatesapi.dto.PreferenciasUsuarioResponseDTO;
import com.carlesso.pilatesapi.service.PreferenciasUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Preferências do usuário",
        description =
                "Consulta e atualização das preferências pessoais do usuário autenticado (idioma, tema e notificações).")
@RestController
@RequestMapping("/users/me/preferencias")
public class PreferenciasUsuarioController {

    private final PreferenciasUsuarioService service;

    public PreferenciasUsuarioController(PreferenciasUsuarioService service) {
        this.service = service;
    }

    @Operation(
            summary = "Consultar preferências do usuário autenticado",
            description =
                    "Requer autenticação JWT. Retorna as preferências configuradas; usuário sem preferências salvas recebe os valores padrão.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferências retornadas com sucesso"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @GetMapping
    public ResponseEntity<PreferenciasUsuarioResponseDTO> consultar(Authentication authentication) {
        return ResponseEntity.ok(service.buscarPorEmail(authentication.getName()));
    }

    @Operation(
            summary = "Atualizar preferências do usuário autenticado",
            description =
                    "Requer autenticação JWT. Atualiza idioma, tema e preferências de notificação do usuário logado.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferências atualizadas com sucesso"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Payload inválido (idioma/tema fora dos valores aceitos ou campos obrigatórios ausentes)"),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
        @ApiResponse(responseCode = "404", description = "Usuário do token não encontrado")
    })
    @PutMapping
    public ResponseEntity<PreferenciasUsuarioResponseDTO> atualizar(
            @RequestBody @Valid PreferenciasUsuarioRequestDTO dto, Authentication authentication) {
        return ResponseEntity.ok(service.atualizarPorEmail(authentication.getName(), dto));
    }
}
