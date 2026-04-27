package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthRegisterRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
import com.carlesso.pilatesapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
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
}
