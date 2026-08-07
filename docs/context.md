# Contexto do Projeto — Carlesso Pilates API

Histórico técnico do projeto: o modelo de dados tabela a tabela e as decisões de design com o porquê de cada
uma. Para stack, estrutura de pacotes, rotas, configuração, execução e testes, veja o
[índice da documentação](README.md) — este arquivo não é mais a fonte desses assuntos.

## Objetivo

API REST para gerenciar pacientes e profissionais de um estúdio de pilates. Permite cadastro, consulta, atualização parcial e inativação (soft delete) de pacientes e profissionais, com gestão de planos de pagamento, cobranças, geração automática de aulas, prontuário clínico com anamnese, avaliação, plano de tratamento, sessões, evoluções e reavaliações periódicas, relatório de pagamento de profissionais e relatório de emissão de NFSEs.

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

## Regras de negócio

Consolidadas em [`regras-de-negocio.md`](regras-de-negocio.md), fonte única do assunto.

---

## Decisões de design

- **Soft delete**: `DELETE` não remove o registro — apenas seta `ativo = false`.
- **Atualização parcial via PUT**: DTOs de update têm todos os campos opcionais; o service só sobrescreve os campos não-nulos.
- **DTOs como records**: todos os DTOs de request e response são Java records.
- **Factory method**: `*ResponseDTO.from(Entity)` centraliza o mapeamento entidade → DTO.
- **Tratamento de erros**: `GlobalExceptionHandler` mapeia exceções customizadas (`ResourceNotFoundException` → 404, `ConflictException` → 409, `BusinessException` → 422) e retorna `{"erro": "..."}`. `IllegalArgumentException` segue como 400 e `DataIntegrityViolationException` como 409. Erros de Bean Validation retornam 400 com `{"erro": "Dados inválidos", "campos": {campo: mensagem}}`; JSON malformado retorna 400 com mensagem neutra. O handler estende `ResponseEntityExceptionHandler`, então as exceções do próprio Spring MVC mantêm status e headers do framework (405 com `Allow`, 415, parâmetro ausente e type mismatch → 400) com o corpo trocado para `{"erro": ...}`. Exceções não mapeadas (e 5xx do framework) retornam mensagem neutra e são logadas com stacktrace; `@ResponseStatus` em exceções customizadas é respeitado. O 403 de autorização por URL é escrito pelo `accessDeniedHandler` do `SecurityConfig` com o mesmo contrato `{"erro": "Acesso negado"}`.
- **DDL via Flyway**: `spring.jpa.hibernate.ddl-auto=validate` — o Flyway gerencia o schema; o Hibernate apenas valida.
- **Transações de leitura**: métodos de consulta nos services usam `@Transactional(readOnly = true)` para evitar flush desnecessário e permitir otimizações de conexão.

---
