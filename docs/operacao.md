# Operação

## Observabilidade (Actuator)

O projeto expõe endpoints operacionais do Spring Boot Actuator para acompanhamento da aplicação em desenvolvimento:

| Recurso | URL |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Liveness | http://localhost:8080/actuator/health/liveness |
| Readiness | http://localhost:8080/actuator/health/readiness |
| Info | http://localhost:8080/actuator/info |
| Métricas (Prometheus) | http://localhost:8080/actuator/prometheus |

`health` e `info` são públicos. Os probes de liveness/readiness (`management.endpoint.health.probes.enabled=true`) refletem apenas o estado do processo e são usados pelo `HEALTHCHECK` do container.

### Métricas (Prometheus / Micrometer)

O endpoint `/actuator/prometheus` expõe métricas no formato de scrape do Prometheus: métricas de JVM (`jvm_memory_used_bytes`, `jvm_gc_*`, threads), de HTTP (`http_server_requests_seconds_*`, com histograma habilitado para percentis de latência e séries por status — base para alertas de 5xx), do pool de conexões e do Hibernate, além dos contadores de negócio abaixo.

| Métrica | Significado |
|---|---|
| `pilates_cobrancas_geradas_total` | Cobranças futuras geradas pelo scheduler |
| `pilates_cobrancas_vencidas_total` | Pagamentos marcados como `VENCIDO` pelo scheduler |
| `pilates_pagamentos_confirmados_total` | Pagamentos confirmados |
| `pilates_logins_bloqueados_total` | Tentativas de login barradas pelo rate limit |
| `pilates_emails_reset_enviados_total` | E-mails de redefinição de senha enviados |

Os contadores são declarados em `metrics/BusinessMetrics` e nascem em `0` na subida da aplicação, permitindo alertas por taxa (`rate(...)`) sem esperar o primeiro evento. Todas as séries carregam a tag `application=CarlessoPilatesApi`.

**O endpoint não é público:** exige um JWT de usuário com role `ADMIN` (`SecurityConfig`: `/actuator/**` → `hasRole("ADMIN")`). Sem token retorna `401`; com token de usuário comum, `403`.

Para fazer o scrape, use um token de admin no `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: carlesso-pilates-api
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    authorization:
      type: Bearer
      credentials: <JWT de um usuário ADMIN>
    static_configs:
      - targets: ['api:8080']
```

Verificação manual:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@carlessopilates.com","password":"<senha>"}' | jq -r .accessToken)

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/prometheus | head
```

### Logging estruturado e correlation-id

Operações de negócio críticas registram logs (nível `INFO` para eventos, `WARN` para bloqueios/rejeições) em `PagamentoService`, `AuthService`, `PasswordResetService`, `NotaFiscalEmitidaService`, `UserService`, nos schedulers e no bootstrap do admin. Dados sensíveis (senha, token) nunca são logados; e-mail e CPF são sempre mascarados via `LogMasker` (ex.: `j***@dominio.com`, `***.***.***-44`).

Cada requisição recebe um **correlation-id** propagado pelo header `X-Request-Id`:

- O `CorrelationIdFilter` lê o header `X-Request-Id`; se ausente, gera um UUID.
- O id é colocado no MDC (chave `requestId`) e devolvido na resposta no mesmo header, permitindo ao frontend correlacionar suas chamadas com os logs do backend.
- O valor recebido é sanitizado (apenas `[A-Za-z0-9_-]`, máx. 64 chars) para evitar log injection.

O formato de saída é controlado por perfil em `src/main/resources/logback-spring.xml`:

| Perfil | Saída |
|---|---|
| `dev` / default | Texto legível no console, com o correlation-id no pattern (`[requestId]`) |
| `prod` | Uma linha **JSON** por evento (`logstash-logback-encoder`), com `requestId` como campo de topo, pronto para ingestão por agregadores de log |


## Compressão de respostas (gzip)

O Tomcat embarcado comprime as respostas para clientes que enviam `Accept-Encoding: gzip` — o que inclui qualquer navegador. Configuração em `src/main/resources/application.properties`, válida para todos os perfis:

| Propriedade | Valor |
|---|---|
| `server.compression.enabled` | `true` |
| `server.compression.mime-types` | `application/json,text/plain,text/csv,text/css,text/html,application/javascript` |
| `server.compression.min-response-size` | `1024` (bytes) |

O ganho está nas listagens grandes de texto clínico. Medido na base real em 05/08/2026, no paciente com mais registros (384 evoluções):

```
GET /evolucoes-sessao/paciente/3   sem gzip: 329.192 bytes
                                   com gzip:  41.316 bytes   (8,0×)
```

A [decisão de não paginar essa listagem](context.md) pressupõe a compressão ligada.

Decisões por trás dos valores:

- **XLSX fora da lista** — `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` já é um zip; recomprimir gasta CPU sem reduzir bytes. Vale o mesmo para as fotos das análises posturais.
- **CSV dentro** — `text/csv` é texto repetitivo e comprime bem, e o navegador descomprime antes de salvar o arquivo. É o único caso em que o `min-response-size` realmente decide (ver abaixo): CSV pequeno sai sem compressão.
- **`min-response-size=1024`** — abaixo disso o overhead do gzip custa mais do que economiza. Vale entender **onde** ele age; veja abaixo.

### Onde o `min-response-size` age (e onde não age)

O Tomcat só consulta esse limiar quando conhece o tamanho da resposta de antemão — isto é, quando há `Content-Length`. Respostas em `Transfer-Encoding: chunked` são comprimidas **independentemente do limiar**, e é o caso de todo JSON produzido pelos controllers do Spring MVC. Medido em 05/08/2026:

| Resposta | `Content-Length`? | Comprimida? | Por quê |
|---|---|---|---|
| JSON dos controllers | não (chunked) | Sim, sempre | tamanho desconhecido; limiar ignorado |
| `POST /auth/login` (367 bytes) | não (chunked) | Sim | idem — ver BREACH abaixo |
| `GET /pacientes/3` (176 bytes crus) | não (chunked) | Sim | idem |
| Assets do Swagger UI (`swagger-ui.css`) | não (chunked) | Sim — 23,8 KB transferidos | idem, e aqui o ganho é grande |
| CSV do relatório NFSe (99 bytes) | **sim** | **Não** | tamanho conhecido e abaixo do limiar |
| XLSX do relatório (4.991 bytes) | sim | Não | mime-type fora da lista |
| Actuator | não (chunked) | Não | mime-type fora da lista |

Duas conclusões práticas:

1. **Para o JSON da API, o limiar é inerte** — quem decide é a lista de mime-types. Não conte com `min-response-size` para manter respostas pequenas de JSON sem compressão.
2. **O CSV é a exceção**, e é justamente ali que o limiar faz o que promete: o export de NFSe devolve `byte[]` com `Content-Length`, então um CSV pequeno passa sem compressão e um acima de 1 KB é comprimido.

**Sobre o BREACH:** o ataque explora compressão em uma resposta que contém um segredo e reflete entrada do atacante, e depende de o atacante conseguir fazer o navegador da vítima repetir requisições autenticadas enquanto observa o tamanho das respostas. Aqui as pré-condições não existem: a API é stateless com token **Bearer**, sem credencial ambiente (nenhum cookie de sessão), então uma página de terceiros não consegue emitir requisições autenticadas em nome da vítima — e `/auth/login` exige a senha no corpo, ou seja, quem consegue disparar a requisição já tem as credenciais. Por isso a compressão foi mantida também em `/auth/**`, em vez de um filtro de exclusão por rota (que o conector do Tomcat, aliás, não oferece nativamente).

Nada disso altera contrato REST: descomprimido, o corpo é byte a byte o mesmo de antes. Do lado do frontend Angular não há mudança — o navegador negocia e descomprime de forma transparente ao `HttpClient`.

> **Se um dia entrar um proxy reverso na frente da aplicação** (nginx/Traefik terminando o TLS de `api.carlessopilates.com.br`), confira a configuração dele: nginx com `gzip on` repassa sem recomprimir uma resposta que já chega com `Content-Encoding`, então não há trabalho duplicado — mas comprimir só em uma das camadas mantém a configuração em um lugar só. A topologia de produção não está versionada neste repositório.

Verificação rápida com a aplicação de pé:

```bash
# tamanho transferido com e sem compressão
curl -s -H "Authorization: Bearer $TOKEN" -H 'Accept-Encoding: gzip' \
  -o /dev/null -w 'com gzip: %{size_download}\n' http://localhost:8080/evolucoes-sessao/paciente/3
curl -s -H "Authorization: Bearer $TOKEN" \
  -o /dev/null -w 'sem gzip: %{size_download}\n' http://localhost:8080/evolucoes-sessao/paciente/3
```

## Ver também

- [`deploy.md`](deploy.md) — subida dos ambientes e variáveis de ambiente
- [`api.md`](api.md) — contrato REST das rotas monitoradas
