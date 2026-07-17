package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProfissionalRepository
        extends JpaRepository<Profissional, Long>, JpaSpecificationExecutor<Profissional> {

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    long countByAtivoTrue();

    long countByAtivoFalse();
}
