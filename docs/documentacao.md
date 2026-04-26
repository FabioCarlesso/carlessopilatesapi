# Documentação Técnica — Carlesso Pilates API

## 1. Visão Geral

A **Carlesso Pilates API** é uma API REST desenvolvida para gerenciar o cadastro de pacientes e profissionais de um estúdio de pilates. Ela expõe endpoints para criação, consulta, atualização e inativação de pacientes e profissionais, com gestão de planos de pagamento, cobranças e geração automática de aulas.

A aplicação foi construída com **Spring Boot 3** e **Java 21**, utiliza **PostgreSQL** como banco de dados relacional, gerencia o schema com **Flyway**, conta com documentação interativa via **Swagger UI**, observabilidade com **Spring Boot Actuator**, processos automáticos via **Spring Scheduler** e possui suíte de testes cobrindo as camadas de serviço e controller.

---

## 2. Tecnologias

| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework da aplicação |
| Spring Data JPA | 3.4.5 | Persistência e ORM |
| Spring Validation | 3.4.5 | Validação de entrada |
| Spring Boot Actuator | 3.4.5 | Endpoints operacionais de health e info |
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
│   │   │   ├── PacienteController.java      # Endpoints REST de pacientes
│   │   │   ├── ProfissionalController.java  # Endpoints REST de profissionais
│   │   │   ├── PlanoController.java         # /planos
│   │   │   ├── PagamentoController.java     # /pagamentos
│   │   │   └── AulaController.java          # /aulas
│   │   ├── service/
│   │   │   ├── PacienteService.java         # Regras de negócio de pacientes
│   │   │   ├── ProfissionalService.java     # Regras de negócio de profissionais
│   │   │   ├── PlanoService.java            # Regras de plano e frequência
│   │   │   ├── PagamentoService.java        # Cobranças, confirmação, vencimentos
│   │   │   └── AulaService.java             # Geração e controle de aulas
│   │   ├── repository/
│   │   │   ├── PacienteRepository.java
│   │   │   ├── ProfissionalRepository.java
│   │   │   ├── PlanoRepository.java
│   │   │   ├── PagamentoRepository.java
│   │   │   └── AulaRepository.java
│   │   ├── entity/
│   │   │   ├── Paciente.java                # Entidade JPA
│   │   │   ├── Endereco.java                # @Embeddable de endereço
│   │   │   ├── Profissional.java            # Entidade JPA
│   │   │   ├── Plano.java
│   │   │   ├── Pagamento.java
│   │   │   └── Aula.java
│   │   ├── entity/enums/
│   │   │   ├── TipoPagamento.java           # MENSAL, TRIMESTRAL, ANUAL
│   │   │   ├── TipoContrato.java            # CLT, PJ, AUTONOMO
│   │   │   ├── FrequenciaSemanal.java       # UMA_VEZ, DUAS_VEZES, TRES_VEZES
│   │   │   └── StatusPagamento.java         # PENDENTE, PAGO, VENCIDO
│   │   ├── dto/
│   │   │   ├── PacienteRequestDTO.java
│   │   │   ├── PacienteUpdateDTO.java
│   │   │   ├── PacienteResponseDTO.java
│   │   │   ├── EnderecoDTO.java
│   │   │   ├── ProfissionalRequestDTO.java
│   │   │   ├── ProfissionalUpdateDTO.java
│   │   │   ├── ProfissionalResponseDTO.java
│   │   │   ├── ProfissionalPagamentoRelatorioDTO.java
│   │   │   ├── ProfissionalPagamentoAulaDTO.java
│   │   │   ├── PlanoRequestDTO.java
│   │   │   ├── PlanoResponseDTO.java
│   │   │   ├── PagamentoRequestDTO.java
│   │   │   ├── PagamentoResponseDTO.java
│   │   │   └── AulaResponseDTO.java
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
│           ├── V7__insert_profissionais_teste.sql
│           ├── V8__alter_pacientes_uf_to_varchar.sql
│           ├── V9__alter_profissionais_percentual_precision.sql
│           └── V10__add_profissional_to_aulas.sql
└── test/java/com/carlesso/pilatesapi/
    ├── PilatesApiApplicationTests.java
    ├── actuator/
    │   └── ActuatorTest.java                    # 3 casos
    ├── service/
    │   ├── PacienteServiceTest.java             # 12 casos
    │   ├── PacienteServiceIntegrationTest.java  # 4 casos
    │   ├── ProfissionalServiceIntegrationTest.java # 5 casos
    │   ├── ProfissionalServiceTest.java         # 13 casos
    │   ├── PlanoServiceTest.java                # 8 casos
    │   ├── PagamentoServiceTest.java            # 8 casos
    │   └── AulaServiceTest.java                 # 10 casos
    ├── repository/
    │   └── AulaRepositoryTest.java              # 5 casos
    └── controller/
        ├── PacienteControllerTest.java          # 16 casos
        ├── ProfissionalControllerTest.java      # 13 casos
        ├── PlanoControllerTest.java             # 11 casos
        ├── PagamentoControllerTest.java         # 9 casos
        └── AulaControllerTest.java              # 9 casos
```

---

## 4. Regras de Negócio

### 4.1 Pacientes
- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes com `ativo = false` não podem receber novas cobranças nem ter aulas geradas
- Consultas de aulas não retornam registros associados a pacientes inativos

### 4.2 Profissionais
- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- O campo `percentualPagamentoAula` representa o percentual recebido por aula ministrada
- Soft delete: profissionais inativos são mantidos no banco
- O relatório de pagamento considera aulas realizadas vinculadas ao profissional dentro do período solicitado e associadas a pacientes ativos
- O valor devido por aula é proporcional ao valor do pagamento (`valor / quantidade de aulas geradas`) multiplicado pelo `percentualPagamentoAula`

### 4.3 Planos de Pagamento

| Tipo | Duração |
|---|---|
| `MENSAL` | 1 mês |
| `TRIMESTRAL` | 3 meses |
| `ANUAL` | 12 meses |

- A quantidade de dias da semana selecionados deve corresponder à frequência (ex: `DUAS_VEZES` → exatamente 2 dias)
- Ao criar um novo plano para o paciente, o plano ativo anterior é automaticamente inativado
- Alterações de plano valem apenas para os próximos ciclos

### 4.4 Frequência de Aulas

| Enum | Vezes/semana | Aulas/mês (referência) |
|---|---|---|
| `UMA_VEZ` | 1 | 4 |
| `DUAS_VEZES` | 2 | 8 |
| `TRES_VEZES` | 3 | 12 |

### 4.5 Pagamentos

- Status possíveis: `PENDENTE` → `PAGO` ou `VENCIDO`
- O valor pago não pode ser menor que o valor do plano
- Não pode haver dois pagamentos para o mesmo plano no mesmo `periodoInicio`
- Ao confirmar como `PAGO`, as aulas do período são geradas automaticamente
- Pagamentos `VENCIDO` bloqueiam geração de novas aulas (via scheduler)

### 4.6 Geração de Aulas

- Aulas são geradas a partir dos dias da semana definidos no plano, percorrendo dia a dia entre `periodoInicio` e `periodoFim`
- Não gera aulas duplicadas: se o paciente já tiver aula naquela data, ela é ignorada
- Requer: paciente ativo + pagamento com status `PAGO`
- Consultas por ID, paciente, pagamento e relatório filtram `paciente.ativo = true`

### 4.7 Processos Automáticos (Scheduler)

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
| `uf` | `VARCHAR(2)` | Estado (sigla) |
| `cep` | `VARCHAR` | CEP |

---

### Entidade: Profissional

Tabela: `profissionais`

| Campo | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `BIGINT` | PK, auto-increment | Identificador único |
| `nome` | `VARCHAR(255)` | NOT NULL | Nome completo |
| `email` | `VARCHAR(255)` | NOT NULL, UNIQUE | Endereço de e-mail |
| `cpf` | `VARCHAR(14)` | NOT NULL, UNIQUE | CPF do profissional |
| `telefone` | `VARCHAR` | — | Telefone de contato |
| `tipo_contrato` | `VARCHAR(30)` | NOT NULL | `CLT` / `PJ` / `AUTONOMO` |
| `percentual_pagamento_aula` | `NUMERIC(5,2)` | NOT NULL | Percentual por aula ministrada |
| `data_inicio` | `DATE` | NOT NULL | Data de início do contrato |
| `ativo` | `BOOLEAN` | NOT NULL, default `true` | Indica se o profissional está ativo |

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
| `profissional_id` | `BIGINT` | nullable, FK | Profissional que ministrou a aula realizada |
| `data` | `DATE` | NOT NULL | Data da aula |
| `realizada` | `BOOLEAN` | NOT NULL, default `false` | Presença confirmada |

Constraint: `UNIQUE (paciente_id, data)`

---

## 6. Endpoints

**Base URL:** `http://localhost:8080`

### 6.1 Pacientes

#### Cadastrar Paciente

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

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `201` | Paciente cadastrado com sucesso |
| `400` | Dados inválidos ou campos obrigatórios ausentes |
| `409` | E-mail ou CPF já cadastrado |

---

#### Listar e Filtrar Pacientes

| | |
|---|---|
| **Método** | `GET` |
| **Rota** | `/pacientes` |
| **Descrição** | Retorna página de pacientes com filtros opcionais por `nome`, `email`, `cpf`, `telefone` e `ativo`. Quando `ativo` é omitido, retorna apenas pacientes ativos. Suporta `page`, `size` e `sort`. |

**Exemplos:**

```http
GET /pacientes?nome=maria&ativo=true&page=0&size=5&sort=nome,asc
GET /pacientes?cpf=123&telefone=119
GET /pacientes?ativo=false
```

| Código | Situação |
|---|---|
| `200` | Lista retornada com sucesso |

---

#### Buscar Paciente por ID

| | |
|---|---|
| **Método** | `GET` |
| **Rota** | `/pacientes/{id}` |

| Código | Situação |
|---|---|
| `200` | Paciente encontrado |
| `404` | Paciente não encontrado |

---

#### Atualizar Paciente

| | |
|---|---|
| **Método** | `PUT` |
| **Rota** | `/pacientes/{id}` |
| **Descrição** | Atualização parcial: apenas os campos presentes no body serão alterados. CPF não pode ser alterado. |

| Código | Situação |
|---|---|
| `200` | Paciente atualizado com sucesso |
| `400` | Dados inválidos |
| `404` | Paciente não encontrado |

---

#### Ativar / Inativar Paciente

| Método | Rota | Descrição | Código |
|---|---|---|---|
| `PATCH` | `/pacientes/{id}/ativar` | Reativa o paciente (`ativo = true`) | 204 / 404 |
| `PATCH` | `/pacientes/{id}/inativar` | Soft delete (`ativo = false`) | 204 / 404 |

---

### 6.2 Profissionais

#### Cadastrar Profissional

| | |
|---|---|
| **Método** | `POST` |
| **Rota** | `/profissionais` |
| **Descrição** | Registra um novo profissional. Retorna `201 Created` com o header `Location`. |

**Corpo da requisição:**

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

> Campos obrigatórios: `nome`, `email`, `cpf`, `tipoContrato`, `percentualPagamentoAula`, `dataInicio`

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `201` | Profissional cadastrado com sucesso |
| `400` | Dados inválidos ou campos obrigatórios ausentes |
| `409` | E-mail ou CPF já cadastrado |

---

#### Listar e Filtrar Profissionais

| | |
|---|---|
| **Método** | `GET` |
| **Rota** | `/profissionais` |
| **Descrição** | Retorna uma página de profissionais filtrando por nome, e-mail, tipo de contrato, percentual por aula e status ativo/inativo. Quando `ativo` é omitido, retorna apenas ativos. Suporta `page`, `size` e `sort`. |

**Exemplos:**

```http
GET /profissionais?page=0&size=10&sort=nome
GET /profissionais?nome=paula&email=email.com&tipoContrato=PJ&percentualPagamentoAula=45.00&ativo=true&page=0&size=10&sort=nome
GET /profissionais?ativo=false
```

| Código | Situação |
|---|---|
| `200` | Lista retornada com sucesso |

---

#### Buscar Profissional por ID

| | |
|---|---|
| **Método** | `GET` |
| **Rota** | `/profissionais/{id}` |

| Código | Situação |
|---|---|
| `200` | Profissional encontrado |
| `404` | Profissional não encontrado |

---

#### Atualizar Profissional

| | |
|---|---|
| **Método** | `PUT` |
| **Rota** | `/profissionais/{id}` |
| **Descrição** | Atualiza os dados de um profissional. Apenas os campos enviados serão alterados. |

| Código | Situação |
|---|---|
| `200` | Profissional atualizado com sucesso |
| `400` | Dados inválidos |
| `404` | Profissional não encontrado |

---

#### Ativar / Inativar Profissional

| Método | Rota | Descrição | Código |
|---|---|---|---|
| `PATCH` | `/profissionais/{id}/ativar` | Reativa o profissional (`ativo = true`) | 204 / 404 |
| `PATCH` | `/profissionais/{id}/inativar` | Soft delete (`ativo = false`) | 204 / 404 |

#### Relatório de Pagamento do Profissional

```http
GET /profissionais/{id}/relatorio-pagamento?inicio=2025-02-01&fim=2025-02-28
```

Retorna aulas realizadas no período e o total devido ao profissional.

```json
{
  "profissionalId": 1,
  "profissionalNome": "Paula Mendes",
  "periodoInicio": "2025-02-01",
  "periodoFim": "2025-02-28",
  "totalAulas": 2,
  "totalPagamento": 22.50,
  "aulas": [
    {
      "aulaId": 10,
      "data": "2025-02-03",
      "pacienteId": 1,
      "pacienteNome": "Ana",
      "pagamentoId": 5,
      "valorPagamento": 200.00,
      "quantidadeAulasPagamento": 8,
      "valorBaseAula": 25.00,
      "percentualPagamentoAula": 45.00,
      "valorProfissional": 11.25
    }
  ]
}
```

---

### 6.3 Planos, Pagamentos e Aulas

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/planos` | Criar plano para paciente |
| `GET` | `/planos/{id}` | Buscar plano por ID |
| `GET` | `/planos/paciente/{id}` | Listar planos do paciente |
| `GET` | `/planos/paciente/{id}/ativo` | Buscar plano ativo do paciente |
| `DELETE` | `/planos/{id}` | Inativar plano |
| `POST` | `/pagamentos` | Criar pagamento (PENDENTE) |
| `GET` | `/pagamentos/{id}` | Buscar pagamento por ID |
| `GET` | `/pagamentos/paciente/{id}` | Listar pagamentos do paciente |
| `PATCH` | `/pagamentos/{id}/pagar` | Confirmar pagamento e gerar aulas |
| `GET` | `/aulas/{id}` | Buscar aula por ID |
| `GET` | `/aulas/paciente/{id}` | Listar aulas do paciente |
| `GET` | `/aulas/pagamento/{id}` | Listar aulas de um pagamento |
| `PATCH` | `/aulas/{id}/realizar` | Marcar aula como realizada; aceita `profissionalId` opcional |

---

## 7. Documentação Interativa

Com a aplicação rodando, acesse o Swagger UI para explorar e testar os endpoints diretamente pelo navegador:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

---

## 8. Observabilidade (Actuator)

O projeto utiliza **Spring Boot Actuator** para expor endpoints operacionais. Em desenvolvimento, apenas `health` e `info` ficam disponíveis via HTTP.

| Endpoint | Descrição |
|---|---|
| `GET /actuator/health` | Status da aplicação |
| `GET /actuator/info` | Metadados configurados da aplicação |

---

## 9. Configuração

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

management.endpoints.web.exposure.include=health,info
management.info.env.enabled=true
info.app.name=${spring.application.name}
info.app.description=Carlesso Pilates API
```

> O DDL mode é `validate` — o Flyway é o responsável por criar e evoluir o schema; o Hibernate apenas valida que as entidades estão de acordo com o banco.

---

## 9.1 Migrações de Banco (Flyway)

O **Flyway** executa automaticamente os scripts SQL ao iniciar a aplicação, seguindo a ordem das versões. Os arquivos ficam em `src/main/resources/db/migration/`.

### Migrações existentes

| Versão | Arquivo | Descrição |
|---|---|---|
| V1 | `V1__create_pacientes_table.sql` | Cria a tabela `pacientes` com todos os campos, PKs e constraints de unicidade |
| V2 | `V2__insert_pacientes_teste.sql` | Insere 10 pacientes de teste representando estados diferentes do Brasil |
| V3 | `V3__create_planos_table.sql` | Cria as tabelas `planos` e `plano_dias_semana` |
| V4 | `V4__create_pagamentos_table.sql` | Cria a tabela `pagamentos` com constraint `UNIQUE (plano_id, periodo_inicio)` |
| V5 | `V5__create_aulas_table.sql` | Cria a tabela `aulas` com constraint `UNIQUE (paciente_id, data)` |
| V6 | `V6__create_profissionais_table.sql` | Cria a tabela `profissionais` com tipo de contrato e percentual por aula |
| V7 | `V7__insert_profissionais_teste.sql` | Ajusta tipo de `percentual_pagamento_aula` e insere profissionais de teste |
| V8 | `V8__alter_pacientes_uf_to_varchar.sql` | Altera coluna `uf` da tabela `pacientes` para `VARCHAR(2)` |

### Comportamento nos testes

Nos testes automatizados o Flyway fica **desabilitado** (`spring.flyway.enabled=false` em `src/test/resources/application.properties`). O banco H2 em memória é gerenciado pelo Hibernate com `ddl-auto=create-drop`, garantindo isolamento e idempotência dos testes.

---

## 10. Como Rodar

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

## 11. Exemplos com curl

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
curl -s "http://localhost:8080/pacientes?nome=maria&ativo=true&page=0&size=5" | jq
```

### Cadastrar profissional

```bash
curl -s -X POST http://localhost:8080/profissionais \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Paula Mendes",
    "email": "paula@carlessopilates.com",
    "cpf": "123.456.111-00",
    "tipoContrato": "PJ",
    "percentualPagamentoAula": 45.00,
    "dataInicio": "2024-01-15"
  }' | jq
```

### Ativar / Inativar paciente

```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/ativar -o /dev/null -w "%{http_code}"
curl -s -X PATCH http://localhost:8080/pacientes/1/inativar -o /dev/null -w "%{http_code}"
```

---

## 12. Infraestrutura Docker

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

## 13. Testes

### Visão geral

A suíte de testes possui **122 casos** distribuídos em treze classes:

| Classe | Tipo | Casos |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito) | 12 |
| `ProfissionalServiceTest` | Unitário (Mockito) | 13 |
| `PlanoServiceTest` | Unitário (Mockito) | 8 |
| `PagamentoServiceTest` | Unitário (Mockito) | 8 |
| `AulaServiceTest` | Unitário (Mockito) | 10 |
| `PacienteServiceIntegrationTest` | JPA (`@DataJpaTest`) | 4 |
| `PacienteControllerTest` | Controller (`@WebMvcTest`) | 16 |
| `ProfissionalControllerTest` | Controller (`@WebMvcTest`) | 13 |
| `PlanoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `PagamentoControllerTest` | Controller (`@WebMvcTest`) | 9 |
| `AulaControllerTest` | Controller (`@WebMvcTest`) | 9 |
| `ActuatorTest` | Integração (`@SpringBootTest`) | 3 |
| `PilatesApiApplicationTests` | Integração (`@SpringBootTest` + H2) | 1 |

### Executar os testes

```bash
JAVA_HOME=/caminho/para/jdk21 mvn test
```

### Tratamento de erros (`GlobalExceptionHandler`)

Um `@RestControllerAdvice` global captura `EntityNotFoundException` e retorna `404` com body:

```json
{ "erro": "Paciente não encontrado: 99" }
```
