# Plano de Melhorias — Carlesso Pilates API

> Análise realizada em 05/07/2026 sobre o branch `master` (commit `c621d49`).
> Este documento é um **planejamento**: nada aqui foi implementado.

## Diagnóstico geral

O projeto está maduro para o seu porte: Spring Boot 3 + Java 21, Flyway com 27 migrations
organizadas (estrutural × seed), autenticação JWT stateless com invalidação por
`token_version`, rate limiting de login, soft delete consistente, 55 classes de teste
(unitários, integração, concorrência), CI com três jobs (build+testes, Flyway contra
PostgreSQL real, build Docker), Swagger e documentação extensa.

As melhorias abaixo estão organizadas por tema e priorizadas em quatro fases ao final.

---

## 1. Correções e lacunas de maior impacto

### 1.1 Handler de erros de validação ausente (bug de contrato de API)
`GlobalExceptionHandler` não trata `MethodArgumentNotValidException` nem
`HandlerMethodValidationException`. Como `server.error.include-message` e
`include-binding-errors` estão como `never`, um erro de Bean Validation (`@NotBlank`,
`@Email`, `@Min`…) retorna **400 com corpo vazio** — o frontend Angular não tem como
saber qual campo falhou. Também não há handler genérico de `Exception` com log, então
erros 500 inesperados não deixam rastro estruturado.

**Plano:** adicionar ao `GlobalExceptionHandler`:
- `MethodArgumentNotValidException` → 400 com mapa `campo → mensagem`;
- `HandlerMethodValidationException` e `ConstraintViolationException` → 400 idem;
- `Exception` genérico → 500 com mensagem neutra e `log.error` com stacktrace.

### 1.2 SMTP não chega ao container (fluxo de recuperação de senha quebrado no Docker)
`docker-compose.yml` não repassa `SMTP_HOST/SMTP_PORT/SMTP_USERNAME/SMTP_PASSWORD` nem
`APP_EMAIL_*` para o serviço `app`. Qualquer deploy via compose (inclusive produção)
sobe com `spring.mail.host` vazio e o `forgot-password` falha no envio do e-mail.

**Plano:** adicionar as variáveis de e-mail ao bloco `environment` do `app` no
`docker-compose.yml` (com defaults vazios) e documentar no `.env.example`.

### 1.3 Testes rodam em H2; produção em PostgreSQL
Nos testes o Flyway fica desabilitado e o schema é gerado pelo Hibernate
(`ddl-auto=create-drop`). Consequências:
- as migrations nunca são exercitadas pela suíte (o job `flyway-postgres` do CI só
  valida que elas *aplicam*, não o comportamento);
- recursos específicos do PostgreSQL — como o índice **parcial** de unicidade da V23
  (`WHERE email IS NOT NULL`) — não existem no banco de teste;
- diferenças de dialeto (tipos, locking, `ON CONFLICT`) passam despercebidas.

**Plano:** migrar os testes de repositório e integração para **Testcontainers**
(PostgreSQL 16) com Flyway habilitado, mantendo H2 apenas para testes puramente de
serviço/web se o tempo de build preocupar. Ajustar o CI (Docker já disponível no runner).

### 1.4 Upgrade do Spring Boot 3.4.5 → 3.5.x
A linha 3.4.x saiu do suporte OSS em meados de 2026. Atualizar o parent para a última
3.5.x e o `springdoc-openapi` para a versão compatível mais recente. Revisar release
notes (poucas quebras esperadas; atenção a `DaoAuthenticationProvider` deprecations).

### 1.5 Padronizar o contrato de erro (RFC 9457 — ProblemDetail)
Hoje cada erro retorna `{"erro": "..."}` ad-hoc. O Spring 6 tem suporte nativo a
`ProblemDetail`. Padronizar (tipo, título, status, detalhe, campo(s) inválido(s),
timestamp) melhora o consumo no frontend e resolve o item 1.1 de forma uniforme.
Exige coordenação com o frontend Angular — tratar como mudança de contrato versionada.

---

## 2. Segurança

### 2.1 Refresh token e TTL do access token
O access token dura 24 h (`JWT_EXPIRATION_MS` default) e não há refresh token: logout
real só via troca de senha (`token_version`). **Plano:** reduzir o TTL do access token
(15–60 min) e adicionar refresh token rotativo persistido (tabela própria, revogável),
reaproveitando o padrão já usado em `password_reset_tokens` (hash SHA-256 no banco).

### 2.2 Rate limiting: por IP e preparado para múltiplas instâncias
`LoginAttemptService` é in-memory e chaveado só por e-mail: não sobrevive a restart,
não funciona com 2+ réplicas e um atacante pode variar o e-mail livremente.
**Plano:** acrescentar bucket por IP (com cuidado com proxies/`X-Forwarded-For`);
se houver plano de escalar horizontalmente, mover o estado para Redis — caso
contrário, documentar a limitação como decisão consciente.

### 2.3 Hardening do container
O `Dockerfile` roda como **root** e sem `HEALTHCHECK`. **Plano:**
- criar usuário não-root (`adduser -S app`) e `USER app`;
- `HEALTHCHECK` apontando para `/actuator/health`;
- extrair camadas do jar (layered jars) para melhorar cache de build;
- configurar memória para container (`-XX:MaxRAMPercentage=75`).

### 2.4 Varredura de dependências e atualizações automáticas
Não há `dependabot.yml` nem verificação de CVEs. **Plano:** adicionar
Dependabot (maven, github-actions, docker) e um job de OWASP Dependency-Check ou
`mvn versions:display-dependency-updates` periódico; considerar CodeQL.

### 2.5 Ajustes menores
- CORS: `allowCredentials(true)` é desnecessário com Bearer token via header —
  remover reduz superfície (cookies nunca são usados).
- Revisar headers de segurança (HSTS quando atrás de TLS; demais defaults do
  Spring Security já cobrem X-Content-Type-Options etc.).

---

## 3. Robustez operacional e observabilidade

### 3.1 Logging estruturado e abrangente
Apenas 2 classes usam logger (`CobrancaScheduler`, `JwtAuthenticationFilter`).
**Plano:** logs de negócio nos serviços críticos (criação/confirmação de pagamento,
bloqueio de login, emissão de NFSE, bootstrap do admin), filtro de correlation-id
(MDC) e, em produção, saída JSON (logback encoder) para facilitar agregação.

### 3.2 Métricas
Só `health` é exposto. **Plano:** adicionar `micrometer-registry-prometheus`,
expor `/actuator/prometheus` protegido (rede interna ou auth), e métricas de negócio
(cobranças geradas, pagamentos confirmados, logins bloqueados).

### 3.3 Schedulers sem lock distribuído
`CobrancaScheduler` em 2+ instâncias duplicaria trabalho (a unique constraint
`(plano_id, periodo_inicio)` protege parcialmente, mas com corrida e erro).
**Plano:** ShedLock (tabela no próprio PostgreSQL) se houver réplicas; caso o deploy
seja sempre single-instance, documentar a restrição.

### 3.4 Backup do banco de produção
O compose de produção não tem estratégia de backup. **Plano:** documentar/automatizar
`pg_dump` agendado com retenção e teste de restore.

### 3.5 Entrega contínua (CD)
O CI builda a imagem mas não publica. **Plano:** job de push para o GHCR com tags
por SHA e por release (`v*`), e um runbook de deploy (pull + `docker compose up -d`).

---

## 4. Qualidade de código e manutenção

### 4.1 Cobertura de testes medida (JaCoCo)
A suíte é grande, mas não há medição. **Plano:** plugin JaCoCo no `verify`, publicar
relatório como artefato do CI e definir um gate mínimo razoável (ex.: 70–80% em linhas,
sem “caça a número”).

### 4.2 Análise estática e formatação
Nada configurado. **Plano:** Spotless (formatação determinística) + SpotBugs ou
Error Prone no build; falhar o CI em findings novos.

### 4.3 Otimizações de persistência
- `PagamentoService.atualizarVencidos()` carrega todas as entidades e altera uma a
  uma — trocar por `@Modifying UPDATE ... WHERE status = 'PENDENTE' AND ...` (1 query).
- `gerarCobrancasFuturas()` faz 2 queries por plano (N+1) — aceitável na escala atual;
  otimizar com fetch join / consulta agregada se a base crescer.
- `Plano.diasSemana` usa `@ElementCollection(fetch = EAGER)` — revisar necessidade.

### 4.4 Paginação nos sub-recursos
`GET /pagamentos/paciente/{id}`, `GET /aulas/paciente/{id}`, listas de avaliações,
sessões e evoluções retornam a lista completa. Com histórico de anos isso cresce sem
limite. **Plano:** aceitar `Pageable` nesses endpoints (mantendo compatibilidade com o
frontend — pode ser aditivo, com default alto).

### 4.5 Locking otimista
Nenhuma entidade tem `@Version`: dois PUTs concorrentes se sobrescrevem em silêncio.
**Plano:** adicionar `@Version` nas entidades editáveis (paciente, profissional,
avaliação, plano de tratamento, evolução…), retornando 409 em conflito. Exige
migration + envio do campo pelo frontend.

### 4.6 Consistência e versionamento de rotas
Convivem `/pacientes` (sem prefixo) e `/api/nfse-emitidas`, `/api/relatorios/nfse`
(com prefixo). **Plano:** padronizar tudo sob um prefixo único e versionado
(ex.: `/api/v1/...`), com período de transição/redirect coordenado com o frontend.

### 4.7 Documentação
- README com ~1.270 linhas: extrair contratos de endpoint (já cobertos pelo Swagger)
  e detalhes de migrations para `docs/`, deixando o README como guia de partida.
- Remover `docs/documentacao.docx` (binário versionado) — manter apenas `.md`/`.html`
  ou mover para releases/wiki.

### 4.8 Boilerplate (opcional, baixa prioridade)
DTOs já são records (ótimo). As entidades têm muito getter/setter manual — Lombok
(`@Getter/@Setter`) e MapStruct para mapeamentos reduziriam código, mas é escolha de
estilo; só adotar se o time quiser a dependência.

---

## 5. Evoluções funcionais (backlog de produto)

1. **Agenda com horário**: `Aula` tem apenas `data` — sem hora não há como detectar
   conflito de horário de profissional/sala nem montar agenda diária.
2. **Lembretes de cobrança por e-mail**: a infra de e-mail (SMTP + templates Thymeleaf)
   já existe; um job diário poderia avisar vencimentos próximos/vencidos.
3. **Relatório financeiro consolidado**: receita × repasse a profissionais por
   período (hoje existe por profissional, falta a visão do estúdio).
4. **Auditoria**: Spring Data Auditing (`@CreatedBy/@LastModifiedBy`) — relevante por
   se tratar de dados clínicos.
5. **LGPD**: política de retenção, exportação e anonimização de dados de paciente
   (dados de saúde são dados sensíveis).

---

## Roadmap sugerido

| Fase | Objetivo | Itens |
|---|---|---|
| **1 — Correções rápidas** (dias) | Fechar bugs de contrato e gaps de deploy | 1.1 handler de validação, 1.2 SMTP no compose, 2.3 Dockerfile non-root, 2.4 Dependabot, 4.1 JaCoCo |
| **2 — Fundação técnica** (1–2 semanas) | Confiabilidade da suíte e da plataforma | 1.3 Testcontainers, 1.4 upgrade 3.5.x, 3.1 logging, 3.2 métricas, 4.2 análise estática |
| **3 — Segurança e escala** (2–3 semanas) | Preparar para produção séria | 2.1 refresh token, 2.2 rate limit por IP, 1.5 ProblemDetail, 4.4 paginação, 4.5 `@Version`, 3.3 ShedLock, 3.5 CD, 3.4 backup |
| **4 — Produto** (contínuo) | Valor de negócio | Seção 5 + 4.6 versionamento de rotas |

Cada item das fases 1–3 cabe em um PR pequeno e independente, o que facilita revisão
e rollback.
