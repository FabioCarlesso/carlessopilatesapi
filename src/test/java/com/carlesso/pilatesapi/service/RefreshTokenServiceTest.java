package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.entity.RefreshToken;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.UnauthorizedException;
import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import com.carlesso.pilatesapi.repository.RefreshTokenRepository;
import com.carlesso.pilatesapi.util.TokenOpaco;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;

    @Mock
    private RefreshTokenRepository repository;

    private SimpleMeterRegistry meterRegistry;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = novoService(TTL_MS);
    }

    private RefreshTokenService novoService(long ttlMs) {
        return new RefreshTokenService(repository, new BusinessMetrics(meterRegistry), ttlMs);
    }

    @Test
    void emitir_devePersistirApenasOHashComVersaoDoUsuarioENovaFamilia() {
        User user = usuario(true, 3);

        String raw = service.emitir(user);

        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken salvo = captor.getValue();
        assertThat(raw).isNotBlank();
        assertThat(salvo.getTokenHash()).isEqualTo(TokenOpaco.hash(raw)).isNotEqualTo(raw);
        assertThat(salvo.getUser()).isSameAs(user);
        assertThat(salvo.getTokenVersion()).isEqualTo(3);
        assertThat(salvo.getFamilyId()).isNotNull();
        assertThat(salvo.getExpiresAt())
                .isAfter(LocalDateTime.now().plusDays(6))
                .isBefore(LocalDateTime.now().plusDays(8));
    }

    @Test
    void emitir_emLoginsDistintos_deveAbrirFamiliasDistintas() {
        User user = usuario(true, 0);

        service.emitir(user);
        service.emitir(user);

        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getFamilyId())
                .isNotEqualTo(captor.getAllValues().get(1).getFamilyId());
    }

    @Test
    void rotacionar_comTokenValido_deveConsumirOAtualEEmitirNovoNaMesmaFamilia() {
        User user = usuario(true, 0);
        RefreshToken atual = token(user, 0);
        when(repository.findByTokenHash(TokenOpaco.hash("raw-atual"))).thenReturn(Optional.of(atual));

        var rotacionado = service.rotacionar("raw-atual");

        assertThat(rotacionado.user()).isSameAs(user);
        assertThat(rotacionado.refreshToken()).isNotBlank().isNotEqualTo("raw-atual");
        assertThat(atual.getUsedAt()).isNotNull();
        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(2)).save(captor.capture());
        RefreshToken novo = captor.getAllValues().get(1);
        assertThat(novo.getFamilyId()).isEqualTo(atual.getFamilyId());
        assertThat(novo.getTokenHash()).isEqualTo(TokenOpaco.hash(rotacionado.refreshToken()));
        verify(repository, never()).revogarFamilia(any(), any());
    }

    @Test
    void rotacionar_comTokenJaRotacionado_deveRevogarAFamiliaERetornar401() {
        User user = usuario(true, 0);
        RefreshToken reusado = token(user, 0);
        reusado.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(reusado));

        assertThatThrownBy(() -> service.rotacionar("raw-reusado"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(RefreshTokenService.ERRO_TOKEN_INVALIDO);

        verify(repository).revogarFamilia(eq(reusado.getFamilyId()), any());
        verify(repository, never()).save(any());
        assertThat(meterRegistry.counter("pilates.refresh.tokens.reusados").count())
                .isEqualTo(1.0);
    }

    @Test
    void rotacionar_comTokenDesconhecido_deveRetornar401() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotacionar("inexistente"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(RefreshTokenService.ERRO_TOKEN_INVALIDO);
        verify(repository, never()).save(any());
    }

    @Test
    void rotacionar_comTokenRevogadoNoLogout_deveRetornar401SemRevogarFamilia() {
        RefreshToken revogado = token(usuario(true, 0), 0);
        revogado.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(revogado));

        assertThatThrownBy(() -> service.rotacionar("raw")).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).revogarFamilia(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotacionar_comTokenExpirado_deveRetornar401() {
        RefreshToken expirado = token(usuario(true, 0), 0);
        expirado.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.rotacionar("raw")).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rotacionar_comTtlCurtoInjetado_tokenRecemEmitidoJaDeveEstarExpirado() {
        RefreshTokenService ttlZero = novoService(0);
        User user = usuario(true, 0);
        ttlZero.emitir(user);
        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(captor.getValue()));

        assertThatThrownBy(() -> ttlZero.rotacionar("raw")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotacionar_aposTrocaDeSenha_deveRevogarAFamiliaERetornar401() {
        User user = usuario(true, 1);
        RefreshToken anterior = token(user, 0);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(anterior));

        assertThatThrownBy(() -> service.rotacionar("raw")).isInstanceOf(UnauthorizedException.class);
        verify(repository).revogarFamilia(eq(anterior.getFamilyId()), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotacionar_comUsuarioInativo_deveRevogarAFamiliaERetornar401() {
        RefreshToken token = token(usuario(false, 0), 0);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.rotacionar("raw")).isInstanceOf(UnauthorizedException.class);
        verify(repository).revogarFamilia(eq(token.getFamilyId()), any());
        verify(repository, never()).save(any());
    }

    @Test
    void revogar_comTokenAtivo_deveMarcarRevogado() {
        RefreshToken token = token(usuario(true, 0), 0);
        when(repository.findByTokenHash(TokenOpaco.hash("raw"))).thenReturn(Optional.of(token));

        service.revogar("raw");

        assertThat(token.getRevokedAt()).isNotNull();
        verify(repository).save(token);
    }

    @Test
    void revogar_comTokenJaRevogado_naoDeveAlterarNada() {
        RefreshToken token = token(usuario(true, 0), 0);
        LocalDateTime revogadoEm = LocalDateTime.now().minusHours(1);
        token.setRevokedAt(revogadoEm);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.revogar("raw");

        assertThat(token.getRevokedAt()).isEqualTo(revogadoEm);
        verify(repository, never()).save(any());
    }

    @Test
    void revogar_comTokenDesconhecido_naoDeveLancarErro() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.revogar("inexistente");

        verify(repository, never()).save(any());
    }

    private RefreshToken token(User user, long tokenVersion) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("qualquer-hash");
        token.setFamilyId(UUID.randomUUID());
        token.setTokenVersion(tokenVersion);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        return token;
    }

    private User usuario(boolean ativo, long tokenVersion) {
        User u = new User();
        u.setName("Maria");
        u.setEmail("maria@email.com");
        u.setPassword("hash");
        u.setRole(Role.USER);
        u.setAtivo(ativo);
        u.setTokenVersion(tokenVersion);
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}
