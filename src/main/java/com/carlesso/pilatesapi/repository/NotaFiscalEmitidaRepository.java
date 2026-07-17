package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.NotaFiscalEmitida;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotaFiscalEmitidaRepository extends JpaRepository<NotaFiscalEmitida, Long> {

    Optional<NotaFiscalEmitida> findByPacienteIdAndCompetencia(Long pacienteId, LocalDate competencia);

    @Query(
            """
            select n
            from NotaFiscalEmitida n
            join fetch n.paciente
            where n.paciente.id = :pacienteId
            order by n.competencia desc
            """)
    List<NotaFiscalEmitida> findByPacienteIdOrderByCompetenciaDesc(@Param("pacienteId") Long pacienteId);

    @Query(
            """
            select distinct n.paciente.id
            from NotaFiscalEmitida n
            where n.paciente.id in :pacienteIds
              and n.competencia < :competencia
            """)
    List<Long> findPacienteIdsComNotaEmitidaAntes(
            @Param("pacienteIds") List<Long> pacienteIds, @Param("competencia") LocalDate competencia);
}
