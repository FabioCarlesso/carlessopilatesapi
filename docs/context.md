# Contexto do Projeto — Carlesso Pilates API

## Objetivo

API REST para gerenciar pacientes de um estúdio de pilates. Permite cadastro, consulta, atualização parcial e inativação (soft delete) de pacientes, com dados de endereço embutidos e paginação nas listagens.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.5 |
| Persistência | Spring Data JPA + Hibernate |
| Banco de dados | PostgreSQL 16 |
| Validação | Spring Validation (Bean Validation) |
| Documentação | springdoc-openapi 2.8.3 (Swagger UI) |
| Build | Maven 3.9 |
| Containerização | Docker + Docker Compose |
| Testes | JUnit 5 + Mockito + MockMvc + H2 (test scope) |

---

## Estrutura de pacotes

```
com.carlesso.pilatesapi
├── config
│   ├── GlobalExceptionHandler.java   — captura EntityNotFoundException → 404
│   └── OpenApiConfig.java            — configuração do Swagger/OpenAPI
├── controller
│   └── PacienteController.java       — endpoints REST
├── service
│   └── PacienteService.java          — lógica de negócio
├── repository
│   └── PacienteRepository.java       — acesso ao banco via Spring Data JPA
├── entity
│   ├── Paciente.java                 — entidade JPA, tabela `pacientes`
│   └── Endereco.java                 — @Embeddable, colunas embutidas em `pacientes`
└── dto
    ├── PacienteRequestDTO.java       — payload de criação (record)
    ├── PacienteUpdateDTO.java        — payload de atualização parcial (record)
    ├── PacienteResponseDTO.java      — resposta da API (record com factory method `from`)
    └── EnderecoDTO.java              — DTO de endereço (record)
```

---

## Modelo de dados

### Tabela `pacientes`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `nome` | VARCHAR | NOT NULL |
| `email` | VARCHAR | NOT NULL, UNIQUE |
| `cpf` | VARCHAR | NOT NULL, UNIQUE |
| `telefone` | VARCHAR | — |
| `data_nascimento` | DATE | — |
| `ativo` | BOOLEAN | NOT NULL, default `true` |
| `logradouro` | VARCHAR | — |
| `numero` | VARCHAR | — |
| `bairro` | VARCHAR | — |
| `cidade` | VARCHAR | — |
| `uf` | VARCHAR | — |
| `cep` | VARCHAR | — |

O endereço é um `@Embeddable` (`Endereco`), suas colunas ficam diretamente na tabela `pacientes`.

---

## Endpoints

| Método | Rota | Ação | Retorno |
|---|---|---|---|
| POST | `/pacientes` | Cadastrar paciente | 201 + Location header |
| GET | `/pacientes` | Listar ativos (paginado) | 200 + Page |
| GET | `/pacientes/{id}` | Buscar por ID | 200 / 404 |
| PUT | `/pacientes/{id}` | Atualização parcial | 200 / 404 |
| DELETE | `/pacientes/{id}` | Soft delete (inativar) | 204 / 404 |

Campos obrigatórios no cadastro: `nome`, `email`, `cpf`.  
CPF não pode ser alterado após o cadastro.  
A listagem filtra apenas pacientes com `ativo = true`, ordenados por `nome` por padrão.

---

## Decisões de design

- **Soft delete**: o `DELETE` não remove o registro — apenas seta `ativo = false`. Pacientes inativos não aparecem na listagem.
- **Atualização parcial via PUT**: o `PacienteUpdateDTO` tem todos os campos opcionais; o service só sobrescreve o que vier não-nulo.
- **DTOs como records**: `PacienteRequestDTO`, `PacienteUpdateDTO`, `PacienteResponseDTO` e `EnderecoDTO` são Java records.
- **Factory method**: `PacienteResponseDTO.from(Paciente)` centraliza o mapeamento entidade → DTO.
- **Tratamento de 404**: `GlobalExceptionHandler` (`@RestControllerAdvice`) captura `EntityNotFoundException` e retorna `{"erro": "Paciente não encontrado: <id>"}`.
- **DDL automático**: `spring.jpa.hibernate.ddl-auto=update` — o schema é gerenciado pelo Hibernate.

---

## Configuração

### Variáveis de ambiente

| Variável | Padrão |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `carlesso_pilates` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |

### URLs de desenvolvimento

| Recurso | URL |
|---|---|
| API base | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/api-docs` |

---

## Como rodar

### Docker Compose (recomendado)

```bash
docker compose up --build -d
docker compose logs -f app
docker compose down
```

### Local com Maven

```bash
# Requer Java 21 e PostgreSQL rodando
psql -U postgres -c "CREATE DATABASE carlesso_pilates;"
JAVA_HOME=~/jdk mvn spring-boot:run
```

> No ambiente de desenvolvimento, Java 21 (Temurin) está instalado em `~/jdk`.  
> Sempre usar `JAVA_HOME=~/jdk mvn <goal>` para compilar e rodar localmente.

---

## Testes

| Classe | Tipo | Casos |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito, sem Spring) | 11 |
| `PacienteControllerTest` | `@WebMvcTest` + MockMvc | 13 |
| `PilatesApiApplicationTests` | `@SpringBootTest` + H2 | 1 |

```bash
JAVA_HOME=~/jdk mvn test
```

Os testes de integração usam H2 em memória configurado em `src/test/resources/application.properties`.

---

## Infraestrutura Docker

### Dockerfile

Build multi-stage:
- Estágio `build`: `maven:3.9-eclipse-temurin-21` — compila e gera o JAR
- Estágio `runtime`: `eclipse-temurin:21-jre-alpine` — executa o JAR

### docker-compose.yml

| Serviço | Imagem | Porta |
|---|---|---|
| `db` | `postgres:16-alpine` | 5432 |
| `app` | build local | 8080 |

O serviço `app` aguarda o `db` estar saudável via healthcheck (`pg_isready`) antes de iniciar.
