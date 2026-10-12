package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.ContatoCobranca;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContatoCobrancaRepository extends JpaRepository<ContatoCobranca, Long> {

    /**
     * Histórico do pagamento, do contato mais recente para o mais antigo. Dois contatos no mesmo dia desempatam pelo
     * momento do registro, e o {@code id} fecha o empate restante para a ordem ser estável.
     */
    List<ContatoCobranca> findByPagamentoIdOrderByDataContatoDescRegistradoEmDescIdDesc(Long pagamentoId);

    Optional<ContatoCobranca> findByIdAndPagamentoId(Long id, Long pagamentoId);
}
