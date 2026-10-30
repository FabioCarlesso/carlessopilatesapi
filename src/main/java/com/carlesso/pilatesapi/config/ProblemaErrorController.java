package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.Problemas;
import com.carlesso.pilatesapi.exception.TipoProblema;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Substitui o {@code BasicErrorController} do Spring Boot no {@code /error}. Por ali passam os erros que nunca chegam
 * ao {@link GlobalExceptionHandler}: requisição recusada pelo {@code StrictHttpFirewall} do Spring Security, exceção
 * lançada dentro de um filtro e {@code sendError} do container. Sem este controller eles sairiam no JSON padrão do
 * Boot ({@code timestamp/status/error/path}), fora do contrato de erro da API.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ProblemaErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemaErrorController.class);

    @RequestMapping
    public ResponseEntity<ProblemDetail> erro(HttpServletRequest request) {
        HttpStatusCode status = statusDe(request);
        String detalhe;
        if (status.is5xxServerError()) {
            // Só loga quando há exceção: um GET direto em /error (rota pública) não deve poluir o log de erros.
            if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable excecao) {
                log.error("Erro ao processar a requisição fora do DispatcherServlet", excecao);
            }
            detalhe = GlobalExceptionHandler.ERRO_INTERNO;
        } else {
            // Mensagem do catálogo, nunca a do container: a da exceção (ex.: o motivo da recusa do firewall) é
            // detalhe interno.
            detalhe = TipoProblema.doStatus(status)
                    .map(TipoProblema::titulo)
                    .orElseGet(() -> HttpStatus.valueOf(status.value()).getReasonPhrase());
        }
        ProblemDetail problema = Problemas.doStatus(status, detalhe);
        problema.setInstance(instanciaDe(request));
        return ResponseEntity.status(status).body(problema);
    }

    /**
     * Caminho original da requisição; sem ele o Spring preencheria {@code instance} com {@code /error}. Uma URI que
     * nem chega a ser válida (justamente o que o firewall recusa) usa o caminho da raiz.
     */
    private URI instanciaDe(HttpServletRequest request) {
        Object uriOriginal = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (uriOriginal != null) {
            try {
                return URI.create(uriOriginal.toString());
            } catch (IllegalArgumentException e) {
                log.debug("URI original inválida no /error: {}", uriOriginal);
            }
        }
        return URI.create("/");
    }

    private HttpStatusCode statusDe(HttpServletRequest request) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (codigo instanceof Integer valor && HttpStatus.resolve(valor) != null) {
            return HttpStatus.valueOf(valor);
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
