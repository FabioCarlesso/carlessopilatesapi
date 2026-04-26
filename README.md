# Carlesso Pilates API

API REST para gestão de pacientes e profissionais do estúdio Carlesso Pilates, desenvolvida com Spring Boot 3 e Java 21.

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring Data JPA | 3.4.5 |
| Spring Validation | 3.4.5 |
| Spring Boot Actuator | 3.4.5 |
| PostgreSQL | 16 |
| Flyway | (via spring-boot-starter-parent) |
| springdoc-openapi | 2.8.3 |
| Spring Scheduler | (via spring-boot-starter) |
| Maven | 3.9 |
| Docker / Docker Compose | - |
| JUnit 5 + Mockito | (via spring-boot-starter-test) |
| H2 (testes) | (in-memory) |

---

## Estrutura do projeto

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
│   │   │   ├── PacienteRepository.java      # Acesso ao banco
│   │   │   ├── ProfissionalRepository.java  # Acesso ao banco
│   │   │   ├── PlanoRepository.java
│   │   │   ├── PagamentoRepository.java
│   │   │   └── AulaRepository.java
│   │   ├── entity/
│   │   │   ├── Paciente.java                # Entidade JPA
│   │   │   ├── Endereco.java                # Embeddable de endereço
│   │   │   ├── Profissional.java            # Entidade JPA
│   │   │   ├── Plano.java                   # Plano de pagamento do paciente
│   │   │   ├── Pagamento.java               # Cobrança por período
│   │   │   └── Aula.java                    # Aula agendada (com presença)
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
│   │       └── CobrancaScheduler.java       # Atualiza vencidos + gera cobranças futuras
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
    │   └── ActuatorTest.java
    ├── service/
    │   ├── PacienteServiceTest.java
    │   ├── PacienteServiceIntegrationTest.java
    │   ├── ProfissionalServiceTest.java
    │   ├── PlanoServiceTest.java
    │   ├── PagamentoServiceTest.java
    │   └── AulaServiceTest.java
    └── controller/
        ├── PacienteControllerTest.java
        ├── ProfissionalControllerTest.java
        ├── PlanoControllerTest.java
        ├── PagamentoControllerTest.java
        └── AulaControllerTest.java
```

---

## Endpoints

Base URL: `http://localhost:8080`

### Pacientes

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/pacientes` | Cadastrar novo paciente |
| `GET` | `/pacientes` | Listar e filtrar pacientes (paginado) |
| `GET` | `/pacientes/{id}` | Buscar paciente por ID |
| `PUT` | `/pacientes/{id}` | Atualizar dados do paciente |
| `PATCH` | `/pacientes/{id}/ativar` | Reativar paciente |
| `PATCH` | `/pacientes/{id}/inativar` | Inativar paciente (soft delete) |

### Profissionais

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/profissionais` | Cadastrar novo profissional |
| `GET` | `/profissionais` | Listar e filtrar profissionais (paginado) |
| `GET` | `/profissionais/{id}` | Buscar profissional por ID |
| `PUT` | `/profissionais/{id}` | Atualizar dados do profissional |
| `PATCH` | `/profissionais/{id}/ativar` | Reativar profissional |
| `PATCH` | `/profissionais/{id}/inativar` | Inativar profissional (soft delete) |
| `GET` | `/profissionais/{id}/relatorio-pagamento` | Gerar relatório de pagamento por período |

### Planos de Pagamento

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/planos` | Criar plano para paciente |
| `GET` | `/planos/{id}` | Buscar plano por ID |
| `GET` | `/planos/paciente/{id}` | Listar planos do paciente |
| `GET` | `/planos/paciente/{id}/ativo` | Buscar plano ativo do paciente |
| `DELETE` | `/planos/{id}` | Inativar plano |

### Pagamentos

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/pagamentos` | Criar pagamento (PENDENTE) |
| `GET` | `/pagamentos/{id}` | Buscar pagamento por ID |
| `GET` | `/pagamentos/paciente/{id}` | Listar pagamentos do paciente |
| `PATCH` | `/pagamentos/{id}/pagar` | Confirmar pagamento e gerar aulas |

### Aulas

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/aulas/{id}` | Buscar aula por ID |
| `GET` | `/aulas/paciente/{id}` | Listar aulas do paciente |
| `GET` | `/aulas/pagamento/{id}` | Listar aulas de um pagamento |
| `PATCH` | `/aulas/{id}/realizar` | Marcar aula como realizada, opcionalmente com `profissionalId` |

### Paginação

Os endpoints de listagem suportam os query params padrão do Spring:

```
GET /pacientes?page=0&size=10&sort=nome,asc
GET /profissionais?page=0&size=10&sort=nome,asc
```

O endpoint `GET /pacientes` também suporta filtros opcionais por `nome`, `email`, `cpf`, `telefone` e `ativo`. Quando `ativo` é omitido, retorna apenas pacientes ativos.

```
GET /pacientes?nome=maria&email=email.com&cpf=123&telefone=119&ativo=true&page=0&size=10&sort=nome,asc
GET /pacientes?ativo=false
```

O endpoint `GET /profissionais` também suporta filtros opcionais por `nome`, `email`, `tipoContrato`, `percentualPagamentoAula` e `ativo`. Quando `ativo` é omitido, retorna apenas profissionais ativos.

```
GET /profissionais?nome=paula&email=email.com&tipoContrato=PJ&percentualPagamentoAula=45.00&ativo=true&page=0&size=10&sort=nome,asc
GET /profissionais?ativo=false
GET /profissionais/1/relatorio-pagamento?inicio=2025-02-01&fim=2025-02-28
```

---

## Modelos de dados

### POST /pacientes — corpo da requisição

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

### POST /profissionais — corpo da requisição

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

### PUT /pacientes/{id} — corpo da requisição

Todos os campos são opcionais. Apenas os campos enviados serão atualizados.

```json
{
  "nome": "Maria Souza Silva",
  "telefone": "(11) 99999-0000",
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

### Resposta padrão (200/201)

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

---

## Como rodar

### Opção 1 — Docker Compose (recomendado)

Sobe o banco PostgreSQL e a aplicação juntos, sem instalar nada localmente além do Docker.

```bash
# Clonar o repositório
git clone <url-do-repositorio>
cd carlessopilatesapi

# Subir todos os serviços
docker compose up --build -d

# Acompanhar os logs da aplicação
docker compose logs -f app

# Derrubar os serviços
docker compose down

# Derrubar e remover os dados do banco
docker compose down -v
```

> Se o Docker exigir permissão negada, adicione seu usuário ao grupo docker:
> ```bash
> sudo groupadd docker
> sudo usermod -aG docker $USER
> newgrp docker
> ```
> Ou prefixe os comandos com `sudo`.

### Opção 2 — Rodar localmente (Maven)

Pré-requisitos: Java 21 e PostgreSQL rodando localmente.

**1. Criar o banco de dados:**

```sql
CREATE DATABASE carlesso_pilates;
```

**2. Configurar as variáveis de ambiente** (ou editar `application.properties`):

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=carlesso_pilates
export DB_USER=postgres
export DB_PASSWORD=postgres
```

**3. Compilar e rodar:**

```bash
JAVA_HOME=/caminho/para/jdk21 mvn spring-boot:run
```

---

## Documentação interativa (Swagger UI)

Com a aplicação rodando, acesse:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

---

## Observabilidade (Actuator)

O projeto expõe endpoints operacionais do Spring Boot Actuator para acompanhamento da aplicação em desenvolvimento:

| Recurso | URL |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Info | http://localhost:8080/actuator/info |

Somente `health` e `info` ficam expostos via HTTP.

---

## Migrações de banco (Flyway)

O projeto utiliza **Flyway** para versionamento e execução automática das migrações de banco de dados. As migrações ficam em `src/main/resources/db/migration/` e são aplicadas na ordem de versão ao subir a aplicação.

| Arquivo | Descrição |
|---|---|
| `V1__create_pacientes_table.sql` | Criação da tabela `pacientes` com todos os campos e constraints |
| `V2__insert_pacientes_teste.sql` | Carga inicial com 10 pacientes de teste de diferentes estados do Brasil |
| `V3__create_planos_table.sql` | Criação da tabela `planos` com join table `plano_dias_semana` |
| `V4__create_pagamentos_table.sql` | Criação da tabela `pagamentos` com constraint de unicidade `(plano_id, periodo_inicio)` |
| `V5__create_aulas_table.sql` | Criação da tabela `aulas` com constraint de unicidade `(paciente_id, data)` |
| `V6__create_profissionais_table.sql` | Criação da tabela `profissionais` com tipo de contrato e percentual por aula |
| `V7__insert_profissionais_teste.sql` | Carga inicial com profissionais de teste e ajuste da coluna `percentual_pagamento_aula` |
| `V8__alter_pacientes_uf_to_varchar.sql` | Altera coluna `uf` da tabela `pacientes` para `VARCHAR(2)` |

> Nos testes automatizados o Flyway fica desabilitado (`spring.flyway.enabled=false`), pois o banco H2 é gerenciado pelo Hibernate com `ddl-auto=create-drop`.

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | `localhost` | Host do banco PostgreSQL |
| `DB_PORT` | `5432` | Porta do banco |
| `DB_NAME` | `carlesso_pilates` | Nome do banco de dados |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |

---

## Exemplos com curl

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

### Listar e filtrar pacientes
```bash
curl -s "http://localhost:8080/pacientes?nome=maria&ativo=true&page=0&size=10&sort=nome,asc" | jq
```

### Buscar por ID
```bash
curl -s http://localhost:8080/pacientes/1 | jq
```

### Atualizar paciente
```bash
curl -s -X PUT http://localhost:8080/pacientes/1 \
  -H "Content-Type: application/json" \
  -d '{"telefone": "(11) 99999-0000"}' | jq
```

### Ativar paciente
```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/ativar -w "%{http_code}"
```

### Inativar paciente
```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/inativar -w "%{http_code}"
```

---

## Regras de Negócio

### Pacientes
- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes **inativos** não recebem novas cobranças nem têm aulas geradas

### Profissionais
- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- O `percentualPagamentoAula` representa o percentual recebido por aula ministrada
- Profissionais inativos são mantidos no banco (soft delete)
- O relatório de pagamento considera aulas realizadas vinculadas ao profissional no período informado
- O valor devido por aula é calculado por `valor do pagamento / quantidade de aulas do pagamento * percentualPagamentoAula / 100`

### Planos de Pagamento
- Tipos: `MENSAL`, `TRIMESTRAL`, `ANUAL`
- A quantidade de dias da semana selecionados deve corresponder exatamente à frequência contratada (1x, 2x ou 3x)
- Ao criar um novo plano, o plano ativo anterior é automaticamente inativado

### Frequência de Aulas
| Frequência | Vezes/semana | Aulas/mês (referência) |
|---|---|---|
| `UMA_VEZ` | 1 | 4 |
| `DUAS_VEZES` | 2 | 8 |
| `TRES_VEZES` | 3 | 12 |

### Pagamentos
- Status: `PENDENTE` → `PAGO` ou `VENCIDO`
- Valor não pode ser menor que o valor do plano
- Não pode haver dois pagamentos para o mesmo plano no mesmo período
- Ao confirmar (`PAGO`), as aulas do período são geradas automaticamente

### Geração de Aulas
- Aulas geradas com base nos dias da semana do plano e no período do pagamento
- Sem duplicatas: se a aula do paciente naquela data já existir, ela é ignorada
- Requer paciente ativo e pagamento confirmado
- Ao marcar uma aula como realizada, `profissionalId` pode ser informado para alimentar o relatório de pagamento do profissional

### Scheduler (processos automáticos)
| Horário | Ação |
|---|---|
| 06:00 todo dia | Marca como `VENCIDO` pagamentos `PENDENTE` com data de vencimento passada |
| 07:00 todo dia | Gera cobranças futuras para planos ativos a partir de 7 dias antes do fim do período |

---

## Testes

O projeto possui **121 testes** organizados em treze suítes:

| Suíte | Tipo | Testes |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito) | 12 |
| `PlanoServiceTest` | Unitário (Mockito) | 8 |
| `PagamentoServiceTest` | Unitário (Mockito) | 8 |
| `AulaServiceTest` | Unitário (Mockito) | 10 |
| `ProfissionalServiceTest` | Unitário (Mockito) | 13 |
| `PacienteServiceIntegrationTest` | JPA (`@DataJpaTest`) | 4 |
| `PacienteControllerTest` | Controller (`@WebMvcTest`) | 16 |
| `PlanoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `PagamentoControllerTest` | Controller (`@WebMvcTest`) | 9 |
| `AulaControllerTest` | Controller (`@WebMvcTest`) | 8 |
| `ProfissionalControllerTest` | Controller (`@WebMvcTest`) | 13 |
| `ActuatorTest` | Integração (`@SpringBootTest`) | 3 |
| `PilatesApiApplicationTests` | Integração (`@SpringBootTest`) | 1 |

### Executar os testes

```bash
JAVA_HOME=/caminho/para/jdk21 mvn test
```

Os testes de serviço e controller não necessitam de banco de dados. O `@SpringBootTest` usa H2 em memória automaticamente via `src/test/resources/application.properties`.
