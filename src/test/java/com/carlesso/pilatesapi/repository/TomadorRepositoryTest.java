package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Tomador;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Tomadores da NFSE (#229, V47): unicidade e formato do CPF/CNPJ no banco, e vínculo com vários pacientes. */
@PostgresDataJpaTest
class TomadorRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    TomadorRepository repository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Test
    void umTomadorAtendeVariosPacientes_eAContagemAgrupaPorTomador() {
        Tomador empresa = repository.saveAndFlush(tomador("Empresa Ltda", "11222333000181"));
        Tomador mae = repository.saveAndFlush(tomador("Mãe", "12345678909"));
        pacienteRepository.saveAndFlush(paciente("Ana", empresa));
        pacienteRepository.saveAndFlush(paciente("Bia", empresa));
        pacienteRepository.saveAndFlush(paciente("Caio", mae));
        pacienteRepository.saveAndFlush(paciente("Davi", null));

        var contagem = pacienteRepository.contarPorTomador(List.of(empresa.getId(), mae.getId()));

        assertThat(contagem)
                .extracting(linha -> List.of(linha[0], linha[1]))
                .containsExactlyInAnyOrder(List.of(empresa.getId(), 2L), List.of(mae.getId(), 1L));
        assertThat(pacienteRepository.findByTomadorIdOrderByNomeAsc(empresa.getId()))
                .extracting(Paciente::getNome)
                .containsExactly("Ana", "Bia");
    }

    @Test
    void cpfCnpjDuplicado_eRecusadoPeloBanco() {
        repository.saveAndFlush(tomador("Empresa Ltda", "11222333000181"));

        assertThatThrownBy(() -> repository.saveAndFlush(tomador("Outra", "11222333000181")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cpfCnpjForaDoFormato_eRecusadoPeloCheck() {
        assertThatThrownBy(() -> repository.saveAndFlush(tomador("Empresa", "123")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Tomador tomador(String nome, String cpfCnpj) {
        Tomador t = new Tomador();
        t.setNome(nome);
        t.setCpfCnpj(cpfCnpj);
        return t;
    }

    private Paciente paciente(String nome, Tomador tomador) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setCpfNaNota(tomador != null);
        p.setTomador(tomador);
        return p;
    }
}
