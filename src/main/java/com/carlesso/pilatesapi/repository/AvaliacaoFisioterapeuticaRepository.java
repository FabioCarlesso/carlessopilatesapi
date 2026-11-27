package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoFisioterapeuticaRepository extends JpaRepository<AvaliacaoFisioterapeutica, Long> {

    @Query("SELECT a FROM AvaliacaoFisioterapeutica a JOIN FETCH a.paciente p WHERE a.id = :id")
    Optional<AvaliacaoFisioterapeutica> findByIdComPaciente(@Param("id") Long id);

    /** Ordenação vem do {@code Pageable}; a contagem é explícita porque não pode carregar o {@code JOIN FETCH}. */
    @Query(
            value = "SELECT a FROM AvaliacaoFisioterapeutica a JOIN FETCH a.paciente p WHERE p.id = :pacienteId",
            countQuery = "SELECT COUNT(a) FROM AvaliacaoFisioterapeutica a WHERE a.paciente.id = :pacienteId")
    Page<AvaliacaoFisioterapeutica> findByPaciente(@Param("pacienteId") Long pacienteId, Pageable pageable);

    /**
     * Avaliações feitas até {@code fim}, da mais antiga para a mais recente: o prontuário (#250) recorta daqui as
     * vigentes no período — a última anterior ao início e as feitas dentro dele.
     */
    @Query(
            """
            SELECT a FROM AvaliacaoFisioterapeutica a
            WHERE a.paciente.id = :pacienteId AND a.dataAvaliacao <= :fim
            ORDER BY a.dataAvaliacao ASC, a.id ASC
            """)
    List<AvaliacaoFisioterapeutica> findByPacienteAte(
            @Param("pacienteId") Long pacienteId, @Param("fim") LocalDate fim);
}
