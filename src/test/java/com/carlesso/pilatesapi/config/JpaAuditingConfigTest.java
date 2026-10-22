package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class JpaAuditingConfigTest {

    private final JpaAuditingConfig config = new JpaAuditingConfig();

    @Test
    void horarioDoEstudio_deveUsarOFusoConfiguradoENaoODaJvm() {
        // +14h: nenhum fuso de JVM de CI ou de container coincide com ele, então o teste prova que o
        // relógio de auditoria segue app.fuso-horario.
        ZoneId fuso = ZoneId.of("Pacific/Kiritimati");

        LocalDateTime agora = LocalDateTime.from(
                config.horarioDoEstudio(fuso.getId()).getNow().orElseThrow());

        assertThat(agora).isCloseTo(LocalDateTime.now(fuso), within(5, ChronoUnit.SECONDS));
    }
}
