package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
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
class ProfissionalServiceTest {

    @Mock
    private ProfissionalRepository repository;

    @InjectMocks
    private ProfissionalService service;

    private Profissional profissional() {
        Profissional p = new Profissional();
        p.setId(1L);
        p.setNome("Paula Mendes");
        p.setEmail("paula@email.com");
        p.setCpf("12345678900");
        p.setTelefone("11999999999");
        p.setTipoContrato(TipoContrato.PJ);
        p.setPercentualPagamentoAula(new BigDecimal("45.00"));
        p.setDataInicio(LocalDate.of(2024, 1, 15));
        return p;
    }

    @Test
    void cadastrar_deveRetornarProfissionalCriado() {
        var dto = new ProfissionalRequestDTO("Paula Mendes", "paula@email.com", "12345678900",
                "11999999999", TipoContrato.PJ, new BigDecimal("45.00"), LocalDate.of(2024, 1, 15));
        when(repository.existsByEmail(dto.email())).thenReturn(false);
        when(repository.existsByCpf(dto.cpf())).thenReturn(false);
        when(repository.save(any(Profissional.class))).thenReturn(profissional());

        ProfissionalResponseDTO response = service.cadastrar(dto);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.percentualPagamentoAula()).isEqualByComparingTo("45.00");
        verify(repository).save(any(Profissional.class));
    }

    @Test
    void cadastrar_emailDuplicado_deveLancarConflito() {
        var dto = new ProfissionalRequestDTO("Paula Mendes", "paula@email.com", "12345678900",
                "11999999999", TipoContrato.PJ, new BigDecimal("45.00"), LocalDate.of(2024, 1, 15));
        when(repository.existsByEmail(dto.email())).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paula@email.com");
    }

    @Test
    void cadastrar_cpfDuplicado_deveLancarConflito() {
        var dto = new ProfissionalRequestDTO("Paula Mendes", "paula@email.com", "12345678900",
                "11999999999", TipoContrato.PJ, new BigDecimal("45.00"), LocalDate.of(2024, 1, 15));
        when(repository.existsByEmail(dto.email())).thenReturn(false);
        when(repository.existsByCpf(dto.cpf())).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12345678900");
    }

    @Test
    void listar_semFiltros_delegaAoRepository() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Profissional>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(profissional())));

        var resultado = service.listar(null, null, null, null, null, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listar_comFiltros_deveConsultarRepositoryComSpecification() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Profissional>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(profissional()), pageable, 1));

        var resultado = service.listar(
                "paula",
                "email.com",
                TipoContrato.PJ,
                new BigDecimal("45.00"),
                false,
                pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).tipoContrato()).isEqualTo(TipoContrato.PJ);
        verify(repository).findAll(org.mockito.ArgumentMatchers.<Specification<Profissional>>any(), eq(pageable));
    }

    @Test
    void atualizar_deveAtualizarCamposInformados() {
        when(repository.findById(1L)).thenReturn(Optional.of(profissional()));

        var response = service.atualizar(1L,
                new ProfissionalUpdateDTO("Novo Nome", null, null, null, new BigDecimal("50.00"), null));

        assertThat(response.nome()).isEqualTo("Novo Nome");
        assertThat(response.percentualPagamentoAula()).isEqualByComparingTo("50.00");
    }

    @Test
    void buscarPorId_quandoNaoExiste_deveLancarEntityNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void ativar_deveDefinirAtivoComoTrue() {
        Profissional p = profissional();
        p.setAtivo(false);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        service.ativar(1L);

        assertThat(p.isAtivo()).isTrue();
    }

    @Test
    void ativar_quandoNaoExiste_deveLancarEntityNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ativar(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void inativar_deveDefinirAtivoComoFalse() {
        Profissional p = profissional();
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        service.inativar(1L);

        assertThat(p.isAtivo()).isFalse();
    }

    @Test
    void inativar_quandoNaoExiste_deveLancarEntityNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inativar(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
