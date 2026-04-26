package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    boolean existsByPacienteAndData(Paciente paciente, LocalDate data);

    List<Aula> findByPacienteIdOrderByData(Long pacienteId);

    List<Aula> findByPagamentoIdOrderByData(Long pagamentoId);

    @Query("SELECT a.pagamento.id, COUNT(a) FROM Aula a WHERE a.pagamento.id IN :ids GROUP BY a.pagamento.id")
    List<Object[]> countGroupedByPagamentoId(@Param("ids") List<Long> ids);

    List<Aula> findByProfissionalIdAndRealizadaTrueAndDataBetweenOrderByData(
            Long profissionalId,
            LocalDate inicio,
            LocalDate fim);
}
