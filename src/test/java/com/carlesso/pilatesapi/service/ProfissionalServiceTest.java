package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        p.setNome("Paula Mendes");
        p.setEmail("paula@email.com");
        p.setCpf("12345678900");
        p.setTelefone("11999999999");
        p.setTipoContrato("PJ");
        p.setPercentualPagamentoAula(new BigDecimal("45.00"));
        p.setDataInicio(LocalDate.of(2024, 1, 15));
        setId(p, 1L);
        return p;
    }

    private void setId(Profissional profissional, Long id) {
        try {
            var field = Profissional.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(profissional, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void cadastrar_deveRetornarProfissionalCriado() {
        var dto = new ProfissionalRequestDTO("Paula Mendes", "paula@email.com", "12345678900",
                "11999999999", "PJ", new BigDecimal("45.00"), LocalDate.of(2024, 1, 15));
        when(repository.save(any(Profissional.class))).thenReturn(profissional());

        ProfissionalResponseDTO response = service.cadastrar(dto);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.percentualPagamentoAula()).isEqualByComparingTo("45.00");
        verify(repository).save(any(Profissional.class));
    }

    @Test
    void listar_deveRetornarApenasAtivos() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAllByAtivoTrue(pageable)).thenReturn(new PageImpl<>(List.of(profissional())));

        var resultado = service.listar(pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
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
}
