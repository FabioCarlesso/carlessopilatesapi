package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Paciente;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

    boolean existsByIdAndAtivoTrue(Long id);

    long countByAtivoTrue();

    long countByAtivoFalse();

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    /** Novos cadastros por mês de {@code dataCadastro}; pacientes sem a data (anteriores à V41) não entram. */
    @Query(
            """
            select extract(year from p.dataCadastro) as ano,
                   extract(month from p.dataCadastro) as mes,
                   count(p) as total
            from Paciente p
            where p.dataCadastro between :inicio and :fim
            group by extract(year from p.dataCadastro), extract(month from p.dataCadastro)
            """)
    List<ContagemMensalProjection> countCadastradosPorMes(
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * Inativações por mês de {@code dataInativacao}. O reativar limpa a data, então quem voltou não conta como evasão.
     */
    @Query(
            """
            select extract(year from p.dataInativacao) as ano,
                   extract(month from p.dataInativacao) as mes,
                   count(p) as total
            from Paciente p
            where p.dataInativacao between :inicio and :fim
            group by extract(year from p.dataInativacao), extract(month from p.dataInativacao)
            """)
    List<ContagemMensalProjection> countInativadosPorMes(
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
