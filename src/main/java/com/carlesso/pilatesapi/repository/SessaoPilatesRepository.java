package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.SessaoPilates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessaoPilatesRepository extends JpaRepository<SessaoPilates, Long> {

    @Query("SELECT s FROM SessaoPilates s JOIN FETCH s.paciente pac WHERE s.id = :id AND pac.ativo = true")
    Optional<SessaoPilates> findByIdComPaciente(@Param("id") Long id);

    @Query("""
            SELECT s FROM SessaoPilates s
            JOIN FETCH s.paciente pac
            LEFT JOIN FETCH s.profissional
            LEFT JOIN FETCH s.planoTratamento
            WHERE pac.id = :pacienteId AND pac.ativo = true
            ORDER BY s.data DESC, s.id DESC
            """)
    List<SessaoPilates> findByPacienteOrdenadas(@Param("pacienteId") Long pacienteId);
}
