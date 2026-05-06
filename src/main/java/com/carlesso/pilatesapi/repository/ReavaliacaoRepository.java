package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Reavaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReavaliacaoRepository extends JpaRepository<Reavaliacao, Long> {

    @Query("""
            SELECT r FROM Reavaliacao r
            JOIN FETCH r.paciente p
            LEFT JOIN FETCH r.avaliacaoFisioterapeutica
            LEFT JOIN FETCH r.planoTratamento
            WHERE r.id = :id AND p.ativo = true
            """)
    Optional<Reavaliacao> findAtivaById(@Param("id") Long id);

    @Query("""
            SELECT r FROM Reavaliacao r
            JOIN FETCH r.paciente p
            LEFT JOIN FETCH r.avaliacaoFisioterapeutica
            LEFT JOIN FETCH r.planoTratamento
            WHERE p.id = :pacienteId AND p.ativo = true
            ORDER BY r.dataReavaliacao DESC, r.id DESC
            """)
    List<Reavaliacao> findAtivasByPacienteOrdenadas(@Param("pacienteId") Long pacienteId);
}
