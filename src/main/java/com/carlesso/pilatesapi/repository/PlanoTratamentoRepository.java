package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.PlanoTratamento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanoTratamentoRepository extends JpaRepository<PlanoTratamento, Long> {

    @Query("SELECT p FROM PlanoTratamento p JOIN FETCH p.paciente pac WHERE p.id = :id AND p.ativo = true")
    Optional<PlanoTratamento> findAtivoById(@Param("id") Long id);

    /** Ordenação vem do {@code Pageable}; a contagem é explícita porque não pode carregar o {@code JOIN FETCH}. */
    @Query(
            value =
                    "SELECT p FROM PlanoTratamento p JOIN FETCH p.paciente pac WHERE pac.id = :pacienteId AND p.ativo = true",
            countQuery = "SELECT COUNT(p) FROM PlanoTratamento p WHERE p.paciente.id = :pacienteId AND p.ativo = true")
    Page<PlanoTratamento> findAtivosByPaciente(@Param("pacienteId") Long pacienteId, Pageable pageable);

    /**
     * Planos ativos (não excluídos) cuja vigência — de {@code dataInicio} até {@code dataFimPrevista}, aberta quando
     * nula — cruza o período do prontuário (#250), do mais antigo para o mais recente.
     */
    @Query(
            """
            SELECT p FROM PlanoTratamento p
            WHERE p.paciente.id = :pacienteId AND p.ativo = true
              AND p.dataInicio <= :fim
              AND (p.dataFimPrevista IS NULL OR p.dataFimPrevista >= :inicio)
            ORDER BY p.dataInicio ASC, p.id ASC
            """)
    List<PlanoTratamento> findVigentesNoPeriodo(
            @Param("pacienteId") Long pacienteId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
