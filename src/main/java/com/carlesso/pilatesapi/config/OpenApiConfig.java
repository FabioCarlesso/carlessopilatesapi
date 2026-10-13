package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.Problemas;
import com.carlesso.pilatesapi.exception.TipoProblema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Configuration
public class OpenApiConfig {

    static final String SCHEMA_PROBLEMA = "Problema";
    static final String SCHEMA_CAMPO_INVALIDO = "CampoInvalido";
    private static final String REF_SCHEMAS = "#/components/schemas/";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .info(new Info()
                        .title("Carlesso Pilates API")
                        .description(
                                """
                                API para gestão de pacientes do estúdio Carlesso Pilates.

                                Para testar endpoints protegidos no Swagger UI:
                                1. Execute POST /auth/login com um dos usuários abaixo.
                                2. Copie o campo accessToken retornado.
                                3. Clique em Authorize e cole somente o token, sem o prefixo Bearer.

                                Usuários iniciais para teste no perfil dev:
                                - admin@carlessopilates.com / senha1234 / ADMIN
                                - operacional@carlessopilates.com / senha1234 / ADMIN
                                - recepcao@carlessopilates.com / senha1234 / USER
                                - financeiro@carlessopilates.com / senha1234 / USER
                                - consulta@carlessopilates.com / senha1234 / USER
                                """)
                        .version("v1.0")
                        .contact(new Contact().name("Carlesso Pilates").email("contato@carlessopilates.com.br")));
    }

    /**
     * Toda resposta de erro da API é um {@code application/problem+json} (RFC 9457). Em vez de repetir o schema em cada
     * {@code @ApiResponse}, troca aqui o conteúdo de toda resposta 4xx/5xx declarada pelo schema {@value
     * #SCHEMA_PROBLEMA} e acrescenta o 500 genérico onde ele não foi declarado. Cada resposta leva um exemplo do próprio código.
     */
    @Bean
    public OpenApiCustomizer respostasDeErroComoProblemDetail() {
        return openApi -> {
            // Registrados aqui, e não no bean OpenAPI: o springdoc descarta schemas de components ainda sem
            // referência antes de rodar os customizers.
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents()
                    .addSchemas(SCHEMA_PROBLEMA, schemaProblema())
                    .addSchemas(SCHEMA_CAMPO_INVALIDO, schemaCampoInvalido());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((caminho, path) -> path.readOperations().forEach(operation -> {
                operation.getResponses().forEach((codigo, resposta) -> {
                    if (codigo.startsWith("4") || codigo.startsWith("5")) {
                        resposta.setContent(conteudoProblema(codigo, caminho));
                    }
                });
                operation
                        .getResponses()
                        .putIfAbsent(
                                "500",
                                new ApiResponse()
                                        .description("Erro interno do servidor")
                                        .content(conteudoProblema("500", caminho)));
            }));
        };
    }

    private static Content conteudoProblema(String codigo, String caminho) {
        MediaType conteudo = new MediaType().schema(new Schema<>().$ref(REF_SCHEMAS + SCHEMA_PROBLEMA));
        exemploProblema(codigo, caminho).ifPresent(conteudo::setExample);
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE, conteudo);
    }

    /**
     * Exemplo coerente com o código da resposta. Sem ele o Swagger UI monta um a partir do schema único — o primeiro
     * {@code type} do enum ({@code validation}), um {@code status} qualquer e a lista {@code campos} até num 404. O
     * 400 mostra a validação, o caso mais comum, e é o único com {@code campos}. O {@code instance} é o caminho da
     * própria operação, com as variáveis de path trocadas por {@code 1}.
     */
    static Optional<Map<String, Object>> exemploProblema(String codigo, String caminho) {
        HttpStatusCode status;
        try {
            status = HttpStatusCode.valueOf(Integer.parseInt(codigo));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Optional<TipoProblema> tipo = status.value() == HttpStatus.BAD_REQUEST.value()
                ? Optional.of(TipoProblema.VALIDACAO)
                : TipoProblema.doStatus(status);
        return tipo.map(t -> {
            Map<String, Object> exemplo = new LinkedHashMap<>();
            exemplo.put("type", t.uri().toString());
            exemplo.put("title", t.titulo());
            exemplo.put("status", status.value());
            exemplo.put("detail", t.titulo());
            exemplo.put("instance", caminho.replaceAll("\\{[^}]+}", "1"));
            exemplo.put("timestamp", "2026-01-15T13:45:00Z");
            if (t == TipoProblema.VALIDACAO) {
                exemplo.put("campos", List.of(new Problemas.CampoInvalido("nome", "não deve estar em branco")));
            }
            return exemplo;
        });
    }

    private static Schema<?> schemaProblema() {
        String catalogo = Arrays.stream(TipoProblema.values())
                .map(tipo ->
                        "- `%s` (%d) — %s".formatted(tipo.slug(), tipo.status().value(), tipo.titulo()))
                .collect(Collectors.joining("\n"));
        List<String> tipos = Arrays.stream(TipoProblema.values())
                .map(tipo -> tipo.uri().toString())
                .toList();
        StringSchema type = new StringSchema();
        type.setFormat("uri");
        type.setEnum(tipos);
        type.setDescription("Código estável do erro, prefixado por `" + TipoProblema.BASE_URI
                + "`. Status sem entrada no catálogo usam `about:blank`. Catálogo:\n" + catalogo);
        StringSchema timestamp = new StringSchema();
        timestamp.setFormat("date-time");
        return new ObjectSchema()
                .description("Resposta de erro no formato RFC 9457 (application/problem+json)")
                .addProperty("type", type)
                .addProperty("title", new StringSchema().description("Resumo do tipo de erro"))
                .addProperty("status", new IntegerSchema().description("Status HTTP"))
                .addProperty("detail", new StringSchema().description("Mensagem legível desta ocorrência"))
                .addProperty("instance", new StringSchema().description("Caminho da requisição que falhou"))
                .addProperty("timestamp", timestamp.description("Momento do erro (UTC)"))
                .addProperty(
                        "campos",
                        new ArraySchema()
                                .items(new Schema<>().$ref(REF_SCHEMAS + SCHEMA_CAMPO_INVALIDO))
                                .description("Presente apenas em erros de validação (`validation`)"))
                .required(List.of("type", "title", "status", "detail", "timestamp"));
    }

    private static Schema<?> schemaCampoInvalido() {
        return new ObjectSchema()
                .addProperty("campo", new StringSchema().description("Nome do campo ou parâmetro inválido"))
                .addProperty("mensagem", new StringSchema().description("Motivo da rejeição"))
                .required(List.of("campo", "mensagem"));
    }
}
