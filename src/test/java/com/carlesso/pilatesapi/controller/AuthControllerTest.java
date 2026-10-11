package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthResponseDTO;
import com.carlesso.pilatesapi.dto.ForgotPasswordRequestDTO;
import com.carlesso.pilatesapi.dto.RefreshTokenRequestDTO;
import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.exception.UnauthorizedException;
import com.carlesso.pilatesapi.service.AuthService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.PasswordResetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void login_deveRetornar200() throws Exception {
        var request = new AuthLoginRequestDTO("maria@email.com", "senha1234");
        when(authService.login(any())).thenReturn(AuthResponseDTO.bearer("token", "refresh", maria()));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void refresh_deveRetornar200ComNovoPar() throws Exception {
        var request = new RefreshTokenRequestDTO("refresh-atual");
        when(authService.refresh(request)).thenReturn(AuthResponseDTO.bearer("novo-access", "novo-refresh", maria()));

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("novo-access"))
                .andExpect(jsonPath("$.refreshToken").value("novo-refresh"))
                .andExpect(jsonPath("$.user.email").value("maria@email.com"));
    }

    @Test
    void refresh_comTokenInvalido_deveRetornar401() throws Exception {
        var request = new RefreshTokenRequestDTO("refresh-reusado");
        when(authService.refresh(request)).thenThrow(new UnauthorizedException("Refresh token inválido ou expirado"));

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Refresh token inválido ou expirado"));
    }

    @Test
    void refresh_semToken_deveRetornar400() throws Exception {
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new RefreshTokenRequestDTO(" "))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void logout_deveRetornar204() throws Exception {
        var request = new RefreshTokenRequestDTO("refresh-atual");

        mvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout(request);
    }

    @Test
    void logout_semToken_deveRetornar400() throws Exception {
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void forgotPassword_deveRetornar200() throws Exception {
        var request = new ForgotPasswordRequestDTO("maria@email.com");

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordResetService).solicitarRedefinicao("maria@email.com");
    }

    @Test
    void forgotPassword_comEmailInexistente_aindaAssimDeveRetornar200() throws Exception {
        var request = new ForgotPasswordRequestDTO("naoexiste@email.com");

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_comEmailInvalido_deveRetornar400() throws Exception {
        var request = new ForgotPasswordRequestDTO("email-invalido");

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_aposLimiteDeSolicitacoes_deveRetornar429() throws Exception {
        var request = new ForgotPasswordRequestDTO("maria@email.com");
        doThrow(new TooManyRequestsException("Muitas solicitações. Tente novamente em 15 minutos."))
                .when(passwordResetService)
                .solicitarRedefinicao("maria@email.com");

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("Muitas solicitações. Tente novamente em 15 minutos."));
    }

    @Test
    void resetPassword_deveRetornar200() throws Exception {
        var request = new ResetPasswordRequestDTO("token-valido", "novaSenha123", "novaSenha123");

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordResetService).redefinirSenha(request);
    }

    @Test
    void resetPassword_comPayloadInvalido_deveRetornar400() throws Exception {
        var request = new ResetPasswordRequestDTO("", "curta", "curta");

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_comTokenInvalidoOuExpirado_deveRetornar422() throws Exception {
        var request = new ResetPasswordRequestDTO("token-invalido", "novaSenha123", "novaSenha123");
        doThrow(new BusinessException("Token inválido ou expirado"))
                .when(passwordResetService)
                .redefinirSenha(request);

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Token inválido ou expirado"));
    }

    private static UserResponseDTO maria() {
        return new UserResponseDTO(1L, "Maria", "maria@email.com", Role.USER, true);
    }
}
