package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Plano;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoRepository extends JpaRepository<Plano, Long> {

    Optional<Plano> findByPacienteIdAndAtivoTrue(Long pacienteId);

    List<Plano> findByPacienteId(Long pacienteId);

    List<Plano> findByAtivoTrue();
}
