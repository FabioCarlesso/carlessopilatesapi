package com.carlesso.pilatesapi.util;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Locking otimista entre requisições: o {@code @Version} das entidades só protege a janela entre a leitura e o flush
 * de uma mesma transação. Para pegar a edição concorrente que nasce no GET de cada recepcionista, o cliente devolve
 * no update a {@code version} recebida, e ela precisa bater com a do registro no banco. Ver issue #101.
 *
 * <p>Versão ausente ({@code null}) é o modo legado temporário: o update segue sem a checagem, como antes, até o
 * frontend passar a enviá-la.
 *
 * <p>Os updates gravam com {@code saveAndFlush}: o Hibernate só incrementa a versão no flush, e sem ele a resposta
 * sairia com a versão antiga — o próximo save do mesmo cliente tomaria um 409 indevido.
 */
public final class VersaoGuard {

    private VersaoGuard() {}

    /**
     * Recusa o update quando a versão enviada pelo cliente não é a atual do registro.
     *
     * @throws ObjectOptimisticLockingFailureException mapeada para {@code 409} {@code concurrent-modification} pelo
     *     GlobalExceptionHandler
     */
    public static void exigirVersaoAtual(Long versaoEnviada, Long versaoAtual, Class<?> entidade, Object id) {
        if (versaoEnviada != null && !versaoEnviada.equals(versaoAtual)) {
            throw new ObjectOptimisticLockingFailureException(entidade, id);
        }
    }
}
