package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.dto.PacienteRequestDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresDataJpaTest
@Import(PacienteService.class)
class PacienteServiceIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private PacienteRepository repository;

    @Autowired
    private PacienteService service;

    @BeforeEach
    void setUp() {
        repository.save(paciente("Maria Souza", "maria@email.com", "12345678900", "11912345678", true));
        repository.save(paciente("Mariana Lima", "mariana@email.com", "98765432100", "11999990000", false));
        repository.save(paciente("João Silva", "joao@email.com", "11122233344", "21912340000", true));
    }

    @Test
    void listar_semStatusRetornaAtivosPorPadrao() {
        var resultado = service.listar(null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .extracting("nome")
                .containsExactlyInAnyOrder("Maria Souza", "João Silva");
    }

    @Test
    void listar_filtraPorCamposTextuaisEStatus() {
        var resultado = service.listar("mari", "email.com", "987", "1199", false, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .singleElement()
                .satisfies(paciente -> {
                    assertThat(paciente.nome()).isEqualTo("Mariana Lima");
                    assertThat(paciente.ativo()).isFalse();
                });
    }

    @Test
    void listar_filtroNomeEmBranco_deveIgnorarFiltro() {
        var resultado = service.listar("   ", null, null, null, null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .extracting("nome")
                .containsExactlyInAnyOrder("Maria Souza", "João Silva");
    }

    @Test
    void cadastrar_semEmailEsemCpf_devePersistirViaService() {
        var dto = new PacienteRequestDTO("Cliente Importado", null, null, null, null, null);

        var response = service.cadastrar(dto);

        assertThat(response.email()).isNull();
        assertThat(response.cpf()).isNull();
        Paciente recarregado = repository.findById(response.id()).orElseThrow();
        assertThat(recarregado.getEmail()).isNull();
        assertThat(recarregado.getCpf()).isNull();
    }

    @Test
    void cadastrar_comCpfDuplicado_deveLancarConflict() {
        var primeiro = new PacienteRequestDTO("Primeiro", null, "55566677788", null, null, null);
        var duplicado = new PacienteRequestDTO("Outro", null, "55566677788", null, null, null);

        service.cadastrar(primeiro);

        assertThatThrownBy(() -> service.cadastrar(duplicado))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CPF");
    }

    @Test
    void cadastrar_comEmailDuplicado_deveLancarConflict() {
        var primeiro = new PacienteRequestDTO("Primeiro", "duplicado@email.com", null, null, null, null);
        var duplicado = new PacienteRequestDTO("Outro", "duplicado@email.com", null, null, null, null);

        service.cadastrar(primeiro);

        assertThatThrownBy(() -> service.cadastrar(duplicado))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("E-mail");
    }

    @Test
    void cadastrar_multiplosSemEmailEsemCpf_devePermitir() {
        var primeiro = new PacienteRequestDTO("Importado A", null, null, null, null, null);
        var segundo = new PacienteRequestDTO("Importado B", null, null, null, null, null);

        var r1 = service.cadastrar(primeiro);
        var r2 = service.cadastrar(segundo);

        assertThat(r1.id()).isNotEqualTo(r2.id());
    }

    @Test
    void listar_semAtivoNaoRetornaTodosPacientes() {
        var ativos = service.listar(null, null, null, null, null, PageRequest.of(0, 10));
        var inativos = service.listar(null, null, null, null, false, PageRequest.of(0, 10));

        assertThat(ativos.getTotalElements()).isEqualTo(2);
        assertThat(inativos.getTotalElements()).isEqualTo(1);
        assertThat(ativos.getTotalElements() + inativos.getTotalElements())
                .isEqualTo(3);
    }

    private Paciente paciente(String nome, String email, String cpf, String telefone, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail(email);
        paciente.setCpf(cpf);
        paciente.setTelefone(telefone);
        paciente.setAtivo(ativo);
        return paciente;
    }
}
