package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.BloqueioAgenda;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, Long> {

    /**
     * Bloqueios que intersectam o período, incluindo os que começam antes de {@code inicio} ou terminam depois de
     * {@code fim}.
     *
     * <p>A condição é a interseção clássica de intervalos fechados ({@code dataInicio <= fim AND dataFim >= inicio}),
     * e não {@code dataInicio BETWEEN inicio AND fim}: férias coletivas de 20/12 a 05/01 precisam aparecer na consulta
     * de janeiro mesmo tendo começado em dezembro, senão a agenda do mês não avisaria nada.
     */
    @Query(
            """
            SELECT b
            FROM BloqueioAgenda b
            WHERE b.dataInicio <= :fim
              AND b.dataFim >= :inicio
            ORDER BY b.dataInicio, b.id
            """)
    List<BloqueioAgenda> findQueIntersectamPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
