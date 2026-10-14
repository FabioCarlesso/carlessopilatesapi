package com.carlesso.pilatesapi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.exception.TipoProblema;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Erros que nunca passam pelo GlobalExceptionHandler — aqui, a URL recusada pelo {@code StrictHttpFirewall} — também
 * precisam sair em {@code application/problem+json}. Exige servidor real ({@code RANDOM_PORT}): o {@code MockMvc} não
 * faz o error dispatch do container para {@code /error}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErroForaDoDispatcherIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Test
    void urlRecusadaPeloFirewall_respondeProblemDetail() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/pacientes/%2e%2e/x"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(response.body())
                .contains("\"type\":\"" + TipoProblema.REQUISICAO_INVALIDA.uri() + "\"")
                .contains("\"instance\":\"/pacientes/%2e%2e/x\"")
                .contains("\"timestamp\"")
                .doesNotContain("\"error\"");
    }
}
