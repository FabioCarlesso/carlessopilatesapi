package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.UserAlterarSenhaRequestDTO;
import com.carlesso.pilatesapi.dto.UserRequestDTO;
import com.carlesso.pilatesapi.dto.UserResponseDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;

    private User usuario(Long id, String email, Role role) {
        User u = new User();
        u.setName("Usuário Teste");
        u.setEmail(email);
        u.setPassword("hash");
        u.setRole(role);
        setId(u, id);
        return u;
    }

    private void setId(User u, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void criar_comEmailNovo_deveRetornarResponseDTO() {
        var dto = new UserRequestDTO("Ana", "ana@email.com", "senha123", Role.USER);
        User saved = usuario(1L, "ana@email.com", Role.USER);
        when(repository.existsByEmail("ana@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("hash");
        when(repository.save(any())).thenReturn(saved);

        UserResponseDTO response = service.criar(dto);

        assertThat(response.email()).isEqualTo("ana@email.com");
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void criar_comEmailDuplicado_deveLancarConflictException() {
        var dto = new UserRequestDTO("Ana", "ana@email.com", "senha123", Role.USER);
        when(repository.existsByEmail("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessage("E-mail já cadastrado");
    }

    @Test
    void criar_deveNormalizarEmail() {
        var dto = new UserRequestDTO("Ana", "ANA@EMAIL.COM", "senha123", Role.USER);
        User saved = usuario(1L, "ana@email.com", Role.USER);
        when(repository.existsByEmail("ana@email.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(repository.save(any())).thenReturn(saved);

        service.criar(dto);

        verify(repository).existsByEmail("ana@email.com");
    }

    @Test
    void buscarPorId_comIdExistente_deveRetornarResponseDTO() {
        User u = usuario(1L, "user@email.com", Role.USER);
        when(repository.findById(1L)).thenReturn(Optional.of(u));

        UserResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void buscarPorId_comIdInexistente_deveLancarResourceNotFoundException() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void atualizar_comDadosValidos_deveRetornarResponseAtualizado() {
        User u = usuario(1L, "user@email.com", Role.USER);
        var dto = new UserUpdateDTO("Novo Nome", null, null, null);
        when(repository.findById(1L)).thenReturn(Optional.of(u));
        when(repository.save(any())).thenReturn(u);

        UserResponseDTO response = service.atualizar(1L, dto, "admin@email.com");

        assertThat(response.name()).isEqualTo("Novo Nome");
    }

    @Test
    void atualizar_adminAlterandoProprioRole_deveLancarBusinessException() {
        User admin = usuario(1L, "admin@email.com", Role.ADMIN);
        var dto = new UserUpdateDTO(null, null, null, Role.USER);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.atualizar(1L, dto, "admin@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível alterar o próprio perfil de acesso");
    }

    @Test
    void inativar_comIdValido_deveSetarAtivoFalse() {
        User u = usuario(2L, "user@email.com", Role.USER);
        when(repository.findById(2L)).thenReturn(Optional.of(u));
        when(repository.save(any())).thenReturn(u);

        service.inativar(2L, "admin@email.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
    }

    @Test
    void inativar_contaPropriaAdmin_deveLancarBusinessException() {
        User admin = usuario(1L, "admin@email.com", Role.ADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.inativar(1L, "admin@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível inativar a própria conta");

        verify(repository, never()).save(any());
    }

    @Test
    void inativar_comIdInexistente_deveLancarResourceNotFoundException() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inativar(99L, "admin@email.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inativar_usuarioJaInativo_deveSetarAtivoFalseNovamente() {
        User u = usuario(2L, "user@email.com", Role.USER);
        u.setAtivo(false);
        when(repository.findById(2L)).thenReturn(Optional.of(u));
        when(repository.save(any())).thenReturn(u);

        service.inativar(2L, "admin@email.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
    }

    @Test
    void inativar_ultimoAdmin_deveLancarBusinessException() {
        User admin = usuario(2L, "admin2@email.com", Role.ADMIN);
        when(repository.findById(2L)).thenReturn(Optional.of(admin));
        when(repository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(of(admin));

        assertThatThrownBy(() -> service.inativar(2L, "admin@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível inativar o último administrador ativo");

        verify(repository, never()).save(any());
    }

    @Test
    void inativar_adminComOutroAdminAtivo_deveInativar() {
        User admin = usuario(2L, "admin2@email.com", Role.ADMIN);
        User outroAdmin = usuario(3L, "admin3@email.com", Role.ADMIN);
        when(repository.findById(2L)).thenReturn(Optional.of(admin));
        when(repository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(of(admin, outroAdmin));
        when(repository.save(any())).thenReturn(admin);

        service.inativar(2L, "admin@email.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
    }

    @Test
    void inativar_adminJaInativo_naoDeveValidarUltimoAdminAtivo() {
        User admin = usuario(2L, "admin2@email.com", Role.ADMIN);
        admin.setAtivo(false);
        when(repository.findById(2L)).thenReturn(Optional.of(admin));
        when(repository.save(any())).thenReturn(admin);

        service.inativar(2L, "admin@email.com");

        verify(repository, never()).findActiveByRoleForUpdate(Role.ADMIN);
        verify(repository).save(any());
    }

    @Test
    void atualizar_rebaixandoUltimoAdmin_deveLancarBusinessException() {
        User admin = usuario(2L, "admin2@email.com", Role.ADMIN);
        var dto = new UserUpdateDTO(null, null, null, Role.USER);
        when(repository.findById(2L)).thenReturn(Optional.of(admin));
        when(repository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(of(admin));

        assertThatThrownBy(() -> service.atualizar(2L, dto, "admin@email.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Não é possível rebaixar o último administrador ativo");

        verify(repository, never()).save(any());
    }

    @Test
    void atualizar_rebaixandoAdminComOutroAdminAtivo_deveAtualizar() {
        User admin = usuario(2L, "admin2@email.com", Role.ADMIN);
        User outroAdmin = usuario(3L, "admin3@email.com", Role.ADMIN);
        var dto = new UserUpdateDTO(null, null, null, Role.USER);
        when(repository.findById(2L)).thenReturn(Optional.of(admin));
        when(repository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(of(admin, outroAdmin));
        when(repository.save(any())).thenReturn(admin);

        UserResponseDTO response = service.atualizar(2L, dto, "admin@email.com");

        assertThat(response).isNotNull();
        verify(repository).save(any());
    }

    @Test
    void alterarSenha_comDadosValidos_devePersistirNovaSenhaCriptografada() {
        User u = usuario(1L, "user@email.com", Role.USER);
        u.setPassword("hash-atual");
        var dto = new UserAlterarSenhaRequestDTO("senhaAtual1", "novaSenha123", "novaSenha123");
        when(repository.findByEmail("user@email.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("senhaAtual1", "hash-atual")).thenReturn(true);
        when(passwordEncoder.matches("novaSenha123", "hash-atual")).thenReturn(false);
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hash-novo");
        when(repository.save(any())).thenReturn(u);

        service.alterarSenha("user@email.com", dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hash-novo");
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(1);
    }

    @Test
    void alterarSenha_comEmailNormalizado_deveBuscarUsuario() {
        User u = usuario(1L, "user@email.com", Role.USER);
        u.setPassword("hash-atual");
        var dto = new UserAlterarSenhaRequestDTO("senhaAtual1", "novaSenha123", "novaSenha123");
        when(repository.findByEmail("user@email.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("senhaAtual1", "hash-atual")).thenReturn(true);
        when(passwordEncoder.matches("novaSenha123", "hash-atual")).thenReturn(false);
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hash-novo");
        when(repository.save(any())).thenReturn(u);

        service.alterarSenha("USER@EMAIL.COM", dto);

        verify(repository).findByEmail("user@email.com");
    }

    @Test
    void alterarSenha_comSenhaAtualIncorreta_deveLancarBusinessException() {
        User u = usuario(1L, "user@email.com", Role.USER);
        u.setPassword("hash-atual");
        var dto = new UserAlterarSenhaRequestDTO("errada1234", "novaSenha123", "novaSenha123");
        when(repository.findByEmail("user@email.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("errada1234", "hash-atual")).thenReturn(false);

        assertThatThrownBy(() -> service.alterarSenha("user@email.com", dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Senha atual incorreta");

        verify(repository, never()).save(any());
    }

    @Test
    void alterarSenha_comConfirmacaoDivergente_deveLancarBusinessException() {
        User u = usuario(1L, "user@email.com", Role.USER);
        u.setPassword("hash-atual");
        var dto = new UserAlterarSenhaRequestDTO("senhaAtual1", "novaSenha123", "outraSenha123");
        when(repository.findByEmail("user@email.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("senhaAtual1", "hash-atual")).thenReturn(true);

        assertThatThrownBy(() -> service.alterarSenha("user@email.com", dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Confirmação de senha não confere");

        verify(repository, never()).save(any());
    }

    @Test
    void alterarSenha_reutilizandoSenhaAtual_deveLancarBusinessException() {
        User u = usuario(1L, "user@email.com", Role.USER);
        u.setPassword("hash-atual");
        var dto = new UserAlterarSenhaRequestDTO("senhaAtual1", "senhaAtual1", "senhaAtual1");
        when(repository.findByEmail("user@email.com")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("senhaAtual1", "hash-atual")).thenReturn(true);

        assertThatThrownBy(() -> service.alterarSenha("user@email.com", dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A nova senha deve ser diferente da senha atual");

        verify(repository, never()).save(any());
    }

    @Test
    void alterarSenha_comUsuarioInexistente_deveLancarResourceNotFoundException() {
        var dto = new UserAlterarSenhaRequestDTO("senhaAtual1", "novaSenha123", "novaSenha123");
        when(repository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.alterarSenha("ghost@email.com", dto))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void atualizar_rebaixandoAdminInativo_deveAtualizarSemValidarUltimoAdminAtivo() {
        User admin = usuario(2L, "admin2@email.com", Role.ADMIN);
        admin.setAtivo(false);
        var dto = new UserUpdateDTO(null, null, null, Role.USER);
        when(repository.findById(2L)).thenReturn(Optional.of(admin));
        when(repository.save(any())).thenReturn(admin);

        UserResponseDTO response = service.atualizar(2L, dto, "admin@email.com");

        assertThat(response).isNotNull();
        verify(repository, never()).findActiveByRoleForUpdate(Role.ADMIN);
        verify(repository).save(any());
    }
}
