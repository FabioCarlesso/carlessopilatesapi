package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.ReavaliacaoRequestDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoResponseDTO;
import com.carlesso.pilatesapi.dto.ReavaliacaoUpdateDTO;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.AvaliacaoFisioterapeuticaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PlanoTratamentoRepository;
import com.carlesso.pilatesapi.repository.ReavaliacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReavaliacaoServiceTest {

    @Mock
    private ReavaliacaoRepository reavaliacaoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private AvaliacaoFisioterapeuticaRepository avaliacaoRepository;

    @Mock
    private PlanoTratamentoRepository planoTratamentoRepository;

    @InjectMocks
    private ReavaliacaoService service;

    private Paciente paciente() {
        Paciente p = new Paciente();
        p.setNome("Carlos Silva");
        p.setEmail("carlos@email.com");
        p.setCpf("98765432100");
        setFieldId(p, Paciente.class, 1L);
        return p;
    }

    private Paciente outroPaciente() {
        Paciente p = new Paciente();
        p.setNome("Bruna Souza");
        p.setEmail("bruna@email.com");
        p.setCpf("12345678900");
        setFieldId(p, Paciente.class, 2L);
        return p;
    }

    private Reavaliacao reavaliacao(Paciente paciente) {
        Reavaliacao r = new Reavaliacao();
        r.setPaciente(paciente);
        r.setDataReavaliacao(LocalDate.of(2026, 5, 1));
        r.setComparativoAvaliacaoAnterior("Melhora geral observada");
        r.setEvolucaoDor("Dor reduziu de 7 para 4");
        r.setEvolucaoForca("Ganho de força nos extensores");
        r.setEvolucaoMobilidade("Amplitude de quadril aumentou 15°");
        r.setEvolucaoFuncional("Consegue subir escadas sem dor");
        r.setObjetivosAlcancados("Retorno às atividades diárias");
        r.setPontosAtencao("Ainda apresenta dor ao agachar");
        r.setAjustesRecomendados("Aumentar carga nos exercícios de glúteo");
        r.setObservacoesGerais("Paciente motivado com a evolução");
        r.setDataCriacao(LocalDateTime.of(2026, 5, 1, 10, 0));
        setFieldId(r, Reavaliacao.class, 1L);
        return r;
    }

    private ReavaliacaoRequestDTO requestDTO() {
        return new ReavaliacaoRequestDTO(
                1L,
                null,
                null,
                LocalDate.of(2026, 5, 1),
                "Melhora geral observada",
                "Dor reduziu de 7 para 4",
                "Ganho de força nos extensores",
                "Amplitude de quadril aumentou 15°",
                "Consegue subir escadas sem dor",
                "Retorno às atividades diárias",
                "Ainda apresenta dor ao agachar",
                "Aumentar carga nos exercícios de glúteo",
                "Paciente motivado com a evolução"
        );
    }

    private void setFieldId(Object obj, Class<?> clazz, Long id) {
        try {
            var field = clazz.getDeclaredField("id");
            field.setAccessible(true);
            field.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void criar_comPacienteValido_deveRetornarResponseDTO() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(reavaliacaoRepository.save(any(Reavaliacao.class))).thenReturn(reavaliacao(p));

        ReavaliacaoResponseDTO response = service.criar(requestDTO());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.pacienteId()).isEqualTo(1L);
        assertThat(response.nomePaciente()).isEqualTo("Carlos Silva");
        assertThat(response.dataReavaliacao()).isEqualTo(LocalDate.of(2026, 5, 1));
        verify(reavaliacaoRepository).save(any(Reavaliacao.class));
    }

    @Test
    void criar_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.findByIdAndAtivoTrue(99L)).thenReturn(Optional.empty());

        var dto = new ReavaliacaoRequestDTO(
                99L, null, null, LocalDate.of(2026, 5, 1),
                null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comAvaliacaoFisioterapeuticaVinculada_deveAssociarCorretamente() {
        Paciente p = paciente();
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(p);
        setFieldId(avaliacao, AvaliacaoFisioterapeutica.class, 2L);

        Reavaliacao reavaliacaoComVinculo = reavaliacao(p);
        reavaliacaoComVinculo.setAvaliacaoFisioterapeutica(avaliacao);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(avaliacaoRepository.findAtivaById(2L)).thenReturn(Optional.of(avaliacao));
        when(reavaliacaoRepository.save(any(Reavaliacao.class))).thenReturn(reavaliacaoComVinculo);

        var dto = new ReavaliacaoRequestDTO(
                1L, 2L, null, LocalDate.of(2026, 5, 1),
                null, null, null, null, null, null, null, null, null
        );

        ReavaliacaoResponseDTO response = service.criar(dto);

        assertThat(response.avaliacaoFisioterapeuticaId()).isEqualTo(2L);
    }

    @Test
    void criar_comPlanoTratamentoVinculado_deveAssociarCorretamente() {
        Paciente p = paciente();
        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(p);
        setFieldId(plano, PlanoTratamento.class, 3L);

        Reavaliacao reavaliacaoComPlano = reavaliacao(p);
        reavaliacaoComPlano.setPlanoTratamento(plano);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(planoTratamentoRepository.findAtivoById(3L)).thenReturn(Optional.of(plano));
        when(reavaliacaoRepository.save(any(Reavaliacao.class))).thenReturn(reavaliacaoComPlano);

        var dto = new ReavaliacaoRequestDTO(
                1L, null, 3L, LocalDate.of(2026, 5, 1),
                null, null, null, null, null, null, null, null, null
        );

        ReavaliacaoResponseDTO response = service.criar(dto);

        assertThat(response.planoTratamentoId()).isEqualTo(3L);
    }

    @Test
    void criar_comAvaliacaoFisioterapeuticaInexistente_deveLancarResourceNotFoundException() {
        Paciente p = paciente();
        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(avaliacaoRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        var dto = new ReavaliacaoRequestDTO(
                1L, 99L, null, LocalDate.of(2026, 5, 1),
                null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void criar_comAvaliacaoFisioterapeuticaDeOutroPaciente_deveLancarBusinessException() {
        Paciente p = paciente();
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(outroPaciente());
        setFieldId(avaliacao, AvaliacaoFisioterapeutica.class, 2L);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(avaliacaoRepository.findAtivaById(2L)).thenReturn(Optional.of(avaliacao));

        var dto = new ReavaliacaoRequestDTO(
                1L, 2L, null, LocalDate.of(2026, 5, 1),
                null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence ao paciente");
    }

    @Test
    void criar_comPlanoTratamentoDeOutroPaciente_deveLancarBusinessException() {
        Paciente p = paciente();
        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(outroPaciente());
        setFieldId(plano, PlanoTratamento.class, 3L);

        when(pacienteRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(p));
        when(planoTratamentoRepository.findAtivoById(3L)).thenReturn(Optional.of(plano));

        var dto = new ReavaliacaoRequestDTO(
                1L, null, 3L, LocalDate.of(2026, 5, 1),
                null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.criar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pertence ao paciente");
    }

    @Test
    void buscarPorId_quandoExistente_deveRetornarResponseDTO() {
        when(reavaliacaoRepository.findAtivaById(1L)).thenReturn(Optional.of(reavaliacao(paciente())));

        ReavaliacaoResponseDTO response = service.buscarPorId(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.evolucaoDor()).isEqualTo("Dor reduziu de 7 para 4");
    }

    @Test
    void buscarPorId_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(reavaliacaoRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listarPorPaciente_deveRetornarReavaliacoesDoPaciente() {
        Paciente p = paciente();
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(reavaliacaoRepository.findAtivasByPacienteOrdenadas(1L)).thenReturn(List.of(reavaliacao(p)));

        List<ReavaliacaoResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().pacienteId()).isEqualTo(1L);
        assertThat(response.getFirst().dataReavaliacao()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void listarPorPaciente_comPacienteAtivoSemReavaliacoes_deveRetornarListaVazia() {
        when(pacienteRepository.existsByIdAndAtivoTrue(1L)).thenReturn(true);
        when(reavaliacaoRepository.findAtivasByPacienteOrdenadas(1L)).thenReturn(List.of());

        List<ReavaliacaoResponseDTO> response = service.listarPorPaciente(1L);

        assertThat(response).isEmpty();
    }

    @Test
    void listarPorPaciente_comPacienteInexistente_deveLancarResourceNotFoundException() {
        when(pacienteRepository.existsByIdAndAtivoTrue(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listarPorPaciente(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        Reavaliacao r = reavaliacao(paciente());
        when(reavaliacaoRepository.findAtivaById(1L)).thenReturn(Optional.of(r));
        when(reavaliacaoRepository.save(r)).thenReturn(r);

        var dto = new ReavaliacaoUpdateDTO(
                LocalDate.of(2026, 5, 10),
                "Evolução excelente",
                "Sem dor",
                null, null, null, null, null, null, null
        );

        ReavaliacaoResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.dataReavaliacao()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(response.comparativoAvaliacaoAnterior()).isEqualTo("Evolução excelente");
        assertThat(response.evolucaoDor()).isEqualTo("Sem dor");
        assertThat(response.evolucaoForca()).isEqualTo("Ganho de força nos extensores");
        assertThat(response.dataAtualizacao()).isNotNull();
    }

    @Test
    void atualizar_comReavaliacaoInexistente_deveLancarResourceNotFoundException() {
        when(reavaliacaoRepository.findAtivaById(99L)).thenReturn(Optional.empty());

        var dto = new ReavaliacaoUpdateDTO(null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
