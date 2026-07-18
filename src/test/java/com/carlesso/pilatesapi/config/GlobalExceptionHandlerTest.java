package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    void handleNotFound_resourceNotFound_retornaMensagemDoErro() {
        Map<String, String> response =
                handler.handleNotFound(new ResourceNotFoundException("Paciente não encontrado: 1"));

        assertThat(response).containsEntry("erro", "Paciente não encontrado: 1");
    }

    @Test
    void handleNotFound_entityNotFound_retornaMensagemDoErro() {
        Map<String, String> response =
                handler.handleNotFound(new EntityNotFoundException("Profissional não encontrado: 1"));

        assertThat(response).containsEntry("erro", "Profissional não encontrado: 1");
    }

    @Test
    void handleConflict_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleConflict(new ConflictException("E-mail já cadastrado"));

        assertThat(response).containsEntry("erro", "E-mail já cadastrado");
    }

    @Test
    void handleBusiness_retornaMensagemDoErro() {
        Map<String, String> response =
                handler.handleBusiness(new BusinessException("Paciente inativo não pode receber novas cobranças"));

        assertThat(response).containsEntry("erro", "Paciente inativo não pode receber novas cobranças");
    }

    @Test
    void handleBadRequest_retornaMensagemDoErro() {
        Map<String, String> response =
                handler.handleBadRequest(new IllegalArgumentException("Período inicial é obrigatório"));

        assertThat(response).containsEntry("erro", "Período inicial é obrigatório");
    }

    @Test
    void handleNoHandlerFound_retornaMensagemRotaNaoEncontrada() {
        ResponseEntity<Object> response = handler.handleNoHandlerFoundException(
                new NoHandlerFoundException("GET", "/inexistente", new HttpHeaders()),
                new HttpHeaders(),
                HttpStatus.NOT_FOUND,
                webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(Map.of("erro", GlobalExceptionHandler.ERRO_ROTA));
    }

    @Test
    void handleDataIntegrity_retornaMensagemPadrao() {
        Map<String, String> response = handler.handleDataIntegrity(new DataIntegrityViolationException("violação"));

        assertThat(response).containsEntry("erro", "Violação de integridade: registro duplicado ou constraint violada");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleMethodArgumentNotValid_retornaCamposComMensagens() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "pacienteRequestDTO");
        bindingResult.addError(new FieldError("pacienteRequestDTO", "nome", "não deve estar em branco"));
        bindingResult.addError(
                new FieldError("pacienteRequestDTO", "email", "deve ser um endereço de e-mail bem formado"));
        var methodParameter = new MethodParameter(
                getClass().getDeclaredMethod("handleMethodArgumentNotValid_retornaCamposComMensagens"), -1);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(methodParameter, bindingResult),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("erro", GlobalExceptionHandler.ERRO_VALIDACAO);
        assertThat((Map<String, String>) body.get("campos"))
                .containsEntry("nome", "não deve estar em branco")
                .containsEntry("email", "deve ser um endereço de e-mail bem formado");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleMethodArgumentNotValid_mensagemNula_usaMensagemPadrao() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "pacienteRequestDTO");
        bindingResult.addError(new FieldError("pacienteRequestDTO", "nome", null, false, null, null, null));
        var methodParameter = new MethodParameter(
                getClass().getDeclaredMethod("handleMethodArgumentNotValid_mensagemNula_usaMensagemPadrao"), -1);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(methodParameter, bindingResult),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat((Map<String, String>) body.get("campos"))
                .containsEntry("nome", GlobalExceptionHandler.MENSAGEM_CAMPO_PADRAO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_retornaCamposComMensagens() {
        record Filtro(@NotBlank String competencia) {}
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Filtro(""));

            Map<String, Object> response =
                    handler.handleConstraintViolation(new ConstraintViolationException(violations));

            assertThat(response).containsEntry("erro", GlobalExceptionHandler.ERRO_VALIDACAO);
            assertThat((Map<String, String>) response.get("campos")).containsKey("competencia");
        }
    }

    @Test
    void handleHttpMessageNotReadable_retornaMensagemNeutra() {
        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("JSON malformado", new MockHttpInputMessage(new byte[0])),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("erro", GlobalExceptionHandler.ERRO_CORPO_ILEGIVEL));
    }

    @Test
    void handleMaxUploadSizeExceeded_retorna413ComMensagemClara() throws Exception {
        ResponseEntity<Object> response =
                handler.handleException(new MaxUploadSizeExceededException(2L * 1024 * 1024), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isEqualTo(Map.of("erro", GlobalExceptionHandler.ERRO_UPLOAD_EXCEDIDO));
    }

    @Test
    void handleAccessDenied_retornaMensagemAcessoNegado() {
        Map<String, String> response = handler.handleAccessDenied(new AccessDeniedException("negado"));

        assertThat(response).containsEntry("erro", "Acesso negado");
    }

    @Test
    void handleException_parametroObrigatorioAusente_retorna400ComDetalhe() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new MissingServletRequestParameterException("competencia", "String"), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().toString()).contains("competencia");
    }

    @Test
    void handleException_metodoNaoSuportado_retorna405ComHeaderAllow() throws Exception {
        ResponseEntity<Object> response =
                handler.handleException(new HttpRequestMethodNotSupportedException("POST", List.of("GET")), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isNotEmpty();
    }

    @Test
    void handleException_erro5xxDoFramework_retornaMensagemNeutra() throws Exception {
        ResponseEntity<Object> response = handler.handleException(new AsyncRequestTimeoutException(), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(Map.of("erro", GlobalExceptionHandler.ERRO_INTERNO));
    }

    @Test
    void handleUnexpected_excecaoNaoMapeada_retorna500ComMensagemNeutra() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUnexpected(new RuntimeException("detalhe interno que não pode vazar"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("erro", GlobalExceptionHandler.ERRO_INTERNO);
        assertThat(response.getBody().get("erro")).doesNotContain("detalhe interno");
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private static class ExcecaoAnotadaComConflito extends RuntimeException {
        ExcecaoAnotadaComConflito(String message) {
            super(message);
        }
    }

    @Test
    void handleUnexpected_excecaoAnotadaComResponseStatus_preservaStatusDeclarado() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUnexpected(new ExcecaoAnotadaComConflito("Horário já reservado"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("erro", "Horário já reservado");
    }
}
