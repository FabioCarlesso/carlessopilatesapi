# Deploy e execução

## Produção — Railway

O backend de produção roda na **Railway** (`https://carlessopilatesapi-production.up.railway.app`), que builda o
`Dockerfile` deste repositório a cada push na `master`. O frontend roda na Vercel e encaminha `/api` para essa URL.
O `docker-compose.prod.yml` **não** é usado na Railway: ele serve para subir o perfil `prod` localmente.

A configuração do serviço fica só no painel da Railway, não no repositório. O que precisa estar assim:

| Onde (painel do serviço) | Valor | Por quê |
|---|---|---|
| *Settings → Source* | Repositório GitHub, branch `master` | Deploy automático a cada merge |
| *Settings → Source → Wait for CI* | Ligado | A Railway só faz o deploy depois que os checks do GitHub Actions passam (testes, migrations, build da imagem); sem isso, um merge que quebra a CI vai para produção mesmo assim |
| *Settings → Deploy → Healthcheck Path* | `/actuator/health/readiness` | A Railway só troca o tráfego para o deploy novo quando o healthcheck responde 200; se não responder dentro do timeout, o deploy falha e o anterior continua no ar. Não use o `/actuator/health` agregado ([por quê](operacao.md#qual-endpoint-usar-no-healthcheck-externo)) |
| *Variables* | `SPRING_PROFILES_ACTIVE=prod` e as [variáveis de ambiente](#variáveis-de-ambiente) obrigatórias (`DB_*`, `JWT_SECRET`, `APP_INITIAL_ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS`…) | O perfil `prod` desliga o Swagger e o registro público |

### Deploy

1. Mergeie na `master`. Com *Wait for CI* ligado, o deploy fica em espera até a CI passar.
2. Acompanhe na aba *Deployments* do serviço (build, deploy e logs).
3. Verifique: `curl -fsS https://carlessopilatesapi-production.up.railway.app/actuator/health/readiness` deve
   responder `{"status":"UP"}`.

### Rollback

Na aba *Deployments*, abra o deploy anterior e use **Rollback**: a Railway volta a imagem já buildada daquele
deploy, sem novo build. **A imagem volta, o banco não:** o Flyway não desfaz migrations, e a versão antiga sobe com
`ddl-auto=validate` contra o schema novo. Migration aditiva (coluna nova anulável, tabela nova) não impede o
rollback; remoção ou renomeação de coluna impede, e nesse caso a correção vai para frente, numa versão nova — ver
[`banco-de-dados.md`](banco-de-dados.md).

## Opção 1 — Docker Compose (recomendado)

Sobe o banco PostgreSQL e a aplicação juntos, sem instalar nada localmente além do Docker.

O container da aplicação roda com usuário não-root (`app`) e possui `HEALTHCHECK` no `/actuator/health/liveness` (estado `healthy` visível em `docker ps`) — o probe de liveness reflete apenas o estado do processo, então indisponibilidade de banco/SMTP não marca o container como `unhealthy`. Já o healthcheck da plataforma de hospedagem deve usar `/actuator/health/readiness`, e não o `/actuator/health` agregado ([por quê](operacao.md#qual-endpoint-usar-no-healthcheck-externo)). A memória do container é limitada por `mem_limit` (padrão `1g`, ajustável via `APP_MEM_LIMIT`) e a heap da JVM usa 75% desse limite (`-XX:MaxRAMPercentage=75.0`, ajustável via `JAVA_OPTS`).

O projeto usa o padrão de **override do Docker Compose** para isolar os ambientes:

| Ambiente | Comando | Volume PostgreSQL | Dados de seed |
|---|---|---|---|
| **Desenvolvimento** | `docker compose up` (auto-carrega `docker-compose.override.yml`) | `postgres_dev_data` | Sim (10 pacientes, 3 profissionais, 5 usuários) |
| **Perfil prod local** | `docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up` (a produção real é na [Railway](#produção--railway)) | `postgres_prod_data` | Não (apenas admin inicial) |

### Desenvolvimento

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

### Perfil prod local

Sobe o perfil `prod` com banco próprio, para reproduzir localmente o comportamento de produção. Não é o deploy
real, que é feito pela [Railway](#produção--railway).

```bash
# Configurar variáveis de ambiente do perfil prod (nunca versionar este arquivo)
cp .env.example .env.prod
# Edite .env.prod com credenciais seguras, APP_INITIAL_ADMIN_PASSWORD e as
# variáveis SMTP_*/APP_EMAIL_* (necessárias para o e-mail de recuperação de senha)

# Subir com perfil prod (banco limpo, sem seed)
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up --build -d

# Derrubar e remover os dados do banco do perfil prod
docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml down -v
```

> **Admin inicial de produção:** no perfil `prod`, se não existir nenhum `ADMIN` ativo, a aplicação cria o usuário `admin@carlessopilates.com` (ou `APP_INITIAL_ADMIN_EMAIL`) usando a senha definida em `APP_INITIAL_ADMIN_PASSWORD`. A aplicação falha ao iniciar em produção se essa senha não estiver configurada.

> Se o Docker exigir permissão negada, adicione seu usuário ao grupo docker:
> ```bash
> sudo groupadd docker
> sudo usermod -aG docker $USER
> newgrp docker
> ```
> Ou prefixe os comandos com `sudo`.

## Opção 2 — Rodar localmente (Maven)

Pré-requisitos: Java 21 e PostgreSQL rodando localmente. Para rodar a suíte de
testes (`mvn verify`) também é necessário **Docker**: parte dos testes (repositório
e integração) sobe um PostgreSQL 16 via **Testcontainers** — veja [Estratégia de
testes](desenvolvimento.md#estratégia-de-testes).

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
export JWT_EXPIRATION_MS=900000
export JWT_REFRESH_EXPIRATION_MS=604800000
export CORS_ALLOWED_ORIGINS=http://localhost:4200
```

**3. Compilar e rodar:**

```bash
JAVA_HOME=/caminho/para/jdk21 mvn spring-boot:run
```

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | - | Perfil Spring: `prod` na Railway (o `docker-compose.prod.yml` já o define; o override de dev usa `dev`) |
| `DB_HOST` | `localhost` | Host do banco PostgreSQL |
| `DB_PORT` | `5432` | Porta usada pela aplicação para conectar ao banco |
| `DB_HOST_PORT` | `5432` | Porta publicada no host pelo Docker Compose |
| `DB_NAME` | `carlesso_pilates` | Nome do banco de dados |
| `DB_USER` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |
| `JWT_SECRET` | - | Segredo HMAC obrigatório para assinar JWT; use pelo menos 32 caracteres |
| `JWT_EXPIRATION_MS` | `900000` | Expiração do access token em milissegundos (15 min); a sessão é renovada por `POST /auth/refresh` |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | Expiração do refresh token em milissegundos (7 dias), contada a partir de cada rotação |
| `JWT_REFRESH_SESSION_MAX_MS` | `2592000000` | Teto absoluto da sessão em milissegundos (30 dias desde o login); a rotação nunca estende o refresh token além dele |
| `JWT_REFRESH_REUSE_GRACE_MS` | `10000` | Tolerância após a rotação em que reapresentar o token antigo recebe `401` sem derrubar a sessão (retentativa de rede, duas abas); `0` desliga |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Origens permitidas para o frontend Angular |
| `APP_RATE_LIMIT_IP_MAX_TENTATIVAS` | `20` | Tentativas por IP na janela antes do `429` em `/auth/login` (falhas) e `/auth/forgot-password` (solicitações), além do limite fixo por e-mail. Deve ser `> 0`: valor inválido impede a subida |
| `APP_RATE_LIMIT_IP_JANELA_MINUTOS` | `15` | Janela do rate limit por IP, em minutos. Deve ser `> 0`: valor inválido impede a subida |
| `APP_SECURITY_TRUSTED_PROXIES` | - | IPs ou CIDRs dos proxies reversos confiáveis, separados por vírgula. Só conexões vindas deles têm o `X-Forwarded-For` considerado na resolução do IP do cliente; vazio, o cabeçalho é ignorado. Preencha ao colocar a aplicação atrás de nginx/Traefik, senão todos os clientes compartilham o IP do proxy no rate limit (a aplicação loga um `WARN` ao detectar esse cenário) |
| `APP_INITIAL_ADMIN_EMAIL` | `admin@carlessopilates.com` | E-mail do admin inicial criado no perfil `prod` quando não há `ADMIN` ativo |
| `APP_INITIAL_ADMIN_PASSWORD` | - | Senha obrigatória para bootstrap do admin inicial no perfil `prod` |
| `APP_COBRANCA_CRON_VENCIDOS` | `0 0 6 * * *` | Cron expression do scheduler que marca pagamentos como `VENCIDO` |
| `APP_COBRANCA_CRON_COBRANCAS_FUTURAS` | `0 0 7 * * *` | Cron expression do scheduler que gera cobranças futuras |
| `APP_REFRESH_TOKEN_CRON_LIMPEZA` | `0 30 3 * * *` | Cron expression da limpeza diária de refresh tokens vencidos ou revogados |
| `APP_METRICS_SCRAPE_TOKEN` | - | Credencial fixa (mín. 32 caracteres) aceita só em `/actuator/prometheus` para o scrape do Prometheus; vazia, o endpoint exige JWT de `ADMIN` |
| `APP_COBRANCA_VENCIMENTO_DIAS` | `10` | Dias somados ao início do período para definir o vencimento das cobranças geradas |
| `APP_PAGINACAO_TAMANHO_PADRAO` | `10` | Tamanho padrão de página nas listagens paginadas (quando a requisição não informa `size`) |
| `APP_FUSO_HORARIO` | `America/Sao_Paulo` | Fuso do estúdio (ID IANA) usado nos horários gravados pelo servidor e exibidos sem conversão, como o `registradoEm` dos contatos de cobrança. Independe do fuso da JVM |
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

## Ver também

- [`desenvolvimento.md`](desenvolvimento.md) — CI, testes e ferramental
- [`operacao.md`](operacao.md) — Actuator, métricas e logging
- [`banco-de-dados.md`](banco-de-dados.md) — migrations aplicadas na subida
