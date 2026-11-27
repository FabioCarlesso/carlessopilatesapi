package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.PontoDor;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PontoDorRepository extends JpaRepository<PontoDor, Long> {

    List<PontoDor> findByAvaliacaoFisioterapeuticaIdOrderByIdAsc(Long avaliacaoId);

    List<PontoDor> findByReavaliacaoIdOrderByIdAsc(Long reavaliacaoId);

    /**
     * Pontos do paciente com o registro de origem já carregado: o histórico precisa da data de cada avaliação e
     * reavaliação, e sem o fetch seria uma consulta por registro.
     */
    @Query(
            """
            SELECT p FROM PontoDor p
            LEFT JOIN FETCH p.avaliacaoFisioterapeutica
            LEFT JOIN FETCH p.reavaliacao
            WHERE p.paciente.id = :pacienteId
            ORDER BY p.id
            """)
    List<PontoDor> findByPacienteComRegistro(@Param("pacienteId") Long pacienteId);

    /**
     * Trava a linha da avaliação até o fim da transação. A substituição do conjunto é "apaga e insere"; sem a trava,
     * dois PUTs simultâneos apagariam cada um o conjunto antigo e gravariam os dois conjuntos novos somados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AvaliacaoFisioterapeutica a WHERE a.id = :id")
    Optional<AvaliacaoFisioterapeutica> travarAvaliacao(@Param("id") Long id);

    /** Mesma trava de {@link #travarAvaliacao}, para a reavaliação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reavaliacao r WHERE r.id = :id")
    Optional<Reavaliacao> travarReavaliacao(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PontoDor p WHERE p.avaliacaoFisioterapeutica.id = :avaliacaoId")
    int excluirDaAvaliacao(@Param("avaliacaoId") Long avaliacaoId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PontoDor p WHERE p.reavaliacao.id = :reavaliacaoId")
    int excluirDaReavaliacao(@Param("reavaliacaoId") Long reavaliacaoId);
}
