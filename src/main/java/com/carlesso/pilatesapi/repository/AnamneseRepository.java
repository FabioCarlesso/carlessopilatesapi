package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Anamnese;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnamneseRepository extends JpaRepository<Anamnese, Long> {

    Optional<Anamnese> findByIdAndPacienteAtivoTrue(Long id);

    Optional<Anamnese> findByPacienteIdAndPacienteAtivoTrue(Long pacienteId);

    boolean existsByPacienteId(Long pacienteId);
}
