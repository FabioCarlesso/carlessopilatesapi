package com.carlesso.pilatesapi.repository;

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
