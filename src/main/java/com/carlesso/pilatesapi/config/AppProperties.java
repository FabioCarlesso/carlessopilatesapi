package com.carlesso.pilatesapi.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid Cobranca cobranca) {

    public record Cobranca(
            @NotBlank
            @Pattern(regexp = "^(\\S+\\s){5}\\S+$",
                     message = "Deve ser uma cron expression com 6 campos (s m h dom mon dow)")
            String cronVencidos,
            @NotBlank
            @Pattern(regexp = "^(\\S+\\s){5}\\S+$",
                     message = "Deve ser uma cron expression com 6 campos (s m h dom mon dow)")
            String cronCobrancasFuturas,
            @Min(1) int vencimentoDias) {
    }
}
