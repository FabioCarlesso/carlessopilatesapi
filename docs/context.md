# Contexto do Projeto — Carlesso Pilates API

## Objetivo

API REST para gerenciar pacientes e profissionais de um estúdio de pilates. Permite cadastro, consulta, atualização parcial e inativação (soft delete) de pacientes e profissionais, com gestão de planos de pagamento, cobranças, geração automática de aulas, relatório de pagamento de profissionais e relatório de emissão de NFSEs.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.5 |
| Persistência | Spring Data JPA + Hibernate |
| Segurança | Spring Security + JWT stateless |
| Banco de dados | PostgreSQL 16 |
| Migrações | Flyway |
| Validação | Spring Validation (Bean Validation) |
| Documentação | springdoc-openapi 2.8.3 (Swagger UI) |
| JWT | JJWT 0.12.6 |
| Observabilidade | Spring Boot Actuator |
| Scheduler | Spring Scheduler |
| Build | Maven 3.9 |
| Containerização | Docker + Docker Compose |
| Exportação PDF | OpenPDF 1.3.34 |
| Exportação XLSX | Apache POI 5.4.1 |
| Testes | JUnit 5 + Mockito + MockMvc + H2 (test scope) |

---

## Estrutura de pacotes

```
com.carlesso.pilatesapi
├── config
│   ├── AppProperties.java            — @ConfigurationProperties (cobranca)
│   ├── GlobalExceptionHandler.java   — mapeia exceções customizadas para HTTP (404/409/422)
│   ├── OpenApiConfig.java            — configuração do Swagger/OpenAPI
│   └── SecurityConfig.java           — regras de acesso, CORS e sessão stateless
├── exception
│   ├── ResourceNotFoundException.java — 404 (recurso não encontrado)
│   ├── ConflictException.java         — 409 (conflito de estado/duplicidade)
│   ├── BusinessException.java         — 422 (violação de regra de negócio)
│   └── TooManyRequestsException.java  — 429 (muitas tentativas de login)
├── controller
│   ├── PacienteController.java       — endpoints REST de pacientes
│   ├── ProfissionalController.java   — endpoints REST de profissionais
│   ├── PlanoController.java          — endpoints REST de planos
│   ├── PagamentoController.java      — endpoints REST de pagamentos
│   ├── AulaController.java           — endpoints REST de aulas
│   ├── AnamneseController.java       — endpoints REST de anamneses
│   ├── AvaliacaoFisioterapeuticaController.java — endpoints REST de avaliações fisioterapêuticas
│   ├── AuthController.java           — registro/login com JWT
│   ├── UserController.java           — endpoint do usuário autenticado e CRUD administrativo
│   ├── AdminController.java          — endpoints administrativos
│   ├── RelatorioNfseController.java  — endpoint de relatório de emissão de NFSEs
│   └── DashboardController.java      — endpoint de resumo para o painel inicial
├── service
│   ├── PacienteService.java                    — lógica de negócio de pacientes
│   ├── ProfissionalService.java                — lógica de negócio de profissionais
│   ├── AnamneseService.java                    — lógica de negócio de anamneses
│   ├── AvaliacaoFisioterapeuticaService.java   — lógica de negócio de avaliações fisioterapêuticas
│   ├── PlanoService.java                       — regras de plano e frequência
│   ├── PagamentoService.java                   — cobranças, confirmação, vencimentos
│   ├── AulaService.java                        — geração e controle de aulas
│   ├── RelatorioPagamentoExporterService.java  — exporta o relatório em PDF (OpenPDF) e XLSX (Apache POI)
│   ├── RelatorioNfseService.java               — monta relatório de NFSEs por competência
│   ├── RelatorioNfseExporterService.java       — exporta relatório de NFSEs em CSV e XLSX
│   ├── DashboardService.java                   — consolida contadores e totais para o painel inicial
│   ├── AuthService.java                        — registro/login, emissão de token e rate limiting
│   ├── UserService.java                        — CRUD de usuários e definição de perfis de acesso
│   ├── JwtService.java                         — geração (claims role/userId) e validação de JWT
│   ├── LoginAttemptService.java                — rate limiting in-memory por e-mail (5 tentativas / 15 min)
│   └── CustomUserDetailsService.java           — carregamento de usuários para Spring Security
├── repository
│   ├── PacienteRepository.java       — acesso ao banco via Spring Data JPA
│   ├── ProfissionalRepository.java   — acesso ao banco via Spring Data JPA
│   ├── PlanoRepository.java
│   ├── PagamentoRepository.java
│   ├── AulaRepository.java
│   ├── AnamneseRepository.java
│   ├── AvaliacaoFisioterapeuticaRepository.java
│   └── UserRepository.java
├── entity
│   ├── Paciente.java                 — entidade JPA, tabela `pacientes`
│   ├── Endereco.java                 — @Embeddable, colunas embutidas em `pacientes`
│   ├── Profissional.java             — entidade JPA, tabela `profissionais`
│   ├── Plano.java                    — entidade JPA, tabela `planos`
│   ├── Pagamento.java                — entidade JPA, tabela `pagamentos`
│   ├── Aula.java                     — entidade JPA, tabela `aulas`
│   ├── Anamnese.java                 — entidade JPA, tabela `anamneses`
│   ├── AvaliacaoFisioterapeutica.java — entidade JPA, tabela `avaliacoes_fisioterapeuticas`
│   └── User.java                     — entidade JPA, tabela `users`
├── entity/enums
│   ├── TipoPagamento.java            — MENSAL, TRIMESTRAL, ANUAL
│   ├── TipoContrato.java             — CLT, PJ, AUTONOMO
│   ├── FrequenciaSemanal.java        — UMA_VEZ, DUAS_VEZES, TRES_VEZES
│   ├── StatusPagamento.java          — PENDENTE, PAGO, VENCIDO
│   └── Role.java                     — USER, ADMIN
├── security
│   └── JwtAuthenticationFilter.java  — autentica requisições com Authorization Bearer
├── dto
│   ├── PacienteRequestDTO.java       — payload de criação (record)
│   ├── PacienteUpdateDTO.java        — payload de atualização parcial (record)
│   ├── PacienteResponseDTO.java      — resposta da API (record com factory method `from`)
│   ├── EnderecoDTO.java              — DTO de endereço (record)
│   ├── ProfissionalRequestDTO.java   — payload de criação de profissional (record)
│   ├── ProfissionalUpdateDTO.java    — payload de atualização de profissional (record)
│   ├── ProfissionalResponseDTO.java  — resposta da API de profissional (record)
│   ├── ProfissionalResumoDTO.java             — sub-objeto profissional do relatório
│   ├── PeriodoDTO.java                        — sub-objeto período do relatório
│   ├── ResumoFinanceiroDTO.java               — totais consolidados do relatório
│   ├── PagamentoResumoDTO.java                — resumo agrupado por pagamento
│   ├── ProfissionalPagamentoRelatorioDTO.java — relatório de pagamento do profissional (contrato Angular-friendly)
│   ├── ProfissionalPagamentoAulaDTO.java      — detalhe de aula no relatório
│   ├── RelatorioNfseResponseDTO.java          — item do relatório de emissão de NFSE
│   ├── DashboardResumoDTO.java                — resposta do endpoint de resumo do dashboard (record com sub-records)
│   ├── PlanoRequestDTO.java
│   ├── PlanoResponseDTO.java
│   ├── PagamentoRequestDTO.java
│   ├── PagamentoPagarRequestDTO.java — payload opcional para confirmar pagamento
│   ├── PagamentoResponseDTO.java
│   ├── AulaResponseDTO.java
│   ├── AnamneseRequestDTO.java       — payload de criação de anamnese (record)
│   ├── AnamneseUpdateDTO.java        — payload de atualização de anamnese (record)
│   ├── AnamneseResponseDTO.java      — resposta da API de anamnese (record com factory method `from`)
│   ├── AvaliacaoFisioterapeuticaRequestDTO.java — payload de criação de avaliação fisioterapêutica
│   ├── AvaliacaoFisioterapeuticaUpdateDTO.java  — payload de atualização de avaliação fisioterapêutica
│   ├── AvaliacaoFisioterapeuticaResponseDTO.java — resposta da API de avaliação fisioterapêutica
│   ├── AuthRegisterRequestDTO.java
│   ├── AuthLoginRequestDTO.java
│   ├── AuthResponseDTO.java
│   ├── UserRequestDTO.java
│   ├── UserUpdateDTO.java
│   └── UserResponseDTO.java
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

### Tabela `anamneses`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, UNIQUE, FK → pacientes |
| `queixa_principal` | TEXT | NOT NULL |
| `historico_doencas` | TEXT | — |
| `historico_cirurgias` | TEXT | — |
| `historico_lesoes` | TEXT | — |
| `medicamentos_uso` | TEXT | — |
| `alergias` | TEXT | — |
| `nivel_atividade_fisica` | VARCHAR(50) | — |
| `restricoes_medicas` | TEXT | — |
| `objetivos` | TEXT | NOT NULL |
| `observacoes` | TEXT | — |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@OneToOne` com `Paciente`. Cada paciente possui no máximo uma anamnese principal (constraint `UNIQUE paciente_id`).

### Tabela `avaliacoes_fisioterapeuticas`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → pacientes |
| `data_avaliacao` | DATE | NOT NULL |
| `queixa_funcional` | TEXT | NOT NULL |
| `avaliacao_postural` | TEXT | — |
| `mobilidade_articular` | TEXT | — |
| `forca_muscular` | TEXT | — |
| `flexibilidade` | TEXT | — |
| `equilibrio` | TEXT | — |
| `coordenacao_motora` | TEXT | — |
| `padrao_respiratorio` | TEXT | — |
| `escala_dor` | INTEGER | NOT NULL, CHECK 0..10 |
| `testes_funcionais_realizados` | TEXT | — |
| `diagnostico_fisioterapeutico` | TEXT | NOT NULL |
| `observacoes_gerais` | TEXT | — |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@ManyToOne` com `Paciente`. Um paciente pode possuir múltiplas avaliações para manter histórico clínico.

### Índices

| Índice | Tabela / Coluna | Motivação |
|---|---|---|
| `idx_planos_paciente_id` | `planos(paciente_id)` | FK sem cobertura de índice composto existente |
| `idx_pagamentos_paciente_id` | `pagamentos(paciente_id)` | FK sem cobertura de índice composto existente |
| `idx_aulas_pagamento_id` | `aulas(pagamento_id)` | FK sem cobertura de índice composto existente |
| `idx_aulas_profissional_id` | `aulas(profissional_id)` | FK nullable; filtra aulas vinculadas a um profissional |
| `idx_pagamentos_status` | `pagamentos(status)` | Scheduler diário e relatório NFSE filtram por `PENDENTE`/`VENCIDO`/`PAGO` |
| `idx_pagamentos_data_vencimento` | `pagamentos(data_vencimento)` | Scheduler das 06:00 faz range scan diário nessa coluna |
| `idx_aulas_realizada` | `aulas(realizada)` | Relatório de pagamento de profissional filtra `realizada = true` |
| `idx_avaliacoes_fisioterapeuticas_paciente_id` | `avaliacoes_fisioterapeuticas(paciente_id)` | Listagem de avaliações por paciente |
> **Nota:** colunas `plano_dias_semana(plano_id)`, `pagamentos(plano_id)`, `aulas(paciente_id)` e `anamneses(paciente_id)` **não** possuem índice dedicado porque já são o prefixo esquerdo de índices compostos existentes ou possuem índice automático de constraint `UNIQUE`, que o PostgreSQL pode usar para buscas na coluna isolada.

---

## Endpoints

| Método | Rota | Ação | Retorno |
|---|---|---|---|
| POST | `/auth/register` | Registrar usuário `USER` com senha BCrypt e retornar JWT | 200 / 400 / 409 |
| POST | `/auth/login` | Autenticar e retornar JWT | 200 / 400 / 401 |
| GET | `/users/me` | Consultar usuário autenticado sem expor senha | 200 / 401 |
| POST | `/users` | Criar usuário com role `USER` ou `ADMIN` | 201 / 400 / 401 / 403 / 409 |
| GET | `/users` | Listar usuários paginados sem expor senha | 200 / 401 / 403 |
| GET | `/users/{id}` | Buscar usuário por ID | 200 / 401 / 403 / 404 |
| PUT | `/users/{id}` | Atualizar nome, e-mail, senha e perfil de acesso | 200 / 400 / 401 / 403 / 404 / 409 |
| DELETE | `/users/{id}` | Excluir usuário | 204 / 401 / 403 / 404 |
| GET | `/admin/health` | Health administrativo | 200 / 401 / 403 |
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
| GET | `/profissionais/{id}/relatorio-pagamento?inicio=YYYY-MM-DD&fim=YYYY-MM-DD` | Gerar relatório de pagamento do profissional (JSON) | 200 / 400 / 404 |
| GET | `/profissionais/{id}/relatorio-pagamento/pdf?inicio=YYYY-MM-DD&fim=YYYY-MM-DD` | Exportar relatório em PDF (`application/pdf`, `Content-Disposition: attachment`) | 200 / 400 / 404 |
| GET | `/profissionais/{id}/relatorio-pagamento/xlsx?inicio=YYYY-MM-DD&fim=YYYY-MM-DD` | Exportar relatório em Excel/XLSX (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment`) | 200 / 400 / 404 |
| POST | `/planos` | Criar plano para paciente | 201 / 400 / 404 / 422 |
| GET | `/planos/{id}` | Buscar plano por ID | 200 / 404 |
| GET | `/planos/paciente/{id}` | Listar planos do paciente | 200 |
| GET | `/planos/paciente/{id}/ativo` | Buscar plano ativo | 200 / 204 |
| DELETE | `/planos/{id}` | Inativar plano | 204 / 404 / 409 |
| POST | `/pagamentos` | Criar pagamento (PENDENTE) | 201 / 400 / 404 / 409 / 422 |
| GET | `/pagamentos/{id}` | Buscar pagamento | 200 / 404 |
| GET | `/pagamentos/paciente/{id}` | Listar pagamentos | 200 |
| PATCH | `/pagamentos/{id}/pagar` | Confirmar e gerar aulas; aceita `dataPagamento` opcional no corpo | 200 / 404 / 409 / 422 |
| GET | `/aulas/{id}` | Buscar aula | 200 / 404 |
| GET | `/aulas/paciente/{id}` | Listar aulas do paciente | 200 |
| GET | `/aulas/pagamento/{id}` | Listar aulas do pagamento | 200 |
| PATCH | `/aulas/{id}/realizar?profissionalId={id}` | Marcar como realizada e opcionalmente vincular profissional | 200 / 404 / 409 / 422 |
| GET | `/api/relatorios/nfse?competencia=MM/AAAA&notaAnteriorEmitida={true|false}&formato={JSON|CSV|XLSX}` | Gerar relatório de emissão de NFSEs por competência | 200 / 400 / 422 |
| POST | `/anamneses` | Criar anamnese para um paciente | 201 / 400 / 404 / 409 |
| GET | `/anamneses/{id}` | Buscar anamnese por ID | 200 / 404 |
| GET | `/anamneses/paciente/{pacienteId}` | Buscar anamnese por paciente | 200 / 404 |
| PUT | `/anamneses/{id}` | Atualizar anamnese (atualização parcial) | 200 / 400 / 404 |
| POST | `/avaliacoes-fisioterapeuticas` | Criar avaliação fisioterapêutica para um paciente | 201 / 400 / 404 |
| GET | `/avaliacoes-fisioterapeuticas/{id}` | Buscar avaliação fisioterapêutica por ID | 200 / 404 |
| GET | `/avaliacoes-fisioterapeuticas/paciente/{pacienteId}` | Listar avaliações fisioterapêuticas do paciente | 200 / 404 |
| PUT | `/avaliacoes-fisioterapeuticas/{id}` | Atualizar avaliação fisioterapêutica (atualização parcial) | 200 / 400 / 404 |
| GET | `/dashboard/resumo` | Resumo consolidado para o painel inicial (pacientes, profissionais, pagamentos, aulas) | 200 / 401 |

Campos obrigatórios no cadastro de pacientes: `nome`, `email`, `cpf`.  
Campos obrigatórios no cadastro de profissionais: `nome`, `email`, `cpf`, `tipoContrato`, `percentualPagamentoAula`, `dataInicio`.  
`/auth/**` é público. `/users/me` exige autenticação. O CRUD de `/users` e `/admin/**` exigem role `ADMIN`. As demais rotas de negócio exigem `Authorization: Bearer <token>`.
CPF não pode ser alterado após o cadastro.  
`GET /pacientes` retorna pacientes ativos por padrão e aceita `ativo=false` para consultar inativos.
`GET /profissionais` retorna profissionais ativos por padrão e aceita filtros opcionais por `nome`, `email`, `tipoContrato`, `percentualPagamentoAula` e `ativo=false` para consultar inativos.

---

## Regras de negócio

### Segurança
- Autenticação stateless com Spring Security e JWT
- Senhas são armazenadas com `BCryptPasswordEncoder`
- O segredo JWT vem de `JWT_SECRET`; não há segredo fixo no código
- JWT inclui claims `role` e `userId` — o filtro reconstrói o contexto de segurança sem consulta ao banco por requisição
- Rate limiting de `/auth/login`: 5 tentativas falhas por e-mail em janela de 15 minutos retorna `429 Too Many Requests`
- Admin não pode excluir a própria conta nem alterar o próprio perfil de acesso (`422 Unprocessable Entity`)
- CORS permite o frontend Angular configurado em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`)
- Token ausente, inválido ou expirado em rota protegida retorna `401`; usuário sem `ADMIN` em `/admin/**` retorna `403`

### Pacientes
- Apenas um plano ativo por paciente por vez
- Pacientes inativos não recebem cobranças nem têm aulas geradas
- Consultas de aulas não retornam registros associados a pacientes inativos

### Profissionais
- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- Soft delete mantém o registro no banco
- O relatório de pagamento considera apenas aulas `realizada = true` vinculadas ao profissional, dentro do período informado e associadas a pacientes ativos
- A consulta do relatório de pagamento consolida dados da aula, paciente, pagamento e contagem de aulas por pagamento em um único `JOIN` com `GROUP BY`
- Valor por aula no relatório: `valor do pagamento / quantidade de aulas do pagamento`
- Valor devido ao profissional por aula: `valor por aula * percentualPagamentoAula / 100`
- O relatório retorna um contrato Angular-friendly com sub-objetos `profissional`, `periodo`, `resumo`, `pagamentos`, `aulas` e `geradoEm`. O bloco `pagamentos` agrega aulas pelo `pagamentoId` para facilitar a exibição financeira em UIs.
- O relatório de pagamento é limitado a períodos de até 366 dias e até 5.000 aulas para evitar exportações excessivas em memória.
- A exportação em PDF e XLSX reusa o mesmo cálculo do endpoint JSON. PDF usa OpenPDF; XLSX usa Apache POI (abas `Resumo`, `Pagamentos`, `Aulas`). Os endpoints retornam `Content-Disposition: attachment` com nome `relatorio-pagamento-profissional-{id}-{inicio}-{fim}.{ext}`.

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

### NFSE
- O relatório de emissão de NFSEs considera apenas pagamentos `PAGO`, com `dataPagamento` dentro da competência informada e pacientes ativos
- `competencia` é obrigatória no formato `MM/AAAA`; mês deve estar entre `01` e `12`
- O relatório retorna `Nome`, `CPF/CNPJ`, `ValorPago`, `Competencia`, `DescricaoServico`, `NotaAnteriorEmitida`, `DataPagamento` e `Observacoes`
- `DescricaoServico` é gerada automaticamente como `Aulas de Pilates - Competência MM/AAAA`
- Como o modelo atual não possui entidade de nota fiscal, `NotaAnteriorEmitida` é inferida pela existência de pagamento confirmado anterior para o mesmo paciente
- O filtro opcional `notaAnteriorEmitida` permite retornar apenas registros com ou sem nota anterior inferida
- `formato` aceita `JSON`, `CSV` e `XLSX`; CSV e XLSX retornam `Content-Disposition: attachment` com nome `relatorio-nfse-{MM-AAAA}.{ext}`
- Registros sem nome do paciente, CPF/CNPJ, valor positivo ou data de pagamento retornam `422 Unprocessable Entity`

### Aulas
- Geradas percorrendo dia a dia entre `periodoInicio` e `periodoFim`
- Sem duplicatas: ignora datas onde o paciente já tem aula registrada
- Requer: paciente ativo + pagamento `PAGO`
- Consultas por ID, paciente, pagamento e relatório filtram `paciente.ativo = true`
- Uma aula realizada pode ser vinculada ao profissional que ministrou a aula

### Anamnese
- Cada paciente possui no máximo uma anamnese principal (regra de unicidade por `paciente_id`)
- Criar anamnese para paciente inexistente ou inativo retorna `404`
- Tentar criar segunda anamnese para o mesmo paciente retorna `409`
- Campos obrigatórios: `queixaPrincipal` e `objetivos`
- Consultas e atualizações de anamnese filtram `paciente.ativo = true`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `queixaPrincipal` e `objetivos` não aceitam strings em branco quando enviados
- `dataAtualizacao` é registrada automaticamente em cada atualização

### Avaliação Fisioterapêutica
- Um paciente pode possuir múltiplas avaliações fisioterapêuticas para manter histórico clínico
- Criar avaliação para paciente inexistente ou inativo retorna `404`
- Campos obrigatórios: `dataAvaliacao`, `queixaFuncional`, `escalaDor` e `diagnosticoFisioterapeutico`
- `escalaDor` aceita apenas valores inteiros de 0 a 10
- Consultas por ID e por paciente filtram `paciente.ativo = true`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; campos textuais obrigatórios não aceitam strings em branco quando enviados
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Scheduler (processos automáticos)
| Cron (default) | Ação | Propriedade |
|---|---|---|
| `0 0 6 * * *` | Marca como `VENCIDO` pagamentos `PENDENTE` com `dataVencimento` passada | `app.cobranca.cron-vencidos` |
| `0 0 7 * * *` | Gera cobranças futuras quando faltam ≤ 7 dias para o fim do período atual | `app.cobranca.cron-cobrancas-futuras` |

A quantidade de dias até o vencimento das cobranças geradas é controlada por `app.cobranca.vencimento-dias` (default `10`).

---

## Decisões de design

- **Soft delete**: `DELETE` não remove o registro — apenas seta `ativo = false`.
- **Atualização parcial via PUT**: DTOs de update têm todos os campos opcionais; o service só sobrescreve os campos não-nulos.
- **DTOs como records**: todos os DTOs de request e response são Java records.
- **Factory method**: `*ResponseDTO.from(Entity)` centraliza o mapeamento entidade → DTO.
- **Tratamento de erros**: `GlobalExceptionHandler` mapeia exceções customizadas (`ResourceNotFoundException` → 404, `ConflictException` → 409, `BusinessException` → 422) e retorna `{"erro": "..."}`. `IllegalArgumentException` segue como 400 e `DataIntegrityViolationException` como 409.
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
| `JWT_SECRET` | obrigatório, mínimo recomendado de 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |
| `APP_COBRANCA_CRON_VENCIDOS` | `0 0 6 * * *` |
| `APP_COBRANCA_CRON_COBRANCAS_FUTURAS` | `0 0 7 * * *` |
| `APP_COBRANCA_VENCIMENTO_DIAS` | `10` |
| `APP_PAGINACAO_TAMANHO_PADRAO` | `10` |

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
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}
app.cobranca.cron-vencidos=${APP_COBRANCA_CRON_VENCIDOS:0 0 6 * * *}
app.cobranca.cron-cobrancas-futuras=${APP_COBRANCA_CRON_COBRANCAS_FUTURAS:0 0 7 * * *}
app.cobranca.vencimento-dias=${APP_COBRANCA_VENCIMENTO_DIAS:10}
spring.data.web.pageable.default-page-size=${APP_PAGINACAO_TAMANHO_PADRAO:10}
```

Os valores `app.cobranca.*` são vinculados pela classe `config/AppProperties` (Spring `@ConfigurationProperties`), evitando magic numbers e cron expressions hardcoded em código. A paginação usa a propriedade nativa do Spring Data Web, alimentada pela variável de ambiente `APP_PAGINACAO_TAMANHO_PADRAO`.

---

## Como rodar

### Docker Compose (recomendado)

```bash
cp .env.example .env
docker compose up --build -d
docker compose logs -f app
docker compose down
```

### Local com Maven

```bash
# Requer Java 21 e PostgreSQL rodando
psql -U postgres -c "CREATE DATABASE carlesso_pilates;"
export JWT_SECRET=replace_with_a_secret_with_at_least_32_characters
JAVA_HOME=~/jdk mvn spring-boot:run
```

> No ambiente de desenvolvimento, Java 21 (Temurin) está instalado em `~/jdk`.  
> Sempre usar `JAVA_HOME=~/jdk mvn <goal>` para compilar e rodar localmente.

---

## Testes

| Classe | Tipo | Casos |
|---|---|---|
| `PacienteServiceTest` | Unitário (Mockito, sem Spring) | 12 |
| `ProfissionalServiceTest` | Unitário (Mockito, sem Spring) | 15 |
| `RelatorioPagamentoExporterServiceTest` | Unitário | 3 |
| `RelatorioNfseServiceTest` | Unitário (Mockito, sem Spring) | 5 |
| `RelatorioNfseExporterServiceTest` | Unitário | 3 |
| `PlanoServiceTest` | Unitário (Mockito, sem Spring) | 9 |
| `PagamentoServiceTest` | Unitário (Mockito, sem Spring) | 10 |
| `AulaServiceTest` | Unitário (Mockito, sem Spring) | 14 |
| `PacienteServiceIntegrationTest` | `@DataJpaTest` + H2 | 4 |
| `ProfissionalServiceIntegrationTest` | `@DataJpaTest` + H2 | 5 |
| `CobrancaSchedulerIntegrationTest` | `@DataJpaTest` + H2 | 11 |
| `AulaRepositoryTest` | `@DataJpaTest` + H2 | 6 |
| `PagamentoRepositoryTest` | `@DataJpaTest` + H2 | 2 |
| `PacienteControllerTest` | `@WebMvcTest` + MockMvc | 16 |
| `ProfissionalControllerTest` | `@WebMvcTest` + MockMvc | 17 |
| `PlanoControllerTest` | `@WebMvcTest` + MockMvc | 11 |
| `PagamentoControllerTest` | `@WebMvcTest` + MockMvc | 11 |
| `AulaControllerTest` | `@WebMvcTest` + MockMvc | 10 |
| `RelatorioNfseControllerTest` | `@WebMvcTest` + MockMvc | 6 |
| `AnamneseServiceTest` | Unitário (Mockito, sem Spring) | 17 |
| `AnamneseControllerTest` | `@WebMvcTest` + MockMvc | 14 |
| `AvaliacaoFisioterapeuticaServiceTest` | Unitário (Mockito, sem Spring) | 8 |
| `AvaliacaoFisioterapeuticaControllerTest` | `@WebMvcTest` + MockMvc | 10 |
| `DashboardControllerTest` | `@WebMvcTest` + MockMvc | 2 |
| `DashboardServiceTest` | Unitário (Mockito, sem Spring) | 3 |
| `AppPropertiesTest` | Unitário (ApplicationContextRunner) | 3 |
| `GlobalExceptionHandlerTest` | Unitário | 6 |
| `SecurityIntegrationTest` | `@SpringBootTest` + MockMvc + H2 | 23 |
| `ActuatorTest` | `@SpringBootTest` + H2 | 3 |
| `PilatesApiApplicationTests` | `@SpringBootTest` + H2 | 1 |

```bash
JAVA_HOME=~/jdk mvn test
```

Os testes de integração usam H2 em memória configurado em `src/test/resources/application.properties`.

`CobrancaSchedulerIntegrationTest` usa `@DataJpaTest` + `@Import({PagamentoService.class, AulaService.class, CobrancaScheduler.class})` + `@EnableConfigurationProperties(AppProperties.class)` para testar as duas rotinas agendadas (`atualizarPagamentosVencidos` e `gerarCobrancasFuturas`) com banco H2 real, sem necessidade de subir o contexto completo.

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
