package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.dto.ProfissionalRequestDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.ProfissionalUpdateDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfissionalServiceTest {

    @Mock
    private ProfissionalRepository repository;

    @Mock
    private AulaRepository aulaRepository;

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

    @Test
    void gerarRelatorioPagamento_deveSomarAulasRealizadasNoPeriodo() {
        Profissional profissional = profissional();
        Aula aula = aula(profissional);

        when(repository.findById(1L)).thenReturn(Optional.of(profissional));
        when(aulaRepository.findByProfissionalIdAndRealizadaTrueAndDataBetweenOrderByData(
                1L, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28)))
                .thenReturn(List.of(aula, aula));
        when(aulaRepository.countGroupedByPagamentoId(List.of(5L)))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 8L}));

        var relatorio = service.gerarRelatorioPagamento(
                1L,
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28));

        assertThat(relatorio.totalAulas()).isEqualTo(2);
        assertThat(relatorio.totalPagamento()).isEqualByComparingTo("22.50");
        assertThat(relatorio.aulas().getFirst().valorBaseAula()).isEqualByComparingTo("25.00");
        assertThat(relatorio.aulas().getFirst().valorProfissional()).isEqualByComparingTo("11.25");
    }

    @Test
    void gerarRelatorioPagamento_periodoInvalido_deveLancarBadRequest() {
        assertThatThrownBy(() -> service.gerarRelatorioPagamento(
                1L,
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 2, 28)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ser maior");
    }

    private Aula aula(Profissional profissional) {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana");
        paciente.setEmail("ana@email.com");
        paciente.setCpf("11122233344");

        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.DUAS_VEZES);
        plano.setDataInicio(LocalDate.of(2025, 2, 1));

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setPeriodoInicio(LocalDate.of(2025, 2, 1));
        pagamento.setPeriodoFim(LocalDate.of(2025, 2, 28));
        pagamento.setDataVencimento(LocalDate.of(2025, 2, 10));
        ReflectionTestUtils.setField(pagamento, "id", 5L);

        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(LocalDate.of(2025, 2, 3));
        aula.setRealizada(true);
        return aula;
    }
}
