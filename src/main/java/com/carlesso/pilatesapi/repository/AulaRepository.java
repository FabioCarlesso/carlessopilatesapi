package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.dto.AulaResponseDTO;
import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    boolean existsByPacienteAndData(Paciente paciente, LocalDate data);

    Optional<Aula> findByIdAndPacienteAtivoTrue(Long id);

    /**
     * Aulas do paciente, paginadas. Projeta no DTO pela mesma razão de {@link #findAgendaPorPeriodo}: carregar a
     * entidade arrastaria a cadeia EAGER {@code pagamento} → {@code plano} → {@code diasSemana}. A ordenação vem do
     * {@code Pageable}; a {@code countQuery} é explícita porque o Spring Data não deriva contagem de uma expressão
     * {@code new}.
     */
    @Query(
            value =
                    """
                    SELECT new com.carlesso.pilatesapi.dto.AulaResponseDTO(
                            a.id,
                            paciente.id,
                            paciente.nome,
                            profissional.id,
                            profissional.nome,
                            a.pagamento.id,
                            a.data,
                            a.horario,
                            a.realizada)
                    FROM Aula a
                    JOIN a.paciente paciente
                    LEFT JOIN a.profissional profissional
                    WHERE paciente.id = :pacienteId
                      AND paciente.ativo = true
                    """,
            countQuery =
                    """
                    SELECT COUNT(a)
                    FROM Aula a
                    WHERE a.paciente.id = :pacienteId
                      AND a.paciente.ativo = true
                    """)
    Page<AulaResponseDTO> findByPacienteId(@Param("pacienteId") Long pacienteId, Pageable pageable);

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
                    a.horario,
                    a.realizada)
            FROM Aula a
            JOIN a.paciente paciente
            LEFT JOIN a.profissional profissional
            WHERE a.data BETWEEN :inicio AND :fim
              AND paciente.ativo = true
              AND (:profissionalId IS NULL OR profissional.id = :profissionalId)
              AND (:pacienteId IS NULL OR paciente.id = :pacienteId)
              AND (:realizada IS NULL OR a.realizada = :realizada)
            ORDER BY a.data, a.horario NULLS LAST, a.id
            """)
    List<AulaResponseDTO> findAgendaPorPeriodo(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("profissionalId") Long profissionalId,
            @Param("pacienteId") Long pacienteId,
            @Param("realizada") Boolean realizada);

    /**
     * Aulas do profissional que disputam o horário de uma aula: mesmo dia e início estritamente dentro da janela
     * {@code (de, ate)}. Os limites nulos abrem a janela naquele lado — é o caso de uma aula perto da meia-noite, em que
     * subtrair ou somar a duração daria a volta no relógio. A própria aula é excluída por {@code aulaId}, e aulas de
     * paciente inativo não ocupam vaga. O {@code cast} tipa o parâmetro no {@code IS NULL}: sem ele o PostgreSQL não
     * infere o tipo de um {@code LocalTime} nulo e recusa a consulta.
     */
    @Query(
            """
            SELECT COUNT(a)
            FROM Aula a
            WHERE a.profissional.id = :profissionalId
              AND a.data = :data
              AND a.horario IS NOT NULL
              AND (cast(:de as LocalTime) IS NULL OR a.horario > :de)
              AND (cast(:ate as LocalTime) IS NULL OR a.horario < :ate)
              AND a.id <> :aulaId
              AND a.paciente.ativo = true
            """)
    long countConcorrentesDoProfissional(
            @Param("profissionalId") Long profissionalId,
            @Param("data") LocalDate data,
            @Param("de") LocalTime de,
            @Param("ate") LocalTime ate,
            @Param("aulaId") Long aulaId);

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

    /**
     * Aulas por mês de {@code data}, separando realizadas das não realizadas — o mesmo par do {@code AulasResumo}.
     *
     * <p>Sem o filtro de paciente ativo que o resumo aplica: numa série histórica ele faria a inativação de hoje
     * apagar aulas que de fato aconteceram meses atrás.
     */
    @Query(
            """
            select extract(year from a.data) as ano,
                   extract(month from a.data) as mes,
                   sum(case when a.realizada = true then 1 else 0 end) as realizadas,
                   sum(case when a.realizada = false then 1 else 0 end) as agendadas
            from Aula a
            where a.data between :inicio and :fim
            group by extract(year from a.data), extract(month from a.data)
            """)
    List<AulasMensalProjection> countPorMes(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Aulas realizadas por profissional no período, do maior total para o menor. Profissional inativo entra se teve
     * aulas; aula sem profissional fica de fora (o join é interno). Sem filtro de paciente ativo, pela mesma razão de
     * {@link #countPorMes}.
     */
    @Query(
            """
            select new com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO(profissional.id, profissional.nome, count(a))
            from Aula a
            join a.profissional profissional
            where a.realizada = true
              and a.data between :inicio and :fim
            group by profissional.id, profissional.nome
            order by count(a) desc, profissional.nome asc, profissional.id asc
            """)
    List<AulasPorProfissionalDTO> countRealizadasPorProfissional(
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    interface AulasMensalProjection {
        Integer getAno();

        Integer getMes();

        Long getRealizadas();

        Long getAgendadas();
    }

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
