package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Plano;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanoRepository extends JpaRepository<Plano, Long> {

    Optional<Plano> findByPacienteIdAndAtivoTrue(Long pacienteId);

    List<Plano> findByPacienteId(Long pacienteId);

    List<Plano> findByAtivoTrue();

    /** Planos ativos com os dias da semana numa consulta só, para o relatório de pacientes (#230) não fazer N+1. */
    @Query("SELECT DISTINCT p FROM Plano p LEFT JOIN FETCH p.diasSemana WHERE p.ativo = true")
    List<Plano> findAtivosComDiasSemana();
}
