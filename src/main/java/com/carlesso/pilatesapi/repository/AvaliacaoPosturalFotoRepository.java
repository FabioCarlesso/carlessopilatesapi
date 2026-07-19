package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.AvaliacaoPosturalFoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvaliacaoPosturalFotoRepository extends JpaRepository<AvaliacaoPosturalFoto, Long> {

    Optional<AvaliacaoPosturalFoto> findByAvaliacaoPosturalId(Long avaliacaoPosturalId);
}
