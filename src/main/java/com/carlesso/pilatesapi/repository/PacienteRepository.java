package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Paciente;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long>, JpaSpecificationExecutor<Paciente> {

    boolean existsByIdAndAtivoTrue(Long id);

    long countByAtivoTrue();

    List<Paciente> findByAtivo(boolean ativo, Sort sort);

    long countByAtivoFalse();

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    /** Pacientes vinculados a cada tomador, para a coluna da listagem de tomadores: [tomadorId, total]. */
    @Query(
            """
            select p.tomador.id, count(p)
            from Paciente p
            where p.tomador.id in :tomadorIds
            group by p.tomador.id
            """)
    List<Object[]> contarPorTomador(@Param("tomadorIds") Collection<Long> tomadorIds);

    List<Paciente> findByTomadorIdOrderByNomeAsc(Long tomadorId);

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
