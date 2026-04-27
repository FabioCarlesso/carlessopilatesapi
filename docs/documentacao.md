# Documentação Técnica — Carlesso Pilates API

## 1. Visão Geral

A **Carlesso Pilates API** é uma API REST desenvolvida para gerenciar o cadastro de pacientes e profissionais de um estúdio de pilates. Ela expõe endpoints para criação, consulta, atualização e inativação de pacientes e profissionais, com gestão de planos de pagamento, cobranças e geração automática de aulas.

A aplicação foi construída com **Spring Boot 3** e **Java 21**, utiliza **PostgreSQL** como banco de dados relacional, gerencia o schema com **Flyway**, conta com autenticação stateless via **Spring Security + JWT**, documentação interativa via **Swagger UI**, observabilidade com **Spring Boot Actuator**, processos automáticos via **Spring Scheduler** e possui suíte de testes cobrindo as camadas de serviço e controller.

---

## 2. Tecnologias

| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework da aplicação |
| Spring Data JPA | 3.4.5 | Persistência e ORM |
| Spring Validation | 3.4.5 | Validação de entrada |
| Spring Security | 6.4.5 | Autenticação e autorização stateless |
| Spring Boot Actuator | 3.4.5 | Endpoints operacionais de health e info |
| PostgreSQL | 16 | Banco de dados relacional |
| Flyway | (via spring-boot-starter-parent) | Versionamento e migração de schema do banco |
| Spring Scheduler | (via spring-boot-starter) | Processos automáticos agendados |
| springdoc-openapi | 2.8.3 | Documentação Swagger/OpenAPI |
| Maven | 3.9 | Build e gerenciamento de dependências |
| Docker | - | Containerização |
| Docker Compose | - | Orquestração local |
| OpenPDF | 1.3.34 | Geração de relatórios em PDF |
| Apache POI | 5.4.1 | Geração de planilhas XLSX |
| JJWT | 0.12.6 | Geração e validação de tokens JWT |
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
│   │   │   ├── AulaController.java          # /aulas
│   │   │   ├── AuthController.java          # /auth/register e /auth/login
│   │   │   ├── UserController.java          # /users/me e CRUD administrativo
│   │   │   └── AdminController.java         # /admin/health
│   │   ├── service/
│   │   │   ├── PacienteService.java                    # Regras de negócio de pacientes
│   │   │   ├── ProfissionalService.java                # Regras de negócio de profissionais
│   │   │   ├── PlanoService.java                       # Regras de plano e frequência
│   │   │   ├── PagamentoService.java                   # Cobranças, confirmação, vencimentos
│   │   │   ├── AulaService.java                        # Geração e controle de aulas
│   │   │   ├── RelatorioPagamentoExporterService.java  # Exportação do relatório em PDF e XLSX
│   │   │   ├── AuthService.java                        # Registro/login e emissão de JWT
│   │   │   ├── UserService.java                        # CRUD administrativo de usuários e perfis
│   │   │   └── JwtService.java                         # Geração e validação de JWT
│   │   ├── repository/
│   │   │   ├── PacienteRepository.java
│   │   │   ├── ProfissionalRepository.java
│   │   │   ├── PlanoRepository.java
│   │   │   ├── PagamentoRepository.java
│   │   │   ├── AulaRepository.java
│   │   │   └── UserRepository.java
│   │   ├── entity/
│   │   │   ├── Paciente.java                # Entidade JPA
│   │   │   ├── Endereco.java                # @Embeddable de endereço
│   │   │   ├── Profissional.java            # Entidade JPA
│   │   │   ├── Plano.java
│   │   │   ├── Pagamento.java
│   │   │   ├── Aula.java
│   │   │   └── User.java
│   │   ├── entity/enums/
│   │   │   ├── TipoPagamento.java           # MENSAL, TRIMESTRAL, ANUAL
│   │   │   ├── TipoContrato.java            # CLT, PJ, AUTONOMO
│   │   │   ├── FrequenciaSemanal.java       # UMA_VEZ, DUAS_VEZES, TRES_VEZES
│   │   │   ├── StatusPagamento.java         # PENDENTE, PAGO, VENCIDO
│   │   │   └── Role.java                    # USER, ADMIN
│   │   ├── dto/
│   │   │   ├── PacienteRequestDTO.java
│   │   │   ├── PacienteUpdateDTO.java
│   │   │   ├── PacienteResponseDTO.java
│   │   │   ├── EnderecoDTO.java
│   │   │   ├── ProfissionalRequestDTO.java
│   │   │   ├── ProfissionalUpdateDTO.java
│   │   │   ├── ProfissionalResponseDTO.java
│   │   │   ├── ProfissionalResumoDTO.java
│   │   │   ├── PeriodoDTO.java
│   │   │   ├── ResumoFinanceiroDTO.java
│   │   │   ├── PagamentoResumoDTO.java
│   │   │   ├── ProfissionalPagamentoRelatorioDTO.java
│   │   │   ├── ProfissionalPagamentoAulaDTO.java
│   │   │   ├── PlanoRequestDTO.java
│   │   │   ├── PlanoResponseDTO.java
│   │   │   ├── PagamentoRequestDTO.java
│   │   │   ├── PagamentoPagarRequestDTO.java
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
│           ├── V10__add_profissional_to_aulas.sql
│           ├── V11__create_users_table.sql
│           └── V12__insert_users_perfis_acesso.sql
└── test/java/com/carlesso/pilatesapi/
    ├── PilatesApiApplicationTests.java
    ├── actuator/
    │   └── ActuatorTest.java                    # 3 casos
    ├── service/
    │   ├── PacienteServiceTest.java                   # 12 casos
    │   ├── PacienteServiceIntegrationTest.java        # 4 casos
    │   ├── ProfissionalServiceIntegrationTest.java    # 5 casos
    │   ├── ProfissionalServiceTest.java               # 15 casos
    │   ├── PlanoServiceTest.java                      # 9 casos
    │   ├── PagamentoServiceTest.java                  # 8 casos
    │   ├── AulaServiceTest.java                       # 14 casos
    │   └── RelatorioPagamentoExporterServiceTest.java # 3 casos
    ├── repository/
    │   └── AulaRepositoryTest.java              # 6 casos
    └── controller/
        ├── PacienteControllerTest.java          # 16 casos
        ├── ProfissionalControllerTest.java      # 17 casos
        ├── PlanoControllerTest.java             # 11 casos
        ├── PagamentoControllerTest.java         # 11 casos
        └── AulaControllerTest.java              # 10 casos
```

---

## 4. Regras de Negócio

### 4.0 Segurança
- `POST /auth/register` e `POST /auth/login` são públicos
- `GET /users/me` exige usuário autenticado
- `POST /users`, `GET /users`, `GET /users/{id}`, `PUT /users/{id}` e `DELETE /users/{id}` exigem role `ADMIN`
- `/admin/**` exige role `ADMIN`
- As demais rotas de negócio exigem `Authorization: Bearer <accessToken>`
- Senhas são persistidas com BCrypt e nunca retornadas nos DTOs
- Usuários administrativos podem definir os perfis de acesso disponíveis: `USER` e `ADMIN`
- O segredo JWT é configurado por `JWT_SECRET`; token ausente, inválido ou expirado retorna `401`
- CORS permite integração com Angular pela variável `CORS_ALLOWED_ORIGINS`

### 4.1 Pacientes
- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes com `ativo = false` não podem receber novas cobranças nem ter aulas geradas
- Consultas de aulas não retornam registros associados a pacientes inativos

### 4.2 Profissionais
- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- O campo `percentualPagamentoAula` representa o percentual recebido por aula ministrada
- Soft delete: profissionais inativos são mantidos no banco
- O relatório de pagamento considera aulas realizadas vinculadas ao profissional dentro do período solicitado e associadas a pacientes ativos
- A consulta do relatório de pagamento consolida dados de aula, paciente, pagamento e contagem de aulas por pagamento em uma única query com `JOIN` e `GROUP BY`
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
- Métodos de leitura nos services usam `@Transactional(readOnly = true)` para reduzir flush desnecessário e permitir otimizações de conexão.

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

### 6.0 Autenticação e Usuários

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/auth/register` | Público | Cria usuário com role `USER`, senha BCrypt e retorna JWT |
| `POST` | `/auth/login` | Público | Autentica e-mail/senha e retorna JWT |
| `GET` | `/users/me` | Autenticado | Retorna `id`, `name`, `email` e `role` do usuário logado |
| `POST` | `/users` | `ADMIN` | Cria usuário com role `USER` ou `ADMIN` |
| `GET` | `/users` | `ADMIN` | Lista usuários cadastrados sem expor senha |
| `GET` | `/users/{id}` | `ADMIN` | Busca usuário por ID |
| `PUT` | `/users/{id}` | `ADMIN` | Atualiza nome, e-mail, senha e perfil de acesso |
| `DELETE` | `/users/{id}` | `ADMIN` | Remove usuário |
| `GET` | `/admin/health` | `ADMIN` | Endpoint administrativo inicial |

Fluxo esperado: o frontend faz login ou registro, recebe `accessToken` e envia `Authorization: Bearer <token>` nas chamadas protegidas.

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

Retorna aulas realizadas no período e o total devido ao profissional. O contrato é estruturado em sub-objetos para facilitar o consumo no Angular sem mapeamentos adicionais.

```json
{
  "profissional": {
    "id": 1,
    "nome": "Paula Mendes",
    "cpf": "12345678900",
    "tipoContrato": "PJ",
    "percentualPagamentoAula": 45.00
  },
  "periodo": {
    "inicio": "2025-02-01",
    "fim": "2025-02-28"
  },
  "resumo": {
    "totalAulas": 2,
    "quantidadePagamentos": 1,
    "totalPagamentosBruto": 200.00,
    "totalProfissional": 22.50
  },
  "pagamentos": [
    {
      "pagamentoId": 5,
      "valorPagamento": 200.00,
      "quantidadeAulasPagamento": 8,
      "quantidadeAulasNoPeriodo": 2,
      "valorBaseAula": 25.00,
      "totalProfissional": 22.50
    }
  ],
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
  ],
  "geradoEm": "2025-03-01T10:00:00"
}
```

#### Exportação do Relatório (PDF / XLSX)

Os mesmos parâmetros (`inicio`, `fim`) podem ser usados para baixar o relatório como arquivo. Ambos os endpoints retornam `Content-Disposition: attachment` com nome `relatorio-pagamento-profissional-{id}-{inicio}-{fim}.{ext}`.
O relatório é limitado a períodos de até 366 dias e até 5.000 aulas para evitar exportações excessivas em memória.

```http
GET /profissionais/{id}/relatorio-pagamento/pdf?inicio=2025-02-01&fim=2025-02-28
GET /profissionais/{id}/relatorio-pagamento/xlsx?inicio=2025-02-01&fim=2025-02-28
```

| Endpoint | `Content-Type` | Tecnologia |
|---|---|---|
| `/relatorio-pagamento/pdf` | `application/pdf` | OpenPDF |
| `/relatorio-pagamento/xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Apache POI (abas `Resumo`, `Pagamentos`, `Aulas`) |

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
| `PATCH` | `/pagamentos/{id}/pagar` | Confirmar pagamento e gerar aulas; aceita `dataPagamento` opcional no corpo |
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
| `JWT_SECRET` | - | Segredo HMAC obrigatório para assinar tokens JWT; use pelo menos 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` | Expiração do access token em milissegundos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens permitidas para o frontend Angular |

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
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}
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
| V9 | `V9__alter_profissionais_percentual_precision.sql` | Ajusta precisão do percentual de pagamento por aula |
| V10 | `V10__add_profissional_to_aulas.sql` | Vincula profissional às aulas realizadas |
| V11 | `V11__create_users_table.sql` | Cria a tabela `users` para autenticação e autorização |
| V12 | `V12__insert_users_perfis_acesso.sql` | Insere 5 usuários iniciais com perfis `ADMIN` e `USER` |

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

### Exportar relatório de pagamento (PDF / XLSX)

```bash
curl -s -OJ "http://localhost:8080/profissionais/1/relatorio-pagamento/pdf?inicio=2025-02-01&fim=2025-02-28"
curl -s -OJ "http://localhost:8080/profissionais/1/relatorio-pagamento/xlsx?inicio=2025-02-01&fim=2025-02-28"
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

A suíte de testes possui **151 casos** distribuídos em dezessete classes:

| Classe | Tipo | Casos |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito) | 12 |
| `ProfissionalServiceTest` | Unitário (Mockito) | 15 |
| `PlanoServiceTest` | Unitário (Mockito) | 9 |
| `PagamentoServiceTest` | Unitário (Mockito) | 8 |
| `AulaServiceTest` | Unitário (Mockito) | 14 |
| `RelatorioPagamentoExporterServiceTest` | Unitário | 3 |
| `PacienteServiceIntegrationTest` | JPA (`@DataJpaTest`) | 4 |
| `ProfissionalServiceIntegrationTest` | JPA (`@DataJpaTest`) | 5 |
| `AulaRepositoryTest` | JPA (`@DataJpaTest`) | 6 |
| `PacienteControllerTest` | Controller (`@WebMvcTest`) | 16 |
| `ProfissionalControllerTest` | Controller (`@WebMvcTest`) | 17 |
| `PlanoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `PagamentoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `AulaControllerTest` | Controller (`@WebMvcTest`) | 10 |
| `GlobalExceptionHandlerTest` | Unitário | 6 |
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
