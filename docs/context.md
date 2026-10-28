# Contexto do Projeto — Carlesso Pilates API

Histórico técnico do projeto: o modelo de dados tabela a tabela e as decisões de design com o porquê de cada
uma. Para stack, estrutura de pacotes, rotas, configuração, execução e testes, veja o
[índice da documentação](README.md) — este arquivo não é mais a fonte desses assuntos.

## Objetivo

API REST para gerenciar pacientes e profissionais de um estúdio de pilates. Permite cadastro, consulta, atualização parcial e inativação (soft delete) de pacientes e profissionais, com gestão de planos de pagamento, cobranças, geração automática de aulas, prontuário clínico com anamnese, avaliação, plano de tratamento, sessões, evoluções e reavaliações periódicas, relatório de pagamento de profissionais e relatório de emissão de NFSEs.

---

## Modelo de dados

> As tabelas de negócio têm também as colunas de auditoria `data_criacao`, `data_atualizacao`, `criado_por` e
> `alterado_por` (`V43`, #109); as tabelas abaixo só as listam onde já existiam antes. Ver
> [`regras-de-negocio.md`](regras-de-negocio.md#auditoria-de-criação-e-alteração).

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
| `data_cadastro` | DATE | Preenchida no cadastro; nula para pacientes anteriores à `V41` |
| `data_inativacao` | DATE | Preenchida ao inativar, limpa ao reativar; nula para inativações anteriores à `V41` |
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
| `horario` | TIME | nullable — horário das aulas em todos os dias do plano |

Join table `plano_dias_semana`: `plano_id` + `dia_semana` (MONDAY, TUESDAY…)

### Tabela `users`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `name` | VARCHAR | NOT NULL |
| `email` | VARCHAR | NOT NULL, UNIQUE |
| `password` | VARCHAR | NOT NULL, BCrypt |
| `ativo` | BOOLEAN | NOT NULL, default `true` |
| `token_version` | BIGINT | NOT NULL, default `0` |

Usuários inativos são preservados no banco, mas não podem autenticar nem usar tokens JWT emitidos antes da inativação. `token_version` é incrementado quando a senha é trocada ou redefinida; access tokens com versão anterior deixam de autorizar rotas protegidas e refresh tokens com versão anterior deixam de renovar a sessão.

### Tabela `user_roles`

| Campo | Tipo | Restrição |
|---|---|---|
| `user_id` | BIGINT | FK → `users.id`, `ON DELETE CASCADE` |
| `role` | VARCHAR(30) | NOT NULL — `ADMIN`, `PROFISSIONAL`, `RECEPCAO`, `FINANCEIRO` ou `PACIENTE` (reservado) |

PK composta `(user_id, role)`. Um usuário tem um ou mais papéis e acessa a soma das áreas deles; mapeada como `@ElementCollection` em `User.roles`.

### Tabela `pagamentos`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK |
| `paciente_id` | BIGINT | NOT NULL, FK |
| `plano_id` | BIGINT | NOT NULL, FK |
| `valor` | DECIMAL(10,2) | NOT NULL |
| `status` | VARCHAR(20) | NOT NULL |
| `data_pagamento` | DATE | nullable |
| `forma_pagamento` | VARCHAR(20) | nullable (enum `FormaPagamento`, gravada na confirmação) |
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
| `horario` | TIME | nullable — vazio em aulas anteriores à #106 e nas de plano sem horário |
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

### Tabela `refresh_tokens`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `user_id` | BIGINT | NOT NULL, FK → users `ON DELETE CASCADE` |
| `token_hash` | VARCHAR(64) | NOT NULL, UNIQUE (hash SHA-256; o token em texto puro nunca é persistido) |
| `family_id` | UUID | NOT NULL (cadeia de rotações nascida em um mesmo login) |
| `token_version` | BIGINT | NOT NULL (versão do usuário na emissão) |
| `expires_at` | TIMESTAMP | NOT NULL (nunca além de `family_expires_at`) |
| `family_expires_at` | TIMESTAMP | NOT NULL (teto absoluto da sessão, fixado no login e herdado pela cadeia) |
| `used_at` | TIMESTAMP | nullable (marcado na rotação) |
| `revoked_at` | TIMESTAMP | nullable (logout, reuso detectado, troca de senha ou inativação) |
| `created_at` | TIMESTAMP | NOT NULL |

Relacionamento `@ManyToOne` com `User`. Cada rotação gera um registro novo na mesma família, em vez de atualizar o hash no lugar: é a linha antiga, com `used_at` preenchido, que permite reconhecer o reuso de um token já rotacionado e revogar a família.

### Tabela `bloqueios_agenda`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `data_inicio` | DATE | NOT NULL |
| `data_fim` | DATE | NOT NULL (inclusiva; igual a `data_inicio` num feriado de um dia) |
| `hora_inicio` | TIME | — (nula junto com `hora_fim` = dia inteiro) |
| `hora_fim` | TIME | — |
| `motivo` | VARCHAR(200) | NOT NULL |
| `data_criacao` | TIMESTAMP | NOT NULL |
| `data_atualizacao` | TIMESTAMP | — |

Tabela isolada: sem FK para nenhuma outra entidade e sem constraint impedindo sobreposição. O bloqueio é **informativo** — não participa da geração de aulas nem do agendamento de sessões (decisão da #171) —, então nada o referencia e registrar "feriado" e "manutenção" no mesmo dia é caso de uso, não erro. `hora_inicio`/`hora_fim` já nascem no modelo mesmo sem efeito prático, para que a chegada do horário na aula (#106) não exija nova migration.

### Tabela `lista_espera`

| Campo | Tipo | Restrição |
|---|---|---|
| `id` | BIGINT | PK, auto-increment |
| `paciente_id` | BIGINT | NOT NULL, FK → `pacientes` |
| `dia_semana` | VARCHAR(20) | NOT NULL (`DayOfWeek` como string) |
| `hora_inicio` | TIME | NOT NULL |
| `hora_fim` | TIME | NOT NULL (posterior a `hora_inicio`) |
| `data_entrada` | TIMESTAMP | NOT NULL, definida pelo servidor |
| `observacao` | VARCHAR(500) | — |
| `ativo` | BOOLEAN | NOT NULL, default `true` |

A unicidade de `(paciente_id, dia_semana, hora_inicio, hora_fim)` entre as linhas ativas vive no índice parcial da `V37`, não na entidade: JPA não expressa predicado em `@UniqueConstraint`, e uma constraint total quebraria a reentrada na fila depois da remoção lógica.

`data_entrada` é `TIMESTAMP` e não `DATE` porque é a **única** fonte de ordem da fila: duas inscrições no mesmo dia precisam de desempate (`id` fecha o empate restante). Ela nunca é lida do corpo da requisição — o `ListaEsperaRequestDTO` sequer tem o campo —, senão o cliente escolheria a própria posição. A saída da fila é lógica (`ativo = false`) e não física, para preservar o registro de quem foi chamado a partir da lista; o contrato HTTP continua sendo `DELETE`, como pediu o frontend (#172).

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
| `idx_refresh_tokens_user_id` | `refresh_tokens(user_id)` | FK; cobre o `ON DELETE CASCADE` e consultas por usuário |
| `idx_refresh_tokens_family_id` | `refresh_tokens(family_id)` | Revogação da família inteira (reuso, logout, troca de senha) |
| `idx_refresh_tokens_family_expires_at` | `refresh_tokens(family_expires_at)` | Limpeza diária das sessões vencidas pelo teto absoluto |
| `idx_bloqueios_agenda_periodo` | `bloqueios_agenda(data_inicio, data_fim)` | Única consulta da tabela: interseção com o período de `GET /bloqueios` |
| `idx_lista_espera_dia_hora` | `lista_espera(dia_semana, hora_inicio)` | Filtro de `GET /lista-espera`: dia da semana + interseção da faixa horária |
| `idx_lista_espera_paciente` | `lista_espera(paciente_id)` | FK usada pelo filtro `pacienteId` e pelo join que recorta pacientes inativos |
| `lista_espera_ativa_uidx` | `lista_espera(paciente_id, dia_semana, hora_inicio, hora_fim) WHERE ativo` | Único **parcial**: garante o `409` de duplicidade sob concorrência, que a checagem do service sozinha não sustenta. O predicado permite reentrar na fila após a remoção lógica |
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
- **Tratamento de erros**: toda resposta de erro é `application/problem+json` (RFC 9457, `ProblemDetail` do Spring 6), com `type` estável do catálogo `TipoProblema`, `timestamp` e, em validação, a lista `campos` de `{campo, mensagem}` — o detalhe está em [`arquitetura.md`](arquitetura.md#tratamento-de-erros). Substituiu o corpo ad-hoc `{"erro": "..."}` (#99), que obrigava o frontend a interpretar mensagens em português para reagir a um erro. A troca foi feita sem período de convivência com o formato antigo: o `extrairMensagemErro` do frontend já lia `detail` antes de `erro`, então as mensagens continuam chegando ao usuário e ninguém lia `campos`. O `GlobalExceptionHandler` segue estendendo `ResponseEntityExceptionHandler` para preservar status e headers das exceções do Spring MVC; os 401/403 da autorização por URL são escritos pelo `ProblemaHttpWriter` no `SecurityConfig`, e o `/error` (firewall, exceção em filtro) é servido pelo `ProblemaErrorController` no lugar do `BasicErrorController` — antes o 401 saía com o corpo padrão do `/error` do Spring Boot. `@ResponseStatus` em exceções customizadas é respeitado, e 5xx nunca vazam detalhe interno.
- **Refresh token opaco e rotativo, não JWT**: o refresh precisa ser revogável (logout, reuso, troca de senha), e revogar exige estado no servidor de qualquer jeito; um token opaco com hash no banco dá isso sem um segundo segredo de assinatura. A rotação com família segue a recomendação do OAuth 2.0 Security BCP para clientes públicos (SPA): o reuso de um token já rotacionado denuncia o vazamento e derruba a cadeia. O access token continua JWT stateless e curto — revogá-lo exigiria consultar o banco a cada requisição, que é justamente o que o par curto/longo evita. A detecção de reuso tem uma tolerância de segundos para não derrubar sessões por retentativa de rede, e cada família tem teto absoluto para que a rotação não estenda uma sessão roubada indefinidamente.
- **Credencial dedicada ao scrape de métricas**: com o access token de 15 min, um JWT fixo no `prometheus.yml` deixou de ser viável. `APP_METRICS_SCRAPE_TOKEN` é um segredo estático aceito só em `/actuator/prometheus` (`MetricsScrapeTokenFilter`, `ROLE_METRICS`), comparado em tempo constante; vazá-lo expõe métricas, nunca dados de negócio nem outras rotas do Actuator.
- **Rate limit por IP em memória, X-Forwarded-For só de proxy confiável**: o limite por e-mail não pega credential stuffing (o atacante varia o e-mail), então os endpoints públicos de autenticação ganharam um balde por IP, mais folgado para não punir NAT. O estado continua no heap, como o por e-mail — com check-and-count atômico (`JanelaDeslizante`), para que concorrência não fure o limite: a aplicação roda em instância única e um Redis só para isso seria infraestrutura nova sem ganho hoje — a limitação está registrada em [`regras-de-negocio.md`](regras-de-negocio.md) e precisa ser revista antes de qualquer réplica. O IP é resolvido pela aplicação (`ClientIpResolver`), e não pelo `RemoteIpValve` do Tomcat (`server.forward-headers-strategy=native` com `server.tomcat.remoteip.*`), que faria o mesmo trabalho: a válvula trocaria o `getRemoteAddr()` da aplicação inteira por causa de um recurso só, configura proxies por regex em vez de CIDR e não é exercitada pelo MockMvc, então o comportamento ficaria sem teste de integração. Em ambos os casos a confiança é opt-in por lista explícita; aqui a confiança é opt-in por lista explícita, e o cabeçalho de quem não está nela é ignorado — confiar nele sem proxy seria entregar ao atacante a chave do limite.
- **Paginação pela autoconfiguração do Boot, sem `@EnableSpringDataWebSupport`**: o formato `{content, page:{...}}` vem de `spring.data.web.pageable.serialization-mode=via-dto`. A anotação fazia o mesmo, mas registrava o próprio `PageableHandlerMethodArgumentResolver` e desligava a `SpringDataWebAutoConfiguration`, então `spring.data.web.pageable.default-page-size` (`APP_PAGINACAO_TAMANHO_PADRAO`) era ignorado. Os controllers também trocaram `@PageableDefault(sort = ...)` por `@SortDefault`: o primeiro fixa `size=10` por conta própria. `PaginacaoPadraoControllerTest` e `PaginacaoPropriedadesTest` guardam as duas coisas.
- **Listagens por paciente paginadas, sem período de convivência** (#100): pagamentos, aulas, sessões, avaliações, reavaliações, planos de tratamento e NFS-e trocaram o array por `Page` no mesmo formato de `/pacientes`, numa mudança casada com o frontend, como na troca de formato de erro da #99. A alternativa de responder array sem `page`/`size` e página com eles deixaria a mesma rota com dois contratos no OpenAPI. A ordenação padrão vai do mais recente para o mais antigo (as aulas antes saíam da mais antiga: com paginação, a primeira página seria a de anos atrás). Os campos aceitos em `sort` são uma lista fechada por serviço, e `id` entra sempre como último critério (`PaginacaoGuard`): sem a lista, um campo desconhecido viraria `500`; sem o desempate, registros da mesma data trocariam de página. As consultas com `JOIN FETCH` declaram `countQuery`, e as aulas projetam direto no DTO, como a agenda, para não arrastar a cadeia EAGER `pagamento → plano → diasSemana`. As evoluções ficaram de fora porque a decisão da #158 continua de pé: nenhum gatilho dela foi atingido.
- **Controllers paginados devolvem `PagedModel<T>`, não `Page<T>`**: o JSON é o mesmo — é o `PagedModel` que o modo `via-dto` já serializava —, mas com `Page<T>` o springdoc publicava um único `PagedModel` com `content: object[]`, e o contrato não dizia o tipo dos itens. Com o tipo explícito no retorno, cada rota ganha seu `PagedModel<Dto>`. Vale para todas as listagens paginadas, não só as da #100; `OpenApiPaginacaoContratoTest` guarda isso. Os serviços seguem devolvendo `Page`, que é o que os repositórios produzem.
- **Nulos no fim só nas consultas JPQL**: `sort=horario,desc` abria a página de sessões com as que não têm horário, porque o PostgreSQL usa `NULLS FIRST` em `desc`. O `PaginacaoGuard.comNulosNoFimEDesempatePorId` marca cada critério com `nullsLast`, mas só serve para `@Query`: na Criteria API (consultas derivadas e `Specification`) o Spring Data recusa precedência de nulos com `UnsupportedOperationException`. Por isso pagamentos usa `comDesempatePorId`, sem `nullsLast`, e pode, porque todos os seus campos ordenáveis são obrigatórios. Um campo anulável que entre na lista de pagamentos vai exigir mover a consulta para JPQL.
- **`LocalTime` é `string`/`time` no OpenAPI, por configuração global**: o springdoc descrevia o tipo como o objeto `{hour, minute, second, nano}` em sessões, bloqueios de agenda e lista de espera, embora o JSON traga `"09:00:00"`. Em vez de um `@Schema` por campo, o `OpenApiConfig` troca o schema do tipo com `SpringDocUtils.replaceWithSchema`, de modo que um `LocalTime` novo já nasce certo. `OpenApiHorarioContratoTest` guarda isso.
- **Horário da aula: um por plano, duração fixa e capacidade por profissional** (#106): o plano ganhou um único `horario`, copiado para as aulas na geração, em vez de um horário por dia da semana — a alternativa trocaria a coleção `diasSemana` por pares dia/hora e quebraria o contrato do plano, para um caso (aluno que treina em horários diferentes na semana) que a remarcação já cobre aula a aula. A aula guarda só o início; a duração é uma configuração (`app.agenda.duracao-aula-minutos`), não uma coluna, porque o estúdio não varia a duração por aula. A regra de conflito é capacidade por profissional (`app.agenda.capacidade-por-horario`), e não exclusividade como a issue propunha: no pilates um profissional atende a turma inteira. A constraint `(paciente_id, data)` ficou como estava, mantendo uma aula por dia por paciente e a colisão da remarcação (#170) intacta. Sem constraint de banco possível para a capacidade, a checagem serializa por lock pessimista no profissional.
- **Horário do estúdio, não da JVM, nos registros exibidos sem conversão**: `registradoEm` dos contatos de cobrança é gravado com um `Clock` no fuso `app.fuso-horario` (`America/Sao_Paulo`), porque o frontend mostra a hora como veio e o container roda em UTC. As datas de auditoria (`EntidadeAuditavel`) seguem o mesmo fuso, pelo `DateTimeProvider` do `JpaAuditingConfig` (#109). Os demais `LocalDateTime.now()` fora da auditoria — `dataEntrada` da lista de espera, preferências, tokens e a foto postural — continuam no fuso da JVM; migrá-los é decisão à parte.
- **Locking otimista com versão enviada pelo cliente, e checagem explícita** (#101): o `@Version` sozinho só pega a corrida entre a leitura e o flush de uma mesma transação — a edição concorrente real nasce minutos antes, no `GET` de cada recepcionista. Por isso a `version` vai e volta no contrato e o `VersaoGuard` a compara com a do registro. A comparação é explícita porque atribuir a versão do cliente à entidade gerenciada não funciona: o Hibernate ignora a alteração manual do campo `@Version` e usa a que carregou. Os updates gravam com `saveAndFlush` para a resposta já sair com a versão incrementada; sem o flush ela levaria a antiga e o save seguinte do mesmo cliente tomaria `409`. Versão ausente é aceita (modo legado) em vez de recusada: recusar exigiria deploy casado com o frontend em todas as telas de edição de uma vez, e o modo legado mantém o comportamento anterior até lá.
- **Indicadores do dashboard sem backfill e sem filtro de paciente ativo** (#202): a evasão precisava de datas que o modelo não guardava, então a `V41` criou `data_cadastro` e `data_inativacao` em `pacientes` e as deixou nulas nas linhas existentes. Preencher com a data do deploy inventaria um pico de cadastros e de evasão nesse mês, e não há fonte para a data real; a série de pacientes começa a valer a partir da migration, e o OpenAPI avisa disso. Pela mesma razão de fidelidade ao passado, as séries de aulas e o ranking por profissional não aplicam o filtro `paciente.ativo = true` que o `/dashboard/resumo` e as demais consultas de aulas usam: no histórico, inativar um paciente hoje apagaria aulas que de fato aconteceram. As séries saem em um único `/dashboard/indicadores` porque compartilham o parâmetro `meses` e a tela; o ranking ficou separado porque tem período próprio (`inicio`/`fim`). As agregações são `group by extract(year/month ...)` no banco, e o service só completa os meses sem linha com zero.
- **DDL via Flyway**: `spring.jpa.hibernate.ddl-auto=validate` — o Flyway gerencia o schema; o Hibernate apenas valida.
- **Deploy pela Railway, sem registry de imagens** (#103): a issue propunha publicar a imagem no GHCR e subir a produção por `docker compose` a partir dela, para que o que roda fosse o que a CI testou e houvesse rollback rápido. A produção, porém, roda na Railway, que builda o `Dockerfile` do commit e já cobre os dois pontos: *Wait for CI* segura o deploy até a CI passar, e o rollback do painel reaproveita a imagem do deploy anterior. Publicar no GHCR só faria sentido trocando a fonte do serviço para imagem, o que traria pacote, tag e credencial para gerir sem ganho real; a diferença que sobra — a Railway rebuildar a partir do mesmo commit em vez de reusar o artefato da CI — é pequena com um `Dockerfile` determinístico. A configuração do serviço vive no painel, e o que ela precisa ter está em [`deploy.md`](deploy.md#produção--railway).
- **Papéis de negócio por URL, com `denyAll` no fim** (#131): o `USER` genérico virou `PROFISSIONAL`, `RECEPCAO` e `FINANCEIRO`, acumuláveis — a fisioterapeuta que também cuida do financeiro precisa dos dois, e sem acúmulo a única saída seria dar `ADMIN` a ela —, e a regra padrão deixou de ser `authenticated()`. A autorização continua toda no `SecurityConfig`, sem `@EnableMethodSecurity`: as regras são por área (prefixo de URL), o arquivo já concentrava as exceções de ADMIN e uma matriz num lugar só é mais fácil de revisar que anotações espalhadas por 20 controllers. O `denyAll` final faz o esquecimento falhar fechado — controller novo responde `403` até ganhar regra. Os `USER` existentes foram para `RECEPCAO`, o papel de equipe sem prontuário, porque errar para menos acesso só custa uma promoção pelo admin, e errar para mais expõe dado de saúde. `PACIENTE` entra no enum com `visivel = false` como reserva para a #173, que vai precisar de autorização por *ownership*, não só por papel
- **Forma de pagamento opcional na confirmação, por ora** (#215): a forma entrou no corpo do `PATCH /pagamentos/{id}/pagar`, e não no `POST /pagamentos`, porque a cobrança é gerada pelo scheduler sem saber como o paciente vai pagar — a forma só vira fato na confirmação. Ela nasce opcional para não quebrar o frontend publicado, que manda só `{ "dataPagamento" }`; exigir já obrigaria um deploy casado com a carlessopilatesfe#132. Quando a #132 estiver publicada, a forma passa a ser obrigatória na confirmação (`@NotNull` no DTO e `@Valid` na rota). A coluna continua anulável mesmo depois disso: os pagamentos confirmados antes da #215 não têm forma e não há fonte para reconstruí-la.
- **Transações de leitura**: métodos de consulta nos services usam `@Transactional(readOnly = true)` para evitar flush desnecessário e permitir otimizações de conexão.
- **Autoria com o e-mail do usuário, sem FK e sem renomear as datas** (#109): o autor gravado é o principal que o `JwtAuthenticationFilter` já põe no `SecurityContext` — o e-mail —, então o `AuditorAware` não consulta o banco a cada escrita. Gravar o id exigiria essa consulta e um join para exibir; uma FK para `users` faria a autoria depender do usuário continuar existindo, e o registro de quem alterou um prontuário não pode sumir com ele. As colunas `data_criacao`/`data_atualizacao` existentes foram reaproveitadas, e os campos JSON mantiveram os nomes, porque o frontend os lê e separa pelo nome nos formulários do prontuário; a autoria entrou só como campos novos. `modifyOnCreate = false` preserva o contrato anterior de `dataAtualizacao` nula até a primeira alteração. O histórico de valores (Envers) ficou para uma issue futura.
- **Indicador de saúde de mail desligado enquanto não houver SMTP**: o starter de mail no classpath registra o `MailHealthIndicator` por padrão. Sem SMTP contratado (`spring.mail.host` vazio), ele falha continuamente e derruba o `/actuator/health` agregado para `DOWN`, ainda que app e banco estejam sãos — os probes seguem `UP` porque usam grupos restritos. Por isso `management.health.mail.enabled=false` é declarado uma vez no `application.properties` principal, valendo para todos os perfis; o `application.properties` de teste repete a chave porque sombreia o principal no classpath, e o `MailHealthPropriedadesTest` guarda essa duplicação obrigatória. Ao contratar SMTP, reative com `=true` no perfil desejado e ajuste o teste junto. O healthcheck externo deve apontar para `/actuator/health/readiness` — ver [`operacao.md`](operacao.md).
- **Spring Boot 3.5 e springdoc 2.10** (#93): a linha 3.4 saiu do suporte OSS, então o parent foi para a última 3.5.x e o springdoc para a versão compilada contra ela (a linha 3.x do springdoc é do Boot 4). A troca de versão não exigiu mudança de comportamento, só a remoção do que a 3.5 marcou como obsoleto: o `DaoAuthenticationProvider` recebe o `UserDetailsService` no construtor, os filtros opcionais de pacientes e profissionais devolvem `Specification.unrestricted()` no lugar de `Specification.where(null)` (que sai no Spring Data 4), o `GlobalExceptionHandler` lê `getParameterValidationResults()` e o teste de atomicidade de pagamento usa `@MockitoSpyBean`. Saiu também `spring.mvc.throw-exception-if-no-handler-found`: desde o Boot 3.2 a propriedade é ignorada, porque o `DispatcherServlet` já lança `NoHandlerFoundException` por padrão — o 404 em `problem+json` continua vindo do `spring.web.resources.add-mappings=false`. O BOM da 3.5 traz o Testcontainers 1.21.4, que negocia a versão da API com Docker Engine recente; o contorno `-Dapi.version=1.44` no `mvn verify` deixou de ser necessário.

---
