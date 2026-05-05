package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EvolucaoSessaoRepository extends JpaRepository<EvolucaoSessao, Long> {

    boolean existsBySessaoId(Long sessaoId);

    @Query("SELECT e FROM EvolucaoSessao e JOIN FETCH e.sessao WHERE e.id = :id")
    Optional<EvolucaoSessao> findByIdComSessao(@Param("id") Long id);

    @Query("SELECT e FROM EvolucaoSessao e JOIN FETCH e.sessao WHERE e.sessao.id = :sessaoId")
    Optional<EvolucaoSessao> findBySessaoId(@Param("sessaoId") Long sessaoId);
}
