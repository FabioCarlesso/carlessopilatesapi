package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Tomador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TomadorRepository extends JpaRepository<Tomador, Long>, JpaSpecificationExecutor<Tomador> {

    boolean existsByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpjAndIdNot(String cpfCnpj, Long id);
}
