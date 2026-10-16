package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
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
}
