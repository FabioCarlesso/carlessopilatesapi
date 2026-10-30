package com.carlesso.pilatesapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.dto.RefreshTokenRequestDTO;
import com.carlesso.pilatesapi.dto.UserAlterarSenhaRequestDTO;
import com.carlesso.pilatesapi.entity.RefreshToken;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.repository.RefreshTokenRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.LoginAttemptService;
import com.carlesso.pilatesapi.service.LoginAttemptServiceTestSupport;
import com.carlesso.pilatesapi.service.RefreshTokenService;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import com.carlesso.pilatesapi.util.TokenOpaco;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends PostgresTestcontainerSupport {

    private static final String SENHA = "senha1234";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        LoginAttemptServiceTestSupport.limpar(loginAttemptService);
    }

    @Test
    void fluxoCompleto_loginRefreshReusoELogout() throws Exception {
        criarUsuario("fluxo@email.com", Role.RECEPCAO);

        JsonNode login = login("fluxo@email.com", SENHA);
        String refresh1 = login.get("refreshToken").asText();
        acessarUsersMe(login.get("accessToken").asText()).andExpect(status().isOk());

        // Refresh emite um par novo e válido, diferente do anterior.
        JsonNode rotacao = json(refresh(refresh1).andExpect(status().isOk()));
        String refresh2 = rotacao.get("refreshToken").asText();
        assertThat(refresh2).isNotEqualTo(refresh1);
        assertThat(rotacao.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(rotacao.get("user").get("email").asText()).isEqualTo("fluxo@email.com");
        acessarUsersMe(rotacao.get("accessToken").asText()).andExpect(status().isOk());

        // Logo após a rotação, reapresentar o refresh1 é retentativa de boa-fé (resposta perdida,
        // outra aba): 401, mas a sessão continua com o refresh2.
        refresh(refresh1).andExpect(status().isUnauthorized());
        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(t -> assertThat(t.getRevokedAt()).isNull());

        // Passada a tolerância, é reuso: 401 e a cadeia inteira cai, inclusive o refresh2 que ainda
        // não tinha sido usado.
        envelhecerUso(refresh1);
        refresh(refresh1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh token inválido ou expirado"));
        refresh(refresh2).andExpect(status().isUnauthorized());
        List<RefreshToken> cadeia = refreshTokenRepository.findAll();
        assertThat(cadeia).hasSize(2).allSatisfy(t -> assertThat(t.getRevokedAt())
                .isNotNull());

        // Um novo login abre outra família, que o logout revoga.
        String refresh3 = login("fluxo@email.com", SENHA).get("refreshToken").asText();
        logout(refresh3).andExpect(status().isNoContent());
        refresh(refresh3).andExpect(status().isUnauthorized());

        // Logout com uma cópia já rotacionada também derruba o sucessor.
        String refresh4 = login("fluxo@email.com", SENHA).get("refreshToken").asText();
        String refresh5 = json(refresh(refresh4).andExpect(status().isOk()))
                .get("refreshToken")
                .asText();
        logout(refresh4).andExpect(status().isNoContent());
        refresh(refresh5).andExpect(status().isUnauthorized());

        // Logout é idempotente e não revela se o token existe.
        logout(refresh3).andExpect(status().isNoContent());
        logout("token-que-nao-existe").andExpect(status().isNoContent());
    }

    @Test
    void refresh_naoDeveAfetarSessaoDeOutroLogin() throws Exception {
        criarUsuario("duas-sessoes@email.com", Role.RECEPCAO);
        String sessaoA =
                login("duas-sessoes@email.com", SENHA).get("refreshToken").asText();
        String sessaoB =
                login("duas-sessoes@email.com", SENHA).get("refreshToken").asText();

        refresh(sessaoA).andExpect(status().isOk());
        envelhecerUso(sessaoA);
        refresh(sessaoA).andExpect(status().isUnauthorized());

        refresh(sessaoB).andExpect(status().isOk());
    }

    @Test
    void refresh_aposTrocaDeSenha_deveRetornar401() throws Exception {
        criarUsuario("troca@email.com", Role.RECEPCAO);
        JsonNode login = login("troca@email.com", SENHA);
        String accessToken = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();

        mvc.perform(put("/users/me/senha")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new UserAlterarSenhaRequestDTO(SENHA, "novaSenha123", "novaSenha123"))))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
        acessarUsersMe(accessToken).andExpect(status().isUnauthorized());

        String novoRefresh =
                login("troca@email.com", "novaSenha123").get("refreshToken").asText();
        refresh(novoRefresh).andExpect(status().isOk());
    }

    @Test
    void refresh_comUsuarioInativado_deveRetornar401() throws Exception {
        User admin = criarUsuario("admin@email.com", Role.ADMIN);
        User user = criarUsuario("sera-inativado@email.com", Role.RECEPCAO);
        String refreshToken =
                login("sera-inativado@email.com", SENHA).get("refreshToken").asText();

        mvc.perform(delete("/users/" + user.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(admin)))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_comTokenExpirado_deveRetornar401() throws Exception {
        criarUsuario("expira@email.com", Role.RECEPCAO);
        String refreshToken =
                login("expira@email.com", SENHA).get("refreshToken").asText();

        RefreshToken salvo = buscar(refreshToken);
        salvo.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        refreshTokenRepository.save(salvo);

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void limpeza_deveRemoverSessoesVencidasERevogadasAntigasEPreservarAsAtivas() throws Exception {
        criarUsuario("limpeza@email.com", Role.RECEPCAO);
        String ativa = login("limpeza@email.com", SENHA).get("refreshToken").asText();
        String vencida = login("limpeza@email.com", SENHA).get("refreshToken").asText();
        String revogadaAntiga =
                login("limpeza@email.com", SENHA).get("refreshToken").asText();

        RefreshToken tokenVencido = buscar(vencida);
        tokenVencido.setFamilyExpiresAt(LocalDateTime.now().minusMinutes(1));
        tokenVencido.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(tokenVencido);
        RefreshToken tokenRevogado = buscar(revogadaAntiga);
        tokenRevogado.setRevokedAt(LocalDateTime.now().minusDays(2));
        refreshTokenRepository.save(tokenRevogado);

        assertThat(refreshTokenService.limparObsoletos()).isEqualTo(2);

        assertThat(refreshTokenRepository.findAll()).singleElement().satisfies(t -> assertThat(t.getTokenHash())
                .isEqualTo(TokenOpaco.hash(ativa)));
        refresh(ativa).andExpect(status().isOk());
    }

    @Test
    void refresh_comTokenDesconhecido_deveRetornar401() throws Exception {
        refresh("token-que-nao-existe").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshToken_naoDevePersistirOTokenEmTextoPuro() throws Exception {
        criarUsuario("hash@email.com", Role.RECEPCAO);
        String refreshToken = login("hash@email.com", SENHA).get("refreshToken").asText();

        assertThat(refreshTokenRepository.findAll()).singleElement().satisfies(t -> assertThat(t.getTokenHash())
                .isEqualTo(TokenOpaco.hash(refreshToken))
                .isNotEqualTo(refreshToken));
    }

    private RefreshToken buscar(String refreshToken) {
        return refreshTokenRepository.findAll().stream()
                .filter(t -> t.getTokenHash().equals(TokenOpaco.hash(refreshToken)))
                .findFirst()
                .orElseThrow();
    }

    /** Recua o used_at para fora da tolerância de reuso, sem esperar o relógio. */
    private void envelhecerUso(String refreshToken) {
        RefreshToken usado = buscar(refreshToken);
        usado.setUsedAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.save(usado);
    }

    private JsonNode login(String email, String senha) throws Exception {
        return json(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AuthLoginRequestDTO(email, senha))))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RefreshTokenRequestDTO(refreshToken))));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RefreshTokenRequestDTO(refreshToken))));
    }

    private ResultActions acessarUsersMe(String accessToken) throws Exception {
        return mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private User criarUsuario(String email, Role role) {
        User user = new User();
        user.setName("Usuário Teste");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(SENHA));
        user.setRoles(Set.of(role));
        user.setAtivo(true);
        return userRepository.save(user);
    }
}
