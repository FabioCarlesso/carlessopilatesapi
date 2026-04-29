package com.carlesso.pilatesapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Cobranca cobranca, @Valid Paginacao paginacao) {

    public record Cobranca(
            @NotBlank String cronVencidos,
            @NotBlank String cronCobrancasFuturas,
            @Min(0) int vencimentoDias) {
    }

    public record Paginacao(@Min(1) int tamanhoPadrao) {
    }
}
