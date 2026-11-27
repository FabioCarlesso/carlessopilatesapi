package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Reavaliacao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReavaliacaoRepository extends JpaRepository<Reavaliacao, Long> {

    @Query(
            """
            SELECT r FROM Reavaliacao r
            JOIN FETCH r.paciente p
            LEFT JOIN FETCH r.avaliacaoFisioterapeutica
            LEFT JOIN FETCH r.planoTratamento
            WHERE r.id = :id
            """)
    Optional<Reavaliacao> findByIdComPaciente(@Param("id") Long id);

    /** Ordenação vem do {@code Pageable}; a contagem é explícita porque não pode carregar os {@code JOIN FETCH}. */
    @Query(
            value =
                    """
                    SELECT r FROM Reavaliacao r
                    JOIN FETCH r.paciente p
                    LEFT JOIN FETCH r.avaliacaoFisioterapeutica
                    LEFT JOIN FETCH r.planoTratamento
                    WHERE p.id = :pacienteId
                    """,
            countQuery = "SELECT COUNT(r) FROM Reavaliacao r WHERE r.paciente.id = :pacienteId")
    Page<Reavaliacao> findByPaciente(@Param("pacienteId") Long pacienteId, Pageable pageable);

    /** Reavaliações do período do prontuário (#250), limites inclusivos, da mais antiga para a mais recente. */
    @Query(
            """
            SELECT r FROM Reavaliacao r
            WHERE r.paciente.id = :pacienteId AND r.dataReavaliacao BETWEEN :inicio AND :fim
            ORDER BY r.dataReavaliacao ASC, r.id ASC
            """)
    List<Reavaliacao> findByPacienteNoPeriodo(
            @Param("pacienteId") Long pacienteId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
