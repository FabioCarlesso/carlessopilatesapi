package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalRequestDTO;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.repository.DisponibilidadeProfissionalRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dois cadastros simultâneos de faixas que se cruzam (#216): só um pode passar. Nenhuma constraint do banco impede a
 * sobreposição, então quem garante é o lock no profissional — sem ele, a segunda transação consultaria a tabela sem
 * enxergar a primeira, ainda não comitada, e as duas faixas seriam gravadas.
 */
@SpringBootTest
class DisponibilidadeProfissionalConcurrencyIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private DisponibilidadeProfissionalService service;

    @Autowired
    private DisponibilidadeProfissionalRepository repository;

    @Autowired
    private ProfissionalRepository profissionalRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Profissional profissional;

    @BeforeEach
    void setUp() {
        profissional = new Profissional();
        profissional.setNome("Paula Disponibilidade");
        profissional.setEmail("paula-disponibilidade@email.com");
        profissional.setCpf("90000000316");
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2026, 1, 1));
        profissional = profissionalRepository.save(profissional);
    }

    // Os dados comitam de verdade: sem limpar, vazariam para os demais testes que compartilham o container.
    @AfterEach
    void tearDown() {
        repository.deleteAll(repository.findByProfissionalId(profissional.getId()));
        profissionalRepository.deleteById(profissional.getId());
    }

    @Test
    void doisCadastrosSobrepostosSimultaneos_oSegundoEsperaOCommitERecebeConflito() throws Exception {
        CountDownLatch primeiroComLock = new CountDownLatch(1);
        Long id = profissional.getId();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeiro = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                service.criar(id, faixa(7, 12));
                primeiroComLock.countDown();
                // Segura o lock tempo bastante para o segundo cadastro chegar à checagem e ficar esperando.
                dormir(500);
            }));
            var segundo = executor.submit(() -> {
                assertThat(primeiroComLock.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    service.criar(id, faixa(11, 14));
                    return null;
                } catch (ConflictException e) {
                    return e;
                }
            });

            primeiro.get(10, TimeUnit.SECONDS);
            assertThat(segundo.get(10, TimeUnit.SECONDS)).isInstanceOf(ConflictException.class);
        }

        assertThat(repository.findByProfissionalId(id)).hasSize(1);
    }

    private static DisponibilidadeProfissionalRequestDTO faixa(int inicio, int fim) {
        return new DisponibilidadeProfissionalRequestDTO(
                DayOfWeek.MONDAY, LocalTime.of(inicio, 0), LocalTime.of(fim, 0), null);
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
