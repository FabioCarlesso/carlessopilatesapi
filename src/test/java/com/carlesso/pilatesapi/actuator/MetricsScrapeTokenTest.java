package com.carlesso.pilatesapi.actuator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.security.MetricsScrapeTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.metrics.scrape-token=" + MetricsScrapeTokenTest.SCRAPE_TOKEN)
@AutoConfigureMockMvc
@AutoConfigureMetrics
class MetricsScrapeTokenTest {

    static final String SCRAPE_TOKEN = "scrape-token-de-teste-com-mais-de-32-caracteres";

    @Autowired
    private MockMvc mvc;

    @Test
    void prometheus_comTokenDeScrape_deveRetornar200() throws Exception {
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Bearer " + SCRAPE_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void prometheus_comTokenDeScrapeErrado_deveRetornar401() throws Exception {
        mvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Bearer " + SCRAPE_TOKEN + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeScrape_naoDeveAutorizarOutrasRotas() throws Exception {
        mvc.perform(get("/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + SCRAPE_TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, "Bearer " + SCRAPE_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeScrapeCurto_deveImpedirASubida() {
        assertThatThrownBy(() -> new MetricsScrapeTokenFilter("curto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
