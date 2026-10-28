-- Forma de pagamento (issue #215): como o dinheiro de um pagamento confirmado entrou, para conferir o caixa e
-- conciliar com o extrato. É gravada na confirmação (PATCH /pagamentos/{id}/pagar), não na criação: a cobrança nasce
-- no scheduler sem saber como o paciente vai pagar.
-- Nula de propósito: pagamentos já confirmados não têm como recuperar a forma real, e os ainda em aberto só a ganham
-- ao serem pagos. Os valores válidos são os do enum FormaPagamento, gravado como texto, como `status`.
ALTER TABLE pagamentos ADD COLUMN forma_pagamento VARCHAR(20);
