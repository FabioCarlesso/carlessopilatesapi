package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ResetPasswordRequestDTO;
import com.carlesso.pilatesapi.email.EmailMessage;
import com.carlesso.pilatesapi.email.EmailSender;
import com.carlesso.pilatesapi.email.EmailTemplateService;
import com.carlesso.pilatesapi.entity.PasswordResetToken;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import com.carlesso.pilatesapi.repository.PasswordResetTokenRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String RESET_PASSWORD_URL = "https://app.carlessopilates.com.br/resetar-senha";
    private static final long TOKEN_TTL_MINUTOS = 30;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private EmailSender emailSender;

    @Mock
    private EmailTemplateService emailTemplateService;

    private LoginAttemptService loginAttemptService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService();
        service = novoService(TOKEN_TTL_MINUTOS);
    }

    private PasswordResetService novoService(long tokenTtlMinutos) {
        return new PasswordResetService(
                userRepository, tokenRepository, userService, emailSender, emailTemplateService,
                loginAttemptService, new BusinessMetrics(new SimpleMeterRegistry()),
                RESET_PASSWORD_URL, tokenTtlMinutos);
    }

    private User usuario(Long id, String email, boolean ativo) {
        User u = new User();
        u.setName("Maria");
        u.setEmail(email);
        u.setPassword("hash-atual");
        u.setRole(Role.USER);
        u.setAtivo(ativo);
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    @Test
    void solicitarRedefinicao_comEmailExistenteEAtivo_deveGerarTokenEEnviarEmail() {
        User user = usuario(1L, "maria@email.com", true);
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));
        when(emailTemplateService.criarEmailRedefinicaoSenha(any(), anyString()))
                .thenReturn(new EmailMessage("maria@email.com", "assunto", "html", "texto"));

        service.solicitarRedefinicao("maria@email.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        verify(emailSender).send(any(EmailMessage.class));
    }

    @Test
    void solicitarRedefinicao_deveInvalidarTokensAnterioresAntesDeGerarNovo() {
        User user = usuario(1L, "maria@email.com", true);
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));
        when(emailTemplateService.criarEmailRedefinicaoSenha(any(), anyString()))
                .thenReturn(new EmailMessage("maria@email.com", "assunto", "html", "texto"));

        service.solicitarRedefinicao("maria@email.com");

        verify(tokenRepository).invalidarTokensAtivos(eq(user), any(LocalDateTime.class));
    }

    @Test
    void solicitarRedefinicao_deveRespeitarTtlConfiguradoViaProperty() {
        PasswordResetService customService = novoService(45);
        User user = usuario(1L, "maria@email.com", true);
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(user));
        when(emailTemplateService.criarEmailRedefinicaoSenha(any(), anyString()))
                .thenReturn(new EmailMessage("maria@email.com", "assunto", "html", "texto"));

        customService.solicitarRedefinicao("maria@email.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
                .isAfter(LocalDateTime.now().plusMinutes(44))
                .isBefore(LocalDateTime.now().plusMinutes(46));
    }

    @Test
    void solicitarRedefinicao_comEmailInexistente_naoDeveEnviarEmailNemLancarErro() {
        when(userRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        service.solicitarRedefinicao("naoexiste@email.com");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailSender, emailTemplateService);
    }

    @Test
    void solicitarRedefinicao_comUsuarioInativo_naoDeveEnviarEmail() {
        User inativo = usuario(1L, "inativo@email.com", false);
        when(userRepository.findByEmail("inativo@email.com")).thenReturn(Optional.of(inativo));

        service.solicitarRedefinicao("inativo@email.com");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailSender, emailTemplateService);
    }

    @Test
    void solicitarRedefinicao_emailNormalizadoParaLowercase() {
        when(userRepository.findByEmail("minusculo@email.com")).thenReturn(Optional.empty());

        service.solicitarRedefinicao("MINUSCULO@EMAIL.COM");

        verify(userRepository).findByEmail("minusculo@email.com");
    }

    @Test
    void solicitarRedefinicao_aposLimiteDeSolicitacoes_deveLancarTooManyRequests() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            when(userRepository.findByEmail("bloqueado@email.com")).thenReturn(Optional.empty());
            service.solicitarRedefinicao("bloqueado@email.com");
        }

        assertThatThrownBy(() -> service.solicitarRedefinicao("bloqueado@email.com"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(userRepository, times(LoginAttemptService.MAX_ATTEMPTS)).findByEmail("bloqueado@email.com");
    }

    @Test
    void redefinirSenha_comTokenValido_deveAtualizarSenhaEMarcarTokenUsado() {
        User user = usuario(1L, "maria@email.com", true);
        PasswordResetToken token = tokenValido(user);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        doAnswer(invocation -> {
            User alvo = invocation.getArgument(0);
            alvo.setPassword("hash-nova");
            alvo.incrementarTokenVersion();
            return null;
        }).when(userService).aplicarNovaSenha(eq(user), eq("novaSenha123"));
        var dto = new ResetPasswordRequestDTO("token-bruto", "novaSenha123", "novaSenha123");

        service.redefinirSenha(dto);

        verify(userService).aplicarNovaSenha(user, "novaSenha123");
        assertThat(user.getPassword()).isEqualTo("hash-nova");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void redefinirSenha_comConfirmacaoDivergente_deveLancarBusinessException() {
        var dto = new ResetPasswordRequestDTO("token-bruto", "novaSenha123", "outraSenha123");

        assertThatThrownBy(() -> service.redefinirSenha(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Confirmação de senha não confere");
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void redefinirSenha_comTokenInexistente_deveLancarBusinessException() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        var dto = new ResetPasswordRequestDTO("token-invalido", "novaSenha123", "novaSenha123");

        assertThatThrownBy(() -> service.redefinirSenha(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido ou expirado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void redefinirSenha_comTokenExpirado_deveLancarBusinessException() {
        User user = usuario(1L, "maria@email.com", true);
        PasswordResetToken token = tokenValido(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        var dto = new ResetPasswordRequestDTO("token-expirado", "novaSenha123", "novaSenha123");

        assertThatThrownBy(() -> service.redefinirSenha(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido ou expirado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void redefinirSenha_comTokenJaUtilizado_deveLancarBusinessException() {
        User user = usuario(1L, "maria@email.com", true);
        PasswordResetToken token = tokenValido(user);
        token.setUsedAt(LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        var dto = new ResetPasswordRequestDTO("token-usado", "novaSenha123", "novaSenha123");

        assertThatThrownBy(() -> service.redefinirSenha(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Token inválido ou expirado");
        verify(userRepository, never()).save(any());
    }

    private PasswordResetToken tokenValido(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash("qualquer-hash");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return token;
    }
}
