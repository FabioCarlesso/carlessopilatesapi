# Importação a partir do seufisio

Enquanto a empresa não desliga o `seufisio.com.br`, os dois scripts abaixo trazem a **carga delta** (o que surgiu de novo desde a última execução) para a API. Ambos são **idempotentes** e feitos para rodar quantas vezes forem necessárias, apontando `LOCAL_API_URL` para o ambiente local **ou** para a produção.

Ordem obrigatória: **pacientes primeiro**, evoluções depois (a evolução precisa do paciente já cadastrado).

Se a API do seufisio estiver bloqueada, os mesmos scripts leem as exportações da interface: veja [Sem a API do seufisio](#sem-a-api-do-seufisio-exportações-da-interface).

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
4. Carrega as sessões já cadastradas (`GET /sessoes/paciente/{id}`, percorrendo as páginas de `size=2000` até `page.totalPages`) e casa cada atendimento com uma sessão de mesma chave `data + horário` — é isso que torna a reexecução segura. Quantos atendimentos compartilharem a chave, tantas sessões são consumidas; o excedente vira sessão nova (sem `hora_atendimento` todos os atendimentos do dia caem na mesma chave, e sem isso o segundo seria perdido). Sessões `CANCELADA` ficam de fora da conta: não recebem evolução e não devem bloquear a importação.
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

## Sem a API do seufisio: exportações da interface

Desde novembro de 2026 a API do seufisio responde `403` às chamadas dos scripts, embora o navegador logado continue
acessando. Os dois scripts leem então as exportações feitas pela própria interface. A gravação na API do sistema novo,
a idempotência e o relatório final não mudam; muda só a fonte, lida por `scripts/fonte_arquivos_seufisio.py`.

| Script | Exportação no seufisio | Opção |
|---|---|---|
| `import_seufisio.py` | Relatórios → Clientes: Situação **Ativos/Inativos**, colunas CPF, Data Nascimento, E-mail, Endereço e Situação marcadas → **Exportar XLSX** | `--xlsx ARQUIVO` |
| `import_evolucoes_seufisio.py` | Relatórios → Prontuários por profissional: um **Gerar PDF** por profissional, no período desejado | `--pdf ARQUIVO` (repetível) |

```bash
set -a; source scripts/.env; set +a   # só LOCAL_*; o token do seufisio não é usado

python3 scripts/import_seufisio.py --xlsx scripts/arquivos/clientes.xlsx --dry-run
python3 scripts/import_seufisio.py --xlsx scripts/arquivos/clientes.xlsx

python3 scripts/import_evolucoes_seufisio.py --pdf "scripts/arquivos/Prontuário de Evolução.pdf" --dry-run
python3 scripts/import_evolucoes_seufisio.py --pdf "scripts/arquivos/Prontuário de Evolução.pdf"
```

O que muda em relação à API:

- **Arquivos em `scripts/arquivos/`**, ignorada pelo git: as exportações têm dados de pacientes e texto clínico.
- **PDF exige o `pdftotext`** (pacote `poppler-utils`); o XLSX é lido só com a biblioteca padrão.
- **O PDF não tem CPF nem id do seufisio**: o paciente é resolvido só pelo nome normalizado, com a mesma regra de
  correspondência única do caminho da API. Um nome escrito diferente nos dois sistemas aparece no log como
  `paciente_nao_encontrado` e não é importado; corrija o cadastro e rode de novo. `--cliente-id` não se aplica.
- **O PDF corta os parágrafos na largura da página.** O leitor junta de volta as linhas que chegam à largura máxima
  (~100 caracteres) e descarta o rodapé do seufisio e o cabeçalho de atendimento que o PDF repete quando a página quebra
  logo depois dele. A data/hora logo abaixo do cliente é a do preenchimento e vai para `dataHoraRegistro`.
- **O endereço vem numa coluna só** (`Rua Nº 10 - Bairro, Cidade - UF[, CEP]`) e é separado nos campos do contrato.
- **Prontuários curtos** como "Feriado." ou "Cancelada." são importados como qualquer outro, igual ao caminho da API.

### Datas de cadastro e de inativação

A API não aceita `dataCadastro` nem `dataInativacao`: o `PacienteService` grava a data do dia ao criar e ao inativar.
Depois de uma importação, todos os pacientes caem no mês da carga e as séries de novos cadastros e de evasão do
dashboard ficam com um pico falso. `scripts/gerar_sql_datas_seufisio.py` corrige isso **sem passar pela API**: lê o
XLSX de clientes, casa cada cliente com o paciente (e-mail, depois nome exato único) e gera um `UPDATE` por paciente
para rodar direto no banco.

Exporte o XLSX como na tabela acima, marcando também **Cliente desde**, **Cadastro no seufisio** e **Data de
Inativação**. A data de cadastro é o "Cliente desde", informado pela clínica; o "Cadastro no seufisio" só entra quando
ele está vazio.

```bash
python3 scripts/gerar_sql_datas_seufisio.py --xlsx scripts/arquivos/clientes.xlsx
# revise scripts/arquivos/datas-pacientes.sql e rode no Postgres da Railway
psql "$DATABASE_URL" -f scripts/arquivos/datas-pacientes.sql
```

- O script só **lê** a API; quem grava é o `psql`, numa transação única (`BEGIN`/`COMMIT`).
- Cada `UPDATE` soma 1 em `version`, para que um formulário aberto com a versão antiga receba `409`, e preenche
  `data_atualizacao` e `alterado_por` com o usuário do `LOCAL_EMAIL`.
- A data de inativação só é gravada em quem está inativo nos dois sistemas. Cada `UPDATE` também confere a situação no
  `WHERE` (`AND ativo` / `AND NOT ativo`), então um paciente reativado entre a geração e a execução não é alterado.
- Situação diferente entre o seufisio e o sistema novo aparece no log como `situacao_divergente`, e o paciente fica
  de fora.
- Reexecutar é seguro: grava as mesmas datas de novo.

### Anamneses

O seufisio não exporta anamneses em lote. Cada uma é uma página própria (aba **Avaliações** da ficha → ícone
"abrir" → `/anamnese-personalizada/ver/{matrícula}/{id da anamnese}`). Atenção: o segundo número é o id da anamnese,
e a página não confere se ela pertence ao cliente da URL; por isso o id tem de vir do botão da ficha, nunca ser
adivinhado. Na carga de novembro de 2026, cada página foi salva pelo navegador como um JSON
(`{"cliente", "anamnese", "titulo", "campos": [[rótulo, valor], ...]}`) em `scripts/arquivos/anamneses/`. Os clientes
com anamnese são os que têm "Sim" na coluna **Avaliações / Anamnese** do XLSX de clientes.

```bash
python3 scripts/import_anamneses_seufisio.py --xlsx scripts/arquivos/clientes.xlsx \
    --dir scripts/arquivos/anamneses --dry-run
python3 scripts/import_anamneses_seufisio.py --xlsx scripts/arquivos/clientes.xlsx \
    --dir scripts/arquivos/anamneses
```

| Campo no seufisio | Campo na API |
|---|---|
| Objetivo | `objetivos` (obrigatório: "Não informado no seufisio" quando vazio) |
| Queixa Principal + Queixa Secundária | `queixaPrincipal` (obrigatório: "Não informada no seufisio" quando vazio) |
| Diagnóstico Médico + História Pregressa de Doenças | `historicoDoencas` |
| Alergias/Cirurgias | `alergias` (o seufisio tem um campo só para as duas coisas) |
| Medicamentos em Uso | `medicamentosUso` |
| Restrições | `restricoesMedicas` |
| Atividades (Sim/Não/Passado) | `nivelAtividadeFisica`: Ativo, Sedentário ou Ativo no passado |
| Quais, Avaliação Postural, Força Muscular, Amplitude do Movimento, Perimetria/Exames, Histórico Familiar, OBS Gerais | `observacoes`, uma seção por campo, terminando com a data da anamnese original |

- **Uma anamnese por paciente** é a regra da API. Quando o seufisio tem mais de uma, a mais recente é a principal e as
  outras entram em `observacoes` como "Anamnese anterior (data)".
- **Idempotente**: paciente que já tem anamnese na API é pulado. Anamnese sem nenhum campo preenchido também é pulada.
- **Paciente inativo** é reativado só para criar a anamnese e inativado em seguida. Como a API grava a data do dia ao
  inativar, gere e aplique de novo o SQL de [datas](#datas-de-cadastro-e-de-inativação) depois da carga.

### Planos, faturas e agenda (virada para o sistema novo)

No sistema novo a agenda não é importada diretamente: as aulas nascem quando o pagamento de um plano é confirmado
([geração de aulas](regras-de-negocio.md#geração-de-aulas)). `scripts/import_planos_seufisio.py` recria esse caminho a
partir de quatro arquivos:

| Arquivo | Onde exportar no seufisio |
|---|---|
| `--clientes` | Relatórios → Clientes (o mesmo XLSX das datas) |
| `--planos` | Relatórios → Clientes → aba **Planos Fixos**, com Periodicidade, Valor, Início plano, Fim plano e Horários Programados marcados → Exportar XLS |
| `--contas` | Relatórios → Financeiros → **Contas a receber**, com o período de vencimento desejado → Exportar XLS |
| `--pagas` | Texto `Nº:DD/MM/AAAA:P` (PIX) ou `:D` (dinheiro) por fatura **paga**, lido da tabela da tela: a exportação XLS não traz data nem forma de pagamento |

```bash
python3 scripts/import_planos_seufisio.py --clientes ... --planos ... --contas ... --pagas ... --dry-run
python3 scripts/import_planos_seufisio.py --clientes ... --planos ... --contas ... --pagas ...
```

Para cada cliente ativo com pacote fixo vigente ou já vendido:

1. **Plano** `MENSAL`, com os dias e o horário do pacote mais recente e o menor valor entre pacotes e faturas (a API
   recusa pagamento menor que o plano). Os nomes dos pacotes do seufisio ("PAC DUPLA 8 AULAS", "VIP CLASS"…) não têm
   onde ficar.
2. **Um pagamento por fatura**: todas as em aberto, inclusive as vencidas, e as pagas de pacotes vigentes ou futuros.
   Faturas pagas de pacotes encerrados são histórico e ficam de fora. Os períodos seguem o ciclo mensal da API,
   ancorados no início do pacote vigente; a fatura de um pacote conhecido usa o início dele, o que cobre os pacotes
   bimestrais (uma fatura a cada dois meses).
3. **Faturas pagas** são confirmadas com a data e a forma do seufisio (data futura vira hoje), o que gera as aulas. As
   em aberto ficam `PENDENTE`, e o scheduler as marca `VENCIDO`: a agenda desses alunos só aparece quando o pagamento
   for confirmado no sistema novo.
4. **Ajuste das aulas**: a API guarda um horário por plano, então as aulas dos dias com outro horário são remarcadas;
   a profissional do horário é vinculada às aulas futuras, e aulas passadas com sessão importada naquela data são
   marcadas como realizadas por ela. Esse passo depende das profissionais cadastradas na API e roda de novo a cada
   execução.

Idempotente: cliente que já tem plano ativo é pulado nos passos 1 a 3, e o passo 4 só altera o que ainda falta.

`--abertas-como-pagas` confirma também as faturas em aberto, com o vencimento como data de pagamento e sem forma de
pagamento, inclusive nos planos já importados. Use quando o seufisio não era mantido em dia e não há inadimplência
de fato. Foi o caso da carga de novembro de 2026: todas as faturas em aberto eram de alunos da Claudia, cujos
pagamentos não eram baixados no seufisio.

Aulas passadas só viram realizadas quando há sessão com evolução naquela data. Sem evolução (faltas, reposições ou
profissional que não registrava prontuário no seufisio), a aula fica não realizada e sem profissional, e entra como
"agendada" na série de aulas do dashboard. `--passadas-como-realizadas` marca também essas aulas como realizadas pela
profissional do horário. Isso entra no relatório de repasse e **não tem volta pela API**: `PATCH /aulas/{id}/realizar`
não tem rota inversa, e desfazer exige SQL. Na carga de novembro de 2026 a opção foi usada porque as aulas da
Claudia, que não registrava prontuário, aconteceram de fato.

**Atenção à virada**: com planos ativos, o scheduler das 07:00 passa a gerar a próxima cobrança de cada plano quando
faltam 7 dias ou menos para o fim do período. A partir da carga, as cobranças devem ser emitidas só no sistema novo,
ou o aluno recebe duas.

## Autoria dos registros importados

Os dois scripts gravam pela API, então a [auditoria](regras-de-negocio.md#auditoria-de-criação-e-alteração) trata a
carga como qualquer outra escrita: `criadoPor` é o e-mail do usuário usado no `/auth/login` do script, e
`dataCriacao` é o momento da importação, não a data original no seufisio (a data da sessão continua em `data`). Para
distinguir a carga das escritas feitas na tela, rode a importação com um usuário `ADMIN` dedicado a ela.

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

> O `accessToken` da API local expira em `JWT_EXPIRATION_MS` (15 min por padrão), menos que uma carga completa. Os scripts guardam também o `refreshToken` do login (`SessaoLocal`) e, quando uma chamada autenticada volta `401`, renovam a sessão via `POST /auth/refresh` e repetem a chamada uma vez — a carga atravessa a expiração sem intervenção.
>
> O token JWT do seufisio expira (~2 dias). Se demorar para rodar, capture um novo no DevTools. O header `x-version-app` é obrigatório: sem ele a API do seufisio responde `426 Upgrade Required`.
>
> **Segurança**: o `.gitignore` está configurado para nunca versionar dumps (`scripts/*.json`, `scripts/*.csv`) nem variáveis locais (`scripts/.env`). Não commitar tokens nem dados de pacientes em hipótese alguma. Logs e `--dry-run` mascaram CPF/e-mail/nome e nunca imprimem o texto clínico das evoluções.

## Ver também

- [`api.md`](api.md) — endpoints consumidos pelos scripts
- [`deploy.md`](deploy.md) — subida do ambiente de produção antes da carga
- [`banco-de-dados.md`](banco-de-dados.md) — por que a produção não é populada por dump
