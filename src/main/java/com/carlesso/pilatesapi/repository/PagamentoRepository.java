package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByPacienteId(Long pacienteId);

    List<Pagamento> findByStatusAndDataVencimentoBefore(StatusPagamento status, LocalDate data);

    boolean existsByPlanoAndPeriodoInicio(Plano plano, LocalDate periodoInicio);

    Optional<Pagamento> findTopByPlanoOrderByPeriodoFimDesc(Plano plano);
}
