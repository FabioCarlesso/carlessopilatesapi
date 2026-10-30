package com.carlesso.pilatesapi.exception;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Catálogo de {@code type} das respostas de erro (RFC 9457). O slug é o código estável em que o cliente deve se
 * apoiar para reagir ao erro — o {@code detail} é texto para humanos e pode mudar. As URIs identificam o tipo, não
 * precisam ser resolvíveis; não renomeie um slug sem tratar como quebra de contrato.
 */
public enum TipoProblema {
    VALIDACAO("validation", HttpStatus.BAD_REQUEST, "Dados inválidos"),
    REQUISICAO_INVALIDA("bad-request", HttpStatus.BAD_REQUEST, "Requisição inválida"),
    NAO_AUTENTICADO("unauthorized", HttpStatus.UNAUTHORIZED, "Não autenticado"),
    ACESSO_NEGADO("forbidden", HttpStatus.FORBIDDEN, "Acesso negado"),
    NAO_ENCONTRADO("not-found", HttpStatus.NOT_FOUND, "Recurso não encontrado"),
    METODO_NAO_PERMITIDO("method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido"),
    CONFLITO("conflict", HttpStatus.CONFLICT, "Conflito"),
    CONFLITO_CONCORRENCIA("concurrent-modification", HttpStatus.CONFLICT, "Alteração concorrente"),
    ARQUIVO_EXCEDIDO("payload-too-large", HttpStatus.CONTENT_TOO_LARGE, "Arquivo muito grande"),
    MIDIA_NAO_SUPORTADA("unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de mídia não suportado"),
    REGRA_NEGOCIO("business-rule", HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio violada"),
    LIMITE_REQUISICOES("rate-limit", HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições"),
    ERRO_INTERNO("internal-error", HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor");

    public static final String BASE_URI = "https://carlessopilates.com.br/problemas/";

    private final String slug;
    private final HttpStatus status;
    private final String titulo;

    TipoProblema(String slug, HttpStatus status, String titulo) {
        this.slug = slug;
        this.status = status;
        this.titulo = titulo;
    }

    public URI uri() {
        return URI.create(BASE_URI + slug);
    }

    public String slug() {
        return slug;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    /**
     * Tipo genérico de um status — o primeiro do catálogo com ele, por isso {@link #VALIDACAO} e
     * {@link #CONFLITO_CONCORRENCIA} nunca saem daqui. Status sem entrada no catálogo (ex.: 406) ficam vazios e a
     * resposta usa {@code about:blank}, como manda a RFC.
     */
    public static Optional<TipoProblema> doStatus(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return Optional.of(ERRO_INTERNO);
        }
        return Arrays.stream(values())
                .filter(tipo -> tipo != VALIDACAO && tipo != CONFLITO_CONCORRENCIA)
                .filter(tipo -> tipo.status.value() == status.value())
                .findFirst();
    }
}
