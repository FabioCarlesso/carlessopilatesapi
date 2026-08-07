# Arquitetura

Aplicação Spring Boot em camadas, com pacote raiz `com.carlesso.pilatesapi`.

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring Data JPA | 3.4.5 |
| Spring Validation | 3.4.5 |
| Spring Security | 6.4.5 |
| Spring Boot Actuator | 3.4.5 |
| Micrometer (registry Prometheus) | 1.14.6 |
| PostgreSQL | 16 |
| Flyway | (via spring-boot-starter-parent) |
| springdoc-openapi | 2.8.3 |
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
│   │   ├── config/       # SecurityConfig (JWT stateless, CORS), OpenApiConfig, GlobalExceptionHandler
│   │   ├── controller/   # Endpoints REST — um controller por área de negócio
│   │   ├── service/      # Regras de negócio, cálculo de relatórios e exportação PDF/XLSX
│   │   ├── repository/   # Spring Data JPA
│   │   ├── entity/       # Entidades JPA e embeddables
│   │   │   └── enums/    # TipoPagamento, TipoContrato, FrequenciaSemanal, StatusPagamento,
│   │   │                 # VistaPostural, StatusAvaliacaoPostural, Role
│   │   ├── dto/          # Records de request e response
│   │   ├── exception/    # ResourceNotFoundException (404), ConflictException (409),
│   │   │                 # BusinessException (422), TooManyRequestsException (429)
│   │   ├── security/     # JwtAuthenticationFilter — valida o Bearer token por requisição
│   │   ├── email/        # EmailSender e implementações (SMTP), templates transacionais
│   │   ├── storage/      # Persistência do binário das fotos de análise postural
│   │   ├── metrics/      # BusinessMetrics — contadores de negócio expostos no Prometheus
│   │   ├── scheduler/    # Processos automáticos (cobranças, limpeza de rate limit)
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

## Tratamento de erros

A API utiliza exceções customizadas mapeadas pelo `GlobalExceptionHandler` para retornar o status HTTP
semanticamente correto:

| Exceção | HTTP | Quando é lançada |
|---|---|---|
| `ResourceNotFoundException` | `404 Not Found` | Recurso solicitado não existe (ex.: paciente, plano, pagamento ou aula não encontrada) |
| `ConflictException` | `409 Conflict` | Conflito de estado ou duplicidade (ex.: e-mail/CPF já cadastrado, pagamento já confirmado, aula já realizada) |
| `BusinessException` | `422 Unprocessable Entity` | Regra de negócio violada (ex.: paciente inativo não pode receber cobrança, profissional inativo não pode ser vinculado a aula) |
| `IllegalArgumentException` | `400 Bad Request` | Parâmetros de entrada inválidos (ex.: período inicial maior que o final, valor menor que o do plano) |
| `DataIntegrityViolationException` | `409 Conflict` | Violação de constraint do banco (ex.: registro duplicado ao salvar) |
| `MethodArgumentNotValidException` / `HandlerMethodValidationException` / `ConstraintViolationException` | `400 Bad Request` | Bean Validation falhou (`@NotBlank`, `@Email`, `@Min`…); a resposta detalha os campos inválidos |
| `HttpMessageNotReadableException` | `400 Bad Request` | Corpo da requisição malformado (JSON inválido) |
| `AccessDeniedException` | `403 Forbidden` | Usuário autenticado sem permissão para o recurso. Nas rotas protegidas por URL o corpo é escrito pelo `accessDeniedHandler` do `SecurityConfig`; o handler do advice cobre o mesmo contrato caso method security seja adotada |
| Exceções do Spring MVC | Status original do framework | O handler estende `ResponseEntityExceptionHandler`: método não suportado → `405` (com header `Allow`), mídia inválida → `415`, parâmetro obrigatório ausente e type mismatch (ex.: `GET /pacientes/abc`) → `400`. Apenas o corpo é trocado pelo contrato `{"erro": ...}` |
| Demais exceções | `500 Internal Server Error` | Erro inesperado: logado com stacktrace no servidor e respondido com mensagem neutra, sem vazar detalhes internos (erros 5xx do próprio framework recebem o mesmo tratamento). Exceções anotadas com `@ResponseStatus` preservam o status declarado |

Formato da resposta de erro:

```json
{ "erro": "Mensagem descritiva do problema" }
```

Erros de validação de campos incluem também o detalhe por campo:

```json
{
  "erro": "Dados inválidos",
  "campos": {
    "nome": "não deve estar em branco",
    "email": "deve ser um endereço de e-mail bem formado"
  }
}
```

## Ver também

- [`api.md`](api.md) — contrato REST, paginação e contratos de relatório
- [`regras-de-negocio.md`](regras-de-negocio.md) — regras por domínio
- [`banco-de-dados.md`](banco-de-dados.md) — migrations Flyway
- [`context.md`](context.md) — modelo de dados e histórico de decisões de design
