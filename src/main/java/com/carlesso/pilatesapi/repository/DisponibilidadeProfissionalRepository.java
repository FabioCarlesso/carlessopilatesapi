package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.DisponibilidadeProfissional;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisponibilidadeProfissionalRepository extends JpaRepository<DisponibilidadeProfissional, Long> {

    /**
     * Faixas do profissional. A ordem por dia da semana é aplicada pelo service: {@code dia_semana} guarda o nome do
     * {@link DayOfWeek}, e a ordem alfabética do texto ({@code FRIDAY} antes de {@code MONDAY}) não é a da semana.
     */
    List<DisponibilidadeProfissional> findByProfissionalId(Long profissionalId);

    List<DisponibilidadeProfissional> findByProfissionalIdAndDiaSemana(Long profissionalId, DayOfWeek diaSemana);

    boolean existsByProfissionalId(Long profissionalId);

    /**
     * Existe outra faixa do profissional, no mesmo dia, que se sobreponha a {@code [inicio, fim)}?
     *
     * <p>Interseção estrita de intervalos semiabertos ({@code horaInicio < fim AND horaFim > inicio}): faixas que só
     * encostam (12:00–14:00 e 14:00–19:00) não conflitam. {@code ignorarId} exclui a própria faixa num {@code PUT};
     * na criação, o service passa {@code 0}, que nenhum {@code BIGSERIAL} assume.
     */
    @Query(
            """
            SELECT COUNT(d) > 0
            FROM DisponibilidadeProfissional d
            WHERE d.profissional.id = :profissionalId
              AND d.diaSemana = :diaSemana
              AND d.horaInicio < :fim
              AND d.horaFim > :inicio
              AND d.id <> :ignorarId
            """)
    boolean existeSobreposicao(
            @Param("profissionalId") Long profissionalId,
            @Param("diaSemana") DayOfWeek diaSemana,
            @Param("inicio") LocalTime inicio,
            @Param("fim") LocalTime fim,
            @Param("ignorarId") Long ignorarId);
}
