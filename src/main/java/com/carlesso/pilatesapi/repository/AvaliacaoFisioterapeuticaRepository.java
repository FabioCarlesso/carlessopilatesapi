package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoFisioterapeuticaRepository extends JpaRepository<AvaliacaoFisioterapeutica, Long> {

    @Query("SELECT a FROM AvaliacaoFisioterapeutica a JOIN FETCH a.paciente p WHERE a.id = :id")
    Optional<AvaliacaoFisioterapeutica> findByIdComPaciente(@Param("id") Long id);

    @Query(
            "SELECT a FROM AvaliacaoFisioterapeutica a JOIN FETCH a.paciente p WHERE p.id = :pacienteId ORDER BY a.dataAvaliacao DESC, a.id DESC")
    List<AvaliacaoFisioterapeutica> findByPacienteOrdenadas(@Param("pacienteId") Long pacienteId);
}
