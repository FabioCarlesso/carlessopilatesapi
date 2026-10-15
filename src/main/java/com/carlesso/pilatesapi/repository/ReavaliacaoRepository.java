package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Reavaliacao;
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
}
