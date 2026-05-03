package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoFisioterapeuticaRepository extends JpaRepository<AvaliacaoFisioterapeutica, Long> {

    @Query("SELECT a FROM AvaliacaoFisioterapeutica a JOIN FETCH a.paciente p WHERE a.id = :id AND p.ativo = true")
    Optional<AvaliacaoFisioterapeutica> findAtivaById(@Param("id") Long id);

    @Query("SELECT a FROM AvaliacaoFisioterapeutica a JOIN FETCH a.paciente p WHERE p.id = :pacienteId AND p.ativo = true ORDER BY a.dataAvaliacao DESC, a.id DESC")
    List<AvaliacaoFisioterapeutica> findAtivasByPacienteOrdenadas(@Param("pacienteId") Long pacienteId);
}
