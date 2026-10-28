package com.carlesso.pilatesapi.entity.enums;

/** Como o dinheiro de um pagamento confirmado entrou (issue #215). Registrada na confirmação, não na criação. */
public enum FormaPagamento {
    PIX,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    DINHEIRO,
    TRANSFERENCIA
}
