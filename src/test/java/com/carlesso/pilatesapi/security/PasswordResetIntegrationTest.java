package com.carlesso.pilatesapi.security;

import com.carlesso.pilatesapi.dto.ForgotPasswordRequestDTO;
import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.email.EmailMessage;
import com.carlesso.pilatesapi.email.EmailSender;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

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

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        Field historyField = LoginAttemptService.class.getDeclaredField("history");
        historyField.setAccessible(true);
        ((java.util.concurrent.ConcurrentHashMap<?, ?>) historyField.get(loginAttemptService)).clear();
    }

    @Test
    void fluxoCompleto_solicitarERedefinirSenha_devePermitirLoginComNovaSenha() throws Exception {
        User user = criarUsuario("recupera@email.com");
        String tokenAntigo = "Bearer " + jwtService.generateToken(user);

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ForgotPasswordRequestDTO("recupera@email.com"))))
                .andExpect(status().isOk());

        var captor = forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        String rawToken = extrairToken(captor.getValue());

        var resetRequest = new ResetPasswordRequestDTO(rawToken, "novaSenha123", "novaSenha123");
        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        User atualizado = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("novaSenha123", atualizado.getPassword())).isTrue();
        assertThat(atualizado.getTokenVersion()).isEqualTo(user.getTokenVersion() + 1);

        mvc.perform(get("/dashboard/resumo").header(HttpHeaders.AUTHORIZATION, tokenAntigo))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new com.carlesso.pilatesapi.dto.AuthLoginRequestDTO("recupera@email.com", "novaSenha123"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_comMesmoTokenUsadoDuasVezes_segundaVezDeveRetornar422() throws Exception {
        User user = criarUsuario("reuso@email.com");

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ForgotPasswordRequestDTO("reuso@email.com"))))
                .andExpect(status().isOk());

        var captor = forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        String rawToken = extrairToken(captor.getValue());
        var resetRequest = new ResetPasswordRequestDTO(rawToken, "novaSenha123", "novaSenha123");

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(resetRequest)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void resetPassword_comTokenInvalido_deveRetornar422() throws Exception {
        var resetRequest = new ResetPasswordRequestDTO("token-que-nao-existe", "novaSenha123", "novaSenha123");

        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(resetRequest)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void forgotPassword_comEmailInexistente_deveRetornar200SemEnviarEmail() throws Exception {
        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ForgotPasswordRequestDTO("naoexiste@email.com"))))
                .andExpect(status().isOk());

        org.mockito.Mockito.verifyNoInteractions(emailSender);
    }

    @Test
    void forgotPassword_aposLimiteDeSolicitacoes_deveRetornar429() throws Exception {
        criarUsuario("bloqueado@email.com");
        var request = new ForgotPasswordRequestDTO("bloqueado@email.com");

        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            mvc.perform(post("/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request)));
        }

        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    private String extrairToken(EmailMessage message) {
        Matcher matcher = Pattern.compile("token=([^\"&\\s]+)").matcher(message.htmlBody());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private User criarUsuario(String email) {
        User user = new User();
        user.setName("Usuário Teste");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("senha1234"));
        user.setRole(Role.USER);
        user.setAtivo(true);
        return userRepository.save(user);
    }
}
