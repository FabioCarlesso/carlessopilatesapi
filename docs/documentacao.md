# Documentação Técnica — Carlesso Pilates API

## 1. Visão Geral

A **Carlesso Pilates API** é uma API REST desenvolvida para gerenciar o cadastro de pacientes de um estúdio de pilates. Ela expõe endpoints para criação, consulta, atualização e inativação de pacientes, com dados de endereço embutidos e paginação nas listagens.

A aplicação foi construída com **Spring Boot 3** e **Java 21**, utiliza **PostgreSQL** como banco de dados relacional, gerencia o schema com **Flyway**, conta com documentação interativa via **Swagger UI**, processos automáticos via **Spring Scheduler** e possui suíte de testes cobrindo as camadas de serviço e controller.

---

## 2. Tecnologias

| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework da aplicação |
| Spring Data JPA | 3.4.5 | Persistência e ORM |
| Spring Validation | 3.4.5 | Validação de entrada |
| PostgreSQL | 16 | Banco de dados relacional |
| Flyway | (via spring-boot-starter-parent) | Versionamento e migração de schema do banco |
| Spring Scheduler | (via spring-boot-starter) | Processos automáticos agendados |
| springdoc-openapi | 2.8.3 | Documentação Swagger/OpenAPI |
| Maven | 3.9 | Build e gerenciamento de dependências |
| Docker | - | Containerização |
| Docker Compose | - | Orquestração local |
| JUnit 5 + Mockito | (via spring-boot-starter-test) | Testes unitários e de controller |
| H2 | (in-memory, test scope) | Banco em memória para testes de integração |

---

## 3. Arquitetura

A aplicação segue o padrão de camadas do Spring Boot:

```
Requisição HTTP
      │
      ▼
 Controller          ← recebe e valida a requisição, retorna a resposta
      │
      ▼
  Service            ← lógica de negócio e mapeamento de dados
      │
      ▼
 Repository          ← abstração de acesso ao banco via Spring Data JPA
      │
      ▼
 Entity / DB         ← mapeamento objeto-relacional e PostgreSQL
```

### Estrutura de pacotes

```
src/
├── main/
│   ├── java/com/carlesso/pilatesapi/
│   │   ├── config/
│   │   │   ├── GlobalExceptionHandler.java  # Handler 404 para EntityNotFoundException
│   │   │   └── OpenApiConfig.java           # Configuração do Swagger/OpenAPI
│   │   ├── controller/
│   │   │   └── PacienteController.java      # Endpoints REST
│   │   ├── service/
│   │   │   └── PacienteService.java         # Regras de negócio
│   │   ├── repository/
│   │   │   └── PacienteRepository.java      # Acesso ao banco
│   │   ├── entity/
│   │   │   ├── Paciente.java                # Entidade JPA
│   │   │   └── Endereco.java                # Embeddable de endereço
│   │   └── dto/
│   │       ├── PacienteRequestDTO.java      # Payload de criação
│   │       ├── PacienteUpdateDTO.java       # Payload de atualização
│   │       ├── PacienteResponseDTO.java     # Resposta da API
│   │       └── EnderecoDTO.java             # DTO de endereço
│   ├── java/com/carlesso/pilatesapi/
│   │   ├── entity/enums/
│   │   │   ├── TipoPagamento.java
│   │   │   ├── FrequenciaSemanal.java
│   │   │   └── StatusPagamento.java
│   │   ├── entity/
│   │   │   ├── Plano.java
│   │   │   ├── Pagamento.java
│   │   │   └── Aula.java
│   │   ├── service/
│   │   │   ├── PlanoService.java
│   │   │   ├── PagamentoService.java
│   │   │   └── AulaService.java
│   │   ├── controller/
│   │   │   ├── PlanoController.java
│   │   │   ├── PagamentoController.java
│   │   │   └── AulaController.java
│   │   └── scheduler/
│   │       └── CobrancaScheduler.java
│   └── resources/
│       ├── application.properties
│       └── db/migration/
│           ├── V1__create_pacientes_table.sql
│           ├── V2__insert_pacientes_teste.sql
│           ├── V3__create_planos_table.sql
│           ├── V4__create_pagamentos_table.sql
│           ├── V5__create_aulas_table.sql
│           ├── V6__create_profissionais_table.sql
│           └── V7__insert_profissionais_teste.sql
└── test/java/com/carlesso/pilatesapi/
    ├── PilatesApiApplicationTests.java
    ├── service/
    │   ├── PacienteServiceTest.java      # 11 casos
    │   ├── PlanoServiceTest.java         # 8 casos
    │   ├── PagamentoServiceTest.java     # 8 casos
    │   └── AulaServiceTest.java          # 8 casos
    └── controller/
        ├── PacienteControllerTest.java   # 13 casos
        ├── PlanoControllerTest.java      # 11 casos
        ├── PagamentoControllerTest.java  # 9 casos
        └── AulaControllerTest.java       # 7 casos
```

---

## 4. Regras de Negócio

### 4.1 Pacientes
- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes com `ativo = false` não podem receber novas cobranças nem ter aulas geradas

### 4.2 Planos de Pagamento

| Tipo | Duração |
|---|---|
| `MENSAL` | 1 mês |
| `TRIMESTRAL` | 3 meses |
| `ANUAL` | 12 meses |

- A quantidade de dias da semana selecionados deve corresponder à frequência (ex: `DUAS_VEZES` → exatamente 2 dias)
- Ao criar um novo plano para o paciente, o plano ativo anterior é automaticamente inativado
- Alterações de plano valem apenas para os próximos ciclos

### 4.3 Frequência de Aulas

| Enum | Vezes/semana | Aulas/mês (referência) |
|---|---|---|
| `UMA_VEZ` | 1 | 4 |
| `DUAS_VEZES` | 2 | 8 |
| `TRES_VEZES` | 3 | 12 |

### 4.4 Pagamentos

- Status possíveis: `PENDENTE` → `PAGO` ou `VENCIDO`
- O valor pago não pode ser menor que o valor do plano
- Não pode haver dois pagamentos para o mesmo plano no mesmo `periodoInicio`
- Ao confirmar como `PAGO`, as aulas do período são geradas automaticamente
- Pagamentos `VENCIDO` bloqueiam geração de novas aulas (via scheduler)

### 4.5 Geração de Aulas

- Aulas são geradas a partir dos dias da semana definidos no plano, percorrendo dia a dia entre `periodoInicio` e `periodoFim`
- Não gera aulas duplicadas: se o paciente já tiver aula naquela data, ela é ignorada
- Requer: paciente ativo + pagamento com status `PAGO`

### 4.6 Processos Automáticos (Scheduler)

| Cron | Ação |
|---|---|
| `0 0 6 * * *` (06:00) | Marca como `VENCIDO` todos os pagamentos `PENDENTE` com `dataVencimento` anterior à data atual |
| `0 0 7 * * *` (07:00) | Gera cobranças futuras para planos ativos quando faltam ≤ 7 dias para o fim do período atual |

---

## 5. Modelo de Dados

### Entidade: Paciente

Tabela: `pacientes`

| Campo | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Identificador único |
| `nome` | `VARCHAR` | NOT NULL | Nome completo |
| `email` | `VARCHAR` | NOT NULL, UNIQUE | Endereço de e-mail |
| `cpf` | `VARCHAR` | NOT NULL, UNIQUE | CPF do paciente |
| `telefone` | `VARCHAR` | — | Telefone de contato |
| `data_nascimento` | `DATE` | — | Data de nascimento |
| `ativo` | `BOOLEAN` | NOT NULL, default `true` | Indica se o paciente está ativo |

### Entidade embutida: Endereco (`@Embeddable`)

Armazenada nas colunas da própria tabela `pacientes`.

| Campo | Tipo | Descrição |
|---|---|---|
| `logradouro` | `VARCHAR` | Rua, avenida, etc. |
| `numero` | `VARCHAR` | Número |
| `bairro` | `VARCHAR` | Bairro |
| `cidade` | `VARCHAR` | Cidade |
| `uf` | `VARCHAR` | Estado (sigla) |
| `cep` | `VARCHAR` | CEP |

---

### Entidade: Plano

Tabela: `planos`

| Campo | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Identificador único |
| `paciente_id` | `BIGINT` | NOT NULL, FK | Paciente vinculado |
| `tipo` | `VARCHAR(20)` | NOT NULL | MENSAL / TRIMESTRAL / ANUAL |
| `valor` | `DECIMAL(10,2)` | NOT NULL | Valor do plano |
| `frequencia_semanal` | `VARCHAR(20)` | NOT NULL | UMA_VEZ / DUAS_VEZES / TRES_VEZES |
| `data_inicio` | `DATE` | NOT NULL | Data de início do plano |
| `ativo` | `BOOLEAN` | NOT NULL, default `true` | Status do plano |

Tabela de join: `plano_dias_semana`

| Campo | Tipo | Descrição |
|---|---|---|
| `plano_id` | `BIGINT` | FK para planos |
| `dia_semana` | `VARCHAR(15)` | Dia da semana (MONDAY, TUESDAY…) |

### Entidade: Pagamento

Tabela: `pagamentos`

| Campo | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `BIGINT` | PK | Identificador único |
| `paciente_id` | `BIGINT` | NOT NULL, FK | Paciente vinculado |
| `plano_id` | `BIGINT` | NOT NULL, FK | Plano vinculado |
| `valor` | `DECIMAL(10,2)` | NOT NULL | Valor pago |
| `status` | `VARCHAR(20)` | NOT NULL | PENDENTE / PAGO / VENCIDO |
| `data_pagamento` | `DATE` | nullable | Data de confirmação do pagamento |
| `data_vencimento` | `DATE` | NOT NULL | Prazo limite para pagamento |
| `periodo_inicio` | `DATE` | NOT NULL | Início do período cobrado |
| `periodo_fim` | `DATE` | NOT NULL | Fim do período cobrado |

Constraint: `UNIQUE (plano_id, periodo_inicio)`

### Entidade: Aula

Tabela: `aulas`

| Campo | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `BIGINT` | PK | Identificador único |
| `paciente_id` | `BIGINT` | NOT NULL, FK | Paciente vinculado |
| `pagamento_id` | `BIGINT` | NOT NULL, FK | Pagamento que gerou a aula |
| `data` | `DATE` | NOT NULL | Data da aula |
| `realizada` | `BOOLEAN` | NOT NULL, default `false` | Presença confirmada |

Constraint: `UNIQUE (paciente_id, data)`

---

### 5.2 Cadastrar Profissional

| | |
|---|---|
| **Método** | `POST` |
| **Endpoint** | `/profissionais` |
| **Descrição** | Cadastra profissional com percentual de pagamento por aula. |

**Exemplo de payload**

```json
{
  "nome": "Paula Mendes",
  "email": "paula.mendes@carlessopilates.com",
  "cpf": "123.456.111-00",
  "telefone": "(11) 98888-1111",
  "tipoContrato": "PJ",
  "percentualPagamentoAula": 45.00,
  "dataInicio": "2024-01-15"
}
```

## 6. Endpoints

**Base URL:** `http://localhost:8080`

### 5.1 Cadastrar Paciente


| | |
|---|---|
| **Método** | `POST` |
| **Rota** | `/pacientes` |
| **Descrição** | Registra um novo paciente. Retorna `201 Created` com o header `Location` apontando para o recurso. |

**Corpo da requisição:**

```json
{
  "nome": "Maria Souza",
  "email": "maria@email.com",
  "cpf": "123.456.789-00",
  "telefone": "(11) 91234-5678",
  "dataNascimento": "1990-05-20",
  "endereco": {
    "logradouro": "Rua das Flores",
    "numero": "42",
    "bairro": "Centro",
    "cidade": "São Paulo",
    "uf": "SP",
    "cep": "01001-000"
  }
}
```

> Campos obrigatórios: `nome`, `email`, `cpf`

**Resposta — 201 Created:**

```json
{
  "id": 1,
  "nome": "Maria Souza",
  "email": "maria@email.com",
  "cpf": "123.456.789-00",
  "telefone": "(11) 91234-5678",
  "dataNascimento": "1990-05-20",
  "endereco": {
    "logradouro": "Rua das Flores",
    "numero": "42",
    "bairro": "Centro",
    "cidade": "São Paulo",
    "uf": "SP",
    "cep": "01001-000"
  },
  "ativo": true
}
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `201` | Paciente cadastrado com sucesso |
| `400` | Dados inválidos ou campos obrigatórios ausentes |
| `409` | E-mail ou CPF já cadastrado |

---

### 5.2 Listar Pacientes Ativos

| | |
|---|---|
| **Método** | `GET` |
| **Rota** | `/pacientes` |
| **Descrição** | Retorna página de pacientes ativos. Ordenação padrão por `nome`. |

**Query params de paginação:**

| Parâmetro | Padrão | Descrição |
|---|---|---|
| `page` | `0` | Número da página (base 0) |
| `size` | `10` | Quantidade de itens por página |
| `sort` | `nome,asc` | Campo e direção de ordenação |

**Exemplo:**

```
GET /pacientes?page=0&size=5&sort=nome,asc
```

**Resposta — 200 OK:**

```json
{
  "content": [ { ... } ],
  "pageable": { "pageNumber": 0, "pageSize": 10 },
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `200` | Lista retornada com sucesso |

---

### 5.3 Buscar Paciente por ID

| | |
|---|---|
| **Método** | `GET` |
| **Rota** | `/pacientes/{id}` |
| **Descrição** | Retorna os dados completos de um paciente pelo ID. |

**Exemplo:**

```
GET /pacientes/1
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `200` | Paciente encontrado |
| `404` | Paciente não encontrado |

---

### 5.4 Atualizar Paciente

| | |
|---|---|
| **Método** | `PUT` |
| **Rota** | `/pacientes/{id}` |
| **Descrição** | Atualização parcial: apenas os campos presentes no body serão alterados. CPF não pode ser alterado. |

**Corpo da requisição (todos os campos opcionais):**

```json
{
  "nome": "Maria Souza Silva",
  "email": "maria.silva@email.com",
  "telefone": "(11) 99999-0000",
  "dataNascimento": "1990-05-20",
  "endereco": {
    "logradouro": "Av. Paulista",
    "numero": "1000",
    "bairro": "Bela Vista",
    "cidade": "São Paulo",
    "uf": "SP",
    "cep": "01310-100"
  }
}
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `200` | Paciente atualizado com sucesso |
| `400` | Dados inválidos |
| `404` | Paciente não encontrado |

---

### 5.5 Ativar Paciente

| | |
|---|---|
| **Método** | `PATCH` |
| **Rota** | `/pacientes/{id}/ativar` |
| **Descrição** | Reativa um paciente previamente inativado, definindo `ativo = true`. |

**Exemplo:**

```
PATCH /pacientes/1/ativar
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `204` | Paciente ativado com sucesso |
| `404` | Paciente não encontrado |

---

### 5.6 Inativar Paciente

| | |
|---|---|
| **Método** | `PATCH` |
| **Rota** | `/pacientes/{id}/inativar` |
| **Descrição** | Realiza **soft delete**: marca o paciente como inativo (`ativo = false`). O registro permanece no banco. |

**Exemplo:**

```
PATCH /pacientes/1/inativar
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `204` | Paciente inativado com sucesso |
| `404` | Paciente não encontrado |

---

## 7. Documentação Interativa

Com a aplicação rodando, acesse o Swagger UI para explorar e testar os endpoints diretamente pelo navegador:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

---

## 8. Configuração

### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | `localhost` | Host do banco PostgreSQL |
| `DB_PORT` | `5432` | Porta do banco |
| `DB_NAME` | `carlesso_pilates` | Nome do banco de dados |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |

### application.properties

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:carlesso_pilates}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/api-docs
```

> O DDL mode foi alterado de `update` para `validate` — o Flyway é o responsável por criar e evoluir o schema; o Hibernate apenas valida que as entidades estão de acordo com o banco.

---

## 8.1 Migrações de Banco (Flyway)

O **Flyway** executa automaticamente os scripts SQL ao iniciar a aplicação, seguindo a ordem das versões. Os arquivos ficam em `src/main/resources/db/migration/`.

### Convenção de nomes

```
V{versão}__{descrição}.sql
```

### Migrações existentes

| Versão | Arquivo | Descrição |
|---|---|---|
| V1 | `V1__create_pacientes_table.sql` | Cria a tabela `pacientes` com todos os campos, PKs, constraints de unicidade e valor padrão para `ativo` |
| V2 | `V2__insert_pacientes_teste.sql` | Insere 10 pacientes de teste representando estados diferentes do Brasil |

### Pacientes de carga inicial (V2)

| # | Nome | Estado |
|---|---|---|
| 1 | Ana Clara Ferreira | SP |
| 2 | Bruno Santos Lima | RJ |
| 3 | Carla Oliveira Mendes | MG |
| 4 | Diego Alves Costa | PR |
| 5 | Eduarda Rocha Pinheiro | RS |
| 6 | Felipe Nascimento Brito | DF |
| 7 | Gabriela Torres Souza | BA |
| 8 | Henrique Lima Cardoso | CE |
| 9 | Isabela Martins Gomes | PA |
| 10 | João Pedro Araújo | PE |

### Comportamento nos testes

Nos testes automatizados o Flyway fica **desabilitado** (`spring.flyway.enabled=false` em `src/test/resources/application.properties`). O banco H2 em memória é gerenciado pelo Hibernate com `ddl-auto=create-drop`, garantindo isolamento e idempotência dos testes.

---

## 9. Como Rodar

### Opção A — Docker Compose (recomendado)

Não requer instalação de Java ou PostgreSQL localmente.

```bash
# Subir banco e aplicação
docker compose up --build -d

# Acompanhar logs
docker compose logs -f app

# Derrubar os serviços
docker compose down

# Derrubar e apagar dados do banco
docker compose down -v
```

> Caso receba "permission denied" ao usar o Docker, execute com `sudo` ou adicione seu usuário ao grupo docker:
> ```bash
> sudo groupadd docker
> sudo usermod -aG docker $USER
> newgrp docker
> ```

### Opção B — Localmente com Maven

**Pré-requisitos:** Java 21 e PostgreSQL 16 instalados.

```bash
# 1. Criar o banco de dados
psql -U postgres -c "CREATE DATABASE carlesso_pilates;"

# 2. Exportar variáveis de ambiente
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=carlesso_pilates
export DB_USER=postgres
export DB_PASSWORD=postgres

# 3. Rodar
JAVA_HOME=/caminho/para/jdk21 mvn spring-boot:run
```

### Opção C — Apenas o banco no Docker, app local

```bash
# Subir somente o PostgreSQL
docker compose up db -d

# Rodar a aplicação localmente
JAVA_HOME=/caminho/para/jdk21 mvn spring-boot:run
```

---

## 10. Exemplos com curl

### Cadastrar paciente

```bash
curl -s -X POST http://localhost:8080/pacientes \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Maria Souza",
    "email": "maria@email.com",
    "cpf": "123.456.789-00",
    "telefone": "(11) 91234-5678",
    "dataNascimento": "1990-05-20",
    "endereco": {
      "logradouro": "Rua das Flores",
      "numero": "42",
      "bairro": "Centro",
      "cidade": "São Paulo",
      "uf": "SP",
      "cep": "01001-000"
    }
  }' | jq
```

### Listar pacientes

```bash
curl -s "http://localhost:8080/pacientes?page=0&size=5" | jq
```

### Buscar por ID

```bash
curl -s http://localhost:8080/pacientes/1 | jq
```

### Atualizar telefone

```bash
curl -s -X PUT http://localhost:8080/pacientes/1 \
  -H "Content-Type: application/json" \
  -d '{"telefone": "(11) 99999-0000"}' | jq
```

### Ativar paciente

```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/ativar -o /dev/null -w "%{http_code}"
# Retorna: 204
```

### Inativar paciente

```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/inativar -o /dev/null -w "%{http_code}"
# Retorna: 204
```

---

## 11. Infraestrutura Docker

### Dockerfile (multi-stage build)

| Estágio | Imagem base | Função |
|---|---|---|
| `build` | `maven:3.9-eclipse-temurin-21` | Compila o projeto e gera o JAR |
| `runtime` | `eclipse-temurin:21-jre-alpine` | Executa o JAR em imagem enxuta |

### docker-compose.yml — serviços

| Serviço | Imagem | Porta | Descrição |
|---|---|---|---|
| `db` | `postgres:16-alpine` | `5432` | Banco de dados PostgreSQL |
| `app` | build local | `8080` | Aplicação Spring Boot |

O serviço `app` aguarda o `db` estar saudável (healthcheck via `pg_isready`) antes de iniciar.

---

## 12. Testes

### Visão geral

A suíte de testes possui **76 casos** distribuídos em nove classes:

| Classe | Tipo | Casos |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito) | 11 |
| `PlanoServiceTest` | Unitário (Mockito) | 8 |
| `PagamentoServiceTest` | Unitário (Mockito) | 8 |
| `AulaServiceTest` | Unitário (Mockito) | 8 |
| `PacienteControllerTest` | Controller (`@WebMvcTest`) | 13 |
| `PlanoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `PagamentoControllerTest` | Controller (`@WebMvcTest`) | 9 |
| `AulaControllerTest` | Controller (`@WebMvcTest`) | 7 |
| `PilatesApiApplicationTests` | Integração (`@SpringBootTest` + H2) | 1 |

### Estratégia por camada

**Serviço (`PacienteServiceTest`)**

Usa `@ExtendWith(MockitoExtension.class)` — nenhum contexto Spring é carregado. O `PacienteRepository` é mockado com `@Mock`. Cobre:

- `cadastrar` — payload completo (com endereço) e sem endereço
- `listar` — retorno paginado com e sem resultados
- `buscarPorId` — paciente encontrado e `EntityNotFoundException` para ID inexistente
- `atualizar` — atualização de campos individuais, atualização de endereço, ID inexistente
- `inativar` — inativação correta do flag `ativo`, ID inexistente

**Controller (`PacienteControllerTest`)**

Usa `@WebMvcTest(PacienteController.class)` — carrega apenas a camada web. O `PacienteService` é mockado com `@MockitoBean`. Cobre:

| Endpoint | Cenários testados |
|---|---|
| `POST /pacientes` | 201 com header Location, 400 sem nome, 400 sem CPF, 400 e-mail inválido, 400 body vazio |
| `GET /pacientes` | 200 com página de resultados, 200 com página vazia |
| `GET /pacientes/{id}` | 200 com dados, 404 com mensagem de erro |
| `PUT /pacientes/{id}` | 200 com dados atualizados, 404 |
| `PATCH /pacientes/{id}/ativar` | 204 sem corpo, 404 |
| `PATCH /pacientes/{id}/inativar` | 204 sem corpo, 404 |

**Integração (`PilatesApiApplicationTests`)**

Usa `@SpringBootTest` com banco H2 em memória (configurado em `src/test/resources/application.properties`). Valida que o contexto da aplicação sobe corretamente.

### Executar os testes

```bash
JAVA_HOME=/caminho/para/jdk21 mvn test
```

Saída esperada:
```
Tests run: 11 — PacienteServiceTest
Tests run: 13 — PacienteControllerTest
Tests run:  1 — PilatesApiApplicationTests
Tests run: 76, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Tratamento de erros (`GlobalExceptionHandler`)

Um `@RestControllerAdvice` global captura `EntityNotFoundException` e retorna `404` com body:

```json
{ "erro": "Paciente não encontrado: 99" }
```
