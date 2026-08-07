# Desenvolvimento

Pipeline de integração contínua, estratégia de testes e ferramental do projeto.

## Integração Contínua (CI)

O projeto roda um pipeline no **GitHub Actions** (`.github/workflows/ci.yml`) a cada `push` e `pull_request` para `master` (e sob demanda via `workflow_dispatch`). São três jobs:

| Job | O que faz |
|---|---|
| `build-test` | Compila com JDK 21 (Temurin) e roda toda a suíte de testes com `mvn -B verify`. O mesmo `verify` também checa formatação (**Spotless**) e roda a análise estática (**SpotBugs**). Testes unitários e de controller usam H2 em memória; testes de repositório e integração sobem um PostgreSQL 16 via Testcontainers (o runner já tem Docker). Publica os relatórios de teste e o relatório de cobertura JaCoCo como artefatos (`surefire-reports` e `jacoco-report`). |
| `flyway-postgres` | Sobe um PostgreSQL 16 e aplica todas as migrations com `mvn flyway:migrate` + `flyway:validate`. Complementa a validação das migrations (os testes de integração via Testcontainers já as exercitam). |
| `docker-build` | Builda a imagem a partir do `Dockerfile` multi-stage (sem push para registry). |

Os jobs `flyway-postgres` e `docker-build` dependem de `build-test`. Nenhum segredo de produção é usado no CI.

Para reproduzir localmente os mesmos passos:

```bash
# Build + testes (equivalente ao job build-test)
mvn -B verify

# Validar as migrations contra um PostgreSQL local (equivalente ao job flyway-postgres)
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/carlesso_pilates \
  -Dflyway.user=postgres -Dflyway.password=postgres

# Build da imagem (equivalente ao job docker-build)
docker build -t carlessopilatesapi:ci .
```

> O plugin `flyway-maven-plugin` está configurado no `pom.xml` apontando para `filesystem:src/main/resources/db/migration`; a conexão é passada por linha de comando.

## Estratégia de testes

A suíte usa dois bancos, conforme o que cada teste precisa exercitar:

- **H2 em memória** (padrão em `src/test/resources/application.properties`, com Flyway
  desabilitado e schema via `ddl-auto=create-drop`) — testes unitários, de serviço
  (Mockito) e de controller (`@WebMvcTest`), que não dependem de recursos específicos
  do PostgreSQL.
- **PostgreSQL 16 via Testcontainers** — testes de repositório (`@DataJpaTest`) e de
  integração (`@SpringBootTest`), que herdam de
  `com.carlesso.pilatesapi.support.PostgresTestcontainerSupport`. Essa base sobe um
  container **singleton** (reaproveitado por toda a suíte) e deixa o **Flyway** criar o
  schema, igual à produção. Assim as migrations são exercitadas pelos testes e recursos
  como o **índice parcial de unicidade** da V23 (`WHERE email/cpf IS NOT NULL`), que não
  existe no H2, ficam cobertos.

> Rodar esses testes exige **Docker** disponível na máquina. No CI o runner
> `ubuntu-latest` já o fornece; localmente, garanta o daemon ativo antes de `mvn verify`.

## Cobertura de testes (JaCoCo)

O `mvn verify` mede a cobertura com o **JaCoCo** e falha o build se a cobertura de linhas ficar abaixo do gate mínimo (propriedade `jacoco.line.coverage.minimum` no `pom.xml`, hoje **90%**). DTOs (records) e a classe main ficam fora do gate, mas continuam visíveis no relatório.

Para consultar a cobertura:

- **Localmente:** rode `mvn verify` e abra `target/site/jacoco/index.html` no navegador.
- **No CI:** baixe o artefato `jacoco-report` do job `build-test` (aba *Actions* → execução → *Artifacts*) e abra o `index.html`.

O gate existe para impedir regressão da suíte — a intenção é subi-lo gradualmente, não persegui-lo. Rode sempre o `mvn verify` completo: o relatório e o gate dependem dos dados de execução dos testes (`target/jacoco.exec`), então `-DskipTests` deixaria o JaCoCo sem medir e o gate passaria sem verificar nada.

## Segurança de dependências

| Recurso | Arquivo | O que faz |
|---|---|---|
| **Dependabot** | `.github/dependabot.yml` | Verifica semanalmente (segunda-feira, 06:00 BRT) atualizações nos ecossistemas `maven` (pom.xml), `github-actions` (workflows) e `docker` (imagens base do Dockerfile), abrindo PRs automaticamente. Atualizações patch/minor são agrupadas em um único PR por ecossistema; majors abrem PRs individuais. |
| **CodeQL** | `.github/workflows/codeql.yml` | Análise estática de segurança (SAST) do código Java a cada `push`/`pull_request` para `master` e semanalmente via agenda. Resultados aparecem em *Security → Code scanning*. |

> Os alertas de vulnerabilidade (*Dependabot alerts*) são habilitados nas configurações do repositório (*Settings → Advanced Security*); o monitoramento dos três ecossistemas pode ser conferido em *Insights → Dependency graph → Dependabot*.

## Formatação e análise estática (Spotless + SpotBugs)

O build aplica duas verificações automáticas de qualidade, ambas executadas no `mvn verify` (e, portanto, no CI):

- **Spotless** (`spotless-maven-plugin`) impõe formatação determinística com o **palantir-java-format** (indentação de 4 espaços, imports não utilizados removidos). O goal `spotless:check` roda na fase `check`, então `mvn verify` **falha se algum arquivo estiver fora do formato**.
- **SpotBugs** (`spotbugs-maven-plugin`) faz análise estática de bytecode com `effort=Max` e `threshold=High`, falhando o build **apenas em findings de alta confiança** (evita ruído de falsos positivos). Exclusões justificadas ficam em `config/spotbugs-exclude.xml`.

Comandos úteis:

```bash
# Reformatar todo o código-fonte no padrão do projeto
mvn spotless:apply

# Verificar formatação sem alterar arquivos (o que o CI faz)
mvn spotless:check

# Rodar a análise estática e falhar em findings de alta severidade
mvn spotbugs:check

# Abrir a UI do SpotBugs para inspecionar os findings
mvn spotbugs:gui
```

> Dica: rode `mvn spotless:apply` antes de commitar. Se o CI reprovar por formatação, esse comando resolve automaticamente.

## Testes

As suítes ficam em `src/test/java/com/carlesso/pilatesapi/`, espelhando os pacotes de `main`, e se dividem
por camada:

| Camada | Anotação | O que exercita |
|---|---|---|
| Serviço | `@ExtendWith(MockitoExtension.class)` | Regras de negócio com repositórios mockados, sem contexto Spring |
| Controller | `@WebMvcTest` | Contrato REST: status, payload e validação, com o service mockado |
| Repositório | `@DataJpaTest` | Queries e constraints — os que herdam de `support/PostgresTestcontainerSupport` sobem PostgreSQL real |
| Integração | `@SpringBootTest` | Fluxos ponta a ponta: segurança, scheduler, recuperação de senha e Actuator |

Utilitários sem dependência de Spring (`LogMasker`, `CorrelationIdFilter`) têm testes próprios, sem mocks.

A contagem de testes por suíte não é mantida aqui: `mvn verify` imprime o total por classe e o relatório em
`target/site/jacoco/index.html` mostra a cobertura real de cada uma.

### Executar os testes

```bash
JAVA_HOME=/caminho/para/jdk21 mvn test
```

Os testes de serviço e controller não necessitam de banco de dados. O `@SpringBootTest` usa H2 em memória automaticamente via `src/test/resources/application.properties`.

## CodeGraph

O projeto está configurado para usar o [CodeGraph](https://github.com/colbymchenry/codegraph), um grafo de conhecimento de código que permite a agentes de IA (Claude Code, Cursor, etc.) localizar símbolos, entry points e relações entre chamadas em uma única consulta, sem precisar explorar arquivo por arquivo.

A configuração já está versionada no repositório (`.mcp.json`, `.claude/settings.json`, `.claude/CLAUDE.md`); o índice em si (`.codegraph/`) é local a cada máquina e não é versionado.

**Para usar em uma nova cópia do repositório:**

```bash
# 1. Instalar a CLI (sem Node.js)
curl -fsSL https://raw.githubusercontent.com/colbymchenry/codegraph/main/install.sh | sh
# ou, com Node.js 22.5+:
npm i -g @colbymchenry/codegraph

# 2. Construir o índice do projeto (o agente já está configurado via .mcp.json)
codegraph init
```

Após alterações no código, o CodeGraph reindexiza automaticamente em segundo plano. Para forçar uma sincronização ou verificar o status: `codegraph sync` / `codegraph status`.

Após alterações no código, o CodeGraph reindexiza automaticamente em segundo plano. Para forçar uma
sincronização ou verificar o status: `codegraph sync` / `codegraph status`.

## Ver também

- [`deploy.md`](deploy.md) — subir o projeto localmente e em produção
- [`banco-de-dados.md`](banco-de-dados.md) — migrations Flyway exercitadas pelos testes de integração
- [`arquitetura.md`](arquitetura.md) — camadas e estrutura de pacotes
