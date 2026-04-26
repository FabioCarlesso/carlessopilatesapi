package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    boolean existsByPacienteAndData(Paciente paciente, LocalDate data);

    Optional<Aula> findByIdAndPacienteAtivoTrue(Long id);

    @Query("SELECT a FROM Aula a WHERE a.paciente.id = :pacienteId AND a.paciente.ativo = true ORDER BY a.data")
    List<Aula> findByPacienteIdOrderByData(@Param("pacienteId") Long pacienteId);

    @Query("SELECT a FROM Aula a WHERE a.pagamento.id = :pagamentoId AND a.paciente.ativo = true ORDER BY a.data")
    List<Aula> findByPagamentoIdOrderByData(@Param("pagamentoId") Long pagamentoId);

    @Query("""
            SELECT a.pagamento.id, COUNT(a)
            FROM Aula a
            WHERE a.pagamento.id IN :ids
              AND a.paciente.ativo = true
            GROUP BY a.pagamento.id
            """)
    List<Object[]> countGroupedByPagamentoId(@Param("ids") List<Long> ids);

    @Query("""
            SELECT a
            FROM Aula a
            WHERE a.profissional.id = :profissionalId
              AND a.realizada = true
              AND a.data BETWEEN :inicio AND :fim
              AND a.paciente.ativo = true
            ORDER BY a.data
            """)
    List<Aula> findByProfissionalIdAndRealizadaTrueAndDataBetweenOrderByData(
            @Param("profissionalId") Long profissionalId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);
}
