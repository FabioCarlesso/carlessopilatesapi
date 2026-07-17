package com.carlesso.pilatesapi.dto;

import java.math.BigDecimal;

public record ResumoFinanceiroDTO(
        long totalAulas, long quantidadePagamentos, BigDecimal totalPagamentosBruto, BigDecimal totalProfissional) {}
