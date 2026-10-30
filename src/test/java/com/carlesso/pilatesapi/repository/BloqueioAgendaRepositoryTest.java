package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@PostgresDataJpaTest
class BloqueioAgendaRepositoryTest extends PostgresTestcontainerSupport {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 9, 30);

    @Autowired
    BloqueioAgendaRepository repository;

    @Autowired
    TestEntityManager entityManager;

    /**
     * Os cenários abaixo asseram sobre o conteúdo <em>absoluto</em> da tabela, e o container Postgres é singleton,
     * compartilhado com testes que comitam. Quem cria bloqueios via HTTP já limpa depois de si, mas depender disso
     * deixaria estes testes reféns da ordem da suíte e da higiene alheia — a garantia fica aqui.
     */
    @BeforeEach
    void limparBloqueios() {
        repository.deleteAll();
        entityManager.flush();
    }

    @Test
    void findQueIntersectamPeriodo_retornaBloqueioTotalmenteDentroDoPeriodo() {
        persistir(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), "Independência");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM))
                .extracting(BloqueioAgenda::getMotivo)
                .containsExactly("Independência");
    }

    @Test
    void findQueIntersectamPeriodo_retornaBloqueioQueComecaAntesDoPeriodo() {
        persistir(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 3), "Férias coletivas");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM))
                .extracting(BloqueioAgenda::getMotivo)
                .containsExactly("Férias coletivas");
    }

    @Test
    void findQueIntersectamPeriodo_retornaBloqueioQueTerminaDepoisDoPeriodo() {
        persistir(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5), "Reforma");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM))
                .extracting(BloqueioAgenda::getMotivo)
                .containsExactly("Reforma");
    }

    @Test
    void findQueIntersectamPeriodo_retornaBloqueioQueEnglobaOPeriodoInteiro() {
        persistir(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "Obra na sala principal");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM))
                .extracting(BloqueioAgenda::getMotivo)
                .containsExactly("Obra na sala principal");
    }

    @Test
    void findQueIntersectamPeriodo_incluiBloqueiosQueTocamApenasAsPontasDoPeriodo() {
        persistir(LocalDate.of(2026, 8, 20), INICIO, "Termina no primeiro dia");
        persistir(FIM, LocalDate.of(2026, 10, 10), "Começa no último dia");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM))
                .extracting(BloqueioAgenda::getMotivo)
                .containsExactly("Termina no primeiro dia", "Começa no último dia");
    }

    @Test
    void findQueIntersectamPeriodo_ignoraBloqueioForaDoPeriodo() {
        persistir(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "Mês anterior");
        persistir(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Mês seguinte");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM)).isEmpty();
    }

    @Test
    void findQueIntersectamPeriodo_ordenaPorDataInicio() {
        persistir(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), "Terceiro");
        persistir(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5), "Primeiro");
        persistir(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12), "Segundo");

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM))
                .extracting(BloqueioAgenda::getMotivo)
                .containsExactly("Primeiro", "Segundo", "Terceiro");
    }

    @Test
    void findQueIntersectamPeriodo_permiteDoisBloqueiosNoMesmoDia() {
        LocalDate dia = LocalDate.of(2026, 9, 7);
        persistir(dia, dia, "Feriado");
        BloqueioAgenda comHorario = bloqueio(dia, dia, "Manutenção");
        comHorario.setHoraInicio(LocalTime.of(8, 0));
        comHorario.setHoraFim(LocalTime.of(12, 0));
        entityManager.persist(comHorario);
        entityManager.flush();

        assertThat(repository.findQueIntersectamPeriodo(INICIO, FIM)).hasSize(2);
    }

    @Test
    void persistir_semFaixaDeHorario_marcaBloqueioComoDiaInteiro() {
        persistir(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), "Independência");
        entityManager.clear();

        BloqueioAgenda salvo = repository.findQueIntersectamPeriodo(INICIO, FIM).getFirst();

        assertThat(salvo.isDiaInteiro()).isTrue();
        // dataCriacao é preenchida pelo Spring Data Auditing (o helper não a define).
        assertThat(salvo.getDataCriacao()).isNotNull();
        assertThat(salvo.getDataAtualizacao()).isNull();
    }

    @Test
    void atualizar_preencheDataAtualizacaoPelaAuditoria() {
        persistir(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), "Independência");
        entityManager.clear();

        BloqueioAgenda salvo = repository.findQueIntersectamPeriodo(INICIO, FIM).getFirst();
        salvo.setMotivo("Independência (confirmado)");
        entityManager.flush();

        assertThat(salvo.getDataAtualizacao()).isNotNull();
        assertThat(salvo.getDataAtualizacao()).isAfterOrEqualTo(salvo.getDataCriacao());
    }

    private void persistir(LocalDate dataInicio, LocalDate dataFim, String motivo) {
        entityManager.persist(bloqueio(dataInicio, dataFim, motivo));
        entityManager.flush();
    }

    private BloqueioAgenda bloqueio(LocalDate dataInicio, LocalDate dataFim, String motivo) {
        BloqueioAgenda bloqueio = new BloqueioAgenda();
        bloqueio.setDataInicio(dataInicio);
        bloqueio.setDataFim(dataFim);
        bloqueio.setMotivo(motivo);
        return bloqueio;
    }
}
