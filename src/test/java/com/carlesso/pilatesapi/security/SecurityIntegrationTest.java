package com.carlesso.pilatesapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.UserAlterarSenhaRequestDTO;
import com.carlesso.pilatesapi.dto.UserRequestDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.TipoProblema;
import com.carlesso.pilatesapi.repository.BloqueioAgendaRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.LoginAttemptService;
import com.carlesso.pilatesapi.service.LoginAttemptServiceTestSupport;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends PostgresTestcontainerSupport {

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

    @Autowired
    private BloqueioAgendaRepository bloqueioAgendaRepository;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        // Os testes de bloqueio abaixo criam registros via HTTP, e isso comita: sem limpar, as linhas sobrevivem no
        // container Postgres compartilhado e vazam para qualquer teste que consulte a tabela depois.
        bloqueioAgendaRepository.deleteAll();
        LoginAttemptServiceTestSupport.limpar(loginAttemptService);
    }

    @Test
    void register_publico_naoDeveExistirENaoDeveCriarUsuario() throws Exception {
        String payload =
                """
                {"name": "Intruso", "email": "intruso@email.com", "password": "senha1234"}
                """;

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());

        assertThat(userRepository.findByEmail("intruso@email.com")).isEmpty();
    }

    @Test
    void login_valido_deveRetornarJwt() throws Exception {
        criarUsuario("login@email.com", Role.RECEPCAO);
        var request = new AuthLoginRequestDTO("login@email.com", "senha1234");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("login@email.com"));
    }

    @Test
    void login_invalido_deveRetornar401() throws Exception {
        criarUsuario("login@email.com", Role.RECEPCAO);
        var request = new AuthLoginRequestDTO("login@email.com", "errada123");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
    }

    @Test
    void login_comUsuarioInativo_deveRetornar401() throws Exception {
        criarUsuario("inativo@email.com", Role.RECEPCAO, false);
        var request = new AuthLoginRequestDTO("inativo@email.com", "senha1234");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_aposLimiteDefalhas_deveRetornar429() throws Exception {
        criarUsuario("brute@email.com", Role.RECEPCAO);
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
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Tente novamente em 15 minutos."));
    }

    @Test
    void login_aposSuccesso_deveResetarContador() throws Exception {
        criarUsuario("reset@email.com", Role.RECEPCAO);
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
    void login_comEmailsDiferentesDoMesmoIp_aposLimitePorIp_deveRetornar429() throws Exception {
        criarUsuario("legitimo@email.com", Role.RECEPCAO);

        for (int i = 0; i < loginAttemptService.maxAttemptsPorIp(); i++) {
            mvc.perform(post("/auth/login")
                            .with(deIp("203.0.113.20"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    new AuthLoginRequestDTO("stuffing" + i + "@email.com", "errada123"))))
                    .andExpect(status().isUnauthorized());
        }

        // Mesmo com a senha certa de um e-mail nunca tentado, o IP já está bloqueado.
        mvc.perform(post("/auth/login")
                        .with(deIp("203.0.113.20"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthLoginRequestDTO("legitimo@email.com", "senha1234"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Tente novamente em 15 minutos."));

        // Outro IP não é afetado.
        mvc.perform(post("/auth/login")
                        .with(deIp("203.0.113.21"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthLoginRequestDTO("legitimo@email.com", "senha1234"))))
                .andExpect(status().isOk());
    }

    @Test
    void login_comXForwardedForForjadoSemProxyConfiavel_naoDeveContornarLimitePorIp() throws Exception {
        for (int i = 0; i < loginAttemptService.maxAttemptsPorIp(); i++) {
            mvc.perform(post("/auth/login")
                            .with(deIp("203.0.113.30"))
                            .header("X-Forwarded-For", "198.51.100." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    new AuthLoginRequestDTO("spoof" + i + "@email.com", "errada123"))))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/auth/login")
                        .with(deIp("203.0.113.30"))
                        .header("X-Forwarded-For", "198.51.100.250")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthLoginRequestDTO("spoof@email.com", "errada123"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void usersMe_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersMe_comTokenValido_deveRetornarUsuarioSemSenha() throws Exception {
        User user = criarUsuario("me@email.com", Role.RECEPCAO);

        mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@email.com"))
                .andExpect(jsonPath("$.roles[0]").value("RECEPCAO"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rotaProtegida_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/pacientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value(TipoProblema.NAO_AUTENTICADO.uri().toString()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/pacientes"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void avaliacoesPosturais_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/avaliacoes-posturais/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotaProtegida_comTokenInvalido_deveRetornar401() throws Exception {
        mvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("user@email.com", Role.RECEPCAO);

        mvc.perform(get("/admin/health").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value(TipoProblema.ACESSO_NEGADO.uri().toString()))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Acesso negado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void admin_comRoleAdmin_deveRetornar200() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(get("/admin/health").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void usersRoles_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/users/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersRoles_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("user@email.com", Role.RECEPCAO);

        mvc.perform(get("/users/roles").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersRoles_comAdmin_deveRetornarRolesDisponiveis() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(get("/users/roles").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.value == 'ADMIN')].label").value("Administrador"))
                .andExpect(jsonPath("$[?(@.value == 'RECEPCAO')].label").value("Recepção"))
                .andExpect(jsonPath("$[?(@.value == 'PACIENTE')]").isEmpty());
    }

    @Test
    void usersListar_ordenandoPeloCampoRoleQueDeixouDeExistir_deveRetornar400() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        // O frontend anterior à #131 ordenava por `role`; sem tratamento a PropertyReferenceException virava 500.
        mvc.perform(get("/users").param("sort", "role").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value(TipoProblema.REQUISICAO_INVALIDA.uri().toString()))
                .andExpect(jsonPath("$.detail").value("Ordenação inválida: campo 'role' não existe"));
    }

    @Test
    void usersListar_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("user@email.com", Role.RECEPCAO);

        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersCriar_comAdmin_deveCriarUsuarioComRoleInformada() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserRequestDTO("Financeiro", "FINANCEIRO@EMAIL.COM", "senha1234", Set.of(Role.RECEPCAO));

        mvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.name").value("Financeiro"))
                .andExpect(jsonPath("$.email").value("financeiro@email.com"))
                .andExpect(jsonPath("$.roles[0]").value("RECEPCAO"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByEmail("financeiro@email.com").orElseThrow();
        assertThat(passwordEncoder.matches("senha1234", saved.getPassword())).isTrue();
    }

    @Test
    void usersCriar_comVariosPapeis_deveAcumularEDevolverNaOrdemDoEnum() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserRequestDTO(
                "Fisio e Financeiro", "fisio@email.com", "senha1234", Set.of(Role.FINANCEIRO, Role.PROFISSIONAL));

        mvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.roles[1]").value("FINANCEIRO"));

        assertThat(userRepository.findByEmail("fisio@email.com").orElseThrow().getRoles())
                .containsExactlyInAnyOrder(Role.PROFISSIONAL, Role.FINANCEIRO);
    }

    @Test
    void usersCriar_semPapel_deveRetornar400() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(
                        post("/users")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name": "Sem Papel", "email": "sem@email.com", "password": "senha1234", "roles": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("roles"))
                .andExpect(jsonPath("$.campos[0].mensagem").value("informe ao menos um papel"));
    }

    @Test
    void usersCriar_comPapelReservado_deveRetornar422() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserRequestDTO("Paciente", "paciente@email.com", "senha1234", Set.of(Role.PACIENTE));

        mvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Papel indisponível para cadastro: PACIENTE"));
    }

    @Test
    void papeisAcumulados_devemSomarAsAreasDeCadaUm() throws Exception {
        User user = criarUsuario("recepcao-financeiro@email.com", Role.RECEPCAO);
        user.setRoles(Set.of(Role.RECEPCAO, Role.FINANCEIRO));
        user = userRepository.save(user);
        String token = bearer(user);

        // RECEPCAO traz a agenda, FINANCEIRO traz a NFSe; nenhum dos dois traz o prontuário.
        mvc.perform(get("/aulas/999999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/relatorios/nfse").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isNotIn(401, 403));
        mvc.perform(get("/anamneses/999999").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersAtualizar_comAdmin_deveAlterarPerfilESenha() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("perfil@email.com", Role.RECEPCAO);
        String tokenAntigo = bearer(user);
        var request = new UserUpdateDTO("Perfil Admin", null, "novaSenha123", Set.of(Role.ADMIN), null);

        mvc.perform(put("/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Perfil Admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.getRoles()).containsExactly(Role.ADMIN);
        assertThat(passwordEncoder.matches("novaSenha123", updated.getPassword()))
                .isTrue();
        assertThat(updated.getTokenVersion()).isEqualTo(user.getTokenVersion() + 1);

        mvc.perform(get("/dashboard/resumo").header(HttpHeaders.AUTHORIZATION, tokenAntigo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersAtualizar_adminAlterandoProprioRole_deveRetornar422() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserUpdateDTO(null, null, null, Set.of(Role.RECEPCAO), null);

        mvc.perform(put("/users/{id}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Não é possível alterar o próprio perfil de acesso"));
    }

    @Test
    void usersAtualizar_adminAlterandoProprioNomeSemMudarRole_devePermitir() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        var request = new UserUpdateDTO("Novo Nome", null, null, null, null);

        mvc.perform(put("/users/{id}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo Nome"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void usersAtualizar_rebaixandoAdminInativo_devePermitir() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User adminInativo = criarUsuario("admin-inativo@email.com", Role.ADMIN, false);
        var request = new UserUpdateDTO(null, null, null, Set.of(Role.RECEPCAO), null);

        mvc.perform(put("/users/{id}", adminInativo.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("RECEPCAO"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void usersAtualizar_nomeVazio_deveRetornar400() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("user@email.com", Role.RECEPCAO);
        var request = new UserUpdateDTO("", null, null, null, null);

        mvc.perform(put("/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usersInativar_comAdmin_deveInativarUsuario() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("inativar@email.com", Role.RECEPCAO);

        mvc.perform(delete("/users/{id}", user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        User inativado = userRepository.findById(user.getId()).orElseThrow();
        assertThat(inativado.isAtivo()).isFalse();
    }

    @Test
    void usersInativar_deveInvalidarTokenEmitidoAntesDaInativacao() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("token-inativo@email.com", Role.RECEPCAO);
        String tokenEmitidoAntesDaInativacao = bearer(user);

        mvc.perform(delete("/users/{id}", user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/dashboard/resumo").header(HttpHeaders.AUTHORIZATION, tokenEmitidoAntesDaInativacao))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersInativar_adminInativandoPropraConta_deveRetornar422() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(delete("/users/{id}", admin.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Não é possível inativar a própria conta"));
    }

    @Test
    void usersInativarEAtivar_semToken_deveRetornar401() throws Exception {
        mvc.perform(patch("/users/1/inativar")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/users/1/ativar")).andExpect(status().isUnauthorized());
    }

    @Test
    void usersInativarEAtivar_fluxoCompleto_loginRecusadoEDepoisLiberado() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("reativar@email.com", Role.RECEPCAO);
        String login = mapper.writeValueAsString(new AuthLoginRequestDTO("reativar@email.com", "senha1234"));

        mvc.perform(patch("/users/{id}/inativar", user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isAtivo())
                .isFalse();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/users/{id}/ativar", user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isAtivo())
                .isTrue();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void usersAtivar_usuarioJaAtivo_deveRetornar204() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("ja-ativo@email.com", Role.RECEPCAO);

        mvc.perform(patch("/users/{id}/ativar", user.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isAtivo())
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"/users/999999/inativar", "/users/999999/ativar"})
    void usersInativarEAtivar_idInexistente_deveRetornar404ProblemDetail(String rota) throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(patch(rota).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value(TipoProblema.NAO_ENCONTRADO.uri().toString()))
                .andExpect(jsonPath("$.detail").value("Usuário não encontrado: 999999"));
    }

    @Test
    void usersInativarPorPatch_adminInativandoPropriaConta_deveRetornar422ComAMensagemDoDelete() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);

        mvc.perform(patch("/users/{id}/inativar", admin.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Não é possível inativar a própria conta"));
    }

    @Test
    void alterarSenha_comUsuarioComum_deveTrocarSenhaEPermitirLoginComNovaSenha() throws Exception {
        User user = criarUsuario("trocasenha@email.com", Role.RECEPCAO);
        String tokenAntigo = bearer(user);
        var request = new UserAlterarSenhaRequestDTO("senha1234", "novaSenha123", "novaSenha123");

        mvc.perform(put("/users/me/senha")
                        .header(HttpHeaders.AUTHORIZATION, tokenAntigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        User atualizado = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("novaSenha123", atualizado.getPassword()))
                .isTrue();
        assertThat(passwordEncoder.matches("senha1234", atualizado.getPassword()))
                .isFalse();
        assertThat(atualizado.getTokenVersion()).isEqualTo(user.getTokenVersion() + 1);

        mvc.perform(get("/dashboard/resumo").header(HttpHeaders.AUTHORIZATION, tokenAntigo))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new AuthLoginRequestDTO("trocasenha@email.com", "novaSenha123"))))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new AuthLoginRequestDTO("trocasenha@email.com", "senha1234"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alterarSenha_semToken_deveRetornar401() throws Exception {
        var request = new UserAlterarSenhaRequestDTO("senha1234", "novaSenha123", "novaSenha123");

        mvc.perform(put("/users/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alterarSenha_comSenhaAtualIncorreta_deveRetornar422() throws Exception {
        User user = criarUsuario("erradasenha@email.com", Role.RECEPCAO);
        var request = new UserAlterarSenhaRequestDTO("errada1234", "novaSenha123", "novaSenha123");

        mvc.perform(put("/users/me/senha")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Senha atual incorreta"));
    }

    @Test
    void alterarSenha_comConfirmacaoDivergente_deveRetornar422() throws Exception {
        User user = criarUsuario("confirmacao@email.com", Role.RECEPCAO);
        var request = new UserAlterarSenhaRequestDTO("senha1234", "novaSenha123", "outra123Senha");

        mvc.perform(put("/users/me/senha")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Confirmação de senha não confere"));
    }

    @Test
    void alterarSenha_reutilizandoSenhaAtual_deveRetornar422() throws Exception {
        User user = criarUsuario("reutiliza@email.com", Role.RECEPCAO);
        var request = new UserAlterarSenhaRequestDTO("senha1234", "senha1234", "senha1234");

        mvc.perform(put("/users/me/senha")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A nova senha deve ser diferente da senha atual"));
    }

    @Test
    void alterarSenha_comNovaSenhaCurta_deveRetornar400() throws Exception {
        User user = criarUsuario("curta@email.com", Role.RECEPCAO);
        var request = new UserAlterarSenhaRequestDTO("senha1234", "curta", "curta");

        mvc.perform(put("/users/me/senha")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preferencias_get_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/users/me/preferencias")).andExpect(status().isUnauthorized());
    }

    @Test
    void preferencias_get_comUsuarioComum_deveRetornarPadroes() throws Exception {
        User user = criarUsuario("prefs-get@email.com", Role.RECEPCAO);

        mvc.perform(get("/users/me/preferencias").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("PT_BR"))
                .andExpect(jsonPath("$.tema").value("CLARO"))
                .andExpect(jsonPath("$.notificacoesEmail").value(true))
                .andExpect(jsonPath("$.notificacoesPush").value(false));
    }

    @Test
    void preferencias_get_comAdmin_deveRetornar200() throws Exception {
        User admin = criarUsuario("prefs-admin@email.com", Role.ADMIN);

        mvc.perform(get("/users/me/preferencias").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void preferencias_put_comUsuarioComum_devePersistirEReturnarValoresAtualizados() throws Exception {
        User user = criarUsuario("prefs-put@email.com", Role.RECEPCAO);
        String payload =
                """
                {
                  "idioma": "EN_US",
                  "tema": "ESCURO",
                  "notificacoesEmail": false,
                  "notificacoesPush": true
                }
                """;

        mvc.perform(put("/users/me/preferencias")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("EN_US"))
                .andExpect(jsonPath("$.tema").value("ESCURO"))
                .andExpect(jsonPath("$.notificacoesEmail").value(false))
                .andExpect(jsonPath("$.notificacoesPush").value(true));

        mvc.perform(get("/users/me/preferencias").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("EN_US"))
                .andExpect(jsonPath("$.tema").value("ESCURO"));
    }

    @Test
    void bloqueios_get_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/bloqueios").param("inicio", "2026-09-01").param("fim", "2026-09-30"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bloqueios_get_comUsuarioComum_deveRetornar200() throws Exception {
        User user = criarUsuario("bloqueio-leitura@email.com", Role.RECEPCAO);

        // A consulta é o que alimenta o aviso de feriado na agenda e na tela de sessão:
        // precisa funcionar para a recepção, não só para o ADMIN que cadastra.
        mvc.perform(get("/bloqueios")
                        .param("inicio", "2026-09-01")
                        .param("fim", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    void bloqueios_head_comUsuarioComum_deveSeguirOMesmoGet() throws Exception {
        User user = criarUsuario("bloqueio-head@email.com", Role.RECEPCAO);

        // O Spring MVC resolve HEAD pelo handler de GET, mas a autorização por URL não faz essa equivalência:
        // sem HttpMethod.HEAD no matcher de leitura, este HEAD cairia na regra de ADMIN e responderia 403.
        mvc.perform(head("/bloqueios")
                        .param("inicio", "2026-09-01")
                        .param("fim", "2026-09-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    void bloqueios_post_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("bloqueio-user@email.com", Role.RECEPCAO);

        mvc.perform(
                        post("/bloqueios")
                                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataInicio": "2026-09-07", "dataFim": "2026-09-07", "motivo": "Feriado"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Acesso negado"));
    }

    @Test
    void bloqueios_post_comAdmin_deveCriarBloqueio() throws Exception {
        User admin = criarUsuario("bloqueio-admin@email.com", Role.ADMIN);

        mvc.perform(
                        post("/bloqueios")
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataInicio": "2026-09-07", "dataFim": "2026-09-07", "motivo": "Feriado"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.motivo").value("Feriado"))
                .andExpect(jsonPath("$.diaInteiro").value(true));
    }

    @Test
    void bloqueios_delete_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("bloqueio-delete@email.com", Role.RECEPCAO);

        mvc.perform(delete("/bloqueios/1").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardResumo_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardResumo_comTokenValido_deveRetornar200() throws Exception {
        User user = criarUsuario("dashboard@email.com", Role.RECEPCAO);

        mvc.perform(get("/dashboard/resumo").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacientes").exists())
                .andExpect(jsonPath("$.profissionais").exists())
                .andExpect(jsonPath("$.pagamentos").exists())
                .andExpect(jsonPath("$.aulas").exists())
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @Test
    void rotaNaoMapeadaNaSeguranca_comAdmin_deveRetornar403() throws Exception {
        User admin = criarUsuario("rota@email.com", Role.ADMIN);

        // Menor privilégio: o anyRequest() é denyAll, então nem o ADMIN passa por rota que o SecurityConfig
        // não cita — controller novo precisa ganhar regra explícita antes de responder.
        mvc.perform(get("/rota-que-nao-existe").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rotaInexistenteEmRecursoExistente_comTokenValido_deveRetornar404() throws Exception {
        User user = criarUsuario("rota2@email.com", Role.RECEPCAO);

        mvc.perform(patch("/sessoes/1/inexistente").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUi_devePermanecerAcessivelComAddMappingsDesabilitado() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void cors_devePermitirOrigemDoAngular() throws Exception {
        mvc.perform(options("/pacientes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void cors_naoDevePermitirCredenciaisPorqueAutenticacaoEhBearer() throws Exception {
        mvc.perform(options("/pacientes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void cors_deveExporHeaderDeCorrelationIdParaOFrontend() throws Exception {
        mvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                org.hamcrest.Matchers.containsString("X-Request-Id")));
    }

    private static RequestPostProcessor deIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void pagamentosListar_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/pagamentos")).andExpect(status().isUnauthorized());
    }

    @Test
    void pagamentosListar_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("pagamentos-user@email.com", Role.RECEPCAO);

        mvc.perform(get("/pagamentos").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Acesso negado"));
    }

    @Test
    void pagamentosListar_headComUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("pagamentos-head@email.com", Role.RECEPCAO);

        // Sem HttpMethod.HEAD no matcher, o HEAD escaparia da regra de ADMIN e cairia na regra de /pagamentos/**.
        mvc.perform(head("/pagamentos").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pagamentosListar_comAdmin_deveRetornarPagina() throws Exception {
        User admin = criarUsuario("pagamentos-admin@email.com", Role.ADMIN);

        mvc.perform(get("/pagamentos").param("status", "VENCIDO").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void pagamentosBuscarPorId_comUsuarioSemRoleAdmin_naoDeveCairNaRegraDeAdmin() throws Exception {
        User user = criarUsuario("pagamentos-id@email.com", Role.RECEPCAO);

        // A regra de ADMIN vale só para /pagamentos exato: a busca por ID segue aberta à recepção e ao financeiro,
        // então um ID inexistente responde 404, e não 403.
        mvc.perform(get("/pagamentos/999999").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pagamentosCriar_comUsuarioSemRoleAdmin_naoDeveCairNaRegraDeAdmin() throws Exception {
        User user = criarUsuario("pagamentos-post@email.com", Role.RECEPCAO);

        mvc.perform(post("/pagamentos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cobrancas_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("cobrancas-user@email.com", Role.RECEPCAO);

        mvc.perform(get("/pagamentos/999999/cobrancas").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/pagamentos/999999/cobrancas/1").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/pagamentos/999999/cobrancas")
                                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"dataContato": "2026-01-10", "observacao": "Ligação"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Acesso negado"));
    }

    @Test
    void cobrancas_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/pagamentos/999999/cobrancas")).andExpect(status().isUnauthorized());
    }

    @Test
    void indicadoresDashboard_comUsuarioSemRoleAdmin_deveRetornar403() throws Exception {
        User user = criarUsuario("indicadores-user@email.com", Role.RECEPCAO);

        mvc.perform(get("/dashboard/indicadores").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(head("/dashboard/indicadores").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/dashboard/aulas-por-profissional").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void indicadoresDashboard_semToken_deveRetornar401() throws Exception {
        mvc.perform(get("/dashboard/indicadores")).andExpect(status().isUnauthorized());
        mvc.perform(get("/dashboard/aulas-por-profissional")).andExpect(status().isUnauthorized());
    }

    @Test
    void indicadoresDashboard_comAdmin_deveRetornar200() throws Exception {
        User admin = criarUsuario("indicadores-admin@email.com", Role.ADMIN);

        mvc.perform(get("/dashboard/indicadores").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receita.length()").value(12));
        mvc.perform(get("/dashboard/aulas-por-profissional").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void resumoDashboard_comUsuarioSemRoleAdmin_continuaLiberado() throws Exception {
        User user = criarUsuario("resumo-user@email.com", Role.RECEPCAO);

        mvc.perform(get("/dashboard/resumo").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    void relatorioFinanceiro_comFinanceiro_geraORelatorioEComRecepcaoNega() throws Exception {
        User financeiro = criarUsuario("relatorio-financeiro@email.com", Role.FINANCEIRO);
        User recepcao = criarUsuario("relatorio-recepcao@email.com", Role.RECEPCAO);

        mvc.perform(get("/api/relatorios/financeiro")
                        .param("inicio", "2020-01-01")
                        .param("fim", "2020-01-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(financeiro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receita.total").value(0))
                .andExpect(jsonPath("$.resultadoLiquido").value(0));
        mvc.perform(get("/api/relatorios/financeiro")
                        .param("inicio", "2020-01-01")
                        .param("fim", "2020-01-31")
                        .header(HttpHeaders.AUTHORIZATION, bearer(recepcao)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type")
                        .value(TipoProblema.ACESSO_NEGADO.uri().toString()));
    }

    @Test
    void cobrancas_comAdmin_pagamentoInexistente_deveRetornar404() throws Exception {
        User admin = criarUsuario("cobrancas-admin@email.com", Role.ADMIN);

        mvc.perform(get("/pagamentos/999999/cobrancas").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "GET    | /anamneses/999999                              | PROFISSIONAL",
                "POST   | /anamneses                                     | PROFISSIONAL",
                "GET    | /avaliacoes-fisioterapeuticas/999999           | PROFISSIONAL",
                "GET    | /avaliacoes-posturais/999999                   | PROFISSIONAL",
                "GET    | /evolucoes-sessao/999999                       | PROFISSIONAL",
                "GET    | /reavaliacoes/999999                           | PROFISSIONAL",
                "GET    | /planos-tratamento/999999                      | PROFISSIONAL",
                "GET    | /pacientes                                     | PROFISSIONAL RECEPCAO FINANCEIRO",
                "POST   | /pacientes                                     | PROFISSIONAL RECEPCAO FINANCEIRO",
                "GET    | /aulas/999999                                  | PROFISSIONAL RECEPCAO",
                "GET    | /sessoes                                       | PROFISSIONAL RECEPCAO",
                "GET    | /lista-espera                                  | PROFISSIONAL RECEPCAO",
                "GET    | /bloqueios/999999                              | PROFISSIONAL RECEPCAO FINANCEIRO",
                "GET    | /planos/999999                                 | RECEPCAO FINANCEIRO",
                "POST   | /pagamentos                                    | RECEPCAO FINANCEIRO",
                "GET    | /pagamentos/999999                             | RECEPCAO FINANCEIRO",
                "GET    | /api/nfse-emitidas/paciente/999999             | FINANCEIRO",
                "GET    | /api/relatorios/nfse                           | FINANCEIRO",
                "GET    | /api/relatorios/financeiro                     | FINANCEIRO",
                "GET    | /api/relatorios/financeiro/pdf                 | FINANCEIRO",
                "GET    | /api/relatorios/financeiro/xlsx                | FINANCEIRO",
                "GET    | /profissionais                                 | PROFISSIONAL RECEPCAO FINANCEIRO",
                "GET    | /profissionais/999999                          | PROFISSIONAL RECEPCAO FINANCEIRO",
                "GET    | /profissionais/999999/relatorio-pagamento      | FINANCEIRO",
                "GET    | /profissionais/999999/relatorio-pagamento/pdf  | FINANCEIRO",
                "PUT    | /profissionais/999999                          | ",
                "GET    | /dashboard/resumo                              | PROFISSIONAL RECEPCAO FINANCEIRO",
                "GET    | /users/me                                      | PROFISSIONAL RECEPCAO FINANCEIRO PACIENTE",
                "PATCH  | /users/999999/inativar                         | ",
                "PATCH  | /users/999999/ativar                           | ",
            })
    void matrizDePapeis_deveLiberarSoOsPapeisDaArea(String metodo, String rota, String papeisLiberados)
            throws Exception {
        // ADMIN entra em todas as áreas; os demais só nas listadas. "Liberado" é não cair em 401/403 —
        // o status final (200, 400, 404) depende do payload vazio e do ID inexistente, não da autorização.
        Set<String> liberados = new HashSet<>(Set.of("ADMIN"));
        if (papeisLiberados != null) {
            liberados.addAll(Arrays.asList(papeisLiberados.trim().split("\\s+")));
        }

        for (Role role : Role.values()) {
            User user = criarUsuario(role.name().toLowerCase() + "@email.com", role);
            int status = mvc.perform(request(HttpMethod.valueOf(metodo), rota)
                            .header(HttpHeaders.AUTHORIZATION, bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            if (liberados.contains(role.name())) {
                assertThat(status).as("%s em %s %s", role, metodo, rota).isNotIn(401, 403);
            } else {
                assertThat(status).as("%s em %s %s", role, metodo, rota).isEqualTo(403);
            }
        }
    }

    private User criarUsuario(String email, Role role) {
        return criarUsuario(email, role, true);
    }

    private User criarUsuario(String email, Role role, boolean ativo) {
        User user = new User();
        user.setName("Usuário Teste");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("senha1234"));
        user.setRoles(Set.of(role));
        user.setAtivo(ativo);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
