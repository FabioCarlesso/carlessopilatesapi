package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@Import(PacienteService.class)
class PacienteServiceIntegrationTest {

    @Autowired
    private PacienteRepository repository;

    @Autowired
    private PacienteService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

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
