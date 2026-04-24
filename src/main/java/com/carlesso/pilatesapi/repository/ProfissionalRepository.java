package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Profissional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    Page<Profissional> findAllByAtivoTrue(Pageable pageable);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);
}
