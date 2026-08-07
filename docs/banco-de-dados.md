# Banco de dados (Flyway)

O projeto utiliza **Flyway** para versionamento e execução automática das migrações. As migrações são divididas em dois diretórios:

- `src/main/resources/db/migration/` — DDL estrutural, aplicado em **todos** os ambientes
- `src/main/resources/db/seed/` — dados de teste, aplicados **apenas** no perfil `dev`

## Migrations estruturais (`db/migration/`)

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
| `V28__create_avaliacoes_posturais_table.sql` | Cria tabela `avaliacoes_posturais` (simetrógrafo virtual): landmarks em `JSONB`, soft delete e índice parcial de unicidade `(avaliacao_fisioterapeutica_id, vista) WHERE ativo = true` |
| `V29__add_foto_and_proporcao_to_avaliacoes_posturais.sql` | Adiciona `foto`/`foto_content_type` (MVP em `bytea`, upload em issue própria) e `proporcao_imagem` — razão largura/altura usada para calcular ângulos fiéis sobre coordenadas normalizadas |
| `V30__create_avaliacoes_posturais_fotos_table.sql` | Move a foto da análise postural para a tabela própria `avaliacoes_posturais_fotos` (o `bytea` fora da tabela principal mantém listagens e buscas sem carregar o binário) e remove a coluna `foto` criada na `V29`; `foto_content_type` permanece como marcador barato de "foto presente" |
| `V31__add_numero_registro_to_profissionais.sql` | Adiciona `numero_registro` (nullable) em `profissionais` — número no conselho profissional (CREFITO, CREF etc.) |
| `V32__add_profissional_snapshot_to_evolucoes_sessao.sql` | Adiciona o snapshot `profissional_id`/`profissional_nome`/`profissional_numero_registro` em `evolucoes_sessao`, com índice na FK e backfill best-effort a partir da sessão |
| `V33__add_index_on_aulas_data.sql` | Cria `idx_aulas_data` para a agenda por período (`GET /aulas?inicio=&fim=`); a UNIQUE `(paciente_id, data)` não cobre o filtro só por data |

## Migrations de seed (`db/seed/`) — apenas perfil `dev`

| Arquivo | Descrição |
|---|---|
| `V7__insert_profissionais_teste.sql` | Carga inicial com 3 profissionais de teste |
| `V12__insert_users_perfis_acesso.sql` | Insere 5 usuários de teste com perfis `ADMIN` e `USER` (senha: `senha1234`) |

> A seed antiga de pacientes (`V2__insert_pacientes_teste.sql`) foi removida em favor da importação a partir de sistemas externos via `scripts/import_seufisio.py`. Para zerar um ambiente dev existente que ainda tenha esses pacientes, derrube o volume com `docker compose down -v` e suba novamente.

### Usuários de seed (perfil `dev`)

Os usuários criados pela `V12` usam a senha `senha1234` e representam os perfis disponíveis:

| E-mail | Perfil |
|---|---|
| `admin@carlessopilates.com` | `ADMIN` |
| `operacional@carlessopilates.com` | `ADMIN` |
| `recepcao@carlessopilates.com` | `USER` |
| `financeiro@carlessopilates.com` | `USER` |
| `consulta@carlessopilates.com` | `USER` |

Em produção não há seed: o admin inicial é criado pela aplicação a partir de `APP_INITIAL_ADMIN_EMAIL` e
`APP_INITIAL_ADMIN_PASSWORD` — ver [`deploy.md`](deploy.md).

> Nos testes automatizados o Flyway fica desabilitado (`spring.flyway.enabled=false`), pois o banco H2 é gerenciado pelo Hibernate com `ddl-auto=create-drop`.

## Ver também

- [`desenvolvimento.md`](desenvolvimento.md#estratégia-de-testes) — como as migrations são exercitadas nos testes
- [`deploy.md`](deploy.md) — perfis `dev`/`prod` e variáveis de ambiente
- [`context.md`](context.md) — modelo de dados
