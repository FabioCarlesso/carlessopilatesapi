package com.carlesso.pilatesapi.util;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Regra compartilhada das listagens paginadas: ordenação só por campos conhecidos e {@code id} como último critério.
 *
 * <p>Sem a lista de campos, um {@code sort} desconhecido estouraria como {@code PropertyReferenceException} dentro do
 * repositório e sairia como {@code 500}. Sem o desempate, linhas com a mesma data — comuns, já que aulas e cobranças
 * nascem em lote — podem trocar de página entre uma requisição e outra.
 */
public final class PaginacaoGuard {

    private PaginacaoGuard() {}

    /**
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler, quando algum campo de
     *     ordenação não está em {@code camposOrdenaveis}
     */
    public static Pageable comDesempatePorId(
            Pageable pageable, Set<String> camposOrdenaveis, Sort.Direction direcaoDesempate) {
        for (Sort.Order order : pageable.getSort()) {
            if (!camposOrdenaveis.contains(order.getProperty())) {
                throw new IllegalArgumentException("Campo de ordenação inválido: %s. Aceitos: %s"
                        .formatted(
                                order.getProperty(),
                                String.join(
                                        ", ", camposOrdenaveis.stream().sorted().toList())));
            }
        }
        if (pageable.isUnpaged()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by(direcaoDesempate, "id")));
    }
}
