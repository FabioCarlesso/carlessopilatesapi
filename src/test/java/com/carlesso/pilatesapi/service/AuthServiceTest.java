package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.AuthLoginRequestDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final int MAX_POR_IP = 3;
    private static final String ORIGEM = "203.0.113.10";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private LoginAttemptService loginAttemptService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(MAX_POR_IP, 15);
        service = new AuthService(
                authenticationManager,
                jwtService,
                refreshTokenService,
                loginAttemptService,
                new BusinessMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void login_falho_contaNosBaldesDeEmailEDeOrigem() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        for (int i = 0; i < MAX_POR_IP; i++) {
            var dto = new AuthLoginRequestDTO("vitima" + i + "@email.com", "errada123");
            assertThatThrownBy(() -> service.login(dto, ORIGEM)).isInstanceOf(BadCredentialsException.class);
        }

        assertThat(loginAttemptService.isIpBlocked("login:" + ORIGEM)).isTrue();
        assertThatThrownBy(() -> service.login(new AuthLoginRequestDTO("outra@email.com", "senha1234"), ORIGEM))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Muitas tentativas. Tente novamente em 15 minutos.");
    }

    @Test
    void login_bemSucedido_devolveAReservaDaOrigemSemZerarAsFalhasAnteriores() {
        User user = usuario();
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("x"))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(jwtService.generateToken(user)).thenReturn("access");
        when(refreshTokenService.emitir(user)).thenReturn("refresh");

        assertThatThrownBy(() -> service.login(new AuthLoginRequestDTO("outra@email.com", "errada123"), ORIGEM))
                .isInstanceOf(BadCredentialsException.class);
        service.login(new AuthLoginRequestDTO("maria@email.com", "senha1234"), ORIGEM);

        // Só a falha conta: restam MAX_POR_IP - 1 reservas antes do bloqueio.
        for (int i = 0; i < MAX_POR_IP - 1; i++) {
            assertThat(loginAttemptService.tryAcquireIp("login:" + ORIGEM)).isTrue();
        }
        assertThat(loginAttemptService.tryAcquireIp("login:" + ORIGEM)).isFalse();
    }

    @Test
    void login_barradoPeloEmail_naoConsomeACotaDaOrigemNemAutentica() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            loginAttemptService.tryAcquire("bloqueado@email.com");
        }

        assertThatThrownBy(() -> service.login(new AuthLoginRequestDTO("bloqueado@email.com", "senha1234"), ORIGEM))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(authenticationManager);
        assertThat(loginAttemptService.contemChaveIp("login:" + ORIGEM)).isFalse();
    }

    @Test
    void login_barradoPelaOrigem_naoAutenticaNemContaNoEmail() {
        for (int i = 0; i < MAX_POR_IP; i++) {
            loginAttemptService.tryAcquireIp("login:" + ORIGEM);
        }

        assertThatThrownBy(() -> service.login(new AuthLoginRequestDTO("maria@email.com", "senha1234"), ORIGEM))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(authenticationManager);
        assertThat(loginAttemptService.contemChave("maria@email.com")).isFalse();
    }

    private static User usuario() {
        User user = new User();
        user.setName("Maria");
        user.setEmail("maria@email.com");
        user.setRole(Role.RECEPCAO);
        user.setAtivo(true);
        return user;
    }
}
