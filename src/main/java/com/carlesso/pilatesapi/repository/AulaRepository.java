package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    boolean existsByPacienteAndData(Paciente paciente, LocalDate data);

    List<Aula> findByPacienteIdOrderByData(Long pacienteId);

    List<Aula> findByPagamentoIdOrderByData(Long pagamentoId);
}
