package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvolucaoSessaoRepository extends JpaRepository<EvolucaoSessao, Long> {

    boolean existsBySessaoId(Long sessaoId);

    void deleteBySessaoId(Long sessaoId);

    @Query(
            """
            SELECT e FROM EvolucaoSessao e
            JOIN FETCH e.sessao s
            WHERE e.id = :id
            """)
    Optional<EvolucaoSessao> findByIdComSessao(@Param("id") Long id);

    @Query(
            """
            SELECT e FROM EvolucaoSessao e
            JOIN FETCH e.sessao s
            WHERE s.id = :sessaoId
            """)
    Optional<EvolucaoSessao> findBySessaoId(@Param("sessaoId") Long sessaoId);
}
