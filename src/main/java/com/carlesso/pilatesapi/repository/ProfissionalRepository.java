package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Profissional;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfissionalRepository
        extends JpaRepository<Profissional, Long>, JpaSpecificationExecutor<Profissional> {

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    long countByAtivoTrue();

    long countByAtivoFalse();

    /**
     * Profissional com lock de escrita, para serializar a checagem de capacidade da agenda. Sem o lock, duas
     * atribuições simultâneas ao mesmo horário contariam a mesma ocupação e passariam juntas do limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Profissional p where p.id = :id")
    Optional<Profissional> findByIdForUpdate(@Param("id") Long id);
}
