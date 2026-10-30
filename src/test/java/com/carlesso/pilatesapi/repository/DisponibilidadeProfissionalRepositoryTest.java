package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Consulta de sobreposição contra o schema real da V46 (#216). Cada teste cria seu próprio profissional, então a
 * asserção não depende do conteúdo deixado na tabela por outros testes do container compartilhado.
 */
@PostgresDataJpaTest
class DisponibilidadeProfissionalRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    DisponibilidadeProfissionalRepository repository;

    @Autowired
    TestEntityManager entityManager;

    private Profissional profissional;
    private DisponibilidadeProfissional manha;

    @BeforeEach
    void setUp() {
        profissional = entityManager.persist(profissional("disp-repo@email.com", "90000000216"));
        // Segunda 12:00–14:00, a faixa de referência dos cenários de sobreposição.
        manha = persistir(profissional, DayOfWeek.MONDAY, 12, 14);
    }

    @Test
    void existeSobreposicao_inicioDentroDaFaixa() {
        assertThat(sobrepoe(DayOfWeek.MONDAY, 13, 15)).isTrue();
    }

    @Test
    void existeSobreposicao_fimDentroDaFaixa() {
        assertThat(sobrepoe(DayOfWeek.MONDAY, 11, 13)).isTrue();
    }

    @Test
    void existeSobreposicao_faixaQueEnglobaAExistente() {
        assertThat(sobrepoe(DayOfWeek.MONDAY, 8, 19)).isTrue();
    }

    @Test
    void existeSobreposicao_faixaContidaNaExistente() {
        assertThat(sobrepoe(DayOfWeek.MONDAY, 12, 13)).isTrue();
    }

    @Test
    void existeSobreposicao_faixasQueSoEncostamNaoConflitam() {
        assertThat(sobrepoe(DayOfWeek.MONDAY, 14, 19)).isFalse();
        assertThat(sobrepoe(DayOfWeek.MONDAY, 7, 12)).isFalse();
    }

    @Test
    void existeSobreposicao_outroDiaNaoConflita() {
        assertThat(sobrepoe(DayOfWeek.TUESDAY, 12, 14)).isFalse();
    }

    @Test
    void existeSobreposicao_outroProfissionalNaoConflita() {
        Profissional outro = entityManager.persist(profissional("disp-repo-outro@email.com", "90000000217"));

        assertThat(repository.existeSobreposicao(
                        outro.getId(), DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0), 0L))
                .isFalse();
    }

    @Test
    void existeSobreposicao_ignoraAPropriaFaixa() {
        assertThat(repository.existeSobreposicao(
                        profissional.getId(),
                        DayOfWeek.MONDAY,
                        LocalTime.of(12, 0),
                        LocalTime.of(15, 0),
                        manha.getId()))
                .isFalse();
    }

    @Test
    void findByProfissionalIdAndDiaSemana_trazSoAsFaixasDoDia() {
        persistir(profissional, DayOfWeek.MONDAY, 14, 19);
        persistir(profissional, DayOfWeek.FRIDAY, 7, 12);

        assertThat(repository.findByProfissionalIdAndDiaSemana(profissional.getId(), DayOfWeek.MONDAY))
                .hasSize(2);
        assertThat(repository.existsByProfissionalId(profissional.getId())).isTrue();
        assertThat(repository.findByProfissionalId(profissional.getId())).hasSize(3);
    }

    @Test
    void persistir_preencheAuditoriaEVersao() {
        entityManager.clear();

        DisponibilidadeProfissional salva = repository.findById(manha.getId()).orElseThrow();

        assertThat(salva.getDataCriacao()).isNotNull();
        assertThat(salva.getVersion()).isZero();
        assertThat(salva.getDiaSemana()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void checkConstraint_recusaFaixaInvertidaGravadaSemPassarPeloService() {
        DisponibilidadeProfissional invertida = faixa(profissional, DayOfWeek.MONDAY, 19, 14);

        assertThatThrownBy(() -> {
                    entityManager.persist(invertida);
                    entityManager.flush();
                })
                .hasMessageContaining("ck_disponibilidades_profissional_faixa");
    }

    private boolean sobrepoe(DayOfWeek dia, int inicio, int fim) {
        return repository.existeSobreposicao(
                profissional.getId(), dia, LocalTime.of(inicio, 0), LocalTime.of(fim, 0), 0L);
    }

    private DisponibilidadeProfissional persistir(Profissional dono, DayOfWeek dia, int inicio, int fim) {
        DisponibilidadeProfissional faixa = entityManager.persist(faixa(dono, dia, inicio, fim));
        entityManager.flush();
        return faixa;
    }

    private static DisponibilidadeProfissional faixa(Profissional dono, DayOfWeek dia, int inicio, int fim) {
        DisponibilidadeProfissional faixa = new DisponibilidadeProfissional();
        faixa.setProfissional(dono);
        faixa.setDiaSemana(dia);
        faixa.setHoraInicio(LocalTime.of(inicio, 0));
        faixa.setHoraFim(LocalTime.of(fim, 0));
        return faixa;
    }

    private static Profissional profissional(String email, String cpf) {
        Profissional profissional = new Profissional();
        profissional.setNome("Paula Disponibilidade");
        profissional.setEmail(email);
        profissional.setCpf(cpf);
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2026, 1, 1));
        return profissional;
    }
}
