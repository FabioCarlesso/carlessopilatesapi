package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.PreferenciasUsuario;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.IdiomaPreferencia;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.entity.enums.TemaPreferencia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresDataJpaTest
class PreferenciasUsuarioRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    private PreferenciasUsuarioRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistirEBuscarPorUserId_devePreservarEnumsEFlags() {
        User user = persistirUsuario("persist@email.com");
        PreferenciasUsuario salvas = entityManager.persistAndFlush(
                preferencias(user, IdiomaPreferencia.EN_US, TemaPreferencia.ESCURO, false, true));
        entityManager.clear();

        Optional<PreferenciasUsuario> encontradas = repository.findByUserId(user.getId());

        assertThat(encontradas).isPresent();
        assertThat(encontradas.get().getId()).isEqualTo(salvas.getId());
        assertThat(encontradas.get().getIdioma()).isEqualTo(IdiomaPreferencia.EN_US);
        assertThat(encontradas.get().getTema()).isEqualTo(TemaPreferencia.ESCURO);
        assertThat(encontradas.get().isNotificacoesEmail()).isFalse();
        assertThat(encontradas.get().isNotificacoesPush()).isTrue();
        assertThat(encontradas.get().getDataCriacao()).isNotNull();
    }

    @Test
    void findByUserEmail_deveRetornarPreferenciasDoUsuario() {
        User user = persistirUsuario("byemail@email.com");
        entityManager.persistAndFlush(
                preferencias(user, IdiomaPreferencia.ES_ES, TemaPreferencia.CLARO, true, true));
        entityManager.clear();

        Optional<PreferenciasUsuario> encontradas = repository.findByUserEmail("byemail@email.com");

        assertThat(encontradas).isPresent();
        assertThat(encontradas.get().getIdioma()).isEqualTo(IdiomaPreferencia.ES_ES);
        assertThat(encontradas.get().getTema()).isEqualTo(TemaPreferencia.CLARO);
    }

    @Test
    void findByUserEmail_quandoSemPreferencias_deveRetornarVazio() {
        persistirUsuario("sem-prefs@email.com");

        assertThat(repository.findByUserEmail("sem-prefs@email.com")).isEmpty();
    }

    @Test
    void persistir_dois_registros_paraMesmoUsuario_deveFalharPorUniqueConstraint() {
        User user = persistirUsuario("unique@email.com");
        entityManager.persistAndFlush(
                preferencias(user, IdiomaPreferencia.PT_BR, TemaPreferencia.CLARO, true, false));

        PreferenciasUsuario duplicada = preferencias(user, IdiomaPreferencia.EN_US, TemaPreferencia.ESCURO, false, true);

        assertThatThrownBy(() -> entityManager.persistAndFlush(duplicada))
                .isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }

    @Test
    void atualizar_devePreencherDataAtualizacao() {
        User user = persistirUsuario("update@email.com");
        PreferenciasUsuario salvas = entityManager.persistAndFlush(
                preferencias(user, IdiomaPreferencia.PT_BR, TemaPreferencia.CLARO, true, false));
        assertThat(salvas.getDataAtualizacao()).isNull();

        salvas.setTema(TemaPreferencia.ESCURO);
        entityManager.persistAndFlush(salvas);
        entityManager.clear();

        PreferenciasUsuario recarregadas = repository.findById(salvas.getId()).orElseThrow();
        assertThat(recarregadas.getDataAtualizacao()).isNotNull();
        assertThat(recarregadas.getTema()).isEqualTo(TemaPreferencia.ESCURO);
    }

    private User persistirUsuario(String email) {
        User user = new User();
        user.setName("Usuário " + email);
        user.setEmail(email);
        user.setPassword("hash");
        user.setRole(Role.USER);
        user.setAtivo(true);
        return entityManager.persist(user);
    }

    private PreferenciasUsuario preferencias(
            User user,
            IdiomaPreferencia idioma,
            TemaPreferencia tema,
            boolean notificacoesEmail,
            boolean notificacoesPush) {
        PreferenciasUsuario p = new PreferenciasUsuario();
        p.setUser(user);
        p.setIdioma(idioma);
        p.setTema(tema);
        p.setNotificacoesEmail(notificacoesEmail);
        p.setNotificacoesPush(notificacoesPush);
        return p;
    }
}
