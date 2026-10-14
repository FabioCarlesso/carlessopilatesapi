package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.exception.Problemas;
import com.carlesso.pilatesapi.exception.TipoProblema;
import jakarta.servlet.RequestDispatcher;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemaErrorControllerTest {

    private final ProblemaErrorController controller = new ProblemaErrorController();

    private static MockHttpServletRequest requisicaoDeErro(int status, String uriOriginal) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, uriOriginal);
        return request;
    }

    @Test
    void erro4xx_usaTipoDoCatalogoEMensagemNeutra() {
        MockHttpServletRequest request = requisicaoDeErro(400, "/pacientes/%2e%2e/x");
        request.setAttribute(
                RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("motivo interno do firewall"));

        ResponseEntity<ProblemDetail> response = controller.erro(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = response.getBody();
        assertThat(problema.getType()).isEqualTo(TipoProblema.REQUISICAO_INVALIDA.uri());
        assertThat(problema.getDetail()).isEqualTo(TipoProblema.REQUISICAO_INVALIDA.titulo());
        assertThat(problema.getInstance()).isEqualTo(URI.create("/pacientes/%2e%2e/x"));
        assertThat(problema.getProperties()).containsKey(Problemas.PROPRIEDADE_TIMESTAMP);
    }

    @Test
    void erro5xx_naoVazaDetalheDaExcecao() {
        MockHttpServletRequest request = requisicaoDeErro(500, "/pacientes");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("detalhe interno"));

        ProblemDetail problema = controller.erro(request).getBody();

        assertThat(problema.getType()).isEqualTo(TipoProblema.ERRO_INTERNO.uri());
        assertThat(problema.getDetail()).isEqualTo(GlobalExceptionHandler.ERRO_INTERNO);
    }

    @Test
    void statusForaDoCatalogo_usaAboutBlankComFraseDoHttp() {
        ProblemDetail problema =
                controller.erro(requisicaoDeErro(406, "/pacientes")).getBody();

        assertThat(problema.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problema.getDetail()).isEqualTo("Not Acceptable");
    }

    @Test
    void semAtributosDeErro_responde500() {
        ResponseEntity<ProblemDetail> response = controller.erro(new MockHttpServletRequest("GET", "/error"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/"));
    }

    @Test
    void uriOriginalInvalida_usaRaizComoInstance() {
        ProblemDetail problema =
                controller.erro(requisicaoDeErro(400, "/pacientes/a b")).getBody();

        assertThat(problema.getInstance()).isEqualTo(URI.create("/"));
    }
}
