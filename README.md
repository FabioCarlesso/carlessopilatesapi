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
| JaCoCo (cobertura) | 0.8.13 |

---

## Estrutura do projeto

```
src/
├── main/
│   ├── java/com/carlesso/pilatesapi/
│   │   ├── config/
│   │   │   ├── GlobalExceptionHandler.java  # Mapeia exceções para HTTP (400/403/404/409/422/500) com detalhe de campos na validação
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
│       ├── application-dev.properties
│       ├── application-prod.properties
│       └── db/
│           ├── migration/          # DDL estrutural — todos os ambientes
│           │   ├── V1__create_pacientes_table.sql
│           │   ├── V3__create_planos_table.sql
│           │   ├── ...
│           │   └── V21__insert_admin_inicial.sql
│           └── seed/               # Dados de teste — apenas perfil dev
│               ├── V2__insert_pacientes_teste.sql
│               ├── V7__insert_profissionais_teste.sql
│               └── V12__insert_users_perfis_acesso.sql
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
| `POST` | `/auth/forgot-password` | Público | Solicita redefinição de senha por e-mail. Sempre retorna `200` com mensagem genérica, mesmo se o e-mail não existir (evita enumeração de usuários). Retorna `429` após 5 solicitações em 15 min para o mesmo e-mail |
| `POST` | `/auth/reset-password` | Público | Redefine a senha a partir de `token`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`. Retorna `422` para token inválido, expirado, já utilizado ou confirmação divergente |
| `GET` | `/users/me` | Autenticado | Retorna dados seguros do usuário autenticado |
| `PUT` | `/users/me/senha` | Autenticado | Troca a própria senha informando `senhaAtual`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`. Retorna `422` para senha atual incorreta, confirmação divergente ou reuso da senha atual. Tokens emitidos antes da troca deixam de autorizar rotas protegidas |
| `GET` | `/users/me/preferencias` | Autenticado | Retorna as preferências do usuário autenticado (idioma, tema e notificações). Usuário sem preferências salvas recebe os valores padrão |
| `PUT` | `/users/me/preferencias` | Autenticado | Atualiza as preferências do usuário autenticado. Valida `idioma` (`PT_BR`, `EN_US`, `ES_ES`) e `tema` (`CLARO`, `ESCURO`); valores inválidos retornam `400` |
| `POST` | `/users` | `ADMIN` | Cria usuário com role `USER` ou `ADMIN` |
| `GET` | `/users` | `ADMIN` | Lista usuários cadastrados sem expor senha |
| `GET` | `/users/roles` | `ADMIN` | Lista as roles disponíveis (`value` e `label`) para uso em formulários administrativos |
| `GET` | `/users/{id}` | `ADMIN` | Busca usuário por ID |
| `PUT` | `/users/{id}` | `ADMIN` | Atualiza nome, e-mail, senha e perfil. Admin não pode alterar o próprio role nem rebaixar o último ADMIN ativo |
| `DELETE` | `/users/{id}` | `ADMIN` | Inativa usuário (soft delete). Admin não pode inativar a própria conta nem o último ADMIN ativo |
| `GET` | `/admin/health` | `ADMIN` | Endpoint inicial administrativo |

As demais rotas de negócio exigem `Authorization: Bearer <accessToken>`. Tokens ausentes, inválidos, expirados ou emitidos antes da última troca/redefinição de senha retornam `401 Unauthorized`; usuário sem role `ADMIN` em `/admin/**` e no CRUD de `/users` recebe `403 Forbidden`.

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

### NFSE emitidas

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/nfse-emitidas` | Registrar ou atualizar a NFSE emitida de um paciente em uma competência |
| `GET` | `/api/nfse-emitidas/paciente/{pacienteId}` | Listar as NFSEs emitidas registradas para um paciente |

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

O campo `notaAnteriorEmitida` é preenchido com base nas NFSEs efetivamente registradas: é `true` quando existe uma NFSE emitida para o paciente em uma competência anterior à consultada (ver seção abaixo). CSV e XLSX são retornados como anexo com nome `relatorio-nfse-{MM-AAAA}.{ext}`.

### NFSE emitidas — registro fiscal

Para que o relatório use dados reais em `notaAnteriorEmitida`, a última NFSE emitida de cada paciente é persistida por competência através de `POST /api/nfse-emitidas`. O registro é idempotente por `(paciente, competência)`: se já existir uma nota para o paciente na competência informada, ela é atualizada; caso contrário, é criada.

Corpo da requisição:

```json
{
  "pacienteId": 1,
  "competencia": "04/2026",
  "numeroNota": "NF-2026-000123",
  "dataEmissao": "2026-04-30",
  "valor": 250.00,
  "observacoes": "Emitida pelo portal municipal"
}
```

- `pacienteId` e `dataEmissao` são obrigatórios; o paciente precisa estar ativo (404 caso contrário). `dataEmissao` não pode ser futura (422 caso contrário).
- `competencia` é obrigatória no formato `MM/AAAA` (400 quando fora do formato).
- `numeroNota` é opcional e limitado a 60 caracteres (400 quando excedido); `valor` e `observacoes` são opcionais; quando informado, `valor` deve ser maior que zero (422 caso contrário).
- O registro é idempotente por `(paciente, competência)` inclusive sob concorrência: requisições simultâneas para o mesmo par resolvem para uma única nota (a colisão da constraint única é repetida automaticamente como atualização).

Resposta (`200 OK`):

```json
{
  "id": 10,
  "pacienteId": 1,
  "nomePaciente": "Ana Souza",
  "competencia": "04/2026",
  "numeroNota": "NF-2026-000123",
  "dataEmissao": "2026-04-30",
  "valor": 250.00,
  "observacoes": "Emitida pelo portal municipal",
  "dataCriacao": "2026-05-01T10:00:00",
  "dataAtualizacao": null
}
```

`GET /api/nfse-emitidas/paciente/{pacienteId}` retorna as notas registradas do paciente, da competência mais recente para a mais antiga.

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

> Campos obrigatórios: `nome`. `email` e `cpf` são opcionais (alguns sistemas externos não fornecem esses dados na importação inicial). Quando informado, `email` precisa ter formato válido.

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

### PUT /users/me/preferencias — corpo da requisição

```json
{
  "idioma": "PT_BR",
  "tema": "CLARO",
  "notificacoesEmail": true,
  "notificacoesPush": false
}
```

> Campos obrigatórios: todos. `idioma` aceita `PT_BR`, `EN_US`, `ES_ES`. `tema` aceita `CLARO`, `ESCURO`. Quando o usuário ainda não tem preferências salvas, `GET /users/me/preferencias` retorna os valores padrão (`idioma=PT_BR`, `tema=CLARO`, `notificacoesEmail=true`, `notificacoesPush=false`).

---

## Como rodar

### Opção 1 — Docker Compose (recomendado)

Sobe o banco PostgreSQL e a aplicação juntos, sem instalar nada localmente além do Docker.

O container da aplicação roda com usuário não-root (`app`) e possui `HEALTHCHECK` no `/actuator/health/liveness` (estado `healthy` visível em `docker ps`) — o probe de liveness reflete apenas o estado do processo, então indisponibilidade de banco/SMTP não marca o container como `unhealthy`. A memória do container é limitada por `mem_limit` (padrão `1g`, ajustável via `APP_MEM_LIMIT`) e a heap da JVM usa 75% desse limite (`-XX:MaxRAMPercentage=75.0`, ajustável via `JAVA_OPTS`).

O projeto usa o padrão de **override do Docker Compose** para isolar os ambientes:

| Ambiente | Comando | Volume PostgreSQL | Dados de seed |
|---|---|---|---|
| **Desenvolvimento** | `docker compose up` (auto-carrega `docker-compose.override.yml`) | `postgres_dev_data` | Sim (10 pacientes, 3 profissionais, 5 usuários) |
| **Produção** | `docker compose -f docker-compose.yml -f docker-compose.prod.yml up` | `postgres_prod_data` | Não (apenas admin inicial) |

#### Desenvolvimento

```bash
# Clonar o repositório
git clone <url-do-repositorio>
cd carlessopilatesapi

# Configurar variáveis de ambiente de desenvolvimento
cp .env.example .env.dev

# Subir todos os serviços (perfil dev com seed automático)
docker compose --env-file .env.dev up --build -d

# Acompanhar os logs da aplicação
docker compose logs -f app

# Derrubar os serviços
docker compose down

# Derrubar e remover os dados do banco de desenvolvimento
docker compose down -v
```

#### Produção

```bash
# Configurar variáveis de ambiente de produção (nunca versionar este arquivo)
cp .env.example .env.prod
# Edite .env.prod com credenciais seguras, APP_INITIAL_ADMIN_PASSWORD e as
# variáveis SMTP_*/APP_EMAIL_* (necessárias para o e-mail de recuperação de senha)

# Subir com perfil prod (banco limpo, sem seed)
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up --build -d

# Derrubar e remover os dados do banco de produção
docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v
```

> **Admin inicial de produção:** no perfil `prod`, se não existir nenhum `ADMIN` ativo, a aplicação cria o usuário `admin@carlessopilates.com` (ou `APP_INITIAL_ADMIN_EMAIL`) usando a senha definida em `APP_INITIAL_ADMIN_PASSWORD`. A aplicação falha ao iniciar em produção se essa senha não estiver configurada.

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

## Integração Contínua (CI)

O projeto roda um pipeline no **GitHub Actions** (`.github/workflows/ci.yml`) a cada `push` e `pull_request` para `master` (e sob demanda via `workflow_dispatch`). São três jobs:

| Job | O que faz |
|---|---|
| `build-test` | Compila com JDK 21 (Temurin) e roda toda a suíte de testes com `mvn -B verify` (H2 em memória). Publica os relatórios de teste e o relatório de cobertura JaCoCo como artefatos (`surefire-reports` e `jacoco-report`). |
| `flyway-postgres` | Sobe um PostgreSQL 16 e aplica todas as migrations com `mvn flyway:migrate` + `flyway:validate`. Cobre o gap dos testes, que rodam com Flyway desabilitado. |
| `docker-build` | Builda a imagem a partir do `Dockerfile` multi-stage (sem push para registry). |

Os jobs `flyway-postgres` e `docker-build` dependem de `build-test`. Nenhum segredo de produção é usado no CI.

Para reproduzir localmente os mesmos passos:

```bash
# Build + testes (equivalente ao job build-test)
mvn -B verify

# Validar as migrations contra um PostgreSQL local (equivalente ao job flyway-postgres)
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/carlesso_pilates \
  -Dflyway.user=postgres -Dflyway.password=postgres

# Build da imagem (equivalente ao job docker-build)
docker build -t carlessopilatesapi:ci .
```

> O plugin `flyway-maven-plugin` está configurado no `pom.xml` apontando para `filesystem:src/main/resources/db/migration`; a conexão é passada por linha de comando.

### Cobertura de testes (JaCoCo)

O `mvn verify` mede a cobertura com o **JaCoCo** e falha o build se a cobertura de linhas ficar abaixo do gate mínimo (propriedade `jacoco.line.coverage.minimum` no `pom.xml`, hoje **90%**). DTOs (records sem lógica) e a classe main ficam fora do cálculo.

Para consultar a cobertura:

- **Localmente:** rode `mvn verify` e abra `target/site/jacoco/index.html` no navegador.
- **No CI:** baixe o artefato `jacoco-report` do job `build-test` (aba *Actions* → execução → *Artifacts*) e abra o `index.html`.

O gate existe para impedir regressão da suíte — a intenção é subi-lo gradualmente, não persegui-lo. Para verificar o gate isoladamente reaproveitando a última execução dos testes: `mvn verify -DskipTests`.

### Segurança de dependências

| Recurso | Arquivo | O que faz |
|---|---|---|
| **Dependabot** | `.github/dependabot.yml` | Verifica semanalmente (segunda-feira, 06:00 BRT) atualizações nos ecossistemas `maven` (pom.xml), `github-actions` (workflows) e `docker` (imagens base do Dockerfile), abrindo PRs automaticamente. Atualizações patch/minor são agrupadas em um único PR por ecossistema; majors abrem PRs individuais. |
| **CodeQL** | `.github/workflows/codeql.yml` | Análise estática de segurança (SAST) do código Java a cada `push`/`pull_request` para `master` e semanalmente via agenda. Resultados aparecem em *Security → Code scanning*. |

> Os alertas de vulnerabilidade (*Dependabot alerts*) são habilitados nas configurações do repositório (*Settings → Advanced Security*); o monitoramento dos três ecossistemas pode ser conferido em *Insights → Dependency graph → Dependabot*.

---

## Documentação interativa (Swagger UI)

Com a aplicação rodando, acesse:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

> Documentação visual offline do projeto: [`docs/documentacao.html`](docs/documentacao.html) — página HTML estática com arquitetura, endpoints, regras de negócio e setup. Abra direto no navegador.

---

## Observabilidade (Actuator)

O projeto expõe endpoints operacionais do Spring Boot Actuator para acompanhamento da aplicação em desenvolvimento:

| Recurso | URL |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Liveness | http://localhost:8080/actuator/health/liveness |
| Readiness | http://localhost:8080/actuator/health/readiness |
| Info | http://localhost:8080/actuator/info |

Somente `health` e `info` ficam expostos via HTTP. Os probes de liveness/readiness (`management.endpoint.health.probes.enabled=true`) refletem apenas o estado do processo e são usados pelo `HEALTHCHECK` do container.

---

## Migrações de banco (Flyway)

O projeto utiliza **Flyway** para versionamento e execução automática das migrações. As migrações são divididas em dois diretórios:

- `src/main/resources/db/migration/` — DDL estrutural, aplicado em **todos** os ambientes
- `src/main/resources/db/seed/` — dados de teste, aplicados **apenas** no perfil `dev`

### Migrations estruturais (`db/migration/`)

| Arquivo | Descrição |
|---|---|
| `V1__create_pacientes_table.sql` | Criação da tabela `pacientes` com todos os campos e constraints |
| `V3__create_planos_table.sql` | Criação da tabela `planos` com join table `plano_dias_semana` |
| `V4__create_pagamentos_table.sql` | Criação da tabela `pagamentos` com constraint de unicidade `(plano_id, periodo_inicio)` |
| `V5__create_aulas_table.sql` | Criação da tabela `aulas` com constraint de unicidade `(paciente_id, data)` |
| `V6__create_profissionais_table.sql` | Criação da tabela `profissionais` com tipo de contrato e percentual por aula |
| `V8__alter_pacientes_uf_to_varchar.sql` | Altera coluna `uf` da tabela `pacientes` para `VARCHAR(2)` |
| `V9__alter_profissionais_percentual_precision.sql` | Ajusta precisão do percentual de pagamento por aula para `NUMERIC(5,2)` |
| `V10__add_profissional_to_aulas.sql` | Vincula profissional às aulas realizadas |
| `V11__create_users_table.sql` | Cria tabela `users` para autenticação e autorização |
| `V13__add_indexes_on_foreign_keys.sql` | Adiciona índices para FKs e filtros recorrentes |
| `V14__create_anamneses_table.sql` | Cria tabela `anamneses` vinculada a pacientes |
| `V15__create_avaliacoes_fisioterapeuticas_table.sql` | Cria histórico de avaliações fisioterapêuticas do paciente |
| `V16__create_planos_tratamento_table.sql` | Cria tabela de planos de tratamento do paciente |
| `V17__create_sessoes_pilates_table.sql` | Cria tabela de sessões de Pilates/Fisioterapia |
| `V18__create_evolucoes_sessao_table.sql` | Cria tabela de evoluções de sessão vinculada a sessões |
| `V19__create_reavaliacoes_table.sql` | Cria tabela de reavaliações periódicas vinculada a pacientes, avaliações e planos de tratamento |
| `V20__add_ativo_to_users.sql` | Adiciona coluna `ativo` à tabela `users` |
| `V21__insert_admin_inicial.sql` | Mantém a versão Flyway reservada; o admin inicial de produção é criado pela aplicação com `APP_INITIAL_ADMIN_PASSWORD` |
| `V22__alter_pacientes_email_cpf_nullable.sql` | Torna `email` e `cpf` opcionais (drop NOT NULL e drop das constraints únicas totais) para suportar importação de pacientes de sistemas externos sem esses dados |
| `V23__add_pacientes_email_cpf_partial_unique.sql` | Recria a unicidade como índice **parcial** (`WHERE col IS NOT NULL`) — múltiplos pacientes podem ter `email`/`cpf` nulos, mas valores preenchidos seguem únicos. `PacienteService.cadastrar` também valida e retorna 409 antes de chegar no banco |
| `V24__add_token_version_to_users.sql` | Adiciona coluna `token_version` em `users` para invalidar JWTs anteriores após troca/redefinição de senha |
| `V25__create_preferencias_usuario_table.sql` | Cria tabela `preferencias_usuario` (1:1 com `users`) para idioma, tema e preferências de notificação |
| `V26__create_notas_fiscais_emitidas_table.sql` | Cria tabela `notas_fiscais_emitidas` para persistir a última NFSE emitida por paciente/competência |
| `V27__create_password_reset_tokens_table.sql` | Cria tabela `password_reset_tokens` para o fluxo de recuperação de senha; token salvo apenas como hash SHA-256 |

### Migrations de seed (`db/seed/`) — apenas perfil `dev`

| Arquivo | Descrição |
|---|---|
| `V7__insert_profissionais_teste.sql` | Carga inicial com 3 profissionais de teste |
| `V12__insert_users_perfis_acesso.sql` | Insere 5 usuários de teste com perfis `ADMIN` e `USER` (senha: `senha1234`) |

> A seed antiga de pacientes (`V2__insert_pacientes_teste.sql`) foi removida em favor da importação a partir de sistemas externos via `scripts/import_seufisio.py`. Para zerar um ambiente dev existente que ainda tenha esses pacientes, derrube o volume com `docker compose down -v` e suba novamente.

> Nos testes automatizados o Flyway fica desabilitado (`spring.flyway.enabled=false`), pois o banco H2 é gerenciado pelo Hibernate com `ddl-auto=create-drop`.

---

## Importação de pacientes a partir do seufisio

Para popular o ambiente com a base real de clientes vinda do `seufisio.com.br`, use `scripts/import_seufisio.py`. O script:

1. Consulta `GET /api/cliente?per_page=500` no seufisio com o Bearer token capturado do navegador (DevTools → aba Network → header `authorization` da requisição da listagem).
2. Para cada cliente, busca o detalhe em `GET /api/cliente/{id}`, mapeando para o contrato de `POST /pacientes`.
3. Faz login na API local (`/auth/login`), carrega todos os CPFs já cadastrados (idempotência: re-rodar não duplica) e cria apenas os pacientes novos em lote. Pacientes com `situacao != 2` no seufisio são marcados como inativos via `PATCH /pacientes/{id}/inativar` após o cadastro.

**Pré-requisitos:**

- Banco dev limpo: `docker compose down -v && docker compose --env-file .env.dev up --build -d`.
- API local rodando e usuário `ADMIN` válido (em dev, qualquer um da seed `V12`).
- Python 3.8+ (apenas biblioteca padrão; nenhuma dependência externa).

**Execução:**

Recomendado: criar `scripts/.env` (já ignorado pelo git) com as credenciais e carregá-lo com `set -a; source ...; set +a` para evitar deixar token/senha em `~/.bash_history`:

```bash
# scripts/.env (NUNCA commitar)
SEUFISIO_TOKEN="eyJ0eXAi..."
SEUFISIO_CLINICA_ID="..."
SEUFISIO_VERSION_APP="..."
LOCAL_API_URL="http://localhost:8080"
LOCAL_EMAIL="admin@carlessopilates.com"
LOCAL_PASSWORD="senha1234"
```

```bash
set -a; source scripts/.env; set +a

# Validar mapeamento sem gravar (recomendado antes da carga real)
python3 scripts/import_seufisio.py --dry-run

# Importação de fato (idempotente: pula CPFs já cadastrados)
python3 scripts/import_seufisio.py
```

**Testes do script:**

```bash
cd scripts && python3 -m unittest test_import_seufisio -v
```

> O token JWT do seufisio expira (~2 dias). Se demorar para rodar, capture um novo no DevTools.
>
> **Segurança**: o `.gitignore` está configurado para nunca versionar dumps (`scripts/*.json`, `scripts/*.csv`) nem variáveis locais (`scripts/.env`). Não commitar tokens nem dados de pacientes em hipótese alguma. Logs e `--dry-run` mascaram CPF/e-mail/nome para não vazar PII via stdout/CI.

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_HOST` | `localhost` | Host do banco PostgreSQL |
| `DB_PORT` | `5432` | Porta usada pela aplicação para conectar ao banco |
| `DB_HOST_PORT` | `5432` | Porta publicada no host pelo Docker Compose |
| `DB_NAME` | `carlesso_pilates` | Nome do banco de dados |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |
| `JWT_SECRET` | - | Segredo HMAC obrigatório para assinar JWT; use pelo menos 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` | Expiração do access token em milissegundos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens permitidas para o frontend Angular |
| `APP_INITIAL_ADMIN_EMAIL` | `admin@carlessopilates.com` | E-mail do admin inicial criado no perfil `prod` quando não há `ADMIN` ativo |
| `APP_INITIAL_ADMIN_PASSWORD` | - | Senha obrigatória para bootstrap do admin inicial no perfil `prod` |
| `APP_COBRANCA_CRON_VENCIDOS` | `0 0 6 * * *` | Cron expression do scheduler que marca pagamentos como `VENCIDO` |
| `APP_COBRANCA_CRON_COBRANCAS_FUTURAS` | `0 0 7 * * *` | Cron expression do scheduler que gera cobranças futuras |
| `APP_COBRANCA_VENCIMENTO_DIAS` | `10` | Dias somados ao início do período para definir o vencimento das cobranças geradas |
| `APP_PAGINACAO_TAMANHO_PADRAO` | `10` | Tamanho padrão de página nas listagens paginadas |
| `APP_EMAIL_PROVIDER` | `smtp` | Provedor de e-mail ativo (seleciona o bean `EmailSender` via `EmailConfig`) |
| `APP_EMAIL_FROM` | `no-reply@carlessopilates.com.br` | Remetente usado no envio de e-mails transacionais |
| `APP_EMAIL_RESET_PASSWORD_URL` | `https://app.carlessopilates.com.br/resetar-senha` | URL do frontend para onde o link de redefinição de senha aponta (`?token=...`) |
| `APP_EMAIL_RESET_PASSWORD_TOKEN_TTL_MINUTOS` | `30` | Validade do token de redefinição de senha; a mesma propriedade define o prazo real e o texto exibido no e-mail |
| `SMTP_HOST` | - | Host do servidor SMTP usado pelo `SmtpEmailSender` |
| `SMTP_PORT` | `587` | Porta do servidor SMTP |
| `SMTP_USERNAME` | - | Usuário de autenticação SMTP |
| `SMTP_PASSWORD` | - | Senha de autenticação SMTP |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0` | Flags da JVM no container Docker; o valor **substitui** o padrão por completo — inclua `-XX:MaxRAMPercentage` (ou `-Xmx`) ao customizar |
| `APP_MEM_LIMIT` | `1g` | Limite de memória do container da aplicação no Docker Compose (a heap da JVM usa 75% desse valor) |

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

Usuários iniciais criados pela migration de seed `V12` no perfil `dev` usam a senha `senha1234` e representam os perfis disponíveis:

| E-mail | Perfil |
|---|---|
| `admin@carlessopilates.com` | `ADMIN` |
| `operacional@carlessopilates.com` | `ADMIN` |
| `recepcao@carlessopilates.com` | `USER` |
| `financeiro@carlessopilates.com` | `USER` |
| `consulta@carlessopilates.com` | `USER` |

### Recuperar senha esquecida
```bash
curl -s -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@email.com"}' -w "%{http_code}"

# Token chega por e-mail (link com ?token=...); com o token em mãos:
curl -s -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-recebido-por-email>","novaSenha":"novaSenha123","confirmacaoNovaSenha":"novaSenha123"}' | jq
```

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

### Registrar NFSE emitida
```bash
curl -s -X POST "http://localhost:8080/api/nfse-emitidas" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"pacienteId":1,"competencia":"04/2026","numeroNota":"NF-2026-000123","dataEmissao":"2026-04-30","valor":250.00}' | jq
curl -s "http://localhost:8080/api/nfse-emitidas/paciente/1" \
  -H "Authorization: Bearer $TOKEN" | jq
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
- `NotaAnteriorEmitida` é baseada nas NFSEs emitidas persistidas: `true` quando há nota registrada para o paciente em competência anterior à consultada
- Registros sem nome, CPF/CNPJ, valor positivo ou data de pagamento retornam erro de regra de negócio (`422`)
- As NFSEs emitidas são persistidas por `(paciente, competência)` via `POST /api/nfse-emitidas`; o registro é idempotente (atualiza a nota existente da competência) e exige paciente ativo

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
- O `status` padrão é `AGENDADA`; mudanças de status usam `PATCH /sessoes/{id}/realizar` ou `PATCH /sessoes/{id}/cancelar`
- Transições permitidas: apenas `AGENDADA -> REALIZADA` e `AGENDADA -> CANCELADA`
- `PUT /sessoes/{id}` faz atualização parcial dos dados da sessão, mas não altera `status`
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

### Recuperação de senha (esqueci minha senha)
- `POST /auth/forgot-password` recebe `email` e sempre retorna `200` com resposta genérica, mesmo se o e-mail não existir ou pertencer a um usuário inativo, para evitar enumeração de usuários
- Rate limiting reaproveita o `LoginAttemptService` (5 solicitações por e-mail em 15 minutos); acima do limite retorna `429`. Chaves cujas tentativas já saíram da janela são removidas do mapa em memória tanto ao consultar o limite quanto periodicamente por `LoginAttemptCleanupScheduler` (a cada 15 min), evitando crescimento ilimitado via solicitações não autenticadas
- Ao gerar um novo token, qualquer token anterior ainda válido do mesmo usuário é invalidado — apenas o token da solicitação mais recente funciona
- Token de redefinição: gerado aleatoriamente (32 bytes, Base64 URL-safe), salvo em `password_reset_tokens` **apenas como hash SHA-256** (nunca em texto puro), com expiração configurável (`app.email.reset-password-token-ttl-minutos`, default 30 min — a mesma propriedade usada no texto do e-mail) e uso único
- O e-mail de redefinição é montado a partir do template Thymeleaf `password-reset.html` e enviado de forma assíncrona (`@Async`) via `EmailSender`, sem bloquear a resposta do `forgot-password`
- `POST /auth/reset-password` recebe `token`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`; token inexistente, expirado ou já utilizado, assim como confirmação divergente, retornam `422` com mensagem genérica. A busca do token usa lock pessimista (`@Lock(PESSIMISTIC_WRITE)`) para impedir que duas requisições concorrentes redimam o mesmo token de uso único
- Ao redefinir com sucesso: a senha é armazenada com `BCryptPasswordEncoder` e o `token_version` do usuário é incrementado (invalidando JWTs emitidos antes da redefinição) via `UserService.aplicarNovaSenha`, reaproveitado também por `PUT /users/me/senha` e pelo CRUD administrativo; o token é marcado como usado
- Troca de provedor de e-mail (SMTP → SES, por exemplo) exige apenas uma nova implementação de `EmailSender` e ajuste em `EmailConfig`/`app.email.provider`, sem alterar `PasswordResetService`

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
| `NotaFiscalEmitidaServiceTest` | Unitário (Mockito) | 6 |
| `AppPropertiesTest` | Unitário (ApplicationContextRunner) | 3 |
| `GlobalExceptionHandlerTest` | Unitário | 17 |
| `PacienteServiceIntegrationTest` | JPA (`@DataJpaTest`) | 4 |
| `ProfissionalServiceIntegrationTest` | JPA (`@DataJpaTest`) | 5 |
| `PagamentoServiceAtomicidadeIntegrationTest` | Integração (`@SpringBootTest` + H2) | 1 |
| `CobrancaSchedulerIntegrationTest` | JPA (`@DataJpaTest`) | 11 |
| `AulaRepositoryTest` | JPA (`@DataJpaTest`) | 6 |
| `PagamentoRepositoryTest` | JPA (`@DataJpaTest`) | 1 |
| `NotaFiscalEmitidaRepositoryTest` | JPA (`@DataJpaTest`) | 3 |
| `SessaoPilatesRepositoryTest` | JPA (`@DataJpaTest`) | 4 |
| `PacienteControllerTest` | Controller (`@WebMvcTest`) | 22 |
| `PlanoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `PagamentoControllerTest` | Controller (`@WebMvcTest`) | 11 |
| `AulaControllerTest` | Controller (`@WebMvcTest`) | 10 |
| `AnamneseControllerTest` | Controller (`@WebMvcTest`) | 14 |
| `AvaliacaoFisioterapeuticaControllerTest` | Controller (`@WebMvcTest`) | 12 |
| `PlanoTratamentoControllerTest` | Controller (`@WebMvcTest`) | 18 |
| `SessaoPilatesControllerTest` | Controller (`@WebMvcTest`) | 21 |
| `ProfissionalControllerTest` | Controller (`@WebMvcTest`) | 17 |
| `RelatorioNfseControllerTest` | Controller (`@WebMvcTest`) | 6 |
| `NotaFiscalEmitidaControllerTest` | Controller (`@WebMvcTest`) | 4 |
| `DashboardControllerTest` | Controller (`@WebMvcTest`) | 2 |
| `DashboardServiceTest` | Unitário (Mockito) | 3 |
| `SessaoPilatesServiceTest` | Unitário (Mockito) | 25 |
| `EvolucaoSessaoServiceTest` | Unitário (Mockito) | 10 |
| `EvolucaoSessaoControllerTest` | Controller (`@WebMvcTest`) | 13 |
| `PreferenciasUsuarioServiceTest` | Unitário (Mockito) | 7 |
| `PreferenciasUsuarioControllerTest` | Controller (`@WebMvcTest`) | 6 |
| `PreferenciasUsuarioRepositoryTest` | Repositório (`@DataJpaTest` + H2) | 5 |
| `PasswordResetServiceTest` | Unitário (Mockito) | 12 |
| `LoginAttemptServiceTest` | Unitário (sem mocks) | 6 |
| `AuthControllerTest` | Controller (`@WebMvcTest`) | 9 |
| `PasswordResetIntegrationTest` | Integração (`@SpringBootTest` + MockMvc + H2, `EmailSender` mockado) | 6 |
| `SecurityIntegrationTest` | Integração (`@SpringBootTest` + MockMvc + H2) | 42 |
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
