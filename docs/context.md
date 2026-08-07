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
- Consultas por ID, paciente, pagamento, período e relatório filtram `paciente.ativo = true`
- Uma aula realizada pode ser vinculada ao profissional que ministrou a aula
- `AulaResponseDTO` expõe `profissionalId` e `profissionalNome`, `null` enquanto a aula não tem profissional vinculado
- `GET /aulas?inicio=&fim=` é a agenda do estúdio: período fechado e obrigatório (`400` se ausente, invertido, acima de 92 dias ou com mais de 5000 registros no resultado), sem paginação, ordenado por `data` e `id`; filtros opcionais `profissionalId`, `pacienteId` e `realizada` são combináveis. A consulta projeta direto no DTO para não arrastar a cadeia EAGER `pagamento → plano → diasSemana`

### Anamnese
- Cada paciente possui no máximo uma anamnese principal (regra de unicidade por `paciente_id`)
- Criar anamnese para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Tentar criar segunda anamnese para o mesmo paciente retorna `409`
- Campos obrigatórios: `queixaPrincipal` e `objetivos`
- Consultas e atualizações de anamnese não filtram por `paciente.ativo`: o histórico de paciente inativo continua legível e editável (ver *Leitura do histórico clínico de paciente inativo*)
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `queixaPrincipal` e `objetivos` não aceitam strings em branco quando enviados
- `dataAtualizacao` é registrada automaticamente em cada atualização

### Avaliação Fisioterapêutica
- Um paciente pode possuir múltiplas avaliações fisioterapêuticas para manter histórico clínico
- Criar avaliação para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `dataAvaliacao`, `queixaFuncional`, `escalaDor` e `diagnosticoFisioterapeutico`
- `escalaDor` aceita apenas valores inteiros de 0 a 10
- Consultas por ID e por paciente não filtram por `paciente.ativo`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; campos textuais obrigatórios não aceitam strings em branco quando enviados
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Plano de Tratamento
- Um paciente pode possuir múltiplos planos de tratamento para manter histórico clínico
- Criar plano de tratamento para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId`, `dataInicio` e `objetivosTratamento`
- `dataFimPrevista`, quando informada, não pode ser anterior a `dataInicio`
- `numeroSessoesPrevistas` aceita apenas valores positivos quando informado
- Consultas por ID e por paciente filtram `plano.ativo = true` (soft delete do próprio plano), mas não por `paciente.ativo`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `objetivosTratamento` não aceita strings em branco quando enviado
- Exclusão é lógica: `DELETE /planos-tratamento/{id}` marca `ativo = false` e preserva o histórico clínico
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Sessões de Pilates/Fisioterapia
- Um paciente pode possuir múltiplas sessões para manter histórico clínico
- Criar sessão para paciente inexistente retorna `404`; para paciente inativo retorna `422`
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
- `GET /sessoes?inicio=&fim=` é a agenda do estúdio: período fechado e obrigatório (`400` se ausente, invertido, acima de 92 dias ou com mais de 5000 registros no resultado), sem paginação, ordenado por `data`, `horario` e `id` — sessões sem `horario` no fim do dia; filtros opcionais `profissionalId`, `pacienteId`, `tipo` e `status` são combináveis. Diferente das aulas, não filtra `paciente.ativo`: sessão é registro clínico e o histórico do ex-aluno continua visível

### Evolução de Sessão
- Cada sessão possui no máximo uma evolução clínica (regra de unicidade por `sessao_id`)
- Criar evolução para sessão inexistente retorna `404`; para sessão de paciente inativo retorna `422`
- Tentar criar segunda evolução para a mesma sessão retorna `409`
- Campos obrigatórios: `sessaoId` e `dataHoraRegistro`
- `dorAntes` e `dorDepois`, quando informados, aceitam apenas valores inteiros de 0 a 10
- Consultas e atualizações de evolução não filtram por `sessao.paciente.ativo`
- `GET /evolucoes-sessao/paciente/{pacienteId}` devolve o histórico completo do paciente em uma única chamada, ordenado por `sessao.data DESC`, `sessao.horario DESC NULLS LAST`, `sessao.id DESC`
- Paciente existente sem nenhuma evolução recebe `200` com lista vazia; apenas paciente inexistente retorna `404`
- **A listagem não é paginada — decisão tomada na #158**, com base na medição da base real em 2026-08-05: 90 pacientes com evolução (de 112 cadastrados), 5.697 evoluções no total; apenas 2 pacientes acima de 300 e 76 abaixo de 100 (mediana 35, média 63, máximo 384). A maior resposta é de 332 KB crus, que caem para **50 KB sob gzip** (razão de 6,5× a 8×). Paginar quebraria o contrato e arrastaria junto endpoint de série e filtros server-side, para economizar dezenas de KB
- O custo dominante da tela é DOM, não rede: renderizar 384 evoluções custa ~1 s a mais que renderizar 4 (2,2 s contra 1,2 s até o DOM estabilizar, em `ng serve`; 4.658 elementos no `<main>`). A solução é virtual scroll no frontend, que não exige mudança de contrato
- **Gatilho para revisitar a paginação** — basta um destes: (1) algum paciente ultrapassar **1.000 evoluções** (no ritmo atual de ~90 evoluções/ano dos pacientes mais assíduos, são ~7 anos); (2) a maior resposta gzipada passar de **150 KB**; (3) a aplicação passar a atender **mais de uma clínica**. Enquanto nenhum ocorrer, a listagem completa em uma chamada é a opção de menor atrito
- A decisão pressupõe a compressão gzip ativa — habilitada na #157 (`server.compression.*`), com 329.192 → 41.316 bytes medidos; sem ela a maior resposta volta a trafegar 321 KB crus
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- Ao excluir uma sessão, a evolução vinculada é removida junto
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Reavaliações
- Um paciente pode possuir múltiplas reavaliações periódicas para comparação com avaliações anteriores e acompanhamento da evolução clínica
- Criar reavaliação para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId` e `dataReavaliacao`
- `avaliacaoFisioterapeuticaId` e `planoTratamentoId` são opcionais; quando informados, o recurso deve existir, estar ativo quando aplicável e pertencer ao mesmo `pacienteId` da reavaliação
- Consultas por ID e por paciente não filtram por `paciente.ativo`
- Listagem por paciente retorna reavaliações ordenadas por `dataReavaliacao DESC, id DESC`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Leitura do histórico clínico de paciente inativo
- `ativo = false` em paciente significa **ex-aluno**, não registro apagado: todo o prontuário dele continua consultável
- As consultas por paciente (`/sessoes`, `/anamneses`, `/avaliacoes-fisioterapeuticas`, `/planos-tratamento`, `/reavaliacoes`, `/evolucoes-sessao`, `/api/nfse-emitidas`) validam apenas a **existência** do paciente (`existsById`); `404 Paciente não encontrado` significa que o id não existe
- Registro ausente para paciente existente usa mensagem própria (ex.: `Anamnese não encontrada para o paciente: {id}`), nunca `Paciente não encontrado`
- **Criar** novo registro clínico continua exigindo paciente ativo, e os oito pontos de criação respondem igual: `404` quando o paciente não existe, `422` quando existe mas está inativo. A regra fica em `util/PacienteGuard.exigirAtivo`, chamada por sessão, anamnese, avaliação, plano de tratamento, reavaliação, NFSE, evolução e análise postural
- **Atualizar** registro já existente de paciente inativo é permitido — consequência direta de o registro voltar a ser visível
- Os filtros por `paciente.ativo` permanecem nas consultas **financeiras/operacionais** (aulas, dashboard, relatórios de pagamento e de NFSE), onde excluir ex-alunos é intencional

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
- **Tratamento de erros**: `GlobalExceptionHandler` mapeia exceções customizadas (`ResourceNotFoundException` → 404, `ConflictException` → 409, `BusinessException` → 422) e retorna `{"erro": "..."}`. `IllegalArgumentException` segue como 400 e `DataIntegrityViolationException` como 409. Erros de Bean Validation retornam 400 com `{"erro": "Dados inválidos", "campos": {campo: mensagem}}`; JSON malformado retorna 400 com mensagem neutra. O handler estende `ResponseEntityExceptionHandler`, então as exceções do próprio Spring MVC mantêm status e headers do framework (405 com `Allow`, 415, parâmetro ausente e type mismatch → 400) com o corpo trocado para `{"erro": ...}`. Exceções não mapeadas (e 5xx do framework) retornam mensagem neutra e são logadas com stacktrace; `@ResponseStatus` em exceções customizadas é respeitado. O 403 de autorização por URL é escrito pelo `accessDeniedHandler` do `SecurityConfig` com o mesmo contrato `{"erro": "Acesso negado"}`.
- **DDL via Flyway**: `spring.jpa.hibernate.ddl-auto=validate` — o Flyway gerencia o schema; o Hibernate apenas valida.
- **Transações de leitura**: métodos de consulta nos services usam `@Transactional(readOnly = true)` para evitar flush desnecessário e permitir otimizações de conexão.

---
