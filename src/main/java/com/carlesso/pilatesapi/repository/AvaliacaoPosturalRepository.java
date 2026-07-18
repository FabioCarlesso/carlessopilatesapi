package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoPostural;
import com.carlesso.pilatesapi.entity.enums.VistaPostural;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoPosturalRepository extends JpaRepository<AvaliacaoPostural, Long> {

    @Query(
            "SELECT a FROM AvaliacaoPostural a JOIN FETCH a.avaliacaoFisioterapeutica WHERE a.id = :id AND a.ativo = true")
    Optional<AvaliacaoPostural> findAtivaById(@Param("id") Long id);

    @Query(
            "SELECT a FROM AvaliacaoPostural a WHERE a.avaliacaoFisioterapeutica.id = :avaliacaoFisioterapeuticaId AND a.ativo = true ORDER BY a.vista, a.id")
    List<AvaliacaoPostural> findAtivasByAvaliacaoFisioterapeutica(
            @Param("avaliacaoFisioterapeuticaId") Long avaliacaoFisioterapeuticaId);

    boolean existsByAvaliacaoFisioterapeuticaIdAndVistaAndAtivoTrue(
            Long avaliacaoFisioterapeuticaId, VistaPostural vista);
}
