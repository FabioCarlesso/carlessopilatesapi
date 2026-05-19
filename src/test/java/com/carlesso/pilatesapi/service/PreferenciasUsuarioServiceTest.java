package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PreferenciasUsuarioRequestDTO;
import com.carlesso.pilatesapi.dto.PreferenciasUsuarioResponseDTO;
import com.carlesso.pilatesapi.entity.PreferenciasUsuario;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PreferenciasUsuarioRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciasUsuarioServiceTest {

    @Mock
    private PreferenciasUsuarioRepository repository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PreferenciasUsuarioService service;

    private User usuario(Long id, String email) {
        User u = new User();
        u.setName("Teste");
        u.setEmail(email);
        u.setPassword("hash");
        u.setRole(Role.USER);
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
    void buscarPorEmail_semPreferenciasSalvas_deveRetornarPadroes() {
        User user = usuario(1L, "novo@email.com");
        when(userRepository.findByEmail("novo@email.com")).thenReturn(Optional.of(user));
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());

        PreferenciasUsuarioResponseDTO response = service.buscarPorEmail("novo@email.com");

        assertThat(response.idioma()).isEqualTo(PreferenciasUsuarioService.IDIOMA_PADRAO);
        assertThat(response.tema()).isEqualTo(PreferenciasUsuarioService.TEMA_PADRAO);
        assertThat(response.notificacoesEmail()).isEqualTo(PreferenciasUsuarioService.NOTIFICACOES_EMAIL_PADRAO);
        assertThat(response.notificacoesPush()).isEqualTo(PreferenciasUsuarioService.NOTIFICACOES_PUSH_PADRAO);
        verify(repository, never()).save(any());
    }

    @Test
    void buscarPorEmail_comPreferenciasSalvas_deveRetornarValoresPersistidos() {
        User user = usuario(1L, "salvo@email.com");
        PreferenciasUsuario salvas = new PreferenciasUsuario();
        salvas.setUser(user);
        salvas.setIdioma(IdiomaPreferencia.EN_US);
        salvas.setTema(TemaPreferencia.ESCURO);
        salvas.setNotificacoesEmail(false);
        salvas.setNotificacoesPush(true);
        when(userRepository.findByEmail("salvo@email.com")).thenReturn(Optional.of(user));
        when(repository.findByUserId(1L)).thenReturn(Optional.of(salvas));

        PreferenciasUsuarioResponseDTO response = service.buscarPorEmail("salvo@email.com");

        assertThat(response.idioma()).isEqualTo(IdiomaPreferencia.EN_US);
        assertThat(response.tema()).isEqualTo(TemaPreferencia.ESCURO);
        assertThat(response.notificacoesEmail()).isFalse();
        assertThat(response.notificacoesPush()).isTrue();
    }

    @Test
    void buscarPorEmail_emailNormalizadoParaLowercase() {
        User user = usuario(1L, "minusculo@email.com");
        when(userRepository.findByEmail("minusculo@email.com")).thenReturn(Optional.of(user));
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());

        service.buscarPorEmail("MINUSCULO@EMAIL.COM");

        verify(userRepository).findByEmail("minusculo@email.com");
    }

    @Test
    void buscarPorEmail_usuarioInexistente_deveLancar404() {
        when(userRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorEmail("naoexiste@email.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void atualizarPorEmail_semPreferenciasSalvas_deveCriarComValoresDoDTO() {
        User user = usuario(1L, "novo@email.com");
        when(userRepository.findByEmail("novo@email.com")).thenReturn(Optional.of(user));
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var dto = new PreferenciasUsuarioRequestDTO(IdiomaPreferencia.ES_ES, TemaPreferencia.ESCURO, false, true);

        PreferenciasUsuarioResponseDTO response = service.atualizarPorEmail("novo@email.com", dto);

        ArgumentCaptor<PreferenciasUsuario> captor = ArgumentCaptor.forClass(PreferenciasUsuario.class);
        verify(repository).save(captor.capture());
        PreferenciasUsuario salvas = captor.getValue();
        assertThat(salvas.getUser()).isEqualTo(user);
        assertThat(salvas.getIdioma()).isEqualTo(IdiomaPreferencia.ES_ES);
        assertThat(salvas.getTema()).isEqualTo(TemaPreferencia.ESCURO);
        assertThat(salvas.isNotificacoesEmail()).isFalse();
        assertThat(salvas.isNotificacoesPush()).isTrue();
        assertThat(response.idioma()).isEqualTo(IdiomaPreferencia.ES_ES);
    }

    @Test
    void atualizarPorEmail_comPreferenciasExistentes_deveAtualizarMesmoRegistro() {
        User user = usuario(1L, "existente@email.com");
        PreferenciasUsuario existente = new PreferenciasUsuario();
        existente.setUser(user);
        existente.setIdioma(IdiomaPreferencia.PT_BR);
        existente.setTema(TemaPreferencia.CLARO);
        existente.setNotificacoesEmail(true);
        existente.setNotificacoesPush(false);
        when(userRepository.findByEmail("existente@email.com")).thenReturn(Optional.of(user));
        when(repository.findByUserId(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var dto = new PreferenciasUsuarioRequestDTO(IdiomaPreferencia.EN_US, TemaPreferencia.ESCURO, false, true);

        PreferenciasUsuarioResponseDTO response = service.atualizarPorEmail("existente@email.com", dto);

        ArgumentCaptor<PreferenciasUsuario> captor = ArgumentCaptor.forClass(PreferenciasUsuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existente);
        assertThat(existente.getIdioma()).isEqualTo(IdiomaPreferencia.EN_US);
        assertThat(existente.getTema()).isEqualTo(TemaPreferencia.ESCURO);
        assertThat(existente.isNotificacoesEmail()).isFalse();
        assertThat(existente.isNotificacoesPush()).isTrue();
        assertThat(response.notificacoesPush()).isTrue();
    }

    @Test
    void atualizarPorEmail_usuarioInexistente_deveLancar404() {
        when(userRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());
        var dto = new PreferenciasUsuarioRequestDTO(IdiomaPreferencia.PT_BR, TemaPreferencia.CLARO, true, true);

        assertThatThrownBy(() -> service.atualizarPorEmail("naoexiste@email.com", dto))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }
}
