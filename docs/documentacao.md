# Documentação Técnica — Carlesso Pilates API

## 1. Visão Geral

A **Carlesso Pilates API** é uma API REST desenvolvida para gerenciar o cadastro de pacientes de um estúdio de pilates. Ela expõe endpoints para criação, consulta, atualização e inativação de pacientes, com dados de endereço embutidos e paginação nas listagens.

A aplicação foi construída com **Spring Boot 3** e **Java 21**, utiliza **PostgreSQL** como banco de dados relacional e conta com documentação interativa via **Swagger UI**.

---

## 2. Tecnologias

| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework da aplicação |
| Spring Data JPA | 3.4.5 | Persistência e ORM |
| Spring Validation | 3.4.5 | Validação de entrada |
| PostgreSQL | 16 | Banco de dados relacional |
| springdoc-openapi | 2.8.3 | Documentação Swagger/OpenAPI |
| Maven | 3.9 | Build e gerenciamento de dependências |
| Docker | - | Containerização |
| Docker Compose | - | Orquestração local |

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
com.carlesso.pilatesapi
├── config/
│   └── OpenApiConfig.java
├── controller/
│   └── PacienteController.java
├── service/
│   └── PacienteService.java
├── repository/
│   └── PacienteRepository.java
├── entity/
│   ├── Paciente.java
│   └── Endereco.java
└── dto/
    ├── PacienteRequestDTO.java
    ├── PacienteUpdateDTO.java
    ├── PacienteResponseDTO.java
    └── EnderecoDTO.java
```

---

## 4. Modelo de Dados

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

## 5. Endpoints

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

### 5.5 Inativar Paciente

| | |
|---|---|
| **Método** | `DELETE` |
| **Rota** | `/pacientes/{id}` |
| **Descrição** | Realiza **soft delete**: marca o paciente como inativo (`ativo = false`). O registro permanece no banco. |

**Exemplo:**

```
DELETE /pacientes/1
```

**Códigos de resposta:**

| Código | Situação |
|---|---|
| `204` | Paciente inativado com sucesso |
| `404` | Paciente não encontrado |

---

## 6. Documentação Interativa

Com a aplicação rodando, acesse o Swagger UI para explorar e testar os endpoints diretamente pelo navegador:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

---

## 7. Configuração

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

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/api-docs
```

---

## 8. Como Rodar

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

## 9. Exemplos com curl

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

### Inativar paciente

```bash
curl -s -X DELETE http://localhost:8080/pacientes/1 -o /dev/null -w "%{http_code}"
# Retorna: 204
```

---

## 10. Infraestrutura Docker

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
