# Carlesso Pilates API

API REST para gestão de pacientes do estúdio Carlesso Pilates, desenvolvida com Spring Boot 3 e Java 21.

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring Data JPA | 3.4.5 |
| Spring Validation | 3.4.5 |
| PostgreSQL | 16 |
| Flyway | (via spring-boot-starter-parent) |
| springdoc-openapi | 2.8.3 |
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
│   └── resources/
│       ├── application.properties
│       └── db/migration/
│           ├── V1__create_pacientes_table.sql  # Criação da tabela pacientes
│           └── V2__insert_pacientes_teste.sql  # Carga inicial com 10 pacientes de teste
└── test/java/com/carlesso/pilatesapi/
    ├── PilatesApiApplicationTests.java  # Context load test
    ├── service/
    │   └── PacienteServiceTest.java     # Testes unitários do serviço
    └── controller/
        └── PacienteControllerTest.java  # Testes do controller com MockMvc
```

---

## Endpoints

Base URL: `http://localhost:8080`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/pacientes` | Cadastrar novo paciente |
| `GET` | `/pacientes` | Listar pacientes ativos (paginado) |
| `GET` | `/pacientes/{id}` | Buscar paciente por ID |
| `PUT` | `/pacientes/{id}` | Atualizar dados do paciente |
| `DELETE` | `/pacientes/{id}` | Inativar paciente (soft delete) |

### Paginação

O endpoint `GET /pacientes` suporta os query params padrão do Spring:

```
GET /pacientes?page=0&size=10&sort=nome,asc
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

## Migrações de banco (Flyway)

O projeto utiliza **Flyway** para versionamento e execução automática das migrações de banco de dados. As migrações ficam em `src/main/resources/db/migration/` e são aplicadas na ordem de versão ao subir a aplicação.

| Arquivo | Descrição |
|---|---|
| `V1__create_pacientes_table.sql` | Criação da tabela `pacientes` com todos os campos e constraints |
| `V2__insert_pacientes_teste.sql` | Carga inicial com 10 pacientes de teste de diferentes estados do Brasil |

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

### Listar pacientes ativos
```bash
curl -s http://localhost:8080/pacientes | jq
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

### Inativar paciente
```bash
curl -s -X DELETE http://localhost:8080/pacientes/1 -w "%{http_code}"
```

---

## Testes

O projeto possui **25 testes** organizados em duas suítes:

| Suíte | Tipo | Testes |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito) | 11 |
| `PacienteControllerTest` | Controller (`@WebMvcTest` + MockMvc) | 13 |
| `PilatesApiApplicationTests` | Integração (`@SpringBootTest`) | 1 |

### Executar os testes

```bash
JAVA_HOME=/caminho/para/jdk21 mvn test
```

Os testes de serviço e controller não necessitam de banco de dados. O `@SpringBootTest` usa H2 em memória automaticamente via `src/test/resources/application.properties`.

### O que é testado

**PacienteServiceTest** — lógica de negócio com repositório mockado:
- `cadastrar` com e sem endereço
- `listar` com e sem resultados
- `buscarPorId` — encontrado e não encontrado (`EntityNotFoundException`)
- `atualizar` — campos parciais, com endereço, não encontrado
- `inativar` — inativação e não encontrado

**PacienteControllerTest** — camada HTTP com serviço mockado:
- `POST /pacientes` — 201, validação de `nome`/`cpf` ausentes, e-mail inválido, body vazio
- `GET /pacientes` — 200 com página, página vazia
- `GET /pacientes/{id}` — 200 encontrado, 404 com mensagem de erro
- `PUT /pacientes/{id}` — 200 com dados atualizados, 404
- `DELETE /pacientes/{id}` — 204 sem corpo, 404
