package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.carlesso.pilatesapi.dto.PlanoRequestDTO;
import com.carlesso.pilatesapi.dto.PlanoResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PlanoServiceTest {

    @Mock
    PlanoRepository planoRepository;

    @Mock
    PacienteRepository pacienteRepository;

    @InjectMocks
    PlanoService service;

    private Paciente pacienteAtivo;
    private Paciente pacienteInativo;

    @BeforeEach
    void setUp() {
        pacienteAtivo = new Paciente();
        pacienteAtivo.setNome("Ana");
        pacienteAtivo.setEmail("ana@email.com");
        pacienteAtivo.setCpf("111.222.333-44");

        pacienteInativo = new Paciente();
        pacienteInativo.setNome("Inativo");
        pacienteInativo.setEmail("inativo@email.com");
        pacienteInativo.setCpf("999.888.777-66");
        pacienteInativo.setAtivo(false);
    }

    @Test
    void metodosDeLeitura_saoTransacionaisReadOnly() throws Exception {
        assertReadOnly("buscarPorId", Long.class);
        assertReadOnly("buscarAtivoPorPaciente", Long.class);
        assertReadOnly("listarPorPaciente", Long.class);
    }

    @Test
    void criarPlano_comSucesso() {
        var dto = new PlanoRequestDTO(
                1L,
                TipoPagamento.MENSAL,
                new BigDecimal("200.00"),
                FrequenciaSemanal.DUAS_VEZES,
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                LocalDate.now());

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteAtivo));
        when(planoRepository.findByPacienteIdAndAtivoTrue(1L)).thenReturn(Optional.empty());

        Plano planoSalvo = new Plano();
        planoSalvo.setPaciente(pacienteAtivo);
        planoSalvo.setTipo(dto.tipo());
        planoSalvo.setValor(dto.valor());
        planoSalvo.setFrequenciaSemanal(dto.frequenciaSemanal());
        planoSalvo.setDiasSemana(dto.diasSemana());
        planoSalvo.setDataInicio(dto.dataInicio());
        when(planoRepository.save(any())).thenReturn(planoSalvo);

        PlanoResponseDTO response = service.criar(dto);

        assertThat(response.tipo()).isEqualTo(TipoPagamento.MENSAL);
        assertThat(response.frequenciaSemanal()).isEqualTo(FrequenciaSemanal.DUAS_VEZES);
        assertThat(response.diasSemana()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }

    @Test
    void criarPlano_pacienteInativo_lancaExcecao() {
        var dto = new PlanoRequestDTO(
                2L,
                TipoPagamento.MENSAL,
                new BigDecimal("200.00"),
                FrequenciaSemanal.UMA_VEZ,
                List.of(DayOfWeek.MONDAY),
                null);

        when(pacienteRepository.findById(2L)).thenReturn(Optional.of(pacienteInativo));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void criarPlano_frequenciaIncompativel_lancaExcecao() {
        // DUAS_VEZES requer exatamente 2 dias — enviando 3 dias
        var dto = new PlanoRequestDTO(
                1L,
                TipoPagamento.MENSAL,
                new BigDecimal("200.00"),
                FrequenciaSemanal.DUAS_VEZES,
                List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                null);

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteAtivo));

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incompatível");
    }

    @Test
    void criarPlano_desativaPlanoAtivoAnterior() {
        var dto = new PlanoRequestDTO(
                1L,
                TipoPagamento.MENSAL,
                new BigDecimal("250.00"),
                FrequenciaSemanal.UMA_VEZ,
                List.of(DayOfWeek.TUESDAY),
                null);

        Plano planoExistente = new Plano();
        planoExistente.setPaciente(pacienteAtivo);

        when(pacienteRepository.findById(1L)).thenReturn(Optional.of(pacienteAtivo));
        when(planoRepository.findByPacienteIdAndAtivoTrue(1L)).thenReturn(Optional.of(planoExistente));

        Plano novoPlano = new Plano();
        novoPlano.setPaciente(pacienteAtivo);
        novoPlano.setTipo(dto.tipo());
        novoPlano.setValor(dto.valor());
        novoPlano.setFrequenciaSemanal(dto.frequenciaSemanal());
        novoPlano.setDiasSemana(dto.diasSemana());
        novoPlano.setDataInicio(LocalDate.now());
        when(planoRepository.save(any())).thenReturn(novoPlano);

        service.criar(dto);

        assertThat(planoExistente.isAtivo()).isFalse();
        verify(planoRepository, times(2)).save(any()); // um para inativar, outro para criar
    }

    @Test
    void criarPlano_pacienteNaoEncontrado_lancaExcecao() {
        var dto = new PlanoRequestDTO(
                99L,
                TipoPagamento.MENSAL,
                new BigDecimal("200.00"),
                FrequenciaSemanal.UMA_VEZ,
                List.of(DayOfWeek.MONDAY),
                null);

        when(pacienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(dto)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarAtivoPorPaciente_retornaPlanoAtivo() {
        Plano plano = new Plano();
        plano.setPaciente(pacienteAtivo);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.now());

        when(planoRepository.findByPacienteIdAndAtivoTrue(1L)).thenReturn(Optional.of(plano));

        var result = service.buscarAtivoPorPaciente(1L);

        assertThat(result).isPresent();
    }

    @Test
    void inativar_planoAtivo_comSucesso() {
        Plano plano = new Plano();
        plano.setPaciente(pacienteAtivo);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.FRIDAY));
        plano.setDataInicio(LocalDate.now());

        when(planoRepository.findById(1L)).thenReturn(Optional.of(plano));

        service.inativar(1L);

        assertThat(plano.isAtivo()).isFalse();
    }

    @Test
    void inativar_planoJaInativo_lancaExcecao() {
        Plano plano = new Plano();
        plano.setPaciente(pacienteAtivo);
        plano.setAtivo(false);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.FRIDAY));
        plano.setDataInicio(LocalDate.now());

        when(planoRepository.findById(1L)).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.inativar(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já está inativo");
    }

    private void assertReadOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = PlanoService.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
