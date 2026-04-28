package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByPacienteId(Long pacienteId);

    List<Pagamento> findByStatusAndDataVencimentoBefore(StatusPagamento status, LocalDate data);

    boolean existsByPlanoAndPeriodoInicio(Plano plano, LocalDate periodoInicio);

    Optional<Pagamento> findTopByPlanoOrderByPeriodoFimDesc(Plano plano);

    @Query("""
            select p
            from Pagamento p
            join fetch p.paciente paciente
            where p.status = :status
              and p.dataPagamento between :inicio and :fim
              and paciente.ativo = true
            order by paciente.nome asc, p.dataPagamento asc, p.id asc
            """)
    List<Pagamento> findPagamentosConfirmadosParaRelatorioNfse(
            @Param("status") StatusPagamento status,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("""
            select distinct p.paciente.id
            from Pagamento p
            where p.status = :status
              and p.dataPagamento < :dataPagamento
              and p.paciente.id in :pacienteIds
            """)
    List<Long> findPacienteIdsComPagamentoConfirmadoAntes(
            @Param("pacienteIds") List<Long> pacienteIds,
            @Param("status") StatusPagamento status,
            @Param("dataPagamento") LocalDate dataPagamento);
}
