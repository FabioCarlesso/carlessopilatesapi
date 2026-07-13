package com.carlesso.pilatesapi.support;

import com.carlesso.pilatesapi.metrics.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Fornece {@link BusinessMetrics} sobre um registry em memória para os slices de
 * teste (@DataJpaTest), onde a autoconfiguração de métricas do Actuator não é carregada.
 */
@TestConfiguration
@Import(BusinessMetrics.class)
public class MetricsTestConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
