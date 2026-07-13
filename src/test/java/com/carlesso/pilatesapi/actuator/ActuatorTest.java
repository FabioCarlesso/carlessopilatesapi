package com.carlesso.pilatesapi.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// Testes desabilitam o export de métricas por padrão; sem isso o registry do
// Prometheus não é criado e /actuator/prometheus responderia 404.
@AutoConfigureObservability(tracing = false)
class ActuatorTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthEndpointReturns200WithStatusUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeReturns200WithStatusUpWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeReturns200WithStatusUpWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoEndpointReturns200() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("CarlessoPilatesApi"));
    }

    @Test
    void unexposedEndpointReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusEndpointReturns401WithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void prometheusEndpointReturns403ForNonAdmin() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void prometheusEndpointExposesJvmHttpAndBusinessMetricsForAdmin() throws Exception {
        // a série http_server_requests só existe depois que ao menos uma requisição
        // passou pelo filtro de métricas do Actuator
        mvc.perform(get("/actuator/health"));

        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests_seconds")
                .contains("pilates_cobrancas_geradas_total")
                .contains("pilates_cobrancas_vencidas_total")
                .contains("pilates_pagamentos_confirmados_total")
                .contains("pilates_logins_bloqueados_total")
                .contains("pilates_emails_reset_enviados_total");
    }
}
