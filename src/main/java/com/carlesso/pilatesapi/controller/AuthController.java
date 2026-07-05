package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthRegisterRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
import com.carlesso.pilatesapi.dto.ForgotPasswordRequestDTO;
import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.service.AuthService;
import com.carlesso.pilatesapi.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Autenticação",
        description = "Registro e login com JWT. Use o accessToken retornado no botão Authorize do Swagger UI."
)
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(
            summary = "Registrar usuário",
            description = "Endpoint público. Cria uma conta com role USER, salva a senha com BCrypt e retorna um JWT."
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody @Valid AuthRegisterRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(dto));
    }

    @Operation(
            summary = "Login",
            description = "Endpoint público. Valida e-mail/senha e retorna accessToken para uso no Authorize do Swagger UI."
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid AuthLoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @Operation(
            summary = "Esqueci minha senha",
            description = "Endpoint público. Envia um e-mail com link de redefinição quando o e-mail informado pertencer a um usuário ativo. " +
                    "Sempre retorna 200 com uma mensagem genérica, independentemente de o e-mail existir, para evitar enumeração de usuários."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação processada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido"),
            @ApiResponse(responseCode = "429", description = "Muitas solicitações para o e-mail informado")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO dto) {
        passwordResetService.solicitarRedefinicao(dto.email());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Redefinir senha",
            description = "Endpoint público. Redefine a senha a partir de um token de redefinição válido, não expirado e ainda não utilizado. " +
                    "Tokens emitidos antes da redefinição deixam de autorizar rotas protegidas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Senha redefinida com sucesso"),
            @ApiResponse(responseCode = "400", description = "Payload inválido"),
            @ApiResponse(responseCode = "422", description = "Token inválido, expirado, já utilizado ou confirmação de senha não confere")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO dto) {
        passwordResetService.redefinirSenha(dto);
        return ResponseEntity.ok().build();
    }
}
