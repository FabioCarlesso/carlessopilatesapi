# Contexto do Projeto — Carlesso Pilates API

## Objetivo

API REST para gerenciar pacientes e profissionais de um estúdio de pilates. Permite cadastro, consulta, atualização parcial e inativação (soft delete) de pacientes e profissionais, com gestão de planos de pagamento, cobranças, geração automática de aulas e relatório de pagamento de profissionais.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.5 |
| Persistência | Spring Data JPA + Hibernate |
| Banco de dados | PostgreSQL 16 |
| Migrações | Flyway |
| Validação | Spring Validation (Bean Validation) |
| Documentação | springdoc-openapi 2.8.3 (Swagger UI) |
| Observabilidade | Spring Boot Actuator |
| Scheduler | Spring Scheduler |
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
│   ├── PacienteController.java       — endpoints REST de pacientes
│   ├── ProfissionalController.java   — endpoints REST de profissionais
│   ├── PlanoController.java          — endpoints REST de planos
│   ├── PagamentoController.java      — endpoints REST de pagamentos
│   └── AulaController.java           — endpoints REST de aulas
├── service
│   ├── PacienteService.java          — lógica de negócio de pacientes
│   ├── ProfissionalService.java      — lógica de negócio de profissionais
│   ├── PlanoService.java             — regras de plano e frequência
│   ├── PagamentoService.java         — cobranças, confirmação, vencimentos
│   └── AulaService.java              — geração e controle de aulas
├── repository
│   ├── PacienteRepository.java       — acesso ao banco via Spring Data JPA
│   ├── ProfissionalRepository.java   — acesso ao banco via Spring Data JPA
│   ├── PlanoRepository.java
│   ├── PagamentoRepository.java
│   └── AulaRepository.java
├── entity
│   ├── Paciente.java                 — entidade JPA, tabela `pacientes`
│   ├── Endereco.java                 — @Embeddable, colunas embutidas em `pacientes`
│   ├── Profissional.java             — entidade JPA, tabela `profissionais`
│   ├── Plano.java                    — entidade JPA, tabela `planos`
│   ├── Pagamento.java                — entidade JPA, tabela `pagamentos`
│   └── Aula.java                     — entidade JPA, tabela `aulas`
├── entity/enums
│   ├── TipoPagamento.java            — MENSAL, TRIMESTRAL, ANUAL
│   ├── TipoContrato.java             — CLT, PJ, AUTONOMO
│   ├── FrequenciaSemanal.java        — UMA_VEZ, DUAS_VEZES, TRES_VEZES
│   └── StatusPagamento.java          — PENDENTE, PAGO, VENCIDO
├── dto
│   ├── PacienteRequestDTO.java       — payload de criação (record)
│   ├── PacienteUpdateDTO.java        — payload de atualização parcial (record)
│   ├── PacienteResponseDTO.java      — resposta da API (record com factory method `from`)
│   ├── EnderecoDTO.java              — DTO de endereço (record)
│   ├── ProfissionalRequestDTO.java   — payload de criação de profissional (record)
│   ├── ProfissionalUpdateDTO.java    — payload de atualização de profissional (record)
│   ├── ProfissionalResponseDTO.java  — resposta da API de profissional (record)
│   ├── ProfissionalPagamentoRelatorioDTO.java — relatório de pagamento do profissional
│   ├── ProfissionalPagamentoAulaDTO.java      — detalhe de aula no relatório
│   ├── PlanoRequestDTO.java
│   ├── PlanoResponseDTO.java
│   ├── PagamentoRequestDTO.java
│   ├── PagamentoPagarRequestDTO.java — payload opcional para confirmar pagamento
│   ├── PagamentoResponseDTO.java
│   └── AulaResponseDTO.java
└── scheduler
    └── CobrancaScheduler.java        — atualiza vencidos e gera cobranças futuras
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
| `uf` | VARCHAR(2) | — |
| `cep` | VARCHAR | — |

O endereço é um `@Embeddable` (`Endereco`), suas colunas ficam diretamente na tabela `pacientes`.

### Tabela `profissionais`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `nome` | VARCHAR(255) | NOT NULL |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE |
| `cpf` | VARCHAR(14) | NOT NULL, UNIQUE |
| `telefone` | VARCHAR | — |
| `tipo_contrato` | VARCHAR(30) | NOT NULL (`CLT`, `PJ`, `AUTONOMO`) |
| `percentual_pagamento_aula` | NUMERIC(5,2) | NOT NULL |
| `data_inicio` | DATE | NOT NULL |
| `ativo` | BOOLEAN | NOT NULL, default `true` |

### Tabela `planos`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → pacientes |
| `tipo` | VARCHAR(20) | NOT NULL |
| `valor` | DECIMAL(10,2) | NOT NULL |
| `frequencia_semanal` | VARCHAR(20) | NOT NULL |
| `data_inicio` | DATE | NOT NULL |
| `ativo` | BOOLEAN | NOT NULL, default `true` |

Join table `plano_dias_semana`: `plano_id` + `dia_semana` (MONDAY, TUESDAY…)

### Tabela `pagamentos`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK |
| `paciente_id` | BIGINT | NOT NULL, FK |
| `plano_id` | BIGINT | NOT NULL, FK |
| `valor` | DECIMAL(10,2) | NOT NULL |
| `status` | VARCHAR(20) | NOT NULL |
| `data_pagamento` | DATE | nullable |
| `data_vencimento` | DATE | NOT NULL |
| `periodo_inicio` | DATE | NOT NULL |
| `periodo_fim` | DATE | NOT NULL |

Constraint: `UNIQUE (plano_id, periodo_inicio)`

### Tabela `aulas`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK |
| `paciente_id` | BIGINT | NOT NULL, FK |
| `pagamento_id` | BIGINT | NOT NULL, FK |
| `profissional_id` | BIGINT | nullable, FK → profissionais |
| `data` | DATE | NOT NULL |
| `realizada` | BOOLEAN | NOT NULL, default `false` |

Constraint: `UNIQUE (paciente_id, data)`

---

## Endpoints

| Método | Rota | Ação | Retorno |
|---|---|---|---|
| POST | `/pacientes` | Cadastrar paciente | 201 + Location header |
| GET | `/pacientes` | Listar e filtrar pacientes por nome, e-mail, CPF, telefone e ativo/inativo (paginado) | 200 + Page |
| GET | `/pacientes/{id}` | Buscar por ID | 200 / 404 |
| PUT | `/pacientes/{id}` | Atualização parcial | 200 / 404 |
| PATCH | `/pacientes/{id}/ativar` | Reativar paciente | 204 / 404 |
| PATCH | `/pacientes/{id}/inativar` | Soft delete (inativar) | 204 / 404 |
| POST | `/profissionais` | Cadastrar profissional | 201 + Location header |
| GET | `/profissionais` | Listar e filtrar por nome, e-mail, contrato, percentual por aula e ativo/inativo (paginado) | 200 + Page |
| GET | `/profissionais/{id}` | Buscar por ID | 200 / 404 |
| PUT | `/profissionais/{id}` | Atualização parcial | 200 / 404 |
| PATCH | `/profissionais/{id}/ativar` | Reativar profissional | 204 / 404 |
| PATCH | `/profissionais/{id}/inativar` | Soft delete (inativar) | 204 / 404 |
| GET | `/profissionais/{id}/relatorio-pagamento?inicio=YYYY-MM-DD&fim=YYYY-MM-DD` | Gerar relatório de pagamento do profissional | 200 / 400 / 404 |
| POST | `/planos` | Criar plano para paciente | 201 |
| GET | `/planos/{id}` | Buscar plano por ID | 200 / 404 |
| GET | `/planos/paciente/{id}` | Listar planos do paciente | 200 |
| GET | `/planos/paciente/{id}/ativo` | Buscar plano ativo | 200 / 404 |
| DELETE | `/planos/{id}` | Inativar plano | 204 |
| POST | `/pagamentos` | Criar pagamento (PENDENTE) | 201 |
| GET | `/pagamentos/{id}` | Buscar pagamento | 200 / 404 |
| GET | `/pagamentos/paciente/{id}` | Listar pagamentos | 200 |
| PATCH | `/pagamentos/{id}/pagar` | Confirmar e gerar aulas; aceita `dataPagamento` opcional no corpo | 200 |
| GET | `/aulas/{id}` | Buscar aula | 200 / 404 |
| GET | `/aulas/paciente/{id}` | Listar aulas do paciente | 200 |
| GET | `/aulas/pagamento/{id}` | Listar aulas do pagamento | 200 |
| PATCH | `/aulas/{id}/realizar?profissionalId={id}` | Marcar como realizada e opcionalmente vincular profissional | 200 / 404 |

Campos obrigatórios no cadastro de pacientes: `nome`, `email`, `cpf`.  
Campos obrigatórios no cadastro de profissionais: `nome`, `email`, `cpf`, `tipoContrato`, `percentualPagamentoAula`, `dataInicio`.  
CPF não pode ser alterado após o cadastro.  
`GET /pacientes` retorna pacientes ativos por padrão e aceita `ativo=false` para consultar inativos.
`GET /profissionais` retorna profissionais ativos por padrão e aceita filtros opcionais por `nome`, `email`, `tipoContrato`, `percentualPagamentoAula` e `ativo=false` para consultar inativos.

---

## Regras de negócio

### Pacientes
- Apenas um plano ativo por paciente por vez
- Pacientes inativos não recebem cobranças nem têm aulas geradas
- Consultas de aulas não retornam registros associados a pacientes inativos

### Profissionais
- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- Soft delete mantém o registro no banco
- O relatório de pagamento considera apenas aulas `realizada = true` vinculadas ao profissional, dentro do período informado e associadas a pacientes ativos
- Valor por aula no relatório: `valor do pagamento / quantidade de aulas do pagamento`
- Valor devido ao profissional por aula: `valor por aula * percentualPagamentoAula / 100`

### Planos
- Tipo determina duração: `MENSAL` (1 mês), `TRIMESTRAL` (3 meses), `ANUAL` (12 meses)
- Dias da semana selecionados devem corresponder à frequência contratada
- Criar novo plano inativa automaticamente o plano anterior

### Pagamentos
- Status: `PENDENTE` → `PAGO` ou `VENCIDO`
- Valor não pode ser menor que o valor do plano
- Sem duplicidade por período (`UNIQUE plano_id + periodo_inicio`)
- Ao confirmar (`PAGO`), as aulas são geradas automaticamente
- A confirmação recebe `dataPagamento` no corpo da requisição; se omitida, usa a data atual

### Aulas
- Geradas percorrendo dia a dia entre `periodoInicio` e `periodoFim`
- Sem duplicatas: ignora datas onde o paciente já tem aula registrada
- Requer: paciente ativo + pagamento `PAGO`
- Consultas por ID, paciente, pagamento e relatório filtram `paciente.ativo = true`
- Uma aula realizada pode ser vinculada ao profissional que ministrou a aula

### Scheduler (processos automáticos)
| Cron | Ação |
|---|---|
| 06:00 todo dia | Marca como `VENCIDO` pagamentos `PENDENTE` com `dataVencimento` passada |
| 07:00 todo dia | Gera cobranças futuras quando faltam ≤ 7 dias para o fim do período atual |

---

## Decisões de design

- **Soft delete**: `DELETE` não remove o registro — apenas seta `ativo = false`.
- **Atualização parcial via PUT**: DTOs de update têm todos os campos opcionais; o service só sobrescreve os campos não-nulos.
- **DTOs como records**: todos os DTOs de request e response são Java records.
- **Factory method**: `*ResponseDTO.from(Entity)` centraliza o mapeamento entidade → DTO.
- **Tratamento de 404**: `GlobalExceptionHandler` captura `EntityNotFoundException` e retorna `{"erro": "..."}`.
- **DDL via Flyway**: `spring.jpa.hibernate.ddl-auto=validate` — o Flyway gerencia o schema; o Hibernate apenas valida.
- **Transações de leitura**: métodos de consulta nos services usam `@Transactional(readOnly = true)` para evitar flush desnecessário e permitir otimizações de conexão.

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
| Actuator health | `http://localhost:8080/actuator/health` |
| Actuator info | `http://localhost:8080/actuator/info` |

### Observabilidade

O projeto usa Spring Boot Actuator para endpoints operacionais. Em desenvolvimento, ficam expostos via HTTP apenas `health` e `info`.

| Endpoint | Uso |
|---|---|
| `GET /actuator/health` | Verificar status da aplicação e componentes monitorados |
| `GET /actuator/info` | Consultar metadados configurados da aplicação |

Configurações relevantes:

```properties
management.endpoints.web.exposure.include=health,info
management.info.env.enabled=true
info.app.name=${spring.application.name}
info.app.description=Carlesso Pilates API
```

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
| `PacienteServiceTest` | Unitário (Mockito, sem Spring) | 12 |
| `ProfissionalServiceTest` | Unitário (Mockito, sem Spring) | 13 |
| `PlanoServiceTest` | Unitário (Mockito, sem Spring) | 9 |
| `PagamentoServiceTest` | Unitário (Mockito, sem Spring) | 8 |
| `AulaServiceTest` | Unitário (Mockito, sem Spring) | 13 |
| `PacienteServiceIntegrationTest` | `@DataJpaTest` + H2 | 4 |
| `ProfissionalServiceIntegrationTest` | `@DataJpaTest` + H2 | 5 |
| `AulaRepositoryTest` | `@DataJpaTest` + H2 | 5 |
| `PacienteControllerTest` | `@WebMvcTest` + MockMvc | 16 |
| `ProfissionalControllerTest` | `@WebMvcTest` + MockMvc | 13 |
| `PlanoControllerTest` | `@WebMvcTest` + MockMvc | 11 |
| `PagamentoControllerTest` | `@WebMvcTest` + MockMvc | 10 |
| `AulaControllerTest` | `@WebMvcTest` + MockMvc | 9 |
| `ActuatorTest` | `@SpringBootTest` + H2 | 3 |
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
