package com.carlesso.pilatesapi.entity.enums;

/**
 * Opção pré-definida de um campo da anamnese, da avaliação fisioterapêutica ou da reavaliação (#248).
 *
 * <p>O {@code name()} da constante é o valor gravado no banco e trafegado na API; o {@link #getRotulo() rótulo} é o
 * texto exibido. O catálogo {@code GET /avaliacoes/opcoes} é montado a partir destes enums, então o FE não duplica as
 * listas.
 *
 * <p><b>Evolução:</b> uma opção nunca é apagada nem renomeada — o valor já está gravado em registros antigos. Para
 * retirá-la de uso, a constante ganha {@code ativa = false}: o catálogo continua listando-a (o FE exibe o valor nos
 * registros que o usam, mas não o oferece para seleção nova) e a API continua aceitando-a, para que a edição de um
 * registro antigo que reenvia o valor não falhe. Incluir uma opção é só acrescentar a constante.
 */
public interface OpcaoAvaliacao {

    /** Valor gravado e trafegado: o nome da constante. */
    String name();

    /** Texto exibido ao usuário, em português. */
    String getRotulo();

    /** {@code false} para a opção aposentada, que só continua valendo nos registros que já a usam. */
    boolean isAtiva();
}
