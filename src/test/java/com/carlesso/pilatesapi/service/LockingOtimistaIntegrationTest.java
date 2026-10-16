package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.dto.PacienteUpdateDTO;
import com.carlesso.pilatesapi.dto.UserUpdateDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;

/** Locking otimista das entidades editáveis contra o schema real (coluna {@code version} da V40). Ver issue #101. */
@SpringBootTest
class LockingOtimistaIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private PacienteService pacienteService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private final List<Long> pacientesCriados = new ArrayList<>();
    private final List<Long> usuariosCriados = new ArrayList<>();

    /** As transações aqui são reais (commitadas): o que fica no banco vazaria para as contagens de outras classes. */
    @AfterEach
    void limpar() {
        pacienteRepository.deleteAllById(pacientesCriados);
        userRepository.deleteAllById(usuariosCriados);
    }

    @Test
    void atualizar_comVersaoAtual_deveIncrementarVersaoEPersistir() {
        Paciente paciente = salvarPaciente();
        assertThat(paciente.getVersion()).isZero();

        var response = pacienteService.atualizar(paciente.getId(), atualizacao("Nome Novo", 0L));

        assertThat(response.version()).isEqualTo(1L);
        Paciente recarregado = pacienteRepository.findById(paciente.getId()).orElseThrow();
        assertThat(recarregado.getNome()).isEqualTo("Nome Novo");
        assertThat(recarregado.getVersion()).isEqualTo(1L);
    }

    @Test
    void atualizar_comVersaoDaResposta_devePermitirSalvarDeNovo() {
        Paciente paciente = salvarPaciente();

        var primeira = pacienteService.atualizar(paciente.getId(), atualizacao("Primeiro", 0L));
        var segunda = pacienteService.atualizar(paciente.getId(), atualizacao("Segundo", primeira.version()));

        assertThat(segunda.version()).isEqualTo(2L);
        assertThat(segunda.nome()).isEqualTo("Segundo");
    }

    @Test
    void atualizar_comVersaoDesatualizada_deveLancarConflitoSemPersistir() {
        Paciente paciente = salvarPaciente();
        pacienteService.atualizar(paciente.getId(), atualizacao("Quem salvou antes", 0L));

        assertThatThrownBy(() -> pacienteService.atualizar(paciente.getId(), atualizacao("Edição perdida", 0L)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Paciente recarregado = pacienteRepository.findById(paciente.getId()).orElseThrow();
        assertThat(recarregado.getNome()).isEqualTo("Quem salvou antes");
        assertThat(recarregado.getVersion()).isEqualTo(1L);
    }

    @Test
    void atualizar_semVersao_deveSeguirNoModoLegado() {
        Paciente paciente = salvarPaciente();
        pacienteService.atualizar(paciente.getId(), atualizacao("Primeiro", 0L));

        var response = pacienteService.atualizar(paciente.getId(), atualizacao("Cliente antigo", null));

        assertThat(response.nome()).isEqualTo("Cliente antigo");
        assertThat(response.version()).isEqualTo(2L);
    }

    @Test
    void atualizarConcorrente_comMesmaVersao_deveAceitarUmERecusarOOutro() throws Exception {
        Paciente paciente = salvarPaciente();
        Long id = paciente.getId();
        CountDownLatch largada = new CountDownLatch(1);

        Callable<Throwable> recepcionista1 = () -> executarCapturandoErro(() -> {
            aguardarLargada(largada);
            pacienteService.atualizar(id, atualizacao("Recepcionista 1", 0L));
        });
        Callable<Throwable> recepcionista2 = () -> executarCapturandoErro(() -> {
            aguardarLargada(largada);
            pacienteService.atualizar(id, atualizacao("Recepcionista 2", 0L));
        });

        List<Throwable> erros;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var resultado1 = executor.submit(recepcionista1);
            var resultado2 = executor.submit(recepcionista2);
            largada.countDown();
            erros = Stream.of(resultado1.get(10, TimeUnit.SECONDS), resultado2.get(10, TimeUnit.SECONDS))
                    .filter(erro -> erro != null)
                    .toList();
        }

        assertThat(erros).singleElement().isInstanceOf(OptimisticLockingFailureException.class);
        Paciente recarregado = pacienteRepository.findById(id).orElseThrow();
        assertThat(recarregado.getVersion()).isEqualTo(1L);
        assertThat(recarregado.getNome()).isIn("Recepcionista 1", "Recepcionista 2");
    }

    @Test
    void atualizarUsuario_comVersaoDesatualizada_deveLancarConflito() {
        User user = userRepository.save(usuario());
        usuariosCriados.add(user.getId());
        var atualizado = userService.atualizar(
                user.getId(), new UserUpdateDTO("Nome Novo", null, null, null, 0L), "operador@email.com");
        assertThat(atualizado.version()).isEqualTo(1L);

        assertThatThrownBy(() -> userService.atualizar(
                        user.getId(), new UserUpdateDTO("Outro Nome", null, null, null, 0L), "operador@email.com"))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private PacienteUpdateDTO atualizacao(String nome, Long version) {
        return new PacienteUpdateDTO(nome, null, null, null, null, version);
    }

    private Paciente salvarPaciente() {
        Paciente paciente = pacienteRepository.save(paciente());
        pacientesCriados.add(paciente.getId());
        return paciente;
    }

    private Paciente paciente() {
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente Concorrência");
        paciente.setAtivo(true);
        return paciente;
    }

    private User usuario() {
        User user = new User();
        user.setName("Usuário Concorrência");
        user.setEmail(UUID.randomUUID() + "@email.com");
        user.setPassword("hash");
        user.setRole(Role.USER);
        user.setAtivo(true);
        return user;
    }

    private Throwable executarCapturandoErro(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable erro) {
            return erro;
        }
    }

    private void aguardarLargada(CountDownLatch largada) {
        try {
            assertThat(largada.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
