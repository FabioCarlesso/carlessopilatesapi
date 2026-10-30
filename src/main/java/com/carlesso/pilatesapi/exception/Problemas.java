package com.carlesso.pilatesapi.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fábrica dos {@link ProblemDetail} da API: garante que toda resposta de erro — do advice ou dos handlers do filter
 * chain de segurança — saia com o mesmo schema ({@code type}, {@code title}, {@code status}, {@code detail},
 * {@code timestamp} e, em validação, {@code campos}).
 */
public final class Problemas {

    public static final String PROPRIEDADE_TIMESTAMP = "timestamp";
    public static final String PROPRIEDADE_CAMPOS = "campos";

    /**
     * Tipo dos status fora do catálogo. Explícito porque, desde o Spring Framework 7, o {@link ProblemDetail} nasce
     * sem {@code type} (a RFC 9457 trata a ausência como {@code about:blank}) e o JSON deixaria de trazer o campo.
     */
    static final URI TIPO_EM_BRANCO = URI.create("about:blank");

    /** Um campo inválido numa resposta de validação. */
    public record CampoInvalido(String campo, String mensagem) {}

    private Problemas() {}

    public static ProblemDetail de(TipoProblema tipo, String detalhe) {
        return de(tipo, tipo.status(), detalhe);
    }

    /** Variante para quando o status difere do padrão do tipo (ex.: 503 classificado como erro interno). */
    public static ProblemDetail de(TipoProblema tipo, HttpStatusCode status, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(tipo.uri());
        problema.setTitle(tipo.titulo());
        problema.setProperty(PROPRIEDADE_TIMESTAMP, Instant.now());
        return problema;
    }

    /** Usa o tipo do catálogo para o status; sem entrada, fica {@code about:blank} com o título padrão do HTTP. */
    public static ProblemDetail doStatus(HttpStatusCode status, String detalhe) {
        return TipoProblema.doStatus(status)
                .map(tipo -> de(tipo, status, detalhe))
                .orElseGet(() -> {
                    ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
                    problema.setType(TIPO_EM_BRANCO);
                    problema.setProperty(PROPRIEDADE_TIMESTAMP, Instant.now());
                    return problema;
                });
    }

    public static ProblemDetail validacao(List<CampoInvalido> campos) {
        ProblemDetail problema = de(TipoProblema.VALIDACAO, TipoProblema.VALIDACAO.titulo());
        problema.setProperty(PROPRIEDADE_CAMPOS, campos);
        return problema;
    }
}
