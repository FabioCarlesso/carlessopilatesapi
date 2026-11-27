package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.EnderecoDTO;
import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.dto.PacienteResponseDTO;
import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.entity.Endereco;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.Tomador;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.repository.TomadorRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository repository;

    @Mock
    private TomadorRepository tomadorRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private AulaService aulaService;

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
                "Maria Souza",
                "maria@email.com",
                "12345678900",
                "11912345678",
                LocalDate.of(1990, 5, 20),
                new EnderecoDTO("Rua das Flores", "42", "Centro", "São Paulo", "SP", "01001000"),
                null,
                null,
                null);
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
        var dto = new PacienteRequestDTO("João", "joao@email.com", "98765432100", null, null, null, null, null, null);
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

    @Test
    void cadastrar_deveRegistrarDataDeCadastroDeHoje() {
        when(repository.save(any(Paciente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.cadastrar(requestDTO());

        ArgumentCaptor<Paciente> salvo = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getDataCadastro()).isEqualTo(LocalDate.now());
        assertThat(salvo.getValue().getDataInativacao()).isNull();
    }

    // -------------------------------------------------------------------------
    // listar
    // -------------------------------------------------------------------------

    @Test
    void listar_deveRetornarPageComPacientesAtivos() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(paciente()), pageable, 1);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable)))
                .thenReturn(page);

        var resultado = service.listar(null, null, null, null, null, null, pageable);

        assertThat(resultado.getTotalElements()).isEqualTo(1);
        assertThat(resultado.getContent().get(0).nome()).isEqualTo("Maria Souza");
    }

    @Test
    void listar_semPacientes_deveRetornarPageVazia() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        var resultado = service.listar(null, null, null, null, null, null, pageable);

        assertThat(resultado.getTotalElements()).isZero();
        assertThat(resultado.getContent()).isEmpty();
    }

    @Test
    void listar_comFiltros_deveConsultarRepositoryComSpecification() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(paciente()), pageable, 1);
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Paciente>>any(), eq(pageable)))
                .thenReturn(page);

        var resultado = service.listar("maria", "email.com", "123", "119", false, null, pageable);

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
    void atualizar_comVersaoDesatualizada_deveLancarConflitoSemSalvar() {
        Paciente paciente = paciente();
        ReflectionTestUtils.setField(paciente, "version", 3L);
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));

        var dto = new PacienteUpdateDTO("Novo Nome", null, null, null, null, 2L, null, null, null);

        assertThatThrownBy(() -> service.atualizar(1L, dto)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(paciente.getNome()).isEqualTo("Maria Souza");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void atualizar_comVersaoAtual_deveSalvarComFlush() {
        Paciente paciente = paciente();
        ReflectionTestUtils.setField(paciente, "version", 3L);
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));
        when(repository.saveAndFlush(paciente)).thenReturn(paciente);

        var response =
                service.atualizar(1L, new PacienteUpdateDTO("Novo Nome", null, null, null, null, 3L, null, null, null));

        assertThat(response.nome()).isEqualTo("Novo Nome");
        verify(repository).saveAndFlush(paciente);
    }

    @Test
    void atualizar_deveAtualizarApenasOsCamposInformados() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = new PacienteUpdateDTO("Novo Nome", null, "11999999999", null, null, null, null, null, null);
        PacienteResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.nome()).isEqualTo("Novo Nome");
        assertThat(response.email()).isEqualTo("maria@email.com");
        assertThat(response.telefone()).isEqualTo("11999999999");
    }

    // -------------------------------------------------------------------------
    // CPF e preferência de nota fiscal (#229)
    // -------------------------------------------------------------------------

    private PacienteUpdateDTO update(String cpf, Boolean cpfNaNota) {
        return new PacienteUpdateDTO(null, null, null, null, null, null, cpf, cpfNaNota, null);
    }

    private Tomador tomador(Long id, boolean ativo) {
        Tomador t = new Tomador();
        ReflectionTestUtils.setField(t, "id", id);
        t.setNome("Empresa Ltda");
        t.setCpfCnpj("11222333000181");
        t.setAtivo(ativo);
        return t;
    }

    private Paciente salvarComo(Paciente paciente) {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        return paciente;
    }

    @Test
    void atualizar_semCpf_completaOCpf() {
        Paciente paciente = paciente();
        paciente.setCpf(null);
        salvarComo(paciente);
        when(repository.existsByCpf("98765432100")).thenReturn(false);

        assertThat(service.atualizar(1L, update("98765432100", null)).cpf()).isEqualTo("98765432100");
    }

    @Test
    void atualizar_comCpfDeOutroPaciente_deveLancarConflito() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(repository.existsByCpf("98765432100")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(1L, update("98765432100", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CPF");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void atualizar_comCpfVazioOuIgual_mantemOAtualSemConsultarDuplicidade() {
        salvarComo(paciente());

        assertThat(service.atualizar(1L, update("", null)).cpf()).isEqualTo("12345678900");
        assertThat(service.atualizar(1L, update("12345678900", null)).cpf()).isEqualTo("12345678900");
        verify(repository, never()).existsByCpf(any());
    }

    @Test
    void cadastrar_comTomadorAtivo_vinculaOTomador() {
        when(tomadorRepository.findById(3L)).thenReturn(Optional.of(tomador(3L, true)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var dto = new PacienteRequestDTO("Aluno Menor", null, null, null, null, null, true, 3L, null);

        var response = service.cadastrar(dto);

        assertThat(response.cpfNaNota()).isTrue();
        assertThat(response.tomadorId()).isEqualTo(3L);
        assertThat(response.tomadorNome()).isEqualTo("Empresa Ltda");
        assertThat(response.tomadorCpfCnpj()).isEqualTo("11222333000181");
    }

    @Test
    void cadastrar_comTomadorInativo_deveLancarErroDeNegocio() {
        when(tomadorRepository.findById(3L)).thenReturn(Optional.of(tomador(3L, false)));
        var dto = new PacienteRequestDTO("Aluno Menor", null, null, null, null, null, true, 3L, null);

        assertThatThrownBy(() -> service.cadastrar(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tomador inativo");
        verify(repository, never()).save(any());
    }

    @Test
    void vincularTomador_vinculaEDesvincula() {
        Paciente paciente = salvarComo(paciente());
        when(tomadorRepository.findById(3L)).thenReturn(Optional.of(tomador(3L, true)));

        assertThat(service.vincularTomador(1L, 3L).tomadorId()).isEqualTo(3L);
        assertThat(service.vincularTomador(1L, null).tomadorId()).isNull();
        assertThat(paciente.getTomador()).isNull();
    }

    @Test
    void vincularTomador_inexistente_deveLancar404() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(tomadorRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vincularTomador(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void vincularTomador_inativo_deveLancarErroDeNegocio() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(tomadorRepository.findById(3L)).thenReturn(Optional.of(tomador(3L, false)));

        assertThatThrownBy(() -> service.vincularTomador(1L, 3L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void vincularTomador_mesmoTomadorJaVinculadoEInativadoDepois_mantemSemConsultar() {
        Paciente paciente = paciente();
        paciente.setTomador(tomador(3L, false));
        salvarComo(paciente);

        assertThat(service.vincularTomador(1L, 3L).tomadorId()).isEqualTo(3L);
        verify(tomadorRepository, never()).findById(any());
    }

    @Test
    void atualizar_desmarcarCpfNaNota_naoDesvinculaOTomador() {
        Paciente paciente = paciente();
        paciente.setCpfNaNota(true);
        paciente.setTomador(tomador(3L, true));
        salvarComo(paciente);

        var response = service.atualizar(1L, update(null, false));

        assertThat(response.cpfNaNota()).isFalse();
        assertThat(response.tomadorId()).isEqualTo(3L);
    }

    @Test
    void cadastrar_semPreferencia_naoDesejaCpfNaNota() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.existsByCpf(any())).thenReturn(false);

        assertThat(service.cadastrar(requestDTO()).cpfNaNota()).isFalse();
    }

    @Test
    void atualizar_comEndereco_deveAtualizarEndereco() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var novoEndereco = new EnderecoDTO("Av. Paulista", "1000", "Bela Vista", "São Paulo", "SP", "01310100");
        var dto = new PacienteUpdateDTO(null, null, null, null, novoEndereco, null, null, null, null);
        PacienteResponseDTO response = service.atualizar(1L, dto);

        assertThat(response.endereco().logradouro()).isEqualTo("Av. Paulista");
        assertThat(response.endereco().numero()).isEqualTo("1000");
    }

    @Test
    void atualizar_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(
                        99L, new PacienteUpdateDTO(null, null, null, null, null, null, null, null, null)))
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
        assertThat(existente.getDataInativacao()).isEqualTo(LocalDate.now());
    }

    @Test
    void inativar_pacienteJaInativo_deveManterADataDeInativacaoOriginal() {
        Paciente existente = paciente();
        existente.setAtivo(false);
        existente.setDataInativacao(LocalDate.of(2026, 1, 15));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        service.inativar(1L);

        assertThat(existente.getDataInativacao()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void ativar_deveReativarELimparADataDeInativacao() {
        Paciente existente = paciente();
        existente.setAtivo(false);
        existente.setDataInativacao(LocalDate.of(2026, 1, 15));
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        service.ativar(1L);

        assertThat(existente.isAtivo()).isTrue();
        assertThat(existente.getDataInativacao()).isNull();
        // Inativo, ele ficou fora da marcação de feriado (#244).
        verify(aulaService).marcarFeriadosDoPaciente(existente.getId());
    }

    @Test
    void ativar_pacienteJaAtivo_naoRefazAMarcacaoDeFeriado() {
        Paciente existente = paciente();
        when(repository.findById(1L)).thenReturn(Optional.of(existente));

        service.ativar(1L);

        verify(aulaService, never()).marcarFeriadosDoPaciente(any());
    }

    @Test
    void inativar_quandoNaoExistente_deveLancarResourceNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inativar(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // Fisioterapeuta padrão (#245)
    // -------------------------------------------------------------------------

    private Profissional profissional(Long id, boolean ativo) {
        Profissional profissional = new Profissional();
        profissional.setId(id);
        profissional.setNome("Camila");
        profissional.setAtivo(ativo);
        return profissional;
    }

    @Test
    void definirProfissionalPadrao_ativo_vinculaEDevolveNoDTO() {
        Paciente paciente = paciente();
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));
        when(profissionalRepository.findById(7L)).thenReturn(Optional.of(profissional(7L, true)));
        when(repository.saveAndFlush(paciente)).thenReturn(paciente);

        var response = service.definirProfissionalPadrao(1L, 7L);

        assertThat(response.profissionalPadraoId()).isEqualTo(7L);
        assertThat(response.profissionalPadraoNome()).isEqualTo("Camila");
    }

    @Test
    void definirProfissionalPadrao_null_removeOPadrao() {
        Paciente paciente = paciente();
        paciente.setProfissionalPadrao(profissional(7L, true));
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));
        when(repository.saveAndFlush(paciente)).thenReturn(paciente);

        var response = service.definirProfissionalPadrao(1L, null);

        assertThat(response.profissionalPadraoId()).isNull();
        verify(profissionalRepository, never()).findById(any());
    }

    @Test
    void definirProfissionalPadrao_inativo_lancaBusinessException() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(profissionalRepository.findById(7L)).thenReturn(Optional.of(profissional(7L, false)));

        assertThatThrownBy(() -> service.definirProfissionalPadrao(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void definirProfissionalPadrao_inexistente_lancaNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(profissionalRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definirProfissionalPadrao(1L, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void atualizar_mesmoPadraoInativadoDepois_mantemSemRecusar() {
        Paciente paciente = paciente();
        paciente.setProfissionalPadrao(profissional(7L, false));
        when(repository.findById(1L)).thenReturn(Optional.of(paciente));
        when(repository.saveAndFlush(paciente)).thenReturn(paciente);

        var response = service.atualizar(1L, new PacienteUpdateDTO(null, null, null, null, null, null, null, null, 7L));

        assertThat(response.profissionalPadraoId()).isEqualTo(7L);
        verify(profissionalRepository, never()).findById(any());
    }
}
