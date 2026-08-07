<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Comandos

| Comando | Quando usar |
|---|---|
| `docker compose --env-file .env.dev up --build -d` | Sobe banco e aplicação no perfil dev |
| `mvn verify` | Suíte completa + Spotless + SpotBugs + gate JaCoCo — o mesmo do job `build-test` da CI |
| `mvn spotless:apply` | Reformata o código no padrão do projeto; rode antes de commitar |
| `mvn spring-boot:run` | Aplicação local, exigindo PostgreSQL de pé |

O `mvn verify` exige **Docker** ativo: testes de repositório e integração sobem um PostgreSQL 16 via
Testcontainers.

## Onde documentar cada mudança

O `README.md` chegou a 1824 linhas porque toda feature nova acrescentava um bloco a ele. Não volte a fazer
isso. Cada assunto tem um dono:

| Se você mudou… | Documente em |
|---|---|
| Uma rota (nova, removida, com parâmetros ou contrato diferente) | `docs/api.md` |
| Uma regra de negócio, validação ou processo automático | `docs/regras-de-negocio.md` |
| Estrutura de pacotes, camadas ou tratamento de erros | `docs/arquitetura.md` |
| Pipeline de CI, testes, cobertura ou ferramental | `docs/desenvolvimento.md` |
| Docker, perfis, execução local ou variável de ambiente | `docs/deploy.md` |
| Actuator, métrica, log ou compressão | `docs/operacao.md` |
| Uma migration Flyway | `docs/banco-de-dados.md` |
| Os scripts de importação do seufisio | `docs/importacao-seufisio.md` |
| Uma decisão técnica e o porquê dela | `docs/context.md` |
| **Como instalar ou rodar o projeto** | `README.md` — e só nesse caso |

Não mantenha à mão o que o repositório ou o OpenAPI já respondem: árvores de arquivos comentadas classe a
classe, corpos de request/response por rota e catálogos de exemplos `curl` nascem desatualizados e foram
removidos de propósito.
