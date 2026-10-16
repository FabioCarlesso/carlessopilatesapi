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
     * Para consultas que passam pela Criteria API (derivadas e {@code Specification}).
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler, quando algum campo de
     *     ordenação não está em {@code camposOrdenaveis}
     */
    public static Pageable comDesempatePorId(
            Pageable pageable, Set<String> camposOrdenaveis, Sort.Direction direcaoDesempate) {
        return validarEOrdenar(pageable, camposOrdenaveis, direcaoDesempate, false);
    }

    /**
     * Para consultas JPQL ({@code @Query}): além do desempate, registros sem valor no campo ordenado vão para o fim nas
     * duas direções. Sem isso o PostgreSQL usa {@code NULLS FIRST} em {@code desc}, e {@code sort=horario,desc} abria a
     * página com as sessões sem horário. Não serve para a Criteria API: o Spring Data recusa precedência de nulos ali
     * com {@code UnsupportedOperationException}.
     *
     * @throws IllegalArgumentException mapeada para {@code 400} pelo GlobalExceptionHandler, quando algum campo de
     *     ordenação não está em {@code camposOrdenaveis}
     */
    public static Pageable comNulosNoFimEDesempatePorId(
            Pageable pageable, Set<String> camposOrdenaveis, Sort.Direction direcaoDesempate) {
        return validarEOrdenar(pageable, camposOrdenaveis, direcaoDesempate, true);
    }

    private static Pageable validarEOrdenar(
            Pageable pageable, Set<String> camposOrdenaveis, Sort.Direction direcaoDesempate, boolean nulosNoFim) {
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
        Sort ordenacao = nulosNoFim
                ? Sort.by(pageable.getSort().stream().map(Sort.Order::nullsLast).toList())
                : pageable.getSort();
        return PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), ordenacao.and(Sort.by(direcaoDesempate, "id")));
    }
}
