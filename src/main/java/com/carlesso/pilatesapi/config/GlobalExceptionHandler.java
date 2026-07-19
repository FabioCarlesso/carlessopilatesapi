package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Estende {@link ResponseEntityExceptionHandler} para que todas as exceções do
 * próprio Spring MVC (método não suportado, mídia inválida, parâmetro ausente,
 * type mismatch etc.) continuem resolvidas pelo framework com o status e os
 * headers corretos (ex.: {@code Allow} no 405) — apenas o corpo é trocado pelo
 * contrato {@code {"erro": ...}} da API em {@link #handleExceptionInternal}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String ERRO_VALIDACAO = "Dados inválidos";
    static final String ERRO_CORPO_ILEGIVEL = "Corpo da requisição inválido ou malformado";
    static final String ERRO_INTERNO = "Erro interno do servidor";
    static final String ERRO_ROTA = "Rota não encontrada";
    static final String ERRO_UPLOAD_EXCEDIDO = "Arquivo excede o tamanho máximo permitido de 2 MB";
    static final String MENSAGEM_CAMPO_PADRAO = "valor inválido";

    @ExceptionHandler({ResourceNotFoundException.class, EntityNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(RuntimeException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(ConflictException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleBusiness(BusinessException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleAuthentication(AuthenticationException e) {
        return Map.of("erro", "Credenciais inválidas");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, String> handleTooManyRequests(TooManyRequestsException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDataIntegrity(DataIntegrityViolationException e) {
        return Map.of("erro", "Violação de integridade: registro duplicado ou constraint violada");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getConstraintViolations()
                .forEach(
                        v -> campos.putIfAbsent(String.valueOf(v.getPropertyPath()), mensagemOuPadrao(v.getMessage())));
        return respostaValidacao(campos);
    }

    /**
     * Autorização por URL é resolvida pelo accessDeniedHandler do SecurityConfig
     * antes do DispatcherServlet; este handler cobre o mesmo contrato para
     * AccessDeniedException lançada dentro de controllers/services (ex.: se
     * method security for adotada no futuro).
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAccessDenied(AccessDeniedException e) {
        return Map.of("erro", "Acesso negado");
    }

    /**
     * Rede de segurança para exceções não mapeadas: logadas com stacktrace e
     * respondidas com mensagem neutra, sem vazar detalhes internos. Exceções
     * anotadas com {@code @ResponseStatus} mantêm o status declarado, já que a
     * precedência deste handler impede o ResponseStatusExceptionResolver de
     * resolvê-las.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        ResponseStatus anotado = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (anotado != null && !anotado.code().is5xxServerError()) {
            String motivo = !anotado.reason().isEmpty()
                    ? anotado.reason()
                    : Objects.requireNonNullElse(e.getMessage(), "Requisição inválida");
            return ResponseEntity.status(anotado.code()).body(Map.of("erro", motivo));
        }
        log.error("Erro não tratado ao processar a requisição", e);
        HttpStatusCode status = anotado != null ? anotado.code() : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of("erro", ERRO_INTERNO));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(
                ex, respostaValidacao(camposDe(ex.getBindingResult())), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(resultado -> {
            String parametro = resultado.getMethodParameter().getParameterName();
            String nome = parametro != null
                    ? parametro
                    : "parametro" + resultado.getMethodParameter().getParameterIndex();
            resultado
                    .getResolvableErrors()
                    .forEach(erro -> campos.putIfAbsent(nome, mensagemOuPadrao(erro.getDefaultMessage())));
        });
        return handleExceptionInternal(ex, respostaValidacao(campos), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, Map.of("erro", ERRO_CORPO_ILEGIVEL), headers, status, request);
    }

    /** Multipart acima de spring.servlet.multipart.max-file-size/max-request-size (413). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, Map.of("erro", ERRO_UPLOAD_EXCEDIDO), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, Map.of("erro", ERRO_ROTA), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, Map.of("erro", ERRO_ROTA), headers, status, request);
    }

    /**
     * Ponto único por onde passam todas as exceções tratadas pela superclasse:
     * mantém status e headers calculados pelo framework e apenas define o corpo
     * no contrato da API. Erros 5xx recebem mensagem neutra e são logados com
     * stacktrace; nos 4xx o detail do framework é seguro para o cliente.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Erro ao processar a requisição", ex);
            body = Map.of("erro", ERRO_INTERNO);
        } else {
            if (body == null && ex instanceof ErrorResponse errorResponse) {
                body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
            }
            if (body == null || body instanceof ProblemDetail) {
                String detalhe = body instanceof ProblemDetail problem && problem.getDetail() != null
                        ? problem.getDetail()
                        : "Requisição inválida";
                body = Map.of("erro", detalhe);
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private Map<String, String> camposDe(BindingResult bindingResult) {
        Map<String, String> campos = new LinkedHashMap<>();
        bindingResult
                .getFieldErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getField(), mensagemOuPadrao(erro.getDefaultMessage())));
        bindingResult
                .getGlobalErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getObjectName(), mensagemOuPadrao(erro.getDefaultMessage())));
        return campos;
    }

    private Map<String, Object> respostaValidacao(Map<String, String> campos) {
        return Map.of("erro", ERRO_VALIDACAO, "campos", campos);
    }

    private String mensagemOuPadrao(String mensagem) {
        return Objects.requireNonNullElse(mensagem, MENSAGEM_CAMPO_PADRAO);
    }
}
