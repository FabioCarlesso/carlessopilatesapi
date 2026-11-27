package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessaoPilatesRepository extends JpaRepository<SessaoPilates, Long> {

    @Query(
            """
            SELECT s FROM SessaoPilates s
            JOIN FETCH s.paciente pac
            LEFT JOIN FETCH s.profissional
            LEFT JOIN FETCH s.planoTratamento
            WHERE s.id = :id
            """)
    Optional<SessaoPilates> findByIdComPaciente(@Param("id") Long id);

    /** Sessão vinculada à aula (#246), com as mesmas associações de {@link #findByIdComPaciente}. */
    @Query(
            """
            SELECT s FROM SessaoPilates s
            JOIN FETCH s.paciente pac
            LEFT JOIN FETCH s.profissional
            LEFT JOIN FETCH s.planoTratamento
            WHERE s.aula.id = :aulaId
            """)
    Optional<SessaoPilates> findByAulaId(@Param("aulaId") Long aulaId);

    /**
     * Sessão vinculada e existência de evolução de cada aula (#246), para as respostas de aula montadas a partir da
     * entidade. Uma consulta para o lote inteiro; aula sem sessão não tem linha.
     */
    @Query(
            """
            SELECT s.aula.id AS aulaId,
                   s.id AS sessaoId,
                   CASE WHEN e.id IS NOT NULL THEN true ELSE false END AS temEvolucao
            FROM SessaoPilates s
            LEFT JOIN EvolucaoSessao e ON e.sessao = s
            WHERE s.aula.id IN :aulaIds
            """)
    List<AtendimentoDaAulaProjection> findAtendimentosDasAulas(@Param("aulaIds") Collection<Long> aulaIds);

    interface AtendimentoDaAulaProjection {
        Long getAulaId();

        Long getSessaoId();

        Boolean getTemEvolucao();
    }

    // UPDATE em massa: garante atomicidade da transição de status contra concorrência,
    // mas bypassa @PreUpdate/lifecycle callbacks da entidade — inclusive o listener de auditoria —
    // e o @Version. Por isso `dataAtualizacao` e `alteradoPor` são passados explicitamente e a versão
    // é incrementada na própria query: sem isso, um formulário aberto antes do realizar/cancelar
    // salvaria por cima da transição sem receber 409.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE SessaoPilates s
            SET s.status = :novoStatus,
                s.dataAtualizacao = :dataAtualizacao,
                s.alteradoPor = :alteradoPor,
                s.version = s.version + 1
            WHERE s.id = :id
              AND s.status = com.carlesso.pilatesapi.entity.enums.StatusSessao.AGENDADA
            """)
    int transicionarStatusSeAgendada(
            @Param("id") Long id,
            @Param("novoStatus") StatusSessao novoStatus,
            @Param("dataAtualizacao") LocalDateTime dataAtualizacao,
            @Param("alteradoPor") String alteradoPor);

    /** Ordenação vem do {@code Pageable}; a contagem é explícita porque não pode carregar os {@code JOIN FETCH}. */
    @Query(
            value =
                    """
                    SELECT s FROM SessaoPilates s
                    JOIN FETCH s.paciente pac
                    LEFT JOIN FETCH s.profissional
                    LEFT JOIN FETCH s.planoTratamento
                    WHERE pac.id = :pacienteId
                    """,
            countQuery = "SELECT COUNT(s) FROM SessaoPilates s WHERE s.paciente.id = :pacienteId")
    Page<SessaoPilates> findByPaciente(@Param("pacienteId") Long pacienteId, Pageable pageable);

    /**
     * Agenda do estúdio: sessões do período, com os filtros opcionais aplicados
     * no banco. Diferente das aulas, não há recorte por {@code paciente.ativo} —
     * sessão é registro clínico e o histórico do ex-aluno continua visível, o
     * mesmo critério de {@link #findByPacienteOrdenadas}. Sessões sem
     * {@code horario} vão para o fim do respectivo dia.
     */
    @Query(
            """
            SELECT s FROM SessaoPilates s
            JOIN FETCH s.paciente pac
            LEFT JOIN FETCH s.profissional prof
            LEFT JOIN FETCH s.planoTratamento
            WHERE s.data BETWEEN :inicio AND :fim
              AND (:profissionalId IS NULL OR prof.id = :profissionalId)
              AND (:pacienteId IS NULL OR pac.id = :pacienteId)
              AND (:tipo IS NULL OR s.tipo = :tipo)
              AND (:status IS NULL OR s.status = :status)
            ORDER BY s.data, s.horario NULLS LAST, s.id
            """)
    List<SessaoPilates> findAgendaPorPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("profissionalId") Long profissionalId,
            @Param("pacienteId") Long pacienteId,
            @Param("tipo") TipoSessao tipo,
            @Param("status") StatusSessao status);
}
