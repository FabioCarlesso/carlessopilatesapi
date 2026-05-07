package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RoleResponseDTO;
import com.carlesso.pilatesapi.dto.UserRequestDTO;
import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService service;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper mapper;

    private UserResponseDTO response() {
        return new UserResponseDTO(1L, "João Silva", "joao@email.com", Role.USER, true);
    }

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin@email.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void criar_deveRetornar201ComLocation() throws Exception {
        var request = new UserRequestDTO("João Silva", "joao@email.com", "senha1234", Role.USER);
        when(service.criar(any())).thenReturn(response());

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("joao@email.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void criar_comEmailDuplicado_deveRetornar409() throws Exception {
        var request = new UserRequestDTO("João Silva", "joao@email.com", "senha1234", Role.USER);
        when(service.criar(any())).thenThrow(new ConflictException("E-mail já cadastrado"));

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("E-mail já cadastrado"));
    }

    @Test
    // Security filters are disabled via @AutoConfigureMockMvc(addFilters = false); auth is covered by SecurityIntegrationTest
    void listarRoles_deveRetornar200ComTodasAsRoles() throws Exception {
        var roles = List.of(
                new RoleResponseDTO("ADMIN", "Administrador"),
                new RoleResponseDTO("USER", "Usuário")
        );
        when(service.listarRoles()).thenReturn(roles);

        mvc.perform(get("/users/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(roles.size()))
                .andExpect(jsonPath("$[?(@.value == 'ADMIN')].label").value("Administrador"))
                .andExpect(jsonPath("$[?(@.value == 'USER')].label").value("Usuário"));
    }

    @Test
    void listar_deveRetornar200ComPagina() throws Exception {
        var page = new PageImpl<>(List.of(response()), PageRequest.of(0, 10), 1);
        when(service.listar(any())).thenReturn(page);

        mvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("joao@email.com"))
                .andExpect(jsonPath("$.content[0].ativo").value(true));
    }

    @Test
    void buscar_comIdExistente_deveRetornar200() throws Exception {
        when(service.buscarPorId(1L)).thenReturn(response());

        mvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void buscar_comIdInexistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(anyLong())).thenThrow(new ResourceNotFoundException("Usuário não encontrado: 99"));

        mvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Usuário não encontrado: 99"));
    }

    @Test
    void atualizar_comDadosValidos_deveRetornar200() throws Exception {
        var dto = new UserUpdateDTO("Novo Nome", null, null, null);
        var updated = new UserResponseDTO(1L, "Novo Nome", "joao@email.com", Role.USER, true);
        when(service.atualizar(eq(1L), any(), anyString())).thenReturn(updated);

        mvc.perform(put("/users/1")
                        .principal(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo Nome"));
    }

    @Test
    void atualizar_adminAlterandoProprioRole_deveRetornar422() throws Exception {
        var dto = new UserUpdateDTO(null, null, null, Role.USER);
        when(service.atualizar(eq(1L), any(), anyString()))
                .thenThrow(new BusinessException("Não é possível alterar o próprio perfil de acesso"));

        mvc.perform(put("/users/1")
                        .principal(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Não é possível alterar o próprio perfil de acesso"));
    }

    @Test
    void inativar_comIdValido_deveRetornar204() throws Exception {
        doNothing().when(service).inativar(eq(1L), anyString());

        mvc.perform(delete("/users/1")
                        .principal(adminAuth()))
                .andExpect(status().isNoContent());

        verify(service).inativar(eq(1L), anyString());
    }

    @Test
    void inativar_contaPropriaAdmin_deveRetornar422() throws Exception {
        doThrow(new BusinessException("Não é possível inativar a própria conta"))
                .when(service).inativar(eq(1L), anyString());

        mvc.perform(delete("/users/1")
                        .principal(adminAuth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Não é possível inativar a própria conta"));
    }

    @Test
    void inativar_comIdInexistente_deveRetornar404() throws Exception {
        doThrow(new ResourceNotFoundException("Usuário não encontrado: 99"))
                .when(service).inativar(eq(99L), anyString());

        mvc.perform(delete("/users/99")
                        .principal(adminAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Usuário não encontrado: 99"));
    }

    @Test
    void inativar_ultimoAdmin_deveRetornar422() throws Exception {
        doThrow(new BusinessException("Não é possível inativar o último administrador ativo"))
                .when(service).inativar(eq(2L), anyString());

        mvc.perform(delete("/users/2")
                        .principal(adminAuth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Não é possível inativar o último administrador ativo"));
    }

    @Test
    void atualizar_rebaixandoUltimoAdmin_deveRetornar422() throws Exception {
        var dto = new UserUpdateDTO(null, null, null, Role.USER);
        when(service.atualizar(eq(2L), any(), anyString()))
                .thenThrow(new BusinessException("Não é possível rebaixar o último administrador ativo"));

        mvc.perform(put("/users/2")
                        .principal(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Não é possível rebaixar o último administrador ativo"));
    }
}
