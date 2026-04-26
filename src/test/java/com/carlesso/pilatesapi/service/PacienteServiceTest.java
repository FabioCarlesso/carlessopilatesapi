package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.EnderecoDTO;
import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.dto.PacienteResponseDTO;
import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.entity.Endereco;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository repository;

    @InjectMocks
    private PacienteService service;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Maria Souza");
        p.setEmail("maria@email.com");
        p.setCpf("12345678900");
        p.setTelefone("11912345678");
        p.setDataNascimento(LocalDate.of(1990, 5, 20));
        p.setEndereco(new Endereco("Rua das Flores", "42", "Centro", "São Paulo", "SP", "01001000"));
        setId(p, 1L);
        return p;
    }

    private PacienteRequestDTO requestDTO() {
        return new PacienteRequestDTO(
                "Maria Souza", "maria@email.com", "12345678900",
                "11912345678", LocalDate.of(1990, 5, 20),
                new EnderecoDTO("Rua das Flores", "42", "Centro", "São Paulo", "SP", "01001000")
        );
    }

    private void setId(Paciente p, Long id) {
        try {
            var field = Paciente.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // cadastrar
    // -------------------------------------------------------------------------

    @Test
    void cadastrar_comDadosCompletos_deveRetornarResponseDTO() {
        when(repository.save(any(Paciente.class))).thenReturn(paciente());

        PacienteResponseDTO response = service.cadastrar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nome()).isEqualTo("Maria Souza");
        assertThat(response.email()).isEqualTo("maria@email.com");
        assertThat(response.cpf()).isEqualTo("12345678900");
        assertThat(response.ativo()).isTrue();
        assertThat(response.endereco()).isNotNull();
        assertThat(response.endereco().cidade()).isEqualTo("São Paulo");
        verify(repository).save(any(Paciente.class));
    }

    @Test
    void cadastrar_semEndereco_deveRetornarEnderecoNulo() {
        var dto = new PacienteRequestDTO("João", "joao@email.com", "98765432100", null, null, null);
        Paciente salvo = new Paciente();
        salvo.setNome("João");
        salvo.setEmail("joao@email.com");
        salvo.setCpf("98765432100");
        setId(salvo, 2L);

        when(repository.save(any(Paciente.class))).thenReturn(salvo);

        PacienteResponseDTO response = service.cadastrar(dto);

        assertThat(response.endereco()).isNull();
        verify(repository).save(any(Paciente.class));
    }

    // -------------------------------------------------------------------------
    // listar
    // -------------------------------------------------------------------------

    @Test
    void listar_deveRetornarPageComPacientesAtivos() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(paciente()), pageable, 1);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable))).thenReturn(page);

        var resultado = service.listar(null, null, null, null, null, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).nome()).isEqualTo("Maria Souza");
    }

    @Test
    void listar_semPacientes_deveRetornarPageVazia() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        var resultado = service.listar(null, null, null, null, null, pageable);

        assertThat(resultado.getTotalElements()).isZero();
        assertThat(resultado.getContent()).isEmpty();
    }

    @Test
    void listar_comFiltros_deveConsultarRepositoryComSpecification() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(paciente()), pageable, 1);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable))).thenReturn(page);

        var resultado = service.listar("maria", "email.com", "123", "119", false, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).email()).isEqualTo("maria@email.com");
        verify(repository).findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable));
    }

    // -------------------------------------------------------------------------
    // buscarPorId
    // -------------------------------------------------------------------------

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));

        PacienteResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nome()).isEqualTo("Maria Souza");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // atualizar
    // -------------------------------------------------------------------------

    @Test
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));

        var dto = new PacienteUpdateDTO("Novo Nome", null, "11999999999", null, null);
        PacienteResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.nome()).isEqualTo("Novo Nome");
        assertThat(response.email()).isEqualTo("maria@email.com");
        assertThat(response.telefone()).isEqualTo("11999999999");
    }

    @Test
    void atualizar_comEndereco_deveAtualizarEndereco() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));

        var novoEndereco = new EnderecoDTO("Av. Paulista", "1000", "Bela Vista", "São Paulo", "SP", "01310100");
        var dto = new PacienteUpdateDTO(null, null, null, null, novoEndereco);
        PacienteResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.endereco().logradouro()).isEqualTo("Av. Paulista");
        assertThat(response.endereco().numero()).isEqualTo("1000");
    }

    @Test
    void atualizar_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(99L, new PacienteUpdateDTO(null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // inativar
    // -------------------------------------------------------------------------

    @Test
    void inativar_deveMarcaPacienteComoInativo() {
        Paciente existente = paciente();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        service.inativar(1L);

        assertThat(existente.isAtivo()).isFalse();
    }

    @Test
    void inativar_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inativar(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
