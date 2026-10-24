package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.security.UsuarioAuditorAware;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Data Auditing contra o schema real da V43 (#109): autor e timestamps preenchidos pelo listener, tanto em
 * tabela que já tinha {@code data_criacao} (anamneses) quanto em tabela que ganhou as colunas agora (pacientes).
 */
@PostgresDataJpaTest
class AuditoriaEntidadesRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    private TestEntityManager entityManager;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criar_comUsuarioAutenticado_devePreencherAutorEDataCriacao() {
        autenticar("recepcao@carlessopilates.com");

        Paciente paciente = entityManager.persistFlushFind(paciente("auditoria.criar@email.com"));

        assertThat(paciente.getCriadoPor()).isEqualTo("recepcao@carlessopilates.com");
        assertThat(paciente.getDataCriacao()).isNotNull();
        assertThat(paciente.getAlteradoPor()).isNull();
        assertThat(paciente.getDataAtualizacao()).isNull();
    }

    @Test
    void alterar_comOutroUsuario_deveRegistrarAlteracaoSemTocarNaCriacao() {
        autenticar("recepcao@carlessopilates.com");
        Anamnese anamnese = entityManager.persistFlushFind(
                anamnese(entityManager.persist(paciente("auditoria.alterar@email.com"))));
        var dataCriacao = anamnese.getDataCriacao();

        autenticar("fisio@carlessopilates.com");
        anamnese.setQueixaPrincipal("Dor cervical");
        entityManager.flush();
        entityManager.clear();

        Anamnese alterada = entityManager.find(Anamnese.class, anamnese.getId());
        assertThat(alterada.getCriadoPor()).isEqualTo("recepcao@carlessopilates.com");
        assertThat(alterada.getDataCriacao()).isEqualTo(dataCriacao);
        assertThat(alterada.getAlteradoPor()).isEqualTo("fisio@carlessopilates.com");
        assertThat(alterada.getDataAtualizacao()).isNotNull().isAfterOrEqualTo(dataCriacao);
    }

    @Test
    void criar_semUsuarioAutenticado_deveRegistrarSystem() {
        Paciente paciente = entityManager.persistFlushFind(paciente("auditoria.system@email.com"));

        assertThat(paciente.getCriadoPor()).isEqualTo(UsuarioAuditorAware.SISTEMA);
    }

    @Test
    void criar_deveIgnorarAutorInformadoManualmente() {
        autenticar("recepcao@carlessopilates.com");
        Paciente novo = paciente("auditoria.manual@email.com");
        novo.setCriadoPor("forjado@email.com");

        Paciente paciente = entityManager.persistFlushFind(novo);

        assertThat(paciente.getCriadoPor()).isEqualTo("recepcao@carlessopilates.com");
    }

    private void autenticar(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        email, null, List.of(new SimpleGrantedAuthority("ROLE_RECEPCAO"))));
    }

    private Paciente paciente(String email) {
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente Auditoria");
        paciente.setEmail(email);
        paciente.setAtivo(true);
        return paciente;
    }

    private Anamnese anamnese(Paciente paciente) {
        Anamnese anamnese = new Anamnese();
        anamnese.setPaciente(paciente);
        anamnese.setQueixaPrincipal("Dor lombar");
        anamnese.setObjetivos("Reduzir dores");
        return anamnese;
    }
}
