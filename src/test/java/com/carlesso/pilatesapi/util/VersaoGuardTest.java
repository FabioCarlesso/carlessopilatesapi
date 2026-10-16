package com.carlesso.pilatesapi.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.entity.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class VersaoGuardTest {

    @Test
    void exigirVersaoAtual_versaoIgual_devePassar() {
        assertThatCode(() -> VersaoGuard.exigirVersaoAtual(3L, 3L, Paciente.class, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void exigirVersaoAtual_versaoDesatualizada_deveLancarConflitoDeConcorrencia() {
        assertThatThrownBy(() -> VersaoGuard.exigirVersaoAtual(2L, 3L, Paciente.class, 1L))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void exigirVersaoAtual_semVersao_deveSeguirNoModoLegado() {
        assertThatCode(() -> VersaoGuard.exigirVersaoAtual(null, 3L, Paciente.class, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void exigirVersaoAtual_versaoEnviadaParaRegistroAindaNaoPersistido_deveLancarConflito() {
        assertThatThrownBy(() -> VersaoGuard.exigirVersaoAtual(0L, null, Paciente.class, null))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
