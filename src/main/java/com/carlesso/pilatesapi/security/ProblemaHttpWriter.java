package com.carlesso.pilatesapi.security;

import com.carlesso.pilatesapi.exception.Problemas;
import com.carlesso.pilatesapi.exception.TipoProblema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

/**
 * Escreve um {@link ProblemDetail} direto na resposta servlet. Os 401/403 da autorização por URL nascem no filter
 * chain, antes do DispatcherServlet, e nunca chegam ao GlobalExceptionHandler — sem isto sairiam fora do contrato de
 * erro da API.
 */
public class ProblemaHttpWriter {

    private final ObjectMapper objectMapper;

    public ProblemaHttpWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escrever(HttpServletRequest request, HttpServletResponse response, TipoProblema tipo, String detalhe)
            throws IOException {
        ProblemDetail problema = Problemas.de(tipo, detalhe);
        problema.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(tipo.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
