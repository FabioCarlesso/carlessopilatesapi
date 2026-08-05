package com.carlesso.pilatesapi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cobre a compressão gzip configurada em {@code server.compression.*}.
 *
 * <p>Precisa de servidor real ({@code RANDOM_PORT}): a compressão acontece no
 * conector do Tomcat, que o {@code MockMvc} não exercita.
 *
 * <p>Usa o {@link HttpClient} do JDK em vez do {@code TestRestTemplate} porque ele
 * não manda {@code Accept-Encoding} por conta própria nem descomprime a resposta —
 * é o que permite observar o header e os bytes como trafegam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompressaoRespostaIntegrationTest {

    /** Recurso público bem acima do {@code min-response-size}: o JSON do OpenAPI. */
    private static final String RECURSO_GRANDE = "/v3/api-docs";

    /**
     * Recurso público cujo {@code Content-Type} está fora da lista configurada — o
     * Actuator responde {@code application/vnd.spring-boot.actuator.v3+json}.
     */
    private static final String RECURSO_MIME_FORA_DA_LISTA = "/actuator/health/liveness";

    private static final int MIN_RESPONSE_SIZE = 1024;

    private static final String EMAIL_LOGIN = "compressao@email.com";

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Limpeza explícita: o contexto Spring (e com ele o H2, que usa
     * {@code DB_CLOSE_DELAY=-1}) é reaproveitado por outras classes da suíte, então um
     * usuário deixado para trás vaza para elas. {@code @Transactional} não resolveria —
     * com {@code RANDOM_PORT} a requisição roda em outra thread e não participa da
     * transação do teste, logo o rollback automático não alcança o que o servidor gravou.
     */
    @AfterEach
    void removerUsuarioDeTeste() {
        userRepository.findByEmail(EMAIL_LOGIN).ifPresent(userRepository::delete);
    }

    @Test
    void respostaGrandeComAcceptEncodingGzip_deveVirComprimida() throws Exception {
        HttpResponse<byte[]> resposta = get(RECURSO_GRANDE, true);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(resposta.body().length)
                .isLessThan(descomprimir(resposta.body()).length());
    }

    @Test
    void respostaGrandeComprimida_deveTerCorpoIdenticoAoNaoComprimido() throws Exception {
        String comGzip = descomprimir(get(RECURSO_GRANDE, true).body());
        String semGzip = new String(get(RECURSO_GRANDE, false).body(), StandardCharsets.UTF_8);

        assertThat(comGzip).isEqualTo(semGzip);
    }

    @Test
    void respostaGrandeSemAcceptEncoding_deveVirLegivelESemCompressao() throws Exception {
        HttpResponse<byte[]> resposta = get(RECURSO_GRANDE, false);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(new String(resposta.body(), StandardCharsets.UTF_8)).startsWith("{");
    }

    /**
     * O que efetivamente mantém uma resposta fora da compressão é o {@code Content-Type}
     * não constar de {@code server.compression.mime-types} — mesmo mecanismo que protege
     * o XLSX dos relatórios e as fotos das análises posturais.
     */
    @Test
    void mimeTypeForaDaLista_naoDeveSerComprimido() throws Exception {
        HttpResponse<byte[]> resposta = get(RECURSO_MIME_FORA_DA_LISTA, true);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Type"))
                .hasValue("application/vnd.spring-boot.actuator.v3+json");
        assertThat(resposta.headers().firstValue("Content-Encoding")).isEmpty();
    }

    /**
     * O {@code min-response-size} <em>não</em> é o que segura as respostas pequenas: o
     * Tomcat só o consulta quando conhece o tamanho de antemão, e todo JSON do Spring MVC
     * sai em {@code Transfer-Encoding: chunked}. O login é o exemplo extremo — bem menor
     * que 1 KB e ainda assim comprimido.
     *
     * <p>Não é um problema de segurança aqui: o BREACH depende de o atacante conseguir
     * fazer o navegador da vítima repetir requisições autenticadas, o que não ocorre com
     * token Bearer (não há credencial ambiente como cookie), e o login ainda exige a senha
     * no corpo. O teste existe para que uma mudança nesse comportamento não passe batida.
     */
    @Test
    void loginChunkedAbaixoDoLimiar_aindaAssimEComprimido() throws Exception {
        User user = new User();
        user.setName("Usuário Teste");
        user.setEmail(EMAIL_LOGIN);
        user.setPassword(passwordEncoder.encode("senha1234"));
        user.setRole(Role.USER);
        user.setAtivo(true);
        userRepository.save(user);

        String payload =
                """
                {"email": "%s", "password": "senha1234"}
                """.formatted(EMAIL_LOGIN);

        HttpRequest requisicao = HttpRequest.newBuilder(uri("/auth/login"))
                .header("Content-Type", "application/json")
                .header("Accept-Encoding", "gzip")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<byte[]> resposta = client.send(requisicao, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Content-Length")).isEmpty();
        assertThat(resposta.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(descomprimir(resposta.body()).length()).isLessThan(MIN_RESPONSE_SIZE);
    }

    private HttpResponse<byte[]> get(String caminho, boolean aceitaGzip) throws Exception {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(uri(caminho));
        if (aceitaGzip) {
            requisicao.header("Accept-Encoding", "gzip");
        }
        return client.send(requisicao.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    private static String descomprimir(byte[] corpo) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(corpo))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
