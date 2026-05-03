package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Anamnese;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnamneseRepository extends JpaRepository<Anamnese, Long> {

    Optional<Anamnese> findByPacienteId(Long pacienteId);

    boolean existsByPacienteId(Long pacienteId);
}
