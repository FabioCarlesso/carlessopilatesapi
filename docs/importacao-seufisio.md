# Importação a partir do seufisio

Enquanto a empresa não desliga o `seufisio.com.br`, os dois scripts abaixo trazem a **carga delta** (o que surgiu de novo desde a última execução) para a API. Ambos são **idempotentes** e feitos para rodar quantas vezes forem necessárias, apontando `LOCAL_API_URL` para o ambiente local **ou** para a produção.

Ordem obrigatória: **pacientes primeiro**, evoluções depois (a evolução precisa do paciente já cadastrado).

| Script | O que traz | Chave de idempotência |
|---|---|---|
| `scripts/import_seufisio.py` | Clientes → `POST /pacientes` | CPF **ou** e-mail já cadastrado |
| `scripts/import_evolucoes_seufisio.py` | Prontuários → `SessaoPilates` REALIZADA + `EvolucaoSessao` | paciente + data + horário da sessão |

## Pacientes

`scripts/import_seufisio.py`:

1. Consulta `GET /api/cliente?per_page=500` no seufisio com o Bearer token capturado do navegador (DevTools → aba Network → header `authorization` da requisição da listagem).
2. Para cada cliente, busca o detalhe em `GET /api/cliente/{id}`, mapeando para o contrato de `POST /pacientes`.
3. Faz login na API local (`/auth/login`) e carrega os pacientes já cadastrados — **ativos e inativos**, em duas passadas de `GET /pacientes?ativo=true|false`. Pula quem já existe por **CPF ou e-mail** (as duas colunas têm unicidade parcial) e cria apenas os novos. Pacientes com `situacao != 2` no seufisio são marcados como inativos via `PATCH /pacientes/{id}/inativar` após o cadastro.
4. Ao final imprime o resumo: total processado / importados / ignorados por motivo / falhas por motivo. O exit code é ≠ 0 apenas em falha real (registro ignorado não é falha).

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

# Validar mapeamento sem gravar (recomendado antes da carga real).
# Com LOCAL_EMAIL/LOCAL_PASSWORD preenchidos, o dry-run já mostra o delta real.
python3 scripts/import_seufisio.py --dry-run

# Importação de fato (idempotente: pula CPF/e-mail já cadastrados)
python3 scripts/import_seufisio.py
```

## Evoluções

`scripts/import_evolucoes_seufisio.py` traz os prontuários do seufisio. Cada prontuário preenchido vira uma `SessaoPilates` **REALIZADA** mais a `EvolucaoSessao` correspondente (a relação é 1:1).

1. Faz login na API, carrega os pacientes locais (ativos e inativos) e resolve cada cliente do seufisio por **CPF**; sem CPF, cai para o nome normalizado (sem acentos, minúsculo) e **só aceita correspondência única** — nomes ambíguos são registrados e pulados, nunca chutados.
2. Busca os prontuários em `GET /api/cliente/{id}/prontuarios?rowsPerPage=200&page=N` (paginado via `last_page`).
3. Converte o HTML do campo `prontuario` em texto puro, que vai para `observacoesFisioterapeuta`. Linhas sem prontuário preenchido (atendimento sem evolução) são ignoradas.
4. Carrega as sessões já cadastradas (`GET /sessoes/paciente/{id}`) e casa cada atendimento com uma sessão de mesma chave `data + horário` — é isso que torna a reexecução segura. Quantos atendimentos compartilharem a chave, tantas sessões são consumidas; o excedente vira sessão nova (sem `hora_atendimento` todos os atendimentos do dia caem na mesma chave, e sem isso o segundo seria perdido). Sessões `CANCELADA` ficam de fora da conta: não recebem evolução e não devem bloquear a importação.
5. Para cada evolução nova: `POST /sessoes` → `PATCH /sessoes/{id}/realizar` → `POST /evolucoes-sessao`, usando a data/hora original (`prontuario_preenchido_em` quando presente, senão a data/hora do atendimento).
6. Quando a sessão já existe, o script confere se ela tem evolução (`GET /evolucoes-sessao/sessao/{id}`) e **cria só a que faltar** — reativando a sessão via `PATCH realizar` se ela ainda estiver `AGENDADA`.

Detalhes operacionais:

- **Pacientes inativos** são reativados temporariamente (`PATCH /pacientes/{id}/ativar`) porque `POST /sessoes` e `POST /evolucoes-sessao` só aceitam pacientes ativos; a reinativação acontece no `finally`, mesmo se algo falhar no meio. Se o `inativar` falhar, o log avisa para reverter à mão. A leitura (`GET /sessoes/paciente/{id}`) não exige mais reativação.
- Se a evolução falhar depois da sessão criada, a **sessão órfã** é registrada no log (`[orfa ...]`) — e a execução seguinte a completa sozinha (passo 6), sem duplicar a sessão. O mesmo vale para uma sessão criada direto no sistema novo que ainda não tenha evolução.
- O tipo da sessão criada é `PILATES` por padrão; use `SEUFISIO_TIPO_SESSAO=FISIOTERAPIA` para mudar. Um valor fora do enum `TipoSessao` aborta o script logo no início, antes de qualquer chamada.

```bash
set -a; source scripts/.env; set +a

# Simular (não grava nada; imprime só o tamanho do texto clínico, nunca o conteúdo)
python3 scripts/import_evolucoes_seufisio.py --dry-run

# Um cliente só, para conferir o resultado antes da carga completa
python3 scripts/import_evolucoes_seufisio.py --cliente-id 8

# Carga completa
python3 scripts/import_evolucoes_seufisio.py

# Delta a partir de uma data (execuções recorrentes)
python3 scripts/import_evolucoes_seufisio.py --desde 2026-07-01
```

> A base de atendimentos é grande (ordem de dezenas de milhares no total). Prefira `--desde` nas execuções recorrentes e `--limite-clientes` / `--cliente-id` ao validar mudanças.

## Carga da produção (runbook de go-live)

**A produção é populada pelos mesmos scripts, apontando `LOCAL_API_URL` para a API de produção — não por dump do banco de desenvolvimento.** O seufisio é a fonte da verdade enquanto não for desligado, e os scripts são idempotentes, então a carga inicial da produção é apenas a primeira execução do processo recorrente.

> **Por que não `pg_dump`/`pg_restore` do ambiente de desenvolvimento:** o perfil `dev` roda o Flyway com `classpath:db/migration,classpath:db/seed`, então o `flyway_schema_history` do banco dev registra as seeds `V7` (profissionais de teste) e `V12` (usuários de desenvolvimento com senha compartilhada). O perfil `prod` resolve apenas `classpath:db/migration` — restaurado em produção, o Flyway **falha na validação** por não encontrar essas migrações. Recuperar exigiria editar o `flyway_schema_history` à mão, apagar os usuários de dev junto com suas dependências (`preferencias_usuario`, `password_reset_tokens`) antes de subir o app — porque o `InitialAdminBootstrap` lança exceção se existir usuário com o e-mail do admin inicial sem nenhum `ADMIN` ativo — e ainda tratar os profissionais fake. Pela API nada disso acontece: a produção sobe limpa e o admin real é criado a partir de `APP_INITIAL_ADMIN_*`.
>
> O custo dessa escolha é tempo: cada evolução são três requisições mais o rate limit. Medido na carga do ambiente local (112 clientes, 9.921 prontuários, 5.140 evoluções criadas): **~28 minutos**, mais alguns minutos para os pacientes. Contra a VPS, some a latência de rede a cada requisição. Rode em `tmux`/`nohup`, de preferência na própria VPS. Um `pg_restore` levaria segundos, mas com toda a higiene descrita acima.
>
> Esse raciocínio só vale enquanto **todo dado relevante estiver no seufisio**. Se em algum momento passarem a existir registros criados apenas no sistema novo, o dump volta a ser necessário.

**1. Conferir o ambiente da VPS** antes de subir o app (`.env.prod`): `JWT_SECRET` forte, `APP_INITIAL_ADMIN_EMAIL`, `APP_INITIAL_ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS` e as variáveis `SMTP_*`/`APP_EMAIL_*`.

**2. Subir a produção e validar que ela está de pé** (Swagger é desabilitado em produção, então use `curl`):

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
curl -fsS http://localhost:8080/actuator/health
```

**3. Apontar os scripts para a produção**, usando o admin real criado pelo bootstrap:

```bash
# scripts/.env — token do seufisio capturado no DevTools na hora da execução
SEUFISIO_TOKEN="eyJ0eXAi..."
SEUFISIO_CLINICA_ID="..."
SEUFISIO_VERSION_APP="..."
LOCAL_API_URL="https://api.carlessopilates.com.br"
LOCAL_EMAIL="<admin real de produção>"
LOCAL_PASSWORD="<senha do admin real>"
```

**4. Simular, depois carregar** — pacientes primeiro, evoluções depois:

```bash
set -a; source scripts/.env; set +a

python3 scripts/import_seufisio.py --dry-run
python3 scripts/import_seufisio.py

python3 scripts/import_evolucoes_seufisio.py --cliente-id <um-cliente> --dry-run
python3 scripts/import_evolucoes_seufisio.py --cliente-id <um-cliente>   # conferir o resultado
nohup python3 scripts/import_evolucoes_seufisio.py > import-evolucoes.log 2>&1 &
```

**5. Conferir** o resumo final de cada script (importados / ignorados / falhas por motivo) e comparar as contagens com o seufisio:

```sql
SELECT count(*) FILTER (WHERE ativo) AS ativos,
       count(*) FILTER (WHERE NOT ativo) AS inativos FROM pacientes;
SELECT count(*) FROM evolucoes_sessao;
-- Nenhum paciente pode ter ficado reativado por engano pela importação de evoluções:
SELECT count(*) FROM pacientes WHERE NOT ativo;
```

Smoke test autenticado: login, `GET /pacientes` e a ficha de um paciente com evoluções.

**6. Corte final.** Enquanto o seufisio seguir em uso, repita os passos 4 e 5 periodicamente com `--desde` para trazer só o delta. Como os scripts são idempotentes, rodar de novo não duplica nada — a última execução antes do desligamento do seufisio é o corte definitivo.

**Testes dos scripts:**

```bash
cd scripts && python3 -m unittest discover -p 'test_*.py' -v
```

> O token JWT do seufisio expira (~2 dias). Se demorar para rodar, capture um novo no DevTools. O header `x-version-app` é obrigatório: sem ele a API do seufisio responde `426 Upgrade Required`.
>
> **Segurança**: o `.gitignore` está configurado para nunca versionar dumps (`scripts/*.json`, `scripts/*.csv`) nem variáveis locais (`scripts/.env`). Não commitar tokens nem dados de pacientes em hipótese alguma. Logs e `--dry-run` mascaram CPF/e-mail/nome e nunca imprimem o texto clínico das evoluções.

## Ver também

- [`api.md`](api.md) — endpoints consumidos pelos scripts
- [`deploy.md`](deploy.md) — subida do ambiente de produção antes da carga
- [`banco-de-dados.md`](banco-de-dados.md) — por que a produção não é populada por dump
