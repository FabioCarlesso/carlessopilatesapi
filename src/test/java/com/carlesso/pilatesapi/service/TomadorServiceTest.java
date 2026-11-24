package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.TomadorRequestDTO;
import com.carlesso.pilatesapi.dto.TomadorUpdateDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Tomador;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.TomadorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TomadorServiceTest {

    @Mock
    TomadorRepository repository;

    @Mock
    PacienteRepository pacienteRepository;

    @InjectMocks
    TomadorService service;

    private Tomador tomador(Long id) {
        Tomador t = new Tomador();
        ReflectionTestUtils.setField(t, "id", id);
        t.setNome("Empresa Ltda");
        t.setCpfCnpj("11222333000181");
        return t;
    }

    @Test
    void cadastrar_normalizaOCpfCnpjParaDigitos() {
        when(repository.existsByCpfCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cadastrar(
                new TomadorRequestDTO(" Empresa Ltda ", "11.222.333/0001-81", "fin@empresa.com", "", null));

        assertThat(response.nome()).isEqualTo("Empresa Ltda");
        assertThat(response.cpfCnpj()).isEqualTo("11222333000181");
        assertThat(response.telefone()).isNull();
        assertThat(response.ativo()).isTrue();
        assertThat(response.quantidadePacientes()).isZero();
    }

    @Test
    void cadastrar_cpfCnpjComQuantidadeDeDigitosInvalida_deveLancarErro() {
        assertThatThrownBy(() -> service.cadastrar(new TomadorRequestDTO("X", "123", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11 (CPF) ou 14 (CNPJ)");
        verify(repository, never()).save(any());
    }

    @Test
    void cadastrar_cpfCnpjDuplicado_deveLancarConflito() {
        when(repository.existsByCpfCnpj("11222333000181")).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(new TomadorRequestDTO("X", "11222333000181", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listar_trazAQuantidadeDePacientesDeCadaTomador() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(tomador(1L), tomador(2L)), pageable, 2));
        when(pacienteRepository.contarPorTomador(List.of(1L, 2L))).thenReturn(List.<Object[]>of(new Object[] {1L, 3L}));

        var pagina = service.listar(null, null, null, pageable);

        assertThat(pagina.getContent()).extracting("quantidadePacientes").containsExactly(3L, 0L);
    }

    @Test
    void listarPacientes_retornaOsPacientesVinculados() {
        when(repository.findById(1L)).thenReturn(Optional.of(tomador(1L)));
        Paciente ana = new Paciente();
        ReflectionTestUtils.setField(ana, "id", 7L);
        ana.setNome("Ana");
        ana.setCpfNaNota(true);
        when(pacienteRepository.findByTomadorIdOrderByNomeAsc(1L)).thenReturn(List.of(ana));

        assertThat(service.listarPacientes(1L)).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(7L);
            assertThat(p.cpfNaNota()).isTrue();
        });
    }

    @Test
    void atualizar_cpfCnpjDeOutroTomador_deveLancarConflito() {
        when(repository.findById(1L)).thenReturn(Optional.of(tomador(1L)));
        when(repository.existsByCpfCnpjAndIdNot("12345678909", 1L)).thenReturn(true);

        assertThatThrownBy(() ->
                        service.atualizar(1L, new TomadorUpdateDTO(null, "123.456.789-09", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void atualizar_parcial_vazioApagaOpcionaisEAusenteMantem() {
        Tomador t = tomador(1L);
        t.setEmail("a@b.com");
        t.setTelefone("45999990000");
        when(repository.findById(1L)).thenReturn(Optional.of(t));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pacienteRepository.contarPorTomador(List.of(1L))).thenReturn(List.of());

        var response = service.atualizar(1L, new TomadorUpdateDTO("Novo Nome", null, "", null, null, null));

        assertThat(response.nome()).isEqualTo("Novo Nome");
        assertThat(response.email()).isNull();
        assertThat(response.telefone()).isEqualTo("45999990000");
        assertThat(response.cpfCnpj()).isEqualTo("11222333000181");
    }

    @Test
    void inativarEAtivar_alteramASituacao() {
        Tomador t = tomador(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(t));

        service.inativar(1L);
        assertThat(t.isAtivo()).isFalse();
        service.ativar(1L);
        assertThat(t.isAtivo()).isTrue();
    }

    @Test
    void buscarPorId_inexistente_deveLancar404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
