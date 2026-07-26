package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import java.util.List;
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

    // O JOIN FETCH da sessão é obrigatório: EvolucaoSessaoResponseDTO.from lê
    // e.getSessao().getId() e a associação é LAZY — sem o fetch, uma lista com
    // centenas de evoluções dispararia um select por item.
    // A ordenação é pela sessão (e não por dataHoraRegistro) porque é a sessão
    // que define a posição na linha do tempo; `horario` desempata sessões do
    // mesmo dia e `id` garante ordem estável quando não há horário.
    @Query(
            """
            SELECT e FROM EvolucaoSessao e
            JOIN FETCH e.sessao s
            JOIN s.paciente pac
            WHERE pac.id = :pacienteId
            ORDER BY s.data DESC, s.horario DESC NULLS LAST, s.id DESC
            """)
    List<EvolucaoSessao> findByPacienteOrdenadas(@Param("pacienteId") Long pacienteId);
}
