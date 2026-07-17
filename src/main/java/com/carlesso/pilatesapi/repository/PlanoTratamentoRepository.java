package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.PlanoTratamento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanoTratamentoRepository extends JpaRepository<PlanoTratamento, Long> {

    @Query(
            "SELECT p FROM PlanoTratamento p JOIN FETCH p.paciente pac WHERE p.id = :id AND p.ativo = true AND pac.ativo = true")
    Optional<PlanoTratamento> findAtivoById(@Param("id") Long id);

    @Query(
            "SELECT p FROM PlanoTratamento p JOIN FETCH p.paciente pac WHERE pac.id = :pacienteId AND p.ativo = true AND pac.ativo = true ORDER BY p.dataInicio DESC, p.id DESC")
    List<PlanoTratamento> findAtivosByPacienteOrdenados(@Param("pacienteId") Long pacienteId);
}
