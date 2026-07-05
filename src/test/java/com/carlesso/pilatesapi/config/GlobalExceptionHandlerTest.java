package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_resourceNotFound_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleNotFound(
                new ResourceNotFoundException("Paciente não encontrado: 1"));

        assertThat(response).containsEntry("erro", "Paciente não encontrado: 1");
    }

    @Test
    void handleNotFound_entityNotFound_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleNotFound(
                new EntityNotFoundException("Profissional não encontrado: 1"));

        assertThat(response).containsEntry("erro", "Profissional não encontrado: 1");
    }

    @Test
    void handleConflict_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleConflict(
                new ConflictException("E-mail já cadastrado"));

        assertThat(response).containsEntry("erro", "E-mail já cadastrado");
    }

    @Test
    void handleBusiness_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleBusiness(
                new BusinessException("Paciente inativo não pode receber novas cobranças"));

        assertThat(response).containsEntry("erro", "Paciente inativo não pode receber novas cobranças");
    }

    @Test
    void handleBadRequest_retornaMensagemDoErro() {
        Map<String, String> response = handler.handleBadRequest(
                new IllegalArgumentException("Período inicial é obrigatório"));

        assertThat(response).containsEntry("erro", "Período inicial é obrigatório");
    }

    @Test
    void handleNoHandlerFound_retornaMensagemRotaNaoEncontrada() {
        Map<String, String> response = handler.handleNoHandlerFound(
                new NoHandlerFoundException("GET", "/inexistente", new HttpHeaders()));

        assertThat(response).containsEntry("erro", "Rota não encontrada");
    }

    @Test
    void handleDataIntegrity_retornaMensagemPadrao() {
        Map<String, String> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("violação"));

        assertThat(response).containsEntry("erro",
                "Violação de integridade: registro duplicado ou constraint violada");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleMethodArgumentNotValid_retornaCamposComMensagens() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "pacienteRequestDTO");
        bindingResult.addError(new FieldError("pacienteRequestDTO", "nome", "não deve estar em branco"));
        bindingResult.addError(new FieldError("pacienteRequestDTO", "email", "deve ser um endereço de e-mail bem formado"));
        var methodParameter = new MethodParameter(
                getClass().getDeclaredMethod("handleMethodArgumentNotValid_retornaCamposComMensagens"), -1);

        Map<String, Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(methodParameter, bindingResult));

        assertThat(response).containsEntry("erro", GlobalExceptionHandler.ERRO_VALIDACAO);
        assertThat((Map<String, String>) response.get("campos"))
                .containsEntry("nome", "não deve estar em branco")
                .containsEntry("email", "deve ser um endereço de e-mail bem formado");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_retornaCamposComMensagens() {
        record Filtro(@NotBlank String competencia) {}
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Filtro(""));

            Map<String, Object> response = handler.handleConstraintViolation(
                    new ConstraintViolationException(violations));

            assertThat(response).containsEntry("erro", GlobalExceptionHandler.ERRO_VALIDACAO);
            assertThat((Map<String, String>) response.get("campos")).containsKey("competencia");
        }
    }

    @Test
    void handleMessageNotReadable_retornaMensagemNeutra() {
        Map<String, String> response = handler.handleMessageNotReadable(
                new HttpMessageNotReadableException("JSON malformado",
                        new MockHttpInputMessage(new byte[0])));

        assertThat(response).containsEntry("erro", GlobalExceptionHandler.ERRO_CORPO_ILEGIVEL);
    }

    @Test
    void handleAccessDenied_retornaMensagemAcessoNegado() {
        Map<String, String> response = handler.handleAccessDenied(
                new AccessDeniedException("negado"));

        assertThat(response).containsEntry("erro", "Acesso negado");
    }

    @Test
    void handleUnexpected_excecaoDoSpringMvc_preservaStatusOriginal() {
        ResponseEntity<Map<String, String>> response = handler.handleUnexpected(
                new MissingServletRequestParameterException("competencia", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).extractingByKey("erro").asString()
                .contains("competencia");
    }

    @Test
    void handleUnexpected_excecaoNaoMapeada_retorna500ComMensagemNeutra() {
        ResponseEntity<Map<String, String>> response = handler.handleUnexpected(
                new RuntimeException("detalhe interno que não pode vazar"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("erro", GlobalExceptionHandler.ERRO_INTERNO);
        assertThat(response.getBody().get("erro")).doesNotContain("detalhe interno");
    }
}
