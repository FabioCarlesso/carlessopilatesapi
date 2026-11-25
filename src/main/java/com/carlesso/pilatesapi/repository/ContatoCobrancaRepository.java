package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.ContatoCobranca;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContatoCobrancaRepository extends JpaRepository<ContatoCobranca, Long> {

    /**
     * Histórico do pagamento, do contato mais recente para o mais antigo. Dois contatos no mesmo dia desempatam pelo
     * momento do registro, e o {@code id} fecha o empate restante para a ordem ser estável.
     */
    List<ContatoCobranca> findByPagamentoIdOrderByDataContatoDescRegistradoEmDescIdDesc(Long pagamentoId);

    Optional<ContatoCobranca> findByIdAndPagamentoId(Long id, Long pagamentoId);

    /**
     * Quantidade de contatos e data do último, por pagamento, numa consulta só: a exportação de cobranças (#232) lê
     * milhares de pagamentos, e buscar o histórico de cada um seria um select por linha. Pagamento sem contato não
     * aparece no resultado.
     */
    @Query(
            """
            select c.pagamento.id as pagamentoId, count(c) as quantidade, max(c.dataContato) as ultimoContato
            from ContatoCobranca c
            where c.pagamento.id in :pagamentoIds
            group by c.pagamento.id""")
    List<ResumoContatosProjection> resumirPorPagamentos(@Param("pagamentoIds") Collection<Long> pagamentoIds);

    interface ResumoContatosProjection {
        Long getPagamentoId();

        Long getQuantidade();

        LocalDate getUltimoContato();
    }
}
