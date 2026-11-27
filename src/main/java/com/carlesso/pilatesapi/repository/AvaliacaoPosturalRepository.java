package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoPosturalRepository extends JpaRepository<AvaliacaoPostural, Long> {

    /** {@code LEFT JOIN}: desde a #247 a análise pode estar solta, sem avaliação nem reavaliação. */
    @Query(
            """
            SELECT a FROM AvaliacaoPostural a
            LEFT JOIN FETCH a.avaliacaoFisioterapeutica
            LEFT JOIN FETCH a.reavaliacao
            WHERE a.id = :id AND a.ativo = true
            """)
    Optional<AvaliacaoPostural> findAtivaById(@Param("id") Long id);

    @Query(
            "SELECT a FROM AvaliacaoPostural a WHERE a.avaliacaoFisioterapeutica.id = :avaliacaoFisioterapeuticaId AND a.ativo = true ORDER BY a.vista, a.id")
    List<AvaliacaoPostural> findAtivasByAvaliacaoFisioterapeutica(
            @Param("avaliacaoFisioterapeuticaId") Long avaliacaoFisioterapeuticaId);

    /** Todas as análises ativas do paciente — soltas, de avaliações e de reavaliações —, da mais recente à mais antiga. */
    @Query(
            "SELECT a FROM AvaliacaoPostural a WHERE a.paciente.id = :pacienteId AND a.ativo = true ORDER BY a.dataCriacao DESC, a.id DESC")
    List<AvaliacaoPostural> findAtivasByPaciente(@Param("pacienteId") Long pacienteId);

    boolean existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
            Long avaliacaoFisioterapeuticaId, VistaPostural vista);

    boolean existsByReavaliacaoIdAndVistaAndAtivoTrue(Long reavaliacaoId, VistaPostural vista);
}
