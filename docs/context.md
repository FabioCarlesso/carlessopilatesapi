# Contexto do Projeto — Carlesso Pilates API

## Objetivo

API REST para gerenciar pacientes e profissionais de um estúdio de pilates. Permite cadastro, consulta, atualização parcial e inativação (soft delete) de pacientes e profissionais, com gestão de planos de pagamento, cobranças, geração automática de aulas, prontuário clínico com anamnese, avaliação, plano de tratamento, sessões, evoluções e reavaliações periódicas, relatório de pagamento de profissionais e relatório de emissão de NFSEs.

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
│   ├── EmailConfig.java              — bean do EmailSender ativo, selecionado via @ConditionalOnProperty("app.email.provider") (hoje só `smtp`; troca futura para `ses` não exige mudança no PasswordResetService)
│   ├── GlobalExceptionHandler.java   — mapeia exceções para HTTP (400/403/404/409/422/500), com detalhe de campos na validação
│   ├── OpenApiConfig.java            — configuração do Swagger/OpenAPI
│   └── SecurityConfig.java           — regras de acesso, CORS e sessão stateless
├── email
│   ├── EmailMessage.java             — record (to, subject, htmlBody, textBody)
│   ├── EmailSender.java              — interface de envio, implementada por SmtpEmailSender (portável para outros provedores)
│   ├── EmailTemplateService.java     — interface de geração de EmailMessage a partir de template + variáveis
│   ├── SmtpEmailSender.java          — implementação via JavaMailSender, envio assíncrono (@Async)
│   └── ThymeleafEmailTemplateService.java — implementação via Thymeleaf + template `password-reset.html`
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
│   ├── PlanoTratamentoController.java — endpoints REST de planos de tratamento
│   ├── SessaoPilatesController.java  — endpoints REST de sessões de Pilates/Fisioterapia
│   ├── EvolucaoSessaoController.java — endpoints REST de evoluções de sessão
│   ├── ReavaliacaoController.java     — endpoints REST de reavaliações periódicas
│   ├── AuthController.java           — registro/login com JWT
│   ├── UserController.java           — endpoint do usuário autenticado e CRUD administrativo
│   ├── AdminController.java          — endpoints administrativos
│   ├── RelatorioNfseController.java  — endpoint de relatório de emissão de NFSEs
│   ├── NotaFiscalEmitidaController.java — registro/consulta de NFSEs emitidas por paciente/competência
│   ├── DashboardController.java      — endpoint de resumo para o painel inicial
│   └── PreferenciasUsuarioController.java — endpoints REST das preferências do usuário autenticado
├── service
│   ├── PacienteService.java                    — lógica de negócio de pacientes
│   ├── ProfissionalService.java                — lógica de negócio de profissionais
│   ├── AnamneseService.java                    — lógica de negócio de anamneses
│   ├── AvaliacaoFisioterapeuticaService.java   — lógica de negócio de avaliações fisioterapêuticas
│   ├── PlanoTratamentoService.java             — lógica de negócio de planos de tratamento
│   ├── SessaoPilatesService.java               — lógica de negócio de sessões de Pilates/Fisioterapia
│   ├── EvolucaoSessaoService.java              — lógica de negócio de evoluções de sessão
│   ├── ReavaliacaoService.java                  — lógica de negócio de reavaliações periódicas
│   ├── PlanoService.java                       — regras de plano e frequência
│   ├── PagamentoService.java                   — cobranças, confirmação, vencimentos
│   ├── AulaService.java                        — geração e controle de aulas
│   ├── RelatorioPagamentoExporterService.java  — exporta o relatório em PDF (OpenPDF) e XLSX (Apache POI)
│   ├── RelatorioNfseService.java               — monta relatório de NFSEs por competência (usa NFSEs emitidas persistidas em `notaAnteriorEmitida`)
│   ├── RelatorioNfseExporterService.java       — exporta relatório de NFSEs em CSV e XLSX
│   ├── NotaFiscalEmitidaService.java           — registro idempotente (upsert por paciente/competência) e consulta de NFSEs emitidas
│   ├── DashboardService.java                   — consolida contadores e totais para o painel inicial
│   ├── AuthService.java                        — registro/login, emissão de token e rate limiting
│   ├── UserService.java                        — CRUD de usuários e definição de perfis de acesso
│   ├── JwtService.java                         — geração (claims role/userId) e validação de JWT
│   ├── LoginAttemptService.java                — rate limiting in-memory por chave (e-mail de login ou de recuperação de senha; 5 tentativas / 15 min)
│   ├── CustomUserDetailsService.java           — carregamento de usuários para Spring Security
│   ├── PreferenciasUsuarioService.java         — leitura/atualização das preferências do usuário com defaults centralizados
│   └── PasswordResetService.java               — geração/validação de token de redefinição de senha (hash SHA-256) e redefinição com invalidação de JWTs ativos
├── repository
│   ├── PacienteRepository.java       — acesso ao banco via Spring Data JPA
│   ├── ProfissionalRepository.java   — acesso ao banco via Spring Data JPA
│   ├── PlanoRepository.java
│   ├── PagamentoRepository.java
│   ├── AulaRepository.java
│   ├── AnamneseRepository.java
│   ├── AvaliacaoFisioterapeuticaRepository.java
│   ├── PlanoTratamentoRepository.java
│   ├── SessaoPilatesRepository.java
│   ├── EvolucaoSessaoRepository.java
│   ├── ReavaliacaoRepository.java
│   ├── UserRepository.java
│   ├── PreferenciasUsuarioRepository.java
│   ├── NotaFiscalEmitidaRepository.java
│   └── PasswordResetTokenRepository.java
├── entity
│   ├── Paciente.java                 — entidade JPA, tabela `pacientes`
│   ├── Endereco.java                 — @Embeddable, colunas embutidas em `pacientes`
│   ├── Profissional.java             — entidade JPA, tabela `profissionais`
│   ├── Plano.java                    — entidade JPA, tabela `planos`
│   ├── Pagamento.java                — entidade JPA, tabela `pagamentos`
│   ├── Aula.java                     — entidade JPA, tabela `aulas`
│   ├── Anamnese.java                 — entidade JPA, tabela `anamneses`
│   ├── AvaliacaoFisioterapeutica.java — entidade JPA, tabela `avaliacoes_fisioterapeuticas`
│   ├── PlanoTratamento.java          — entidade JPA, tabela `planos_tratamento`
│   ├── SessaoPilates.java            — entidade JPA, tabela `sessoes_pilates`
│   ├── EvolucaoSessao.java           — entidade JPA, tabela `evolucoes_sessao`
│   ├── Reavaliacao.java              — entidade JPA, tabela `reavaliacoes`
│   ├── User.java                     — entidade JPA, tabela `users`
│   ├── PreferenciasUsuario.java      — entidade JPA, tabela `preferencias_usuario` (1:1 com `users`)
│   ├── NotaFiscalEmitida.java        — entidade JPA, tabela `notas_fiscais_emitidas` (NFSE emitida por paciente/competência)
│   └── PasswordResetToken.java       — entidade JPA, tabela `password_reset_tokens` (token de redefinição de senha, salvo apenas como hash)
├── entity/enums
│   ├── TipoPagamento.java            — MENSAL, TRIMESTRAL, ANUAL
│   ├── TipoContrato.java             — CLT, PJ, AUTONOMO
│   ├── FrequenciaSemanal.java        — UMA_VEZ, DUAS_VEZES, TRES_VEZES
│   ├── StatusPagamento.java          — PENDENTE, PAGO, VENCIDO
│   ├── TipoSessao.java               — PILATES, FISIOTERAPIA
│   ├── StatusSessao.java             — AGENDADA, REALIZADA, CANCELADA
│   ├── Role.java                     — USER, ADMIN
│   ├── IdiomaPreferencia.java        — PT_BR, EN_US, ES_ES (preferência de idioma do usuário)
│   └── TemaPreferencia.java          — CLARO, ESCURO (preferência de tema do usuário)
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
│   ├── NotaFiscalEmitidaRequestDTO.java       — payload de registro de NFSE emitida (record)
│   ├── NotaFiscalEmitidaResponseDTO.java      — resposta da API de NFSE emitida (record com factory `from`)
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
│   ├── PlanoTratamentoRequestDTO.java — payload de criação de plano de tratamento (record)
│   ├── PlanoTratamentoUpdateDTO.java  — payload de atualização de plano de tratamento (record)
│   ├── PlanoTratamentoResponseDTO.java — resposta da API de plano de tratamento (record com factory method `from`)
│   ├── SessaoPilatesRequestDTO.java  — payload de criação de sessão (record)
│   ├── SessaoPilatesUpdateDTO.java   — payload de atualização de sessão (record)
│   ├── SessaoPilatesResponseDTO.java — resposta da API de sessão (record com factory method `from`)
│   ├── ReavaliacaoRequestDTO.java    — payload de criação de reavaliação periódica
│   ├── ReavaliacaoUpdateDTO.java     — payload de atualização de reavaliação periódica
│   ├── ReavaliacaoResponseDTO.java   — resposta da API de reavaliação periódica
│   ├── AuthRegisterRequestDTO.java
│   ├── AuthLoginRequestDTO.java
│   ├── AuthResponseDTO.java
│   ├── UserRequestDTO.java
│   ├── UserUpdateDTO.java
│   ├── UserResponseDTO.java
│   ├── PreferenciasUsuarioRequestDTO.java  — payload de atualização das preferências (idioma, tema, notificações)
│   ├── PreferenciasUsuarioResponseDTO.java — resposta das preferências (record com factory method `from`)
│   ├── ForgotPasswordRequestDTO.java  — payload de "esqueci minha senha" (record: email)
│   └── ResetPasswordRequestDTO.java   — payload de redefinição de senha (record: token, novaSenha, confirmacaoNovaSenha)
├── scheduler
│   ├── CobrancaScheduler.java        — atualiza vencidos e gera cobranças futuras
│   └── LoginAttemptCleanupScheduler.java — remove periodicamente do LoginAttemptService chaves cujas tentativas já saíram da janela, evitando crescimento ilimitado do mapa em memória
└── util
    ├── CompetenciaUtils.java         — validação/parsing/formatação de competências `MM/AAAA` (compartilhado pelo relatório e registro de NFSE)
    └── EmailNormalizer.java          — normalização de e-mail (lowercase, locale-independente) compartilhada entre os services que identificam usuários por e-mail
```

---

## Modelo de dados

### Tabela `pacientes`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `nome` | VARCHAR | NOT NULL |
| `email` | VARCHAR | UNIQUE quando preenchido (índice parcial `WHERE email IS NOT NULL`) |
| `cpf` | VARCHAR | UNIQUE quando preenchido (índice parcial `WHERE cpf IS NOT NULL`) |
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

### Tabela `users`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `name` | VARCHAR | NOT NULL |
| `email` | VARCHAR | NOT NULL, UNIQUE |
| `password` | VARCHAR | NOT NULL, BCrypt |
| `role` | VARCHAR(30) | NOT NULL |
| `ativo` | BOOLEAN | NOT NULL, default `true` |
| `token_version` | BIGINT | NOT NULL, default `0` |

Usuários inativos são preservados no banco, mas não podem autenticar nem usar tokens JWT emitidos antes da inativação. `token_version` é incrementado quando a senha é trocada ou redefinida; tokens com versão anterior deixam de autorizar rotas protegidas.

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
| `ativo` | BOOLEAN | NOT NULL, default `true` |

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

### Tabela `planos_tratamento`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → pacientes |
| `data_inicio` | DATE | NOT NULL |
| `data_fim_prevista` | DATE | — |
| `objetivos_tratamento` | TEXT | NOT NULL |
| `intervencoes_planejadas` | TEXT | — |
| `numero_sessoes_previstas` | INTEGER | — |
| `frequencia_sessoes` | VARCHAR(100) | — |
| `responsavel_tratamento` | VARCHAR(255) | — |
| `observacoes` | TEXT | — |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@ManyToOne` com `Paciente`. Um paciente pode possuir múltiplos planos de tratamento para manter histórico clínico.

### Tabela `sessoes_pilates`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → pacientes |
| `profissional_id` | BIGINT | nullable, FK → profissionais |
| `plano_tratamento_id` | BIGINT | nullable, FK → planos_tratamento |
| `tipo` | VARCHAR(20) | NOT NULL (`PILATES`, `FISIOTERAPIA`) |
| `status` | VARCHAR(20) | NOT NULL, default `AGENDADA` (`AGENDADA`, `REALIZADA`, `CANCELADA`) |
| `data` | DATE | NOT NULL |
| `horario` | TIME | — |
| `local` | VARCHAR(100) | — |
| `duracao_minutos` | INTEGER | — |
| `observacoes` | TEXT | — |
| `evolucao` | TEXT | legado, não exposto no contrato REST |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@ManyToOne` com `Paciente`, `Profissional` (nullable) e `PlanoTratamento` (nullable). Um paciente pode possuir múltiplas sessões para manter histórico clínico.

### Tabela `evolucoes_sessao`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `sessao_id` | BIGINT | NOT NULL, UNIQUE, FK → sessoes_pilates |
| `data_hora_registro` | TIMESTAMP | NOT NULL |
| `exercicios_realizados` | TEXT | — |
| `equipamentos_utilizados` | TEXT | — |
| `cargas_molas` | TEXT | — |
| `dor_antes` | INTEGER | nullable, CHECK 0..10 |
| `dor_depois` | INTEGER | nullable, CHECK 0..10 |
| `resposta_paciente` | TEXT | — |
| `intercorrencias` | TEXT | — |
| `orientacoes` | TEXT | — |
| `observacoes_fisioterapeuta` | TEXT | — |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@OneToOne` com `SessaoPilates`. Cada sessão possui no máximo uma evolução (constraint `UNIQUE sessao_id`).

### Tabela `reavaliacoes`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → pacientes |
| `avaliacao_fisioterapeutica_id` | BIGINT | nullable, FK → avaliacoes_fisioterapeuticas |
| `plano_tratamento_id` | BIGINT | nullable, FK → planos_tratamento |
| `data_reavaliacao` | DATE | NOT NULL |
| `comparativo_avaliacao_anterior` | TEXT | — |
| `evolucao_dor` | TEXT | — |
| `evolucao_forca` | TEXT | — |
| `evolucao_mobilidade` | TEXT | — |
| `evolucao_funcional` | TEXT | — |
| `objetivos_alcancados` | TEXT | — |
| `pontos_atencao` | TEXT | — |
| `ajustes_recomendados` | TEXT | — |
| `observacoes_gerais` | TEXT | — |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@ManyToOne` obrigatório com `Paciente` e relacionamentos opcionais com `AvaliacaoFisioterapeutica` e `PlanoTratamento`. Um paciente pode possuir múltiplas reavaliações periódicas para comparação longitudinal da evolução clínica.

### Tabela `preferencias_usuario`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `user_id` | BIGINT | NOT NULL, UNIQUE, FK → users `ON DELETE CASCADE` |
| `idioma` | VARCHAR(20) | NOT NULL (`PT_BR`, `EN_US`, `ES_ES`) |
| `tema` | VARCHAR(20) | NOT NULL (`CLARO`, `ESCURO`) |
| `notificacoes_email` | BOOLEAN | NOT NULL |
| `notificacoes_push` | BOOLEAN | NOT NULL |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Relacionamento `@OneToOne` com `User` (1:1, owning side em `preferencias_usuario`). Cada usuário possui no máximo um registro de preferências — quando não existe, o GET retorna os valores padrão definidos no service.

### Tabela `notas_fiscais_emitidas`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → pacientes |
| `competencia` | DATE | NOT NULL (primeiro dia do mês; `MM/AAAA` na API) |
| `numero_nota` | VARCHAR(60) | — |
| `data_emissao` | DATE | NOT NULL |
| `valor` | DECIMAL(10,2) | — (quando informado, deve ser > 0) |
| `observacoes` | TEXT | — |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Restrição `UNIQUE (paciente_id, competencia)` garante uma única NFSE emitida por paciente em cada competência (registro idempotente via `POST /api/nfse-emitidas`). É a fonte de verdade do campo `notaAnteriorEmitida` do relatório de NFSE.

### Tabela `password_reset_tokens`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `user_id` | BIGINT | NOT NULL, FK → users `ON DELETE CASCADE` |
| `token_hash` | VARCHAR(64) | NOT NULL, UNIQUE (hash SHA-256 do token; o token em texto puro nunca é persistido) |
| `expires_at` | TIMESTAMP | NOT NULL |
| `used_at` | TIMESTAMP | nullable (marcado no uso; token de uso único) |
| `created_at` | TIMESTAMP | NOT NULL |

Relacionamento `@ManyToOne` com `User`. Cada solicitação de "esqueci minha senha" gera um novo registro; tokens não são reutilizados entre solicitações.

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
| `idx_planos_tratamento_paciente_id` | `planos_tratamento(paciente_id)` | Listagem de planos de tratamento por paciente |
| `idx_sessoes_pilates_paciente_id` | `sessoes_pilates(paciente_id)` | Listagem de sessões por paciente |
| `idx_sessoes_pilates_data` | `sessoes_pilates(data)` | Busca e ordenação de sessões por data |
| `idx_notas_fiscais_emitidas_paciente` | `notas_fiscais_emitidas(paciente_id)` | Listagem de NFSEs por paciente e lookup do relatório de NFSE |
| `idx_password_reset_tokens_user_id` | `password_reset_tokens(user_id)` | Suporte a possíveis consultas administrativas/cleanup por usuário |
> **Nota:** colunas `plano_dias_semana(plano_id)`, `pagamentos(plano_id)`, `aulas(paciente_id)` e `anamneses(paciente_id)` **não** possuem índice dedicado porque já são o prefixo esquerdo de índices compostos existentes ou possuem índice automático de constraint `UNIQUE`, que o PostgreSQL pode usar para buscas na coluna isolada.

---

## Endpoints

| Método | Rota | Ação | Retorno |
|---|---|---|---|
| POST | `/auth/register` | Registrar usuário `USER` com senha BCrypt e retornar JWT | 200 / 400 / 409 |
| POST | `/auth/login` | Autenticar e retornar JWT | 200 / 400 / 401 |
| POST | `/auth/forgot-password` | Solicitar redefinição de senha por e-mail; sempre retorna 200 genérico (evita enumeração de usuários) | 200 / 400 / 429 |
| POST | `/auth/reset-password` | Redefinir senha a partir de um token de redefinição válido, não expirado e ainda não utilizado | 200 / 400 / 422 |
| GET | `/users/me` | Consultar usuário autenticado sem expor senha | 200 / 401 |
| PUT | `/users/me/senha` | Trocar a própria senha (autenticado) informando senha atual, nova senha e confirmação | 204 / 400 / 401 / 422 |
| GET | `/users/me/preferencias` | Consultar preferências do usuário autenticado; sem registro retorna defaults (`PT_BR`/`CLARO`/email=true/push=false) | 200 / 401 |
| PUT | `/users/me/preferencias` | Atualizar preferências do usuário autenticado (idioma, tema, notificações); idioma/tema fora dos valores aceitos retornam 400 | 200 / 400 / 401 / 404 |
| POST | `/users` | Criar usuário com role `USER` ou `ADMIN` | 201 / 400 / 401 / 403 / 409 |
| GET | `/users` | Listar usuários paginados sem expor senha | 200 / 401 / 403 |
| GET | `/users/roles` | Listar roles disponíveis (`value` e `label`) para formulários administrativos | 200 / 401 / 403 |
| GET | `/users/{id}` | Buscar usuário por ID | 200 / 401 / 403 / 404 |
| PUT | `/users/{id}` | Atualizar nome, e-mail, senha e perfil de acesso | 200 / 400 / 401 / 403 / 404 / 409 / 422 |
| DELETE | `/users/{id}` | Inativar usuário (soft delete) | 204 / 401 / 403 / 404 / 422 |
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
| POST | `/api/nfse-emitidas` | Registrar/atualizar NFSE emitida do paciente na competência (upsert) | 200 / 400 / 404 / 422 |
| GET | `/api/nfse-emitidas/paciente/{pacienteId}` | Listar NFSEs emitidas do paciente (competência desc) | 200 / 404 |
| POST | `/anamneses` | Criar anamnese para um paciente | 201 / 400 / 404 / 409 |
| GET | `/anamneses/{id}` | Buscar anamnese por ID | 200 / 404 |
| GET | `/anamneses/paciente/{pacienteId}` | Buscar anamnese por paciente | 200 / 404 |
| PUT | `/anamneses/{id}` | Atualizar anamnese (atualização parcial) | 200 / 400 / 404 |
| POST | `/avaliacoes-fisioterapeuticas` | Criar avaliação fisioterapêutica para um paciente | 201 / 400 / 404 |
| GET | `/avaliacoes-fisioterapeuticas/{id}` | Buscar avaliação fisioterapêutica por ID | 200 / 404 |
| GET | `/avaliacoes-fisioterapeuticas/paciente/{pacienteId}` | Listar avaliações fisioterapêuticas do paciente | 200 / 404 |
| PUT | `/avaliacoes-fisioterapeuticas/{id}` | Atualizar avaliação fisioterapêutica (atualização parcial) | 200 / 400 / 404 |
| POST | `/planos-tratamento` | Criar plano de tratamento para um paciente | 201 / 400 / 404 |
| GET | `/planos-tratamento/{id}` | Buscar plano de tratamento por ID | 200 / 404 |
| GET | `/planos-tratamento/paciente/{pacienteId}` | Listar planos de tratamento do paciente | 200 / 404 |
| PUT | `/planos-tratamento/{id}` | Atualizar plano de tratamento (atualização parcial) | 200 / 400 / 404 |
| DELETE | `/planos-tratamento/{id}` | Inativar plano de tratamento | 204 / 404 |
| POST | `/sessoes` | Registrar sessão de Pilates ou Fisioterapia | 201 / 400 / 404 / 422 |
| GET | `/sessoes/{id}` | Buscar sessão por ID | 200 / 404 |
| GET | `/sessoes/paciente/{pacienteId}` | Listar sessões do paciente | 200 / 404 |
| PUT | `/sessoes/{id}` | Atualizar sessão (atualização parcial, exceto status) | 200 / 400 / 404 |
| PATCH | `/sessoes/{id}/realizar` | Marcar sessão como REALIZADA (apenas a partir de AGENDADA) | 200 / 404 / 422 |
| PATCH | `/sessoes/{id}/cancelar` | Cancelar sessão (apenas a partir de AGENDADA) | 200 / 404 / 422 |
| DELETE | `/sessoes/{id}` | Excluir sessão permanentemente | 204 / 404 |
| GET | `/dashboard/resumo` | Resumo consolidado para o painel inicial (pacientes, profissionais, pagamentos, aulas) | 200 / 401 |
| POST | `/evolucoes-sessao` | Criar evolução para uma sessão | 201 / 400 / 404 / 409 |
| GET | `/evolucoes-sessao/{id}` | Buscar evolução por ID | 200 / 404 |
| GET | `/evolucoes-sessao/sessao/{sessaoId}` | Buscar evolução por sessão | 200 / 404 |
| PUT | `/evolucoes-sessao/{id}` | Atualizar evolução (atualização parcial) | 200 / 400 / 404 |
| POST | `/reavaliacoes` | Criar reavaliação periódica para um paciente | 201 / 400 / 404 / 422 |
| GET | `/reavaliacoes/{id}` | Buscar reavaliação por ID | 200 / 404 |
| GET | `/reavaliacoes/paciente/{pacienteId}` | Listar reavaliações do paciente | 200 / 404 |
| PUT | `/reavaliacoes/{id}` | Atualizar reavaliação (atualização parcial) | 200 / 400 / 404 |

Campos obrigatórios no cadastro de pacientes: `nome`. `email` e `cpf` são opcionais para suportar importação de bases externas (ex.: `scripts/import_seufisio.py`); quando informado, `email` precisa ter formato válido e cada um (`email`/`cpf`) é único quando preenchido — duplicatas retornam 409.  
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
- JWT inclui claims `role`, `userId` e `tokenVersion`; a cada requisição o filtro valida se o usuário ainda existe, está ativo e se a versão do token corresponde à versão atual do usuário antes de reconstruir o contexto de segurança
- Rate limiting de `/auth/login`: 5 tentativas falhas por e-mail em janela de 15 minutos retorna `429 Too Many Requests`
- Admin não pode inativar a própria conta nem alterar o próprio perfil de acesso (`422 Unprocessable Entity`)
- O sistema deve manter ao menos um usuário `ADMIN` ativo: não é permitido inativar nem rebaixar para `USER` o último administrador ativo (`422 Unprocessable Entity`)
- Usuários com `ativo=false` não conseguem fazer login e tokens emitidos antes da inativação deixam de autorizar rotas protegidas
- Usuário autenticado pode trocar a própria senha via `PUT /users/me/senha`: precisa informar `senhaAtual`, `novaSenha` (mínimo 8 caracteres) e `confirmacaoNovaSenha`; senha atual incorreta, confirmação divergente ou reuso da senha atual retornam `422 Unprocessable Entity`; a nova senha é armazenada com `BCryptPasswordEncoder` e tokens emitidos antes da troca passam a retornar `401 Unauthorized`
- CORS permite o frontend Angular configurado em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`)
- Token ausente, inválido ou expirado em rota protegida retorna `401`; usuário sem `ADMIN` em `/admin/**` retorna `403`

### Recuperação de senha (esqueci minha senha)
- `POST /auth/forgot-password` recebe `email` e sempre retorna `200` com resposta genérica, independentemente de o e-mail existir ou pertencer a um usuário ativo, para evitar enumeração de usuários
- Rate limiting reaproveitando `LoginAttemptService` (mesmo limite de login: 5 solicitações por e-mail em 15 minutos); acima do limite retorna `429 Too Many Requests`. Chaves cujas tentativas já saíram da janela são removidas do mapa em memória tanto ao consultar o limite quanto periodicamente por `LoginAttemptCleanupScheduler` (a cada 15 min), evitando crescimento ilimitado de memória via solicitações não autenticadas
- Quando o e-mail pertence a um usuário ativo, qualquer token de redefinição anterior ainda válido é invalidado e um novo token aleatório (32 bytes, Base64 URL-safe) é gerado e salvo em `password_reset_tokens` **apenas como hash SHA-256**, com expiração configurável (`app.email.reset-password-token-ttl-minutos`, default 30 min — a mesma propriedade usada no texto do e-mail, evitando divergência entre o prazo real e o exibido ao usuário); o token em texto puro nunca é persistido, apenas enviado por e-mail. Apenas o token da solicitação mais recente é válido
- O e-mail é montado a partir do template Thymeleaf `password-reset.html` e enviado de forma assíncrona (`@Async`) via `EmailSender`, sem bloquear a resposta do `forgot-password`
- `POST /auth/reset-password` recebe `token`, `novaSenha` (mínimo 8 caracteres) e `confirmacaoNovaSenha`; token inexistente, expirado ou já utilizado retorna `422 Unprocessable Entity` com mensagem genérica ("Token inválido ou expirado"), assim como confirmação de senha divergente. A busca do token usa lock pessimista (`@Lock(PESSIMISTIC_WRITE)`, mesmo padrão de `UserRepository.findByEmailForUpdate`) para impedir que duas requisições concorrentes redimam o mesmo token de uso único
- Ao redefinir com sucesso, a senha é armazenada com `BCryptPasswordEncoder` e o `token_version` do usuário é incrementado (invalidando JWTs emitidos antes da redefinição) através do mesmo `UserService.aplicarNovaSenha` reaproveitado por `PUT /users/me/senha` e pelo CRUD administrativo de usuários; o token de redefinição é marcado como usado (`used_at`), não podendo ser reutilizado
- A troca de provedor de e-mail (SMTP → SES, por exemplo) é feita apenas registrando uma nova implementação de `EmailSender` e ajustando `EmailConfig`/`app.email.provider`, sem alterar `PasswordResetService`

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
- `NotaAnteriorEmitida` é baseada nas NFSEs emitidas persistidas (`notas_fiscais_emitidas`): `true` quando existe nota registrada para o paciente em competência anterior à consultada
- O filtro opcional `notaAnteriorEmitida` permite retornar apenas registros com ou sem nota anterior emitida
- `formato` aceita `JSON`, `CSV` e `XLSX`; CSV e XLSX retornam `Content-Disposition: attachment` com nome `relatorio-nfse-{MM-AAAA}.{ext}`
- Registros sem nome do paciente, CPF/CNPJ, valor positivo ou data de pagamento retornam `422 Unprocessable Entity`
- As NFSEs emitidas são registradas via `POST /api/nfse-emitidas` (upsert por `(paciente, competência)`): paciente precisa estar ativo (`404`), `competencia` no formato `MM/AAAA` (`400`), `numeroNota` no máximo 60 caracteres (`400`), `dataEmissao` não futura (`422`) e `valor`, quando informado, maior que zero (`422`). O upsert é idempotente mesmo sob concorrência: a colisão da constraint única `(paciente, competência)` é repetida automaticamente como atualização
- Timestamps de auditoria (`dataCriacao`/`dataAtualizacao`) das NFSEs emitidas são preenchidos pelos callbacks `@PrePersist`/`@PreUpdate` da entidade, conforme a convenção do projeto

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

### Plano de Tratamento
- Um paciente pode possuir múltiplos planos de tratamento para manter histórico clínico
- Criar plano de tratamento para paciente inexistente ou inativo retorna `404`
- Campos obrigatórios: `pacienteId`, `dataInicio` e `objetivosTratamento`
- `dataFimPrevista`, quando informada, não pode ser anterior a `dataInicio`
- `numeroSessoesPrevistas` aceita apenas valores positivos quando informado
- Consultas por ID e por paciente filtram `plano.ativo = true` e `paciente.ativo = true`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `objetivosTratamento` não aceita strings em branco quando enviado
- Exclusão é lógica: `DELETE /planos-tratamento/{id}` marca `ativo = false` e preserva o histórico clínico
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Sessões de Pilates/Fisioterapia
- Um paciente pode possuir múltiplas sessões para manter histórico clínico
- Criar sessão para paciente inexistente ou inativo retorna `404`
- Campos obrigatórios: `pacienteId`, `tipo` e `data`
- `tipo` aceita `PILATES` ou `FISIOTERAPIA`
- `status` padrão é `AGENDADA`; mudanças de status devem usar `PATCH /sessoes/{id}/realizar` ou `PATCH /sessoes/{id}/cancelar`
- Transições de status permitidas: apenas `AGENDADA -> REALIZADA` e `AGENDADA -> CANCELADA`; sessões `REALIZADA` ou `CANCELADA` não podem mudar de status novamente
- `profissionalId` e `planoTratamentoId` são opcionais; quando informados, o recurso deve existir e estar ativo, e o plano de tratamento deve pertencer ao mesmo `pacienteId` da sessão
- `duracaoMinutos` aceita apenas valores positivos quando informado
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `status` não faz parte do payload de `PUT /sessoes/{id}`
- A evolução clínica estruturada deve ser registrada em `/evolucoes-sessao`; o campo legado `sessoes_pilates.evolucao` não faz parte do contrato REST
- Exclusão é física (DELETE permanente — sem soft delete, pois sessões canceladas por engano devem poder ser removidas) e remove a evolução vinculada quando existir
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Evolução de Sessão
- Cada sessão possui no máximo uma evolução clínica (regra de unicidade por `sessao_id`)
- Criar evolução para sessão inexistente retorna `404`
- Tentar criar segunda evolução para a mesma sessão retorna `409`
- Campos obrigatórios: `sessaoId` e `dataHoraRegistro`
- `dorAntes` e `dorDepois`, quando informados, aceitam apenas valores inteiros de 0 a 10
- Consultas e atualizações de evolução filtram `sessao.paciente.ativo = true`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- Ao excluir uma sessão, a evolução vinculada é removida junto
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Reavaliações
- Um paciente pode possuir múltiplas reavaliações periódicas para comparação com avaliações anteriores e acompanhamento da evolução clínica
- Criar reavaliação para paciente inexistente ou inativo retorna `404`
- Campos obrigatórios: `pacienteId` e `dataReavaliacao`
- `avaliacaoFisioterapeuticaId` e `planoTratamentoId` são opcionais; quando informados, o recurso deve existir, estar ativo quando aplicável e pertencer ao mesmo `pacienteId` da reavaliação
- Consultas por ID e por paciente filtram `paciente.ativo = true`
- Listagem por paciente retorna reavaliações ordenadas por `dataReavaliacao DESC, id DESC`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Preferências do usuário
- Cada usuário possui no máximo um registro de preferências (constraint `UNIQUE user_id`)
- Defaults centralizados no `PreferenciasUsuarioService`: `idioma=PT_BR`, `tema=CLARO`, `notificacoesEmail=true`, `notificacoesPush=false`
- `GET /users/me/preferencias` retorna os defaults quando o usuário ainda não tem registro salvo, sem persistir nada (1 query única via `findByUserEmail`)
- `PUT /users/me/preferencias` cria o registro na primeira chamada e atualiza nas seguintes; protegido contra criação concorrente para o mesmo usuário por lock pessimista em `users` (`UserRepository.findByEmailForUpdate`)
- Campos obrigatórios no PUT: `idioma`, `tema`, `notificacoesEmail`, `notificacoesPush` (Bean Validation `@NotNull`); idioma/tema fora dos enums retornam `400`
- Identificação do dono é feita exclusivamente por `Authentication.getName()`; e-mail é normalizado para lowercase antes da consulta
- A FK `preferencias_usuario.user_id` usa `ON DELETE CASCADE` para que a remoção física de um usuário (cenário de teste e cleanup) leve junto suas preferências
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
- **Tratamento de erros**: `GlobalExceptionHandler` mapeia exceções customizadas (`ResourceNotFoundException` → 404, `ConflictException` → 409, `BusinessException` → 422) e retorna `{"erro": "..."}`. `IllegalArgumentException` segue como 400 e `DataIntegrityViolationException` como 409. Erros de Bean Validation retornam 400 com `{"erro": "Dados inválidos", "campos": {campo: mensagem}}`; JSON malformado retorna 400 com mensagem neutra; exceções não mapeadas retornam 500 com mensagem genérica e são logadas com stacktrace (exceções do próprio Spring MVC preservam o status original — 405, 415, parâmetro ausente → 400).
- **DDL via Flyway**: `spring.jpa.hibernate.ddl-auto=validate` — o Flyway gerencia o schema; o Hibernate apenas valida.
- **Transações de leitura**: métodos de consulta nos services usam `@Transactional(readOnly = true)` para evitar flush desnecessário e permitir otimizações de conexão.

---

## Configuração

### Variáveis de ambiente

| Variável | Padrão |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_HOST_PORT` | `5432` |
| `DB_NAME` | `carlesso_pilates` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `JWT_SECRET` | obrigatório, mínimo recomendado de 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |
| `APP_INITIAL_ADMIN_EMAIL` | `admin@carlessopilates.com` |
| `APP_INITIAL_ADMIN_PASSWORD` | obrigatório em `prod` quando não há `ADMIN` ativo |
| `APP_COBRANCA_CRON_VENCIDOS` | `0 0 6 * * *` |
| `APP_COBRANCA_CRON_COBRANCAS_FUTURAS` | `0 0 7 * * *` |
| `APP_COBRANCA_VENCIMENTO_DIAS` | `10` |
| `APP_PAGINACAO_TAMANHO_PADRAO` | `10` |
| `APP_EMAIL_PROVIDER` | `smtp` |
| `APP_EMAIL_FROM` | `no-reply@carlessopilates.com.br` |
| `APP_EMAIL_RESET_PASSWORD_URL` | `https://app.carlessopilates.com.br/resetar-senha` |
| `APP_EMAIL_RESET_PASSWORD_TOKEN_TTL_MINUTOS` | `30` |
| `SMTP_HOST` | — (obrigatório para envio real de e-mail) |
| `SMTP_PORT` | `587` |
| `SMTP_USERNAME` | — |
| `SMTP_PASSWORD` | — |

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
app.email.provider=${APP_EMAIL_PROVIDER:smtp}
app.email.from=${APP_EMAIL_FROM:no-reply@carlessopilates.com.br}
app.email.reset-password-url=${APP_EMAIL_RESET_PASSWORD_URL:https://app.carlessopilates.com.br/resetar-senha}
app.email.reset-password-token-ttl-minutos=${APP_EMAIL_RESET_PASSWORD_TOKEN_TTL_MINUTOS:30}
spring.mail.host=${SMTP_HOST:}
spring.mail.port=${SMTP_PORT:587}
spring.mail.username=${SMTP_USERNAME:}
spring.mail.password=${SMTP_PASSWORD:}
```

Os valores `app.cobranca.*` são vinculados pela classe `config/AppProperties` (Spring `@ConfigurationProperties`), evitando magic numbers e cron expressions hardcoded em código. A paginação usa a propriedade nativa do Spring Data Web, alimentada pela variável de ambiente `APP_PAGINACAO_TAMANHO_PADRAO`. A mesma propriedade `app.email.reset-password-token-ttl-minutos` define tanto a expiração real do token de redefinição quanto o texto exibido no e-mail, evitando divergência entre os dois. O indicador de saúde do Actuator para `mail` (`management.health.mail.enabled=false`) é desabilitado apenas nos perfis `dev` e nos testes, onde normalmente não há um servidor SMTP real disponível; em produção ele permanece ativo para refletir o estado real da conectividade SMTP em `/actuator/health`.

---

## Como rodar

### Docker Compose (recomendado)

**Desenvolvimento** (carrega `docker-compose.override.yml` automaticamente):
```bash
cp .env.example .env.dev
docker compose --env-file .env.dev up --build -d
docker compose logs -f app
docker compose down
```

**Produção** (banco limpo, sem seed):
```bash
cp .env.example .env.prod
# Edite .env.prod com credenciais seguras e APP_INITIAL_ADMIN_PASSWORD
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

### Local com Maven

```bash
# Requer Java 21 e PostgreSQL rodando
psql -U postgres -c "CREATE DATABASE carlesso_pilates;"
export JWT_SECRET=replace_with_a_secret_with_at_least_32_characters
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
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
| `PagamentoRepositoryTest` | `@DataJpaTest` + H2 | 1 |
| `PacienteControllerTest` | `@WebMvcTest` + MockMvc | 20 |
| `ProfissionalControllerTest` | `@WebMvcTest` + MockMvc | 17 |
| `PlanoControllerTest` | `@WebMvcTest` + MockMvc | 11 |
| `PagamentoControllerTest` | `@WebMvcTest` + MockMvc | 11 |
| `AulaControllerTest` | `@WebMvcTest` + MockMvc | 10 |
| `RelatorioNfseControllerTest` | `@WebMvcTest` + MockMvc | 6 |
| `NotaFiscalEmitidaServiceTest` | Unitário (Mockito, sem Spring) | 6 |
| `NotaFiscalEmitidaControllerTest` | `@WebMvcTest` + MockMvc | 4 |
| `NotaFiscalEmitidaRepositoryTest` | `@DataJpaTest` + H2 | 3 |
| `AnamneseServiceTest` | Unitário (Mockito, sem Spring) | 17 |
| `AnamneseControllerTest` | `@WebMvcTest` + MockMvc | 14 |
| `AvaliacaoFisioterapeuticaServiceTest` | Unitário (Mockito, sem Spring) | 8 |
| `AvaliacaoFisioterapeuticaControllerTest` | `@WebMvcTest` + MockMvc | 12 |
| `PlanoTratamentoServiceTest` | Unitário (Mockito, sem Spring) | 13 |
| `PlanoTratamentoControllerTest` | `@WebMvcTest` + MockMvc | 18 |
| `SessaoPilatesServiceTest` | Unitário (Mockito, sem Spring) | 25 |
| `SessaoPilatesControllerTest` | `@WebMvcTest` + MockMvc | 21 |
| `SessaoPilatesRepositoryTest` | `@DataJpaTest` + H2 | 4 |
| `DashboardControllerTest` | `@WebMvcTest` + MockMvc | 2 |
| `DashboardServiceTest` | Unitário (Mockito, sem Spring) | 3 |
| `AppPropertiesTest` | Unitário (ApplicationContextRunner) | 3 |
| `GlobalExceptionHandlerTest` | Unitário | 13 |
| `EvolucaoSessaoServiceTest` | Unitário (Mockito, sem Spring) | 10 |
| `EvolucaoSessaoControllerTest` | `@WebMvcTest` + MockMvc | 13 |
| `ReavaliacaoServiceTest` | Unitário (Mockito, sem Spring) | 14 |
| `ReavaliacaoControllerTest` | `@WebMvcTest` + MockMvc | 9 |
| `UserServiceTest` | Unitário (Mockito, sem Spring) | 23 |
| `UserControllerTest` | `@WebMvcTest` + MockMvc | 8 |
| `PreferenciasUsuarioServiceTest` | Unitário (Mockito, sem Spring) | 7 |
| `PreferenciasUsuarioControllerTest` | `@WebMvcTest` + MockMvc | 6 |
| `PreferenciasUsuarioRepositoryTest` | `@DataJpaTest` + H2 | 5 |
| `PasswordResetServiceTest` | Unitário (Mockito, sem Spring) | 12 |
| `LoginAttemptServiceTest` | Unitário (sem mocks) | 6 |
| `AuthControllerTest` | `@WebMvcTest` + MockMvc | 9 |
| `PasswordResetIntegrationTest` | `@SpringBootTest` + MockMvc + H2 (EmailSender mockado) | 6 |
| `SecurityIntegrationTest` | `@SpringBootTest` + MockMvc + H2 | 42 |
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

### Docker Compose — padrão override

O projeto usa três arquivos Docker Compose para isolamento de ambientes:

| Arquivo | Função |
|---|---|
| `docker-compose.yml` | Base comum: serviços `db` e `app` com configurações compartilhadas |
| `docker-compose.override.yml` | Override de **desenvolvimento** — carregado automaticamente por `docker compose up` |
| `docker-compose.prod.yml` | Override de **produção** — carregado explicitamente com `-f docker-compose.prod.yml` |

| Serviço | Imagem | Porta |
|---|---|---|
| `db` | `postgres:16-alpine` | 5432 |
| `app` | build local | 8080 |

O serviço `app` aguarda o `db` estar saudável via healthcheck (`pg_isready`) antes de iniciar.

| Ambiente | Volume PostgreSQL | Perfil Spring | Seed |
|---|---|---|---|
| dev | `postgres_dev_data` | `dev` | `db/migration` + `db/seed` |
| prod | `postgres_prod_data` | `prod` | `db/migration` apenas |

### Migrações Flyway — separação por ambiente

```
src/main/resources/db/
├── migration/   ← DDL estrutural — aplicado em todos os ambientes
└── seed/        ← dados de teste — aplicado apenas no perfil dev
```

Perfil `dev` (`application-dev.properties`): `spring.flyway.locations=classpath:db/migration,classpath:db/seed`
Perfil `prod` (`application-prod.properties`): `spring.flyway.locations=classpath:db/migration`

No perfil `prod`, se não existir nenhum `ADMIN` ativo, a aplicação cria o admin inicial usando `APP_INITIAL_ADMIN_EMAIL` e `APP_INITIAL_ADMIN_PASSWORD`; sem senha configurada, o startup falha. Em dev, o seed `V12` cobre os usuários de teste.
