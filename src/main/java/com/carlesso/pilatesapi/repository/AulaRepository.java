package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    boolean existsByPacienteAndData(Paciente paciente, LocalDate data);

    Optional<Aula> findByIdAndPacienteAtivoTrue(Long id);

    @Query("SELECT a FROM Aula a WHERE a.paciente.id = :pacienteId AND a.paciente.ativo = true ORDER BY a.data")
    List<Aula> findByPacienteIdOrderByData(@Param("pacienteId") Long pacienteId);

    @Query("SELECT a FROM Aula a WHERE a.pagamento.id = :pagamentoId AND a.paciente.ativo = true ORDER BY a.data")
    List<Aula> findByPagamentoIdOrderByData(@Param("pagamentoId") Long pagamentoId);

    /**
     * Agenda do estúdio: aulas do período, com os filtros opcionais aplicados no
     * banco.
     *
     * <p>Projeta direto no DTO em vez de devolver entidades porque carregar uma
     * {@code Aula} arrasta {@code pagamento} → {@code plano} → {@code diasSemana}
     * (a cadeia é toda EAGER) e a coleção de dias vira um select por plano — um
     * N+1 numa listagem que só precisa do {@code pagamento.id}. Como
     * {@code a.pagamento.id} é a própria FK, nem join gera.
     */
    @Query(
            """
            SELECT new com.carlesso.pilatesapi.dto.AulaResponseDTO(
                    a.id,
                    paciente.id,
                    paciente.nome,
                    profissional.id,
                    profissional.nome,
                    a.pagamento.id,
                    a.data,
                    a.realizada)
            FROM Aula a
            JOIN a.paciente paciente
            LEFT JOIN a.profissional profissional
            WHERE a.data BETWEEN :inicio AND :fim
              AND paciente.ativo = true
              AND (:profissionalId IS NULL OR profissional.id = :profissionalId)
              AND (:pacienteId IS NULL OR paciente.id = :pacienteId)
              AND (:realizada IS NULL OR a.realizada = :realizada)
            ORDER BY a.data, a.id
            """)
    List<AulaResponseDTO> findAgendaPorPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("profissionalId") Long profissionalId,
            @Param("pacienteId") Long pacienteId,
            @Param("realizada") Boolean realizada);

    @Query(
            """
            SELECT a.pagamento.id, COUNT(a)
            FROM Aula a
            WHERE a.pagamento.id IN :ids
              AND a.paciente.ativo = true
            GROUP BY a.pagamento.id
            """)
    List<Object[]> countGroupedByPagamentoId(@Param("ids") List<Long> ids);

    @Query(
            """
            SELECT a.id AS aulaId,
                   a.data AS data,
                   paciente.id AS pacienteId,
                   paciente.nome AS pacienteNome,
                   pagamento.id AS pagamentoId,
                   pagamento.valor AS valorPagamento,
                   COUNT(aulaPagamento.id) AS quantidadeAulasPagamento
            FROM Aula a
            JOIN a.paciente paciente
            JOIN a.pagamento pagamento
            JOIN Aula aulaPagamento ON aulaPagamento.pagamento = pagamento
                                  AND aulaPagamento.paciente.ativo = true
            WHERE a.profissional.id = :profissionalId
              AND a.realizada = true
              AND a.data BETWEEN :inicio AND :fim
              AND paciente.ativo = true
            GROUP BY a.id,
                     a.data,
                     paciente.id,
                     paciente.nome,
                     pagamento.id,
                     pagamento.valor
            ORDER BY a.data
            """)
    List<ProfissionalPagamentoAulaProjection> findRelatorioPagamentoByProfissionalIdAndPeriodo(
            @Param("profissionalId") Long profissionalId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query(
            """
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

    @Query(
            """
            select count(a)
            from Aula a
            where a.realizada = :realizada
              and a.data between :inicio and :fim
              and a.paciente.ativo = true
            """)
    long countByRealizadaAndDataBetweenAndPacienteAtivoTrue(
            @Param("realizada") boolean realizada, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    interface ProfissionalPagamentoAulaProjection {
        Long getAulaId();

        LocalDate getData();

        Long getPacienteId();

        String getPacienteNome();

        Long getPagamentoId();

        BigDecimal getValorPagamento();

        Long getQuantidadeAulasPagamento();
    }
}
