# Arquitetura

Aplicação Spring Boot em camadas, com pacote raiz `com.carlesso.pilatesapi`.

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring Data JPA | 3.5.13 |
| Spring Validation | 3.5.16 |
| Spring Security | 6.5.11 |
| Spring Boot Actuator | 3.5.16 |
| Micrometer (registry Prometheus) | 1.15.12 |
| PostgreSQL | 16 |
| Flyway | (via spring-boot-starter-parent) |
| springdoc-openapi | 2.10.0 |
| JJWT | 0.13.0 |
| Spring Scheduler | (via spring-boot-starter) |
| Maven | 3.9 |
| Docker / Docker Compose | - |
| OpenPDF | 1.3.34 |
| Apache POI | 5.5.1 |
| JUnit 5 + Mockito | (via spring-boot-starter-test) |
| H2 (testes) | (in-memory) |
| Testcontainers (testes) | (via spring-boot-starter-parent) |
| JaCoCo (cobertura) | 0.8.15 |

## Estrutura de pacotes

```
src/
├── main/
│   ├── java/com/carlesso/pilatesapi/
│   │   ├── config/       # SecurityConfig (JWT stateless, CORS), OpenApiConfig, GlobalExceptionHandler,
│   │   │                 # ProblemaErrorController (/error)
│   │   ├── controller/   # Endpoints REST — um controller por área de negócio
│   │   ├── service/      # Regras de negócio, cálculo de relatórios e exportação PDF/XLSX
│   │   ├── repository/   # Spring Data JPA
│   │   ├── entity/       # Entidades JPA e embeddables
│   │   │   └── enums/    # TipoPagamento, TipoContrato, FrequenciaSemanal, StatusPagamento,
│   │   │                 # VistaPostural, StatusAvaliacaoPostural, Role
│   │   ├── dto/          # Records de request e response
│   │   ├── exception/    # ResourceNotFoundException (404), ConflictException (409),
│   │   │                 # BusinessException (422), TooManyRequestsException (429),
│   │   │                 # UnauthorizedException (401); TipoProblema e Problemas (RFC 9457)
│   │   ├── security/     # JwtAuthenticationFilter — valida o Bearer token por requisição;
│   │   │                 # MetricsScrapeTokenFilter — credencial fixa do scrape do Prometheus
│   │   │                 # ClientIpResolver — IP do cliente para o rate limit (X-Forwarded-For
│   │   │                 # só de proxy confiável); ProblemaHttpWriter — 401/403 do filter chain
│   │   ├── email/        # EmailSender e implementações (SMTP), templates transacionais
│   │   ├── storage/      # Persistência do binário das fotos de análise postural
│   │   ├── metrics/      # BusinessMetrics — contadores de negócio expostos no Prometheus
│   │   ├── scheduler/    # Processos automáticos (cobranças, limpeza de rate limit e de refresh tokens)
│   │   ├── util/         # LogMasker, PacienteGuard e utilitários compartilhados
│   │   └── web/          # CorrelationIdFilter
│   └── resources/
│       ├── application.properties          # + application-dev / application-prod
│       ├── logback-spring.xml
│       └── db/
│           ├── migration/    # DDL estrutural — todos os ambientes
│           └── seed/         # Dados de teste — apenas perfil dev
└── test/java/com/carlesso/pilatesapi/     # Espelha os pacotes de main
```

O detalhe de cada classe não é mantido aqui: use o Swagger UI para o contrato REST, o
[CodeGraph](desenvolvimento.md#codegraph) para navegar o código e a própria IDE para a árvore de arquivos.

## Camadas

Controllers expõem o contrato REST e delegam para services; services concentram as regras de negócio e
transações; repositories fazem o acesso a dados. DTOs são records, com factory method
`*ResponseDTO.from(Entity)` centralizando o mapeamento entidade → DTO.

Métodos de leitura em services usam `@Transactional(readOnly = true)` para reduzir flush desnecessário e
preparar a aplicação para roteamento futuro de leituras.

## Auditoria

Entidades de negócio estendem `EntidadeAuditavel` (pacote `entity`), um `@MappedSuperclass` com `dataCriacao`,
`dataAtualizacao`, `criadoPor` e `alteradoPor` preenchidos pelo `AuditingEntityListener` do Spring Data. O autor vem
do `UsuarioAuditorAware` (pacote `security`), que lê o e-mail do `SecurityContext` ou devolve `system`; o
`@EnableJpaAuditing` fica em `JpaAuditingConfig`, fora da classe principal, para não ser carregado pelos testes
`@WebMvcTest`. Os timestamps vêm do `DateTimeProvider` `horarioDoEstudio`, no fuso `app.fuso-horario`.

Dois cuidados ao escrever código novo:

- O listener roda no flush. Um update que monta a resposta a partir da entidade precisa usar `saveAndFlush`, ou a
  resposta sai com `dataAtualizacao`/`alteradoPor` antigos.
- UPDATEs em massa (`@Modifying`) não passam pelo listener nem pelo `@Version`: precisam gravar data e autor —
  obtidos do mesmo `DateTimeProvider`/`AuditorAware` — e incrementar a versão explicitamente, e reler a entidade
  para montar a resposta, como `SessaoPilatesRepository.transicionarStatusSeAgendada`.

## Tratamento de erros

Toda resposta de erro segue a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) e sai como
`application/problem+json`. O `GlobalExceptionHandler` monta o corpo pelo `Problemas` (pacote `exception`), e os
401/403 da autorização por URL — que nascem no filter chain, antes do DispatcherServlet — são escritos no mesmo
formato pelo `ProblemaHttpWriter` usado no `SecurityConfig`. O que escapa dos dois — URL recusada pelo
`StrictHttpFirewall`, exceção lançada dentro de um filtro, `sendError` do container — cai no `/error`, servido pelo
`ProblemaErrorController` no lugar do `BasicErrorController` do Spring Boot: mesmo schema, `instance` com o caminho
original e `detail` sempre neutro (o título do tipo), já que a mensagem da exceção ali é detalhe interno.

```json
{
  "type": "https://carlessopilates.com.br/problemas/validation",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Dados inválidos",
  "instance": "/pacientes",
  "timestamp": "2026-10-13T14:02:11.482Z",
  "campos": [
    { "campo": "nome", "mensagem": "não deve estar em branco" },
    { "campo": "email", "mensagem": "deve ser um endereço de e-mail bem formado" }
  ]
}
```

- **`type`** é o código estável do erro: o cliente deve reagir a ele, nunca ao texto de `detail`. As URIs só
  identificam o tipo — não precisam abrir no navegador.
- **`detail`** é a mensagem legível da ocorrência (a que antes vinha em `erro`).
- **`campos`** só aparece em `validation`, com um item por campo ou parâmetro inválido.

### Catálogo de `type`

O catálogo vive no enum `TipoProblema`; o prefixo é `https://carlessopilates.com.br/problemas/`. Renomear um slug é
quebra de contrato.

| Slug | HTTP | Quando |
|---|---|---|
| `validation` | `400` | Bean Validation falhou (`MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`) — traz `campos` |
| `bad-request` | `400` | `IllegalArgumentException` (ex.: período inicial maior que o final), JSON malformado, parâmetro obrigatório ausente, type mismatch (ex.: `GET /pacientes/abc`) |
| `unauthorized` | `401` | Sem token ou token inválido; credenciais erradas no login (mensagem genérica); refresh token inválido, expirado, revogado ou reutilizado (`UnauthorizedException`) |
| `forbidden` | `403` | Usuário autenticado sem permissão. Nas rotas protegidas por URL o corpo vem do `accessDeniedHandler`; o handler do advice cobre `AccessDeniedException` caso method security seja adotada |
| `not-found` | `404` | `ResourceNotFoundException`/`EntityNotFoundException` e rota inexistente |
| `method-not-allowed` | `405` | Método não suportado pela rota (com header `Allow`) |
| `conflict` | `409` | `ConflictException` (e-mail/CPF já cadastrado, aula já realizada…) e `DataIntegrityViolationException` |
| `concurrent-modification` | `409` | Locking otimista perdido: recarregar o recurso e refazer a ação |
| `payload-too-large` | `413` | Upload acima do limite de 2 MB |
| `unsupported-media-type` | `415` | `Content-Type` não aceito pela rota |
| `business-rule` | `422` | `BusinessException`: regra de negócio violada (ex.: paciente inativo não pode receber cobrança) |
| `rate-limit` | `429` | `TooManyRequestsException`: tentativas de login ou de redefinição de senha esgotadas |
| `internal-error` | `5xx` | Erro inesperado: logado com stacktrace e respondido com mensagem neutra, sem vazar detalhes. Erros 5xx do framework preservam o status original (ex.: `503`) |

Exceções do Spring MVC são resolvidas pela superclasse `ResponseEntityExceptionHandler`, que mantém status e headers
do framework; só o corpo é refeito pelo catálogo. Status sem entrada nele (ex.: `406`) usam `type` `about:blank`,
como manda a RFC, mas mantêm `timestamp`. Exceções anotadas com `@ResponseStatus` preservam o status declarado.

O Swagger publica o schema `Problema` (e `CampoInvalido`) e o aplica a toda resposta 4xx/5xx; o 500 genérico é
acrescentado a todas as operações pelo `OpenApiConfig`. Cada resposta traz um exemplo do próprio código (o 400 mostra
`validation` com `campos`), para o Swagger UI não montar um exemplo genérico a partir do schema único.

## Ver também

- [`api.md`](api.md) — contrato REST, paginação e contratos de relatório
- [`regras-de-negocio.md`](regras-de-negocio.md) — regras por domínio
- [`banco-de-dados.md`](banco-de-dados.md) — migrations Flyway
- [`context.md`](context.md) — modelo de dados e histórico de decisões de design
