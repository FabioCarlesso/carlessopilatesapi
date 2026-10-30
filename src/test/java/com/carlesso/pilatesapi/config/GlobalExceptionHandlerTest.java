package com.carlesso.pilatesapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.Problemas;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.exception.TipoProblema;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.exception.UnauthorizedException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
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

    private static void assertProblema(ProblemDetail problema, TipoProblema tipo, String detalhe) {
        assertThat(problema.getType()).isEqualTo(tipo.uri());
        assertThat(problema.getTitle()).isEqualTo(tipo.titulo());
        assertThat(problema.getStatus()).isEqualTo(tipo.status().value());
        assertThat(problema.getDetail()).isEqualTo(detalhe);
        assertThat(problema.getProperties()).containsKey(Problemas.PROPRIEDADE_TIMESTAMP);
    }

    private static ProblemDetail corpo(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Problemas.CampoInvalido> campos(ProblemDetail problema) {
        return (List<Problemas.CampoInvalido>) problema.getProperties().get(Problemas.PROPRIEDADE_CAMPOS);
    }

    @Test
    void handleNotFound_resourceNotFound_retornaProblemaNotFound() {
        ProblemDetail problema = handler.handleNotFound(new ResourceNotFoundException("Paciente não encontrado: 1"));

        assertProblema(problema, TipoProblema.NAO_ENCONTRADO, "Paciente não encontrado: 1");
    }

    @Test
    void handleOrdenacaoInvalida_campoInexistente_retornaProblemaBadRequest() {
        var erro = new PropertyReferenceException(
                "role", TypeInformation.of(com.carlesso.pilatesapi.entity.User.class), List.of());

        ProblemDetail problema = handler.handleOrdenacaoInvalida(erro);

        assertProblema(problema, TipoProblema.REQUISICAO_INVALIDA, "Ordenação inválida: campo 'role' não existe");
    }

    @Test
    void handleNotFound_entityNotFound_retornaProblemaNotFound() {
        ProblemDetail problema = handler.handleNotFound(new EntityNotFoundException("Profissional não encontrado: 1"));

        assertProblema(problema, TipoProblema.NAO_ENCONTRADO, "Profissional não encontrado: 1");
    }

    @Test
    void handleConflict_retornaProblemaConflict() {
        ProblemDetail problema = handler.handleConflict(new ConflictException("E-mail já cadastrado"));

        assertProblema(problema, TipoProblema.CONFLITO, "E-mail já cadastrado");
    }

    @Test
    void handleBusiness_retornaProblemaBusinessRule() {
        ProblemDetail problema =
                handler.handleBusiness(new BusinessException("Paciente inativo não pode receber novas cobranças"));

        assertProblema(problema, TipoProblema.REGRA_NEGOCIO, "Paciente inativo não pode receber novas cobranças");
    }

    @Test
    void handleBadRequest_retornaProblemaBadRequest() {
        ProblemDetail problema =
                handler.handleBadRequest(new IllegalArgumentException("Período inicial é obrigatório"));

        assertProblema(problema, TipoProblema.REQUISICAO_INVALIDA, "Período inicial é obrigatório");
    }

    @Test
    void handleAuthentication_retornaProblemaUnauthorizedComMensagemGenerica() {
        ProblemDetail problema = handler.handleAuthentication(new BadCredentialsException("senha errada para x"));

        assertProblema(problema, TipoProblema.NAO_AUTENTICADO, GlobalExceptionHandler.ERRO_CREDENCIAIS);
    }

    @Test
    void handleUnauthorized_retornaProblemaUnauthorized() {
        ProblemDetail problema = handler.handleUnauthorized(new UnauthorizedException("Refresh token inválido"));

        assertProblema(problema, TipoProblema.NAO_AUTENTICADO, "Refresh token inválido");
    }

    @Test
    void handleTooManyRequests_retornaProblemaRateLimit() {
        ProblemDetail problema =
                handler.handleTooManyRequests(new TooManyRequestsException("Muitas tentativas. Tente novamente."));

        assertProblema(problema, TipoProblema.LIMITE_REQUISICOES, "Muitas tentativas. Tente novamente.");
    }

    @Test
    void handleNoHandlerFound_retornaProblemaRotaNaoEncontrada() {
        ResponseEntity<Object> response = handler.handleNoHandlerFoundException(
                new NoHandlerFoundException("GET", "/inexistente", new HttpHeaders()),
                new HttpHeaders(),
                HttpStatus.NOT_FOUND,
                webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertProblema(corpo(response), TipoProblema.NAO_ENCONTRADO, GlobalExceptionHandler.ERRO_ROTA);
    }

    @Test
    void handleDataIntegrity_retornaProblemaConflictComMensagemPadrao() {
        ProblemDetail problema = handler.handleDataIntegrity(new DataIntegrityViolationException("violação"));

        assertProblema(problema, TipoProblema.CONFLITO, GlobalExceptionHandler.ERRO_INTEGRIDADE);
    }

    @Test
    void handleOptimisticLocking_retornaProblemaDeConcorrencia() {
        ProblemDetail problema =
                handler.handleOptimisticLocking(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        assertProblema(problema, TipoProblema.CONFLITO_CONCORRENCIA, GlobalExceptionHandler.ERRO_CONCORRENCIA);
    }

    @Test
    void handleMethodArgumentNotValid_retornaListaDeCamposInvalidos() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "pacienteRequestDTO");
        bindingResult.addError(new FieldError("pacienteRequestDTO", "nome", "não deve estar em branco"));
        bindingResult.addError(
                new FieldError("pacienteRequestDTO", "email", "deve ser um endereço de e-mail bem formado"));
        var methodParameter = new MethodParameter(
                getClass().getDeclaredMethod("handleMethodArgumentNotValid_retornaListaDeCamposInvalidos"), -1);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(methodParameter, bindingResult),
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = corpo(response);
        assertProblema(problema, TipoProblema.VALIDACAO, TipoProblema.VALIDACAO.titulo());
        assertThat(campos(problema))
                .containsExactly(
                        new Problemas.CampoInvalido("nome", "não deve estar em branco"),
                        new Problemas.CampoInvalido("email", "deve ser um endereço de e-mail bem formado"));
    }

    @Test
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

        assertThat(campos(corpo(response)))
                .containsExactly(new Problemas.CampoInvalido("nome", GlobalExceptionHandler.MENSAGEM_CAMPO_PADRAO));
    }

    @Test
    void handleConstraintViolation_retornaListaDeCamposInvalidos() {
        record Filtro(@NotBlank String competencia) {}
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Filtro(""));

            ProblemDetail problema = handler.handleConstraintViolation(new ConstraintViolationException(violations));

            assertProblema(problema, TipoProblema.VALIDACAO, TipoProblema.VALIDACAO.titulo());
            assertThat(campos(problema))
                    .extracting(Problemas.CampoInvalido::campo)
                    .containsExactly("competencia");
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
        assertProblema(corpo(response), TipoProblema.REQUISICAO_INVALIDA, GlobalExceptionHandler.ERRO_CORPO_ILEGIVEL);
        assertThat(corpo(response).getProperties()).doesNotContainKey(Problemas.PROPRIEDADE_CAMPOS);
    }

    @Test
    void handleMaxUploadSizeExceeded_retorna413ComMensagemClara() throws Exception {
        ResponseEntity<Object> response =
                handler.handleException(new MaxUploadSizeExceededException(2L * 1024 * 1024), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertProblema(corpo(response), TipoProblema.ARQUIVO_EXCEDIDO, GlobalExceptionHandler.ERRO_UPLOAD_EXCEDIDO);
    }

    @Test
    void handleAccessDenied_retornaProblemaForbidden() {
        ProblemDetail problema = handler.handleAccessDenied(new AccessDeniedException("negado"));

        assertProblema(problema, TipoProblema.ACESSO_NEGADO, GlobalExceptionHandler.ERRO_ACESSO_NEGADO);
    }

    @Test
    void handleException_parametroObrigatorioAusente_retornaProblemaBadRequestComDetalheDoFramework() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new MissingServletRequestParameterException("competencia", "String"), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problema = corpo(response);
        assertThat(problema.getType()).isEqualTo(TipoProblema.REQUISICAO_INVALIDA.uri());
        assertThat(problema.getDetail()).contains("competencia");
        assertThat(problema.getProperties()).containsKey(Problemas.PROPRIEDADE_TIMESTAMP);
    }

    @Test
    void handleException_metodoNaoSuportado_retorna405ComHeaderAllow() throws Exception {
        ResponseEntity<Object> response =
                handler.handleException(new HttpRequestMethodNotSupportedException("POST", List.of("GET")), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isNotEmpty();
        assertThat(corpo(response).getType()).isEqualTo(TipoProblema.METODO_NAO_PERMITIDO.uri());
    }

    @Test
    void handleException_statusForaDoCatalogo_usaAboutBlank() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        ProblemDetail problema = corpo(response);
        assertThat(problema.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problema.getStatus()).isEqualTo(406);
        assertThat(problema.getProperties()).containsKey(Problemas.PROPRIEDADE_TIMESTAMP);
    }

    @Test
    void handleException_erro5xxDoFramework_retornaMensagemNeutraPreservandoStatus() throws Exception {
        ResponseEntity<Object> response = handler.handleException(new AsyncRequestTimeoutException(), webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        ProblemDetail problema = corpo(response);
        assertThat(problema.getType()).isEqualTo(TipoProblema.ERRO_INTERNO.uri());
        assertThat(problema.getStatus()).isEqualTo(503);
        assertThat(problema.getDetail()).isEqualTo(GlobalExceptionHandler.ERRO_INTERNO);
    }

    @Test
    void handleUnexpected_excecaoNaoMapeada_retorna500ComMensagemNeutra() {
        ProblemDetail problema = handler.handleUnexpected(new RuntimeException("detalhe interno que não pode vazar"));

        assertProblema(problema, TipoProblema.ERRO_INTERNO, GlobalExceptionHandler.ERRO_INTERNO);
        assertThat(problema.getDetail()).doesNotContain("detalhe interno");
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    private static class ExcecaoAnotadaComConflito extends RuntimeException {
        ExcecaoAnotadaComConflito(String message) {
            super(message);
        }
    }

    @Test
    void handleUnexpected_excecaoAnotadaComResponseStatus_preservaStatusDeclarado() {
        ProblemDetail problema = handler.handleUnexpected(new ExcecaoAnotadaComConflito("Horário já reservado"));

        assertProblema(problema, TipoProblema.CONFLITO, "Horário já reservado");
    }
}
