package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long>, JpaSpecificationExecutor<Pagamento> {

    /**
     * Listagem global paginada (issue #196). Paciente e plano são {@code @ManyToOne} EAGER: sem o grafo, cada linha da
     * página dispararia selects próprios para carregá-los. O grafo vale só para a consulta de conteúdo — a de
     * {@code count} da paginação não faz join.
     */
    @Override
    @EntityGraph(attributePaths = {"paciente", "plano"})
    Page<Pagamento> findAll(Specification<Pagamento> spec, Pageable pageable);

    /**
     * Pagamento com lock de escrita, para regras que decidem pelo status e gravam em seguida. Sem o lock, registrar um
     * contato de cobrança e confirmar o pagamento ao mesmo tempo leriam os dois {@code PENDENTE}, e o contato entraria
     * num pagamento que acabou de virar {@code PAGO}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pagamento p where p.id = :id")
    Optional<Pagamento> findByIdForUpdate(@Param("id") Long id);

    List<Pagamento> findByPacienteId(Long pacienteId);

    List<Pagamento> findByStatusAndDataVencimentoBefore(StatusPagamento status, LocalDate data);

    boolean existsByPlanoAndPeriodoInicio(Plano plano, LocalDate periodoInicio);

    Optional<Pagamento> findTopByPlanoOrderByPeriodoFimDesc(Plano plano);

    @Query(
            """
            select p
            from Pagamento p
            join fetch p.paciente paciente
            where p.status = :status
              and p.dataPagamento between :inicio and :fim
              and paciente.ativo = true
            order by paciente.nome asc, p.dataPagamento asc, p.id asc
            """)
    List<Pagamento> findPagamentosConfirmadosParaRelatorioNfse(
            @Param("status") StatusPagamento status, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    long countByStatus(StatusPagamento status);

    @Query(
            """
            select coalesce(sum(p.valor), 0)
            from Pagamento p
            where p.status = :status
              and p.dataPagamento between :inicio and :fim
            """)
    java.math.BigDecimal sumValorByStatusAndDataPagamentoBetween(
            @Param("status") StatusPagamento status, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
