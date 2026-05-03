package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoFisioterapeuticaRepository extends JpaRepository<AvaliacaoFisioterapeutica, Long> {

    Optional<AvaliacaoFisioterapeutica> findByIdAndPacienteAtivoTrue(Long id);

    List<AvaliacaoFisioterapeutica> findByPacienteIdAndPacienteAtivoTrueOrderByDataAvaliacaoDescIdDesc(Long pacienteId);
}
