package com.carlesso.pilatesapi.security;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.AuthRegisterRequestDTO;
import com.carlesso.pilatesapi.dto.UserRequestDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.LoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        // reset in-memory rate limit history between tests
        Field historyField = LoginAttemptService.class.getDeclaredField("history");
        historyField.setAccessible(true);
        ((java.util.concurrent.ConcurrentHashMap<?, ?>) historyField.get(loginAttemptService)).clear();
    }

    @Test
    void register_deveCriarUsuarioComSenhaCriptografadaERetornarJwt() throws Exception {
        var request = new AuthRegisterRequestDTO("Maria", "maria@email.com", "senha1234");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("maria@email.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());

        User saved = userRepository.findByEmail("maria@email.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("senha1234");
        assertThat(passwordEncoder.matches("senha1234", saved.getPassword())).isTrue();
    }

    @Test
    void register_comEmailDuplicado_deveRetornar409() throws Exception {
        criarUsuario("duplicado@email.com", Role.USER);
        var request = new AuthRegisterRequestDTO("Outro", "duplicado@email.com", "senha1234");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("E-mail já cadastrado"));
    }

    @Test
    void login_valido_deveRetornarJwt() throws Exception {
        criarUsuario("login@email.com", Role.USER);
        var request = new AuthLoginRequestDTO("login@email.com", "senha1234");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@email.com"));
    }

    @Test
    void login_invalido_deveRetornar401() throws Exception {
        criarUsuario("login@email.com", Role.USER);
        var request = new AuthLoginRequestDTO("login@email.com", "errada123");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Credenciais inválidas"));
    }

    @Test
    void login_comUsuarioInativo_deveRetornar401() throws Exception {
        criarUsuario("inativo@email.com", Role.USER, false);
        var request = new AuthLoginRequestDTO("inativo@email.com", "senha1234");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_aposLimiteDefalhas_deveRetornar429() throws Exception {
        criarUsuario("brute@email.com", Role.USER);
        var senhaErrada = new AuthLoginRequestDTO("brute@email.com", "errada123");

        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            mvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(senhaErrada)));
        }

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(senhaErrada)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value("Muitas tentativas. Tente novamente em 15 minutos."));
    }

    @Test
    void login_aposSuccesso_deveResetarContador() throws Exception {
        criarUsuario("reset@email.com", Role.USER);
        var senhaErrada = new AuthLoginRequestDTO("reset@email.com", "errada123");
        var senhaCorreta = new AuthLoginRequestDTO("reset@email.com", "senha1234");

        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            mvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(senhaErrada)));
        }

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(senhaCorreta)))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(senhaErrada)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMe_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMe_comTokenValido_deveRetornarUsuarioSemSenha() throws Exception {
        User user = criarUsuario("me@email.com", Role.USER);

        mvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@email.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rotaProtegida_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/pacientes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegida_comTokenInvalido_deveRetornar401() throws Exception {
        mvc.perform(get("/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("user@email.com", Role.USER);

        mvc.perform(get("/admin/health")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_comRoleAdmin_deveRetornar200() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(get("/admin/health")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void usersListar_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("user@email.com", Role.USER);

        mvc.perform(get("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersCriar_comAdmin_deveCriarUsuarioComRoleInformada() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserRequestDTO("Financeiro", "FINANCEIRO@EMAIL.COM", "senha1234", Role.USER);

        mvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.name").value("Financeiro"))
                .andExpect(jsonPath("$.email").value("financeiro@email.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByEmail("financeiro@email.com").orElseThrow();
        assertThat(passwordEncoder.matches("senha1234", saved.getPassword())).isTrue();
    }

    @Test
    void usersAtualizar_comAdmin_deveAlterarPerfilESenha() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("perfil@email.com", Role.USER);
        var request = new UserUpdateDTO("Perfil Admin", null, "novaSenha123", Role.ADMIN);

        mvc.perform(put("/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Perfil Admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("novaSenha123", updated.getPassword())).isTrue();
    }

    @Test
    void usersAtualizar_adminAlterandoProprioRole_deveRetornar422() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserUpdateDTO(null, null, null, Role.USER);

        mvc.perform(put("/users/{id}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Não é possível alterar o próprio perfil de acesso"));
    }

    @Test
    void usersAtualizar_adminAlterandoProprioNomeSemMudarRole_devePermitir() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserUpdateDTO("Novo Nome", null, null, null);

        mvc.perform(put("/users/{id}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo Nome"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void usersAtualizar_rebaixandoAdminInativo_devePermitir() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User adminInativo = criarUsuario("admin-inativo@email.com", Role.ADMIN, false);
        var request = new UserUpdateDTO(null, null, null, Role.USER);

        mvc.perform(put("/users/{id}", adminInativo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void usersAtualizar_nomeVazio_deveRetornar400() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("user@email.com", Role.USER);
        var request = new UserUpdateDTO("", null, null, null);

        mvc.perform(put("/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usersInativar_comAdmin_deveInativarUsuario() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("inativar@email.com", Role.USER);

        mvc.perform(delete("/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        User inativado = userRepository.findById(user.getId()).orElseThrow();
        assertThat(inativado.isAtivo()).isFalse();
    }

    @Test
    void usersInativar_deveInvalidarTokenEmitidoAntesDaInativacao() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("token-inativo@email.com", Role.USER);
        String tokenEmitidoAntesDaInativacao = bearer(user);

        mvc.perform(delete("/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/dashboard/resumo")
                        .header(HttpHeaders.AUTHORIZATION, tokenEmitidoAntesDaInativacao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersInativar_adminInativandoPropraConta_deveRetornar422() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(delete("/users/{id}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Não é possível inativar a própria conta"));
    }

    @Test
    void dashboardResumo_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/dashboard/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardResumo_comTokenValido_deveRetornar200() throws Exception {
        User user = criarUsuario("dashboard@email.com", Role.USER);

        mvc.perform(get("/dashboard/resumo")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacientes").exists())
                .andExpect(jsonPath("$.profissionais").exists())
                .andExpect(jsonPath("$.pagamentos").exists())
                .andExpect(jsonPath("$.aulas").exists())
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @Test
    void cors_devePermitirOrigemDoAngular() throws Exception {
        mvc.perform(options("/pacientes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    private User criarUsuario(String email, Role role) {
        return criarUsuario(email, role, true);
    }

    private User criarUsuario(String email, Role role, boolean ativo) {
        User user = new User();
        user.setName("Usuário Teste");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("senha1234"));
        user.setRole(role);
        user.setAtivo(ativo);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
