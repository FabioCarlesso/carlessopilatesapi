package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessaoPilatesRepository extends JpaRepository<SessaoPilates, Long> {

    @Query("SELECT s FROM SessaoPilates s JOIN FETCH s.paciente pac WHERE s.id = :id AND pac.ativo = true")
    Optional<SessaoPilates> findByIdComPaciente(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SessaoPilates s
            SET s.status = :novoStatus,
                s.dataAtualizacao = :dataAtualizacao
            WHERE s.id = :id
              AND s.status = com.carlesso.pilatesapi.entity.enums.StatusSessao.AGENDADA
            """)
    int transicionarStatusSeAgendada(@Param("id") Long id,
                                     @Param("novoStatus") StatusSessao novoStatus,
                                     @Param("dataAtualizacao") LocalDateTime dataAtualizacao);

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
