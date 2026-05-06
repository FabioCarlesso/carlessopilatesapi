# Carlesso Pilates API

API REST para gestão de pacientes e profissionais do estúdio Carlesso Pilates, desenvolvida com Spring Boot 3 e Java 21.

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.5 |
| Spring Data JPA | 3.4.5 |
| Spring Validation | 3.4.5 |
| Spring Security | 6.4.5 |
| Spring Boot Actuator | 3.4.5 |
| PostgreSQL | 16 |
| Flyway | (via spring-boot-starter-parent) |
| springdoc-openapi | 2.8.3 |
| JJWT | 0.12.6 |
| Spring Scheduler | (via spring-boot-starter) |
| Maven | 3.9 |
| Docker / Docker Compose | - |
| OpenPDF | 1.3.34 |
| Apache POI | 5.4.1 |
| JUnit 5 + Mockito | (via spring-boot-starter-test) |
| H2 (testes) | (in-memory) |

---

## Estrutura do projeto

```
src/
├── main/
│   ├── java/com/carlesso/pilatesapi/
│   │   ├── config/
│   │   │   ├── GlobalExceptionHandler.java  # Mapeia exceções customizadas para HTTP (404/409/422)
│   │   │   ├── OpenApiConfig.java           # Configuração do Swagger/OpenAPI
│   │   │   └── SecurityConfig.java          # Spring Security, JWT stateless e CORS
│   │   ├── exception/
│   │   │   ├── ResourceNotFoundException.java  # 404 — recurso não encontrado
│   │   │   ├── ConflictException.java          # 409 — conflito de estado/duplicidade
│   │   │   ├── BusinessException.java          # 422 — violação de regra de negócio
│   │   │   └── TooManyRequestsException.java   # 429 — muitas tentativas de login
│   │   ├── controller/
│   │   │   ├── PacienteController.java      # Endpoints REST de pacientes
│   │   │   ├── ProfissionalController.java  # Endpoints REST de profissionais
│   │   │   ├── PlanoController.java         # /planos
│   │   │   ├── PagamentoController.java     # /pagamentos
│   │   │   ├── AulaController.java          # /aulas
│   │   │   ├── AnamneseController.java      # /anamneses
│   │   │   ├── AvaliacaoFisioterapeuticaController.java # /avaliacoes-fisioterapeuticas
│   │   │   ├── PlanoTratamentoController.java          # /planos-tratamento
│   │   │   ├── AuthController.java          # /auth/register e /auth/login
│   │   │   ├── UserController.java          # /users/me e CRUD administrativo de usuários
│   │   │   ├── AdminController.java         # /admin/health
│   │   │   ├── RelatorioNfseController.java # /api/relatorios/nfse
│   │   │   └── DashboardController.java     # /dashboard/resumo
│   │   ├── service/
│   │   │   ├── PacienteService.java                    # Regras de negócio de pacientes
│   │   │   ├── ProfissionalService.java                # Regras de negócio de profissionais
│   │   │   ├── PlanoService.java                       # Regras de plano e frequência
│   │   │   ├── PagamentoService.java                   # Cobranças, confirmação, vencimentos
│   │   │   ├── AulaService.java                        # Geração e controle de aulas
│   │   │   ├── AnamneseService.java                    # Anamnese clínica do paciente
│   │   │   ├── AvaliacaoFisioterapeuticaService.java   # Avaliação fisioterapêutica do paciente
│   │   │   ├── PlanoTratamentoService.java             # Plano de tratamento do paciente
│   │   │   ├── DashboardService.java                   # Contadores e totais para o painel inicial
│   │   │   ├── RelatorioPagamentoExporterService.java  # Exportação do relatório em PDF e XLSX
│   │   │   ├── RelatorioNfseService.java               # Relatório de emissão de NFSEs por competência
│   │   │   ├── RelatorioNfseExporterService.java       # Exportação do relatório de NFSEs em CSV e XLSX
│   │   │   ├── AuthService.java                       # Registro/login, emissão de JWT e rate limiting
│   │   │   ├── UserService.java                       # CRUD administrativo de usuários e perfis
│   │   │   ├── JwtService.java                        # Geração (com claims role/userId) e validação de JWT
│   │   │   ├── LoginAttemptService.java               # Rate limiting in-memory por e-mail (5 tentativas / 15 min)
│   │   │   └── CustomUserDetailsService.java          # Integra usuários ao Spring Security
│   │   ├── repository/
│   │   │   ├── PacienteRepository.java      # Acesso ao banco
│   │   │   ├── ProfissionalRepository.java  # Acesso ao banco
│   │   │   ├── PlanoRepository.java
│   │   │   ├── PagamentoRepository.java
│   │   │   ├── AulaRepository.java
│   │   │   ├── AnamneseRepository.java
│   │   │   ├── AvaliacaoFisioterapeuticaRepository.java
│   │   │   ├── PlanoTratamentoRepository.java
│   │   │   └── UserRepository.java
│   │   ├── entity/
│   │   │   ├── Paciente.java                # Entidade JPA
│   │   │   ├── Endereco.java                # Embeddable de endereço
│   │   │   ├── Profissional.java            # Entidade JPA
│   │   │   ├── Plano.java                   # Plano de pagamento do paciente
│   │   │   ├── Pagamento.java               # Cobrança por período
│   │   │   ├── Aula.java                    # Aula agendada (com presença)
│   │   │   ├── Anamnese.java                # Anamnese clínica do paciente
│   │   │   ├── AvaliacaoFisioterapeutica.java # Avaliação técnica do paciente
│   │   │   ├── PlanoTratamento.java           # Plano de tratamento clínico do paciente
│   │   │   └── User.java                    # Usuário autenticável da API
│   │   ├── security/
│   │   │   └── JwtAuthenticationFilter.java # Validação do Bearer token por requisição
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
│   │   │   ├── RelatorioNfseResponseDTO.java
│   │   │   ├── PlanoRequestDTO.java
│   │   │   ├── PlanoResponseDTO.java
│   │   │   ├── PagamentoRequestDTO.java
│   │   │   ├── PagamentoPagarRequestDTO.java
│   │   │   ├── PagamentoResponseDTO.java
│   │   │   ├── AulaResponseDTO.java
│   │   │   ├── AnamneseRequestDTO.java
│   │   │   ├── AnamneseUpdateDTO.java
│   │   │   ├── AnamneseResponseDTO.java
│   │   │   ├── AvaliacaoFisioterapeuticaRequestDTO.java
│   │   │   ├── AvaliacaoFisioterapeuticaUpdateDTO.java
│   │   │   ├── AvaliacaoFisioterapeuticaResponseDTO.java
│   │   │   ├── AuthRegisterRequestDTO.java
│   │   │   ├── AuthLoginRequestDTO.java
│   │   │   ├── AuthResponseDTO.java
│   │   │   ├── UserRequestDTO.java
│   │   │   ├── UserUpdateDTO.java
│   │   │   └── UserResponseDTO.java
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
│           ├── V10__add_profissional_to_aulas.sql
│           ├── V11__create_users_table.sql
│           ├── V12__insert_users_perfis_acesso.sql
│           ├── V13__add_indexes_on_foreign_keys.sql
│           ├── V14__create_anamneses_table.sql
│           ├── V15__create_avaliacoes_fisioterapeuticas_table.sql
│           ├── V16__create_planos_tratamento_table.sql
│           ├── V17__create_sessoes_pilates_table.sql
│           ├── V18__create_evolucoes_sessao_table.sql
│           └── V19__create_reavaliacoes_table.sql
└── test/java/com/carlesso/pilatesapi/
    ├── PilatesApiApplicationTests.java
    ├── actuator/
    │   └── ActuatorTest.java
    ├── config/
    │   ├── AppPropertiesTest.java
    │   └── GlobalExceptionHandlerTest.java
    ├── scheduler/
    │   └── CobrancaSchedulerIntegrationTest.java
    ├── security/
    │   └── SecurityIntegrationTest.java
    ├── service/
    │   ├── AvaliacaoFisioterapeuticaServiceTest.java
    │   ├── PacienteServiceTest.java
    │   ├── PacienteServiceIntegrationTest.java
    │   ├── ProfissionalServiceIntegrationTest.java
    │   ├── ProfissionalServiceTest.java
    │   ├── PlanoServiceTest.java
    │   ├── PagamentoServiceTest.java
    │   ├── AulaServiceTest.java
    │   ├── RelatorioPagamentoExporterServiceTest.java
    │   ├── RelatorioNfseServiceTest.java
    │   └── RelatorioNfseExporterServiceTest.java
    ├── repository/
    │   ├── AulaRepositoryTest.java
    │   └── PagamentoRepositoryTest.java
    └── controller/
        ├── AvaliacaoFisioterapeuticaControllerTest.java
        ├── PacienteControllerTest.java
        ├── ProfissionalControllerTest.java
        ├── PlanoControllerTest.java
        ├── PagamentoControllerTest.java
        ├── AulaControllerTest.java
        └── RelatorioNfseControllerTest.java
```

---

## Endpoints

Base URL: `http://localhost:8080`

### Autenticação

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/auth/register` | Público | Registra usuário com role `USER`, salva senha com BCrypt e retorna JWT |
| `POST` | `/auth/login` | Público | Valida e-mail/senha e retorna JWT. Retorna `429` após 5 tentativas falhas em 15 min |
| `GET` | `/users/me` | Autenticado | Retorna dados seguros do usuário autenticado |
| `POST` | `/users` | `ADMIN` | Cria usuário com role `USER` ou `ADMIN` |
| `GET` | `/users` | `ADMIN` | Lista usuários cadastrados sem expor senha |
| `GET` | `/users/{id}` | `ADMIN` | Busca usuário por ID |
| `PUT` | `/users/{id}` | `ADMIN` | Atualiza nome, e-mail, senha e perfil. Admin não pode alterar o próprio role nem rebaixar o último ADMIN ativo |
| `DELETE` | `/users/{id}` | `ADMIN` | Inativa usuário (soft delete). Admin não pode inativar a própria conta nem o último ADMIN ativo |
| `GET` | `/admin/health` | `ADMIN` | Endpoint inicial administrativo |

As demais rotas de negócio exigem `Authorization: Bearer <accessToken>`. Tokens ausentes, inválidos ou expirados retornam `401 Unauthorized`; usuário sem role `ADMIN` em `/admin/**` e no CRUD de `/users` recebe `403 Forbidden`.

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
| `GET` | `/profissionais/{id}/relatorio-pagamento` | Gerar relatório de pagamento por período (JSON) |
| `GET` | `/profissionais/{id}/relatorio-pagamento/pdf` | Exportar relatório de pagamento em PDF |
| `GET` | `/profissionais/{id}/relatorio-pagamento/xlsx` | Exportar relatório de pagamento em Excel (XLSX) |

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
| `PATCH` | `/pagamentos/{id}/pagar` | Confirmar pagamento e gerar aulas; aceita `dataPagamento` opcional no corpo |

### Aulas

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/aulas/{id}` | Buscar aula por ID |
| `GET` | `/aulas/paciente/{id}` | Listar aulas do paciente |
| `GET` | `/aulas/pagamento/{id}` | Listar aulas de um pagamento |
| `PATCH` | `/aulas/{id}/realizar` | Marcar aula como realizada, opcionalmente com `profissionalId` |

### Avaliações Fisioterapêuticas

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/avaliacoes-fisioterapeuticas` | Criar avaliação fisioterapêutica para um paciente |
| `GET` | `/avaliacoes-fisioterapeuticas/{id}` | Buscar avaliação fisioterapêutica por ID |
| `GET` | `/avaliacoes-fisioterapeuticas/paciente/{pacienteId}` | Listar avaliações fisioterapêuticas do paciente |
| `PUT` | `/avaliacoes-fisioterapeuticas/{id}` | Atualizar dados da avaliação fisioterapêutica |

### Planos de Tratamento

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/planos-tratamento` | Criar plano de tratamento para um paciente |
| `GET` | `/planos-tratamento/{id}` | Buscar plano de tratamento por ID |
| `GET` | `/planos-tratamento/paciente/{pacienteId}` | Listar planos de tratamento do paciente |
| `PUT` | `/planos-tratamento/{id}` | Atualizar dados do plano de tratamento |
| `DELETE` | `/planos-tratamento/{id}` | Inativar plano de tratamento |

### Evoluções de Sessão

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/evolucoes-sessao` | Registrar evolução clínica de uma sessão |
| `GET` | `/evolucoes-sessao/{id}` | Buscar evolução por ID |
| `GET` | `/evolucoes-sessao/sessao/{sessaoId}` | Buscar evolução pela sessão vinculada |
| `PUT` | `/evolucoes-sessao/{id}` | Atualizar dados da evolução |

### Relatórios

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/relatorios/nfse` | Gerar relatório de emissão de NFSEs por competência (JSON, CSV ou XLSX) |

### Dashboard

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/dashboard/resumo` | Resumo consolidado para o painel inicial (pacientes, profissionais, pagamentos e aulas do mês) |

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
GET /profissionais/1/relatorio-pagamento/pdf?inicio=2025-02-01&fim=2025-02-28
GET /profissionais/1/relatorio-pagamento/xlsx?inicio=2025-02-01&fim=2025-02-28
GET /api/relatorios/nfse?competencia=04/2026
GET /api/relatorios/nfse?competencia=04/2026&notaAnteriorEmitida=false&formato=XLSX
```

### Relatório de pagamento — contrato JSON (Angular-friendly)

A resposta do endpoint `GET /profissionais/{id}/relatorio-pagamento` é estruturada em sub-objetos para facilitar o consumo direto no Angular sem mapeamentos adicionais:

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
    "totalAulas": 8,
    "quantidadePagamentos": 2,
    "totalPagamentosBruto": 400.00,
    "totalProfissional": 90.00
  },
  "pagamentos": [
    {
      "pagamentoId": 5,
      "valorPagamento": 200.00,
      "quantidadeAulasPagamento": 8,
      "quantidadeAulasNoPeriodo": 4,
      "valorBaseAula": 25.00,
      "totalProfissional": 45.00
    }
  ],
  "aulas": [
    {
      "aulaId": 10,
      "data": "2025-02-03",
      "pacienteId": 2,
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

### Exportação PDF/XLSX

Os endpoints `GET /profissionais/{id}/relatorio-pagamento/pdf` e `GET /profissionais/{id}/relatorio-pagamento/xlsx` retornam o relatório como anexo:

| Endpoint | `Content-Type` | `Content-Disposition` |
|---|---|---|
| `/relatorio-pagamento/pdf` | `application/pdf` | `attachment; filename="relatorio-pagamento-profissional-{id}-{inicio}-{fim}.pdf"` |
| `/relatorio-pagamento/xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `attachment; filename="relatorio-pagamento-profissional-{id}-{inicio}-{fim}.xlsx"` |

O XLSX possui três abas: `Resumo`, `Pagamentos` e `Aulas`. O PDF apresenta as mesmas informações em layout único, com tabelas para pagamentos e aulas.

### Resumo do dashboard — contrato JSON

A resposta de `GET /dashboard/resumo` consolida contadores do banco em um único objeto para consumo direto pelo painel inicial:

```json
{
  "pacientes": {
    "totalAtivos": 10,
    "totalInativos": 2
  },
  "profissionais": {
    "totalAtivos": 3,
    "totalInativos": 1
  },
  "pagamentos": {
    "totalPendentes": 5,
    "totalPagos": 8,
    "totalVencidos": 2,
    "receitaMesAtual": 1600.00
  },
  "aulas": {
    "totalRealizadasMesAtual": 40,
    "totalAgendadasMesAtual": 20
  },
  "geradoEm": "2026-04-29T10:00:00"
}
```

- `receitaMesAtual` — soma dos pagamentos com status `PAGO` e `dataPagamento` dentro do mês corrente.
- `totalAgendadasMesAtual` — aulas com `realizada = false` e `data` dentro do mês corrente vinculadas a pacientes ativos.
- `totalRealizadasMesAtual` — aulas com `realizada = true` e `data` dentro do mês corrente vinculadas a pacientes ativos.

### Relatório de emissão de NFSEs

O endpoint `GET /api/relatorios/nfse` exige `competencia` no formato `MM/AAAA` e aceita os filtros opcionais `notaAnteriorEmitida` e `formato` (`JSON`, `CSV` ou `XLSX`). Ele retorna apenas pagamentos confirmados (`PAGO`) com `dataPagamento` dentro da competência informada e pacientes ativos.

Contrato JSON:

```json
[
  {
    "nome": "Ana Souza",
    "cpfCnpj": "11122233344",
    "valorPago": 250.00,
    "competencia": "04/2026",
    "descricaoServico": "Aulas de Pilates - Competência 04/2026",
    "notaAnteriorEmitida": false,
    "dataPagamento": "2026-04-10",
    "observacoes": ""
  }
]
```

Para o modelo atual, `notaAnteriorEmitida` é inferido pela existência de pagamento confirmado anterior para o mesmo paciente. CSV e XLSX são retornados como anexo com nome `relatorio-nfse-{MM-AAAA}.{ext}`.

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

### POST /avaliacoes-fisioterapeuticas — corpo da requisição

```json
{
  "pacienteId": 1,
  "dataAvaliacao": "2026-04-20",
  "queixaFuncional": "Dor ao agachar",
  "avaliacaoPostural": "Anteriorização de cabeça",
  "mobilidadeArticular": "Restrição em quadril direito",
  "forcaMuscular": "Glúteo médio grau 4",
  "flexibilidade": "Encurtamento de cadeia posterior",
  "equilibrio": "Instável em apoio unipodal",
  "coordenacaoMotora": "Boa coordenação",
  "padraoRespiratorio": "Respiração apical",
  "escalaDor": 6,
  "testesFuncionaisRealizados": "Agachamento, ponte, apoio unipodal",
  "diagnosticoFisioterapeutico": "Disfunção lombopélvica",
  "observacoesGerais": "Reavaliar em 30 dias"
}
```

> Campos obrigatórios: `pacienteId`, `dataAvaliacao`, `queixaFuncional`, `escalaDor` e `diagnosticoFisioterapeutico`. `escalaDor` deve estar entre 0 e 10.

---

## Como rodar

### Opção 1 — Docker Compose (recomendado)

Sobe o banco PostgreSQL e a aplicação juntos, sem instalar nada localmente além do Docker.

```bash
# Clonar o repositório
git clone <url-do-repositorio>
cd carlessopilatesapi

# Subir todos os serviços
cp .env.example .env
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
export JWT_SECRET=replace_with_a_secret_with_at_least_32_characters
export JWT_EXPIRATION_MS=86400000
export CORS_ALLOWED_ORIGINS=http://localhost:4200
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
| `V9__alter_profissionais_percentual_precision.sql` | Ajusta precisão do percentual de pagamento por aula |
| `V10__add_profissional_to_aulas.sql` | Vincula profissional às aulas realizadas |
| `V11__create_users_table.sql` | Cria tabela `users` para autenticação e autorização |
| `V12__insert_users_perfis_acesso.sql` | Insere 5 usuários iniciais com perfis `ADMIN` e `USER` |
| `V13__add_indexes_on_foreign_keys.sql` | Adiciona índices para FKs e filtros recorrentes |
| `V14__create_anamneses_table.sql` | Cria tabela `anamneses` vinculada a pacientes |
| `V15__create_avaliacoes_fisioterapeuticas_table.sql` | Cria histórico de avaliações fisioterapêuticas do paciente |
| `V16__create_planos_tratamento_table.sql` | Cria tabela de planos de tratamento do paciente |
| `V17__create_sessoes_pilates_table.sql` | Cria tabela de sessões de Pilates/Fisioterapia |
| `V18__create_evolucoes_sessao_table.sql` | Cria tabela de evoluções de sessão vinculada a sessões |
| `V19__create_reavaliacoes_table.sql` | Cria tabela de reavaliações periódicas vinculada a pacientes, avaliações e planos de tratamento |

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
| `JWT_SECRET` | - | Segredo HMAC obrigatório para assinar JWT; use pelo menos 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` | Expiração do access token em milissegundos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens permitidas para o frontend Angular |
| `APP_COBRANCA_CRON_VENCIDOS` | `0 0 6 * * *` | Cron expression do scheduler que marca pagamentos como `VENCIDO` |
| `APP_COBRANCA_CRON_COBRANCAS_FUTURAS` | `0 0 7 * * *` | Cron expression do scheduler que gera cobranças futuras |
| `APP_COBRANCA_VENCIMENTO_DIAS` | `10` | Dias somados ao início do período para definir o vencimento das cobranças geradas |
| `APP_PAGINACAO_TAMANHO_PADRAO` | `10` | Tamanho padrão de página nas listagens paginadas |

---

## Exemplos com curl

### Registrar e fazer login
```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Maria","email":"maria@email.com","password":"senha1234"}' | jq

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@email.com","password":"senha1234"}' | jq -r .accessToken)

curl -s http://localhost:8080/users/me \
  -H "Authorization: Bearer $TOKEN" | jq
```

Usuários iniciais criados pela migração `V12` usam a senha `senha1234` e representam os perfis disponíveis:

| E-mail | Perfil |
|---|---|
| `admin@carlessopilates.com` | `ADMIN` |
| `operacional@carlessopilates.com` | `ADMIN` |
| `recepcao@carlessopilates.com` | `USER` |
| `financeiro@carlessopilates.com` | `USER` |
| `consulta@carlessopilates.com` | `USER` |

### Gerenciar usuários como ADMIN
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@carlessopilates.com","password":"senha1234"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"Novo Admin","email":"novo.admin@email.com","password":"senha1234","role":"ADMIN"}' | jq

curl -s http://localhost:8080/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

### Cadastrar paciente
```bash
curl -s -X POST http://localhost:8080/pacientes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
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
curl -s "http://localhost:8080/pacientes?nome=maria&ativo=true&page=0&size=10&sort=nome,asc" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Buscar por ID
```bash
curl -s http://localhost:8080/pacientes/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Atualizar paciente
```bash
curl -s -X PUT http://localhost:8080/pacientes/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"telefone": "(11) 99999-0000"}' | jq
```

### Ativar paciente
```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/ativar \
  -H "Authorization: Bearer $TOKEN" -w "%{http_code}"
```

### Inativar paciente
```bash
curl -s -X PATCH http://localhost:8080/pacientes/1/inativar \
  -H "Authorization: Bearer $TOKEN" -w "%{http_code}"
```

### Confirmar pagamento
```bash
curl -s -X PATCH http://localhost:8080/pagamentos/1/pagar \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"dataPagamento": "2025-02-10"}' | jq
```

### Criar avaliação fisioterapêutica
```bash
curl -s -X POST http://localhost:8080/avaliacoes-fisioterapeuticas \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "pacienteId": 1,
    "dataAvaliacao": "2026-04-20",
    "queixaFuncional": "Dor ao agachar",
    "escalaDor": 6,
    "diagnosticoFisioterapeutico": "Disfunção lombopélvica",
    "observacoesGerais": "Reavaliar em 30 dias"
  }' | jq

curl -s http://localhost:8080/avaliacoes-fisioterapeuticas/paciente/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Gerar relatório de pagamento (JSON)
```bash
curl -s "http://localhost:8080/profissionais/1/relatorio-pagamento?inicio=2025-02-01&fim=2025-02-28" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Exportar relatório em PDF
```bash
curl -s -OJ "http://localhost:8080/profissionais/1/relatorio-pagamento/pdf?inicio=2025-02-01&fim=2025-02-28" \
  -H "Authorization: Bearer $TOKEN"
```

### Exportar relatório em Excel (XLSX)
```bash
curl -s -OJ "http://localhost:8080/profissionais/1/relatorio-pagamento/xlsx?inicio=2025-02-01&fim=2025-02-28" \
  -H "Authorization: Bearer $TOKEN"
```

### Gerar relatório de NFSE
```bash
curl -s "http://localhost:8080/api/relatorios/nfse?competencia=04/2026" \
  -H "Authorization: Bearer $TOKEN" | jq
curl -s -OJ "http://localhost:8080/api/relatorios/nfse?competencia=04/2026&formato=CSV" \
  -H "Authorization: Bearer $TOKEN"
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
- O relatório de pagamento considera aulas realizadas vinculadas ao profissional no período informado e ignora aulas de pacientes inativos
- O relatório de pagamento usa uma consulta consolidada com `JOIN` e `GROUP BY` para buscar os dados das aulas e a quantidade de aulas do pagamento sem round-trips adicionais
- O valor devido por aula é calculado por `valor do pagamento / quantidade de aulas do pagamento * percentualPagamentoAula / 100`
- O relatório de pagamento é limitado a períodos de até 366 dias e até 5.000 aulas para evitar exportações excessivas em memória
- O relatório também é exportável em PDF (OpenPDF) e Excel/XLSX (Apache POI), reaproveitando o mesmo cálculo do endpoint JSON

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
- A confirmação de pagamento recebe `dataPagamento` no corpo da requisição; se omitida, usa a data atual

### NFSE
- O relatório de NFSE considera apenas pagamentos `PAGO` com `dataPagamento` dentro da competência `MM/AAAA`
- Pacientes inativos são ignorados
- `Nome`, `CPF/CNPJ`, `ValorPago` e `DataPagamento` vêm do paciente e do pagamento confirmado
- `DescricaoServico` é gerada automaticamente como `Aulas de Pilates - Competência MM/AAAA`
- `NotaAnteriorEmitida` é inferida por pagamento confirmado anterior do mesmo paciente
- Registros sem nome, CPF/CNPJ, valor positivo ou data de pagamento retornam erro de regra de negócio (`422`)

### Geração de Aulas
- Aulas geradas com base nos dias da semana do plano e no período do pagamento
- Sem duplicatas: se a aula do paciente naquela data já existir, ela é ignorada
- Requer paciente ativo e pagamento confirmado
- Consultas de aulas por ID, paciente, pagamento e relatório retornam apenas aulas associadas a pacientes ativos
- Ao marcar uma aula como realizada, `profissionalId` pode ser informado para alimentar o relatório de pagamento do profissional

### Avaliações Fisioterapêuticas
- Um paciente pode ter múltiplas avaliações fisioterapêuticas para manter histórico clínico
- Criar avaliação para paciente inexistente ou inativo retorna `404`
- Campos obrigatórios: `dataAvaliacao`, `queixaFuncional`, `escalaDor` e `diagnosticoFisioterapeutico`
- `escalaDor` aceita valores inteiros de 0 a 10
- Consultas e atualizações filtram avaliações vinculadas a pacientes ativos
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados

### Planos de Tratamento
- Um paciente pode ter múltiplos planos de tratamento para manter histórico clínico
- Criar plano para paciente inexistente ou inativo retorna `404`
- Campos obrigatórios: `pacienteId`, `dataInicio` e `objetivosTratamento`
- `dataFimPrevista`, quando informada, não pode ser anterior a `dataInicio`
- `numeroSessoesPrevistas` aceita apenas valores positivos quando informado
- Consultas e atualizações filtram planos ativos vinculados a pacientes ativos
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `objetivosTratamento` não aceita strings em branco quando enviado
- Exclusão é lógica: `DELETE /planos-tratamento/{id}` marca o plano como inativo e preserva o histórico no banco

### Sessões de Pilates/Fisioterapia
- A evolução clínica estruturada deve ser registrada em `/evolucoes-sessao`
- O campo legado `sessoes_pilates.evolucao` não faz parte do contrato REST de sessões
- Excluir uma sessão remove também a evolução vinculada, quando existir

### Evoluções de Sessão
- Cada sessão possui no máximo uma evolução clínica (regra de unicidade por `sessao_id`)
- Criar evolução para sessão inexistente retorna `404`
- Tentar criar segunda evolução para a mesma sessão retorna `409`
- Campos obrigatórios: `sessaoId` e `dataHoraRegistro`
- `dorAntes` e `dorDepois`, quando informados, aceitam apenas valores inteiros de 0 a 10
- Consultas e atualizações filtram sessões de pacientes ativos
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados

### Scheduler (processos automáticos)
| Horário | Ação | Configuração |
|---|---|---|
| 06:00 todo dia (default) | Marca como `VENCIDO` pagamentos `PENDENTE` com data de vencimento passada | `app.cobranca.cron-vencidos` (env `APP_COBRANCA_CRON_VENCIDOS`) |
| 07:00 todo dia (default) | Gera cobranças futuras para planos ativos a partir de 7 dias antes do fim do período | `app.cobranca.cron-cobrancas-futuras` (env `APP_COBRANCA_CRON_COBRANCAS_FUTURAS`) |

O vencimento das cobranças geradas pelo scheduler é definido por `app.cobranca.vencimento-dias` (env `APP_COBRANCA_VENCIMENTO_DIAS`, default `10`), somado ao início do período. O tamanho padrão de página nas listagens paginadas é controlado por `spring.data.web.pageable.default-page-size`, alimentado pela env `APP_PAGINACAO_TAMANHO_PADRAO` (default `10`).

### Boas práticas Spring
- Métodos de leitura em services usam `@Transactional(readOnly = true)` para reduzir flush desnecessário e preparar a aplicação para roteamento futuro de leituras.

### Tratamento de erros

A API utiliza exceções customizadas mapeadas pelo `GlobalExceptionHandler` para retornar o status HTTP semanticamente correto:

| Exceção | HTTP | Quando é lançada |
|---|---|---|
| `ResourceNotFoundException` | `404 Not Found` | Recurso solicitado não existe (ex.: paciente, plano, pagamento ou aula não encontrada) |
| `ConflictException` | `409 Conflict` | Conflito de estado ou duplicidade (ex.: e-mail/CPF já cadastrado, pagamento já confirmado, aula já realizada) |
| `BusinessException` | `422 Unprocessable Entity` | Regra de negócio violada (ex.: paciente inativo não pode receber cobrança, profissional inativo não pode ser vinculado a aula) |
| `IllegalArgumentException` | `400 Bad Request` | Parâmetros de entrada inválidos (ex.: período inicial maior que o final, valor menor que o do plano) |
| `DataIntegrityViolationException` | `409 Conflict` | Violação de constraint do banco (ex.: registro duplicado ao salvar) |

Formato da resposta de erro:

```json
{ "erro": "Mensagem descritiva do problema" }
```

---

## Testes

O projeto possui testes unitários, de controller e de integração organizados por camada:

| Suíte | Tipo | Testes |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito) | 12 |
| `PlanoServiceTest` | Unitário (Mockito) | 9 |
| `PagamentoServiceTest` | Unitário (Mockito) | 10 |
| `AulaServiceTest` | Unitário (Mockito) | 14 |
| `AnamneseServiceTest` | Unitário (Mockito) | 17 |
| `AvaliacaoFisioterapeuticaServiceTest` | Unitário (Mockito) | 8 |
| `PlanoTratamentoServiceTest` | Unitário (Mockito) | 13 |
| `ProfissionalServiceTest` | Unitário (Mockito) | 15 |
| `RelatorioPagamentoExporterServiceTest` | Unitário | 3 |
| `RelatorioNfseServiceTest` | Unitário (Mockito) | 5 |
| `RelatorioNfseExporterServiceTest` | Unitário | 3 |
| `AppPropertiesTest` | Unitário (ApplicationContextRunner) | 3 |
| `GlobalExceptionHandlerTest` | Unitário | 6 |
| `PacienteServiceIntegrationTest` | JPA (`@DataJpaTest`) | 4 |
| `ProfissionalServiceIntegrationTest` | JPA (`@DataJpaTest`) | 5 |
| `PagamentoServiceAtomicidadeIntegrationTest` | Integração (`@SpringBootTest` + H2) | 1 |
| `CobrancaSchedulerIntegrationTest` | JPA (`@DataJpaTest`) | 11 |
| `AulaRepositoryTest` | JPA (`@DataJpaTest`) | 6 |
| `PagamentoRepositoryTest` | JPA (`@DataJpaTest`) | 2 |
| `PacienteControllerTest` | Controller (`@WebMvcTest`) | 16 |
| `PlanoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `PagamentoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `AulaControllerTest` | Controller (`@WebMvcTest`) | 10 |
| `AnamneseControllerTest` | Controller (`@WebMvcTest`) | 14 |
| `AvaliacaoFisioterapeuticaControllerTest` | Controller (`@WebMvcTest`) | 12 |
| `PlanoTratamentoControllerTest` | Controller (`@WebMvcTest`) | 18 |
| `ProfissionalControllerTest` | Controller (`@WebMvcTest`) | 17 |
| `RelatorioNfseControllerTest` | Controller (`@WebMvcTest`) | 6 |
| `DashboardControllerTest` | Controller (`@WebMvcTest`) | 2 |
| `DashboardServiceTest` | Unitário (Mockito) | 3 |
| `EvolucaoSessaoServiceTest` | Unitário (Mockito) | 10 |
| `EvolucaoSessaoControllerTest` | Controller (`@WebMvcTest`) | 13 |
| `SecurityIntegrationTest` | Integração (`@SpringBootTest` + MockMvc + H2) | 23 |
| `ActuatorTest` | Integração (`@SpringBootTest`) | 3 |
| `PilatesApiApplicationTests` | Integração (`@SpringBootTest`) | 1 |

### Executar os testes

```bash
JAVA_HOME=/caminho/para/jdk21 mvn test
```

Os testes de serviço e controller não necessitam de banco de dados. O `@SpringBootTest` usa H2 em memória automaticamente via `src/test/resources/application.properties`.

---

## Licença

Este projeto está licenciado sob a licença MIT. Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.
