package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
import com.carlesso.pilatesapi.dto.ForgotPasswordRequestDTO;
import com.carlesso.pilatesapi.dto.RefreshTokenRequestDTO;
import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.security.ClientIpResolver;
import com.carlesso.pilatesapi.service.AuthService;
import com.carlesso.pilatesapi.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Autenticação",
        description = "Login, renovação de sessão e recuperação de senha com JWT. Use o accessToken retornado no botão "
                + "Authorize do Swagger UI e o refreshToken em POST /auth/refresh quando ele expirar. "
                + "A criação de usuários é restrita a administradores em POST /users.")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            AuthService authService, PasswordResetService passwordResetService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(
            summary = "Login",
            description =
                    "Endpoint público. Valida e-mail/senha e retorna accessToken (curta duração) para uso no Authorize "
                            + "do Swagger UI e refreshToken para renovar a sessão em POST /auth/refresh.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login realizado"),
        @ApiResponse(responseCode = "400", description = "Payload inválido"),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas"),
        @ApiResponse(
                responseCode = "429",
                description = "Muitas tentativas falhas para o e-mail informado ou a partir do mesmo IP")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @RequestBody @Valid AuthLoginRequestDTO dto, HttpServletRequest request) {
        return ResponseEntity.ok(authService.login(dto, clientIpResolver.resolver(request)));
    }

    @Operation(
            summary = "Renovar sessão",
            description =
                    "Endpoint público. Troca um refreshToken válido por um novo par accessToken + refreshToken; o "
                            + "refreshToken apresentado deixa de valer (rotação). Reapresentar um refreshToken já "
                            + "rotacionado revoga toda a sessão originada no mesmo login.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Novo par de tokens emitido"),
        @ApiResponse(responseCode = "400", description = "Payload inválido"),
        @ApiResponse(
                responseCode = "401",
                description =
                        "Refresh token inválido, expirado, revogado, já utilizado ou emitido antes da última troca de senha")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        return ResponseEntity.ok(authService.refresh(dto));
    }

    @Operation(
            summary = "Logout",
            description = "Endpoint público. Revoga o refreshToken informado. Idempotente: token desconhecido ou já "
                    + "revogado também retorna 204. O accessToken em uso continua válido até expirar.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Refresh token revogado"),
        @ApiResponse(responseCode = "400", description = "Payload inválido")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenRequestDTO dto) {
        authService.logout(dto);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Esqueci minha senha",
            description =
                    "Endpoint público. Envia um e-mail com link de redefinição quando o e-mail informado pertencer a um usuário ativo. "
                            + "Sempre retorna 200 com uma mensagem genérica, independentemente de o e-mail existir, para evitar enumeração de usuários.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitação processada"),
        @ApiResponse(responseCode = "400", description = "Payload inválido"),
        @ApiResponse(
                responseCode = "429",
                description = "Muitas solicitações para o e-mail informado ou a partir do mesmo IP")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @RequestBody @Valid ForgotPasswordRequestDTO dto, HttpServletRequest request) {
        passwordResetService.solicitarRedefinicao(dto.email(), clientIpResolver.resolver(request));
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Redefinir senha",
            description =
                    "Endpoint público. Redefine a senha a partir de um token de redefinição válido, não expirado e ainda não utilizado. "
                            + "Access e refresh tokens emitidos antes da redefinição deixam de valer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Senha redefinida com sucesso"),
        @ApiResponse(responseCode = "400", description = "Payload inválido"),
        @ApiResponse(
                responseCode = "422",
                description = "Token inválido, expirado, já utilizado ou confirmação de senha não confere")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO dto) {
        passwordResetService.redefinirSenha(dto);
        return ResponseEntity.ok().build();
    }
}
