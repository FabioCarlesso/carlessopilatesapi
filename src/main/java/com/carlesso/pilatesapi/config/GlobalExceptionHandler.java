package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.Problemas;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.exception.TipoProblema;
import com.carlesso.pilatesapi.exception.TooManyRequestsException;
import com.carlesso.pilatesapi.exception.UnauthorizedException;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
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
 * {@link ProblemDetail} (RFC 9457) montado por {@link Problemas}, em
 * {@link #handleExceptionInternal}. Todo erro sai como
 * {@code application/problem+json}, com {@code type} do {@link TipoProblema}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String ERRO_REQUISICAO = "Requisição inválida";
    static final String ERRO_CREDENCIAIS = "Credenciais inválidas";
    static final String ERRO_ACESSO_NEGADO = "Acesso negado";
    static final String ERRO_INTEGRIDADE = "Violação de integridade: registro duplicado ou constraint violada";
    static final String ERRO_CORPO_ILEGIVEL = "Corpo da requisição inválido ou malformado";
    static final String ERRO_INTERNO = "Erro interno do servidor";
    static final String ERRO_ROTA = "Rota não encontrada";
    static final String ERRO_UPLOAD_EXCEDIDO = "Arquivo excede o tamanho máximo permitido de 2 MB";
    static final String ERRO_CONCORRENCIA =
            "Registro alterado por outra operação simultânea; recarregue e tente novamente";
    static final String MENSAGEM_CAMPO_PADRAO = "valor inválido";

    @ExceptionHandler({ResourceNotFoundException.class, EntityNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return Problemas.de(TipoProblema.NAO_ENCONTRADO, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException e) {
        return Problemas.de(TipoProblema.REQUISICAO_INVALIDA, e.getMessage());
    }

    /**
     * {@code sort} com campo que a entidade não tem. O Spring Data só descobre ao montar a consulta, e sem este
     * handler o erro do cliente virava 500 — como o {@code sort=role} que deixou de existir com a #131.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleOrdenacaoInvalida(PropertyReferenceException e) {
        return Problemas.de(
                TipoProblema.REQUISICAO_INVALIDA, "Ordenação inválida: campo '" + e.getPropertyName() + "' não existe");
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return Problemas.de(TipoProblema.CONFLITO, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        return Problemas.de(TipoProblema.REGRA_NEGOCIO, e.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return Problemas.de(TipoProblema.NAO_AUTENTICADO, ERRO_CREDENCIAIS);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException e) {
        return Problemas.de(TipoProblema.NAO_AUTENTICADO, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ProblemDetail handleTooManyRequests(TooManyRequestsException e) {
        return Problemas.de(TipoProblema.LIMITE_REQUISICOES, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        return Problemas.de(TipoProblema.CONFLITO, ERRO_INTEGRIDADE);
    }

    /**
     * Locking otimista perdido: outra transação gravou a mesma linha entre a leitura e o flush. É conflito de
     * concorrência, não erro do servidor — sem este handler cairia na rede de segurança e viraria 500, escondendo do
     * cliente que basta recarregar e repetir. Tem {@code type} próprio para o cliente reconhecer o caso sem ler o texto.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException e) {
        return Problemas.de(TipoProblema.CONFLITO_CONCORRENCIA, ERRO_CONCORRENCIA);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
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
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return Problemas.de(TipoProblema.ACESSO_NEGADO, ERRO_ACESSO_NEGADO);
    }

    /**
     * Rede de segurança para exceções não mapeadas: logadas com stacktrace e
     * respondidas com mensagem neutra, sem vazar detalhes internos. Exceções
     * anotadas com {@code @ResponseStatus} mantêm o status declarado, já que a
     * precedência deste handler impede o ResponseStatusExceptionResolver de
     * resolvê-las.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        ResponseStatus anotado = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (anotado != null && !anotado.code().is5xxServerError()) {
            String motivo = !anotado.reason().isEmpty()
                    ? anotado.reason()
                    : Objects.requireNonNullElse(e.getMessage(), ERRO_REQUISICAO);
            return Problemas.doStatus(anotado.code(), motivo);
        }
        log.error("Erro não tratado ao processar a requisição", e);
        HttpStatusCode status = anotado != null ? anotado.code() : HttpStatus.INTERNAL_SERVER_ERROR;
        return Problemas.de(TipoProblema.ERRO_INTERNO, status, ERRO_INTERNO);
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
        return handleExceptionInternal(
                ex, Problemas.de(TipoProblema.REQUISICAO_INVALIDA, ERRO_CORPO_ILEGIVEL), headers, status, request);
    }

    /** Multipart acima de spring.servlet.multipart.max-file-size/max-request-size (413). */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(
                ex, Problemas.de(TipoProblema.ARQUIVO_EXCEDIDO, ERRO_UPLOAD_EXCEDIDO), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(
                ex, Problemas.de(TipoProblema.NAO_ENCONTRADO, ERRO_ROTA), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(
                ex, Problemas.de(TipoProblema.NAO_ENCONTRADO, ERRO_ROTA), headers, status, request);
    }

    /**
     * Ponto único por onde passam todas as exceções tratadas pela superclasse:
     * mantém status e headers calculados pelo framework e garante que o corpo
     * siga o schema da API. Erros 5xx recebem mensagem neutra e são logados com
     * stacktrace; nos 4xx o detail do framework é seguro para o cliente, mas o
     * ProblemDetail dele (type {@code about:blank}, sem timestamp) é refeito
     * pelo catálogo. Corpos já montados por {@link Problemas} passam intactos.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("Erro ao processar a requisição", ex);
            body = Problemas.de(TipoProblema.ERRO_INTERNO, statusCode, ERRO_INTERNO);
        } else if (!(body instanceof ProblemDetail problema
                && problema.getProperties() != null
                && problema.getProperties().containsKey(Problemas.PROPRIEDADE_TIMESTAMP))) {
            if (body == null && ex instanceof ErrorResponse errorResponse) {
                body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
            }
            String detalhe = body instanceof ProblemDetail doFramework && doFramework.getDetail() != null
                    ? doFramework.getDetail()
                    : ERRO_REQUISICAO;
            body = Problemas.doStatus(statusCode, detalhe);
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

    private ProblemDetail respostaValidacao(Map<String, String> campos) {
        return Problemas.validacao(campos.entrySet().stream()
                .map(campo -> new Problemas.CampoInvalido(campo.getKey(), campo.getValue()))
                .toList());
    }

    private String mensagemOuPadrao(String mensagem) {
        return Objects.requireNonNullElse(mensagem, MENSAGEM_CAMPO_PADRAO);
    }
}
