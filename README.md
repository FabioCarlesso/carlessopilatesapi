# Carlesso Pilates API

API REST para gestão de pacientes e profissionais do estúdio Carlesso Pilates, desenvolvida com Spring Boot 3 e Java 21.

Cobre cadastro de pacientes e profissionais, planos de pagamento, cobranças e geração automática de aulas,
prontuário clínico (anamnese, avaliação fisioterapêutica, análise postural, plano de tratamento, sessões e
evoluções), relatórios de pagamento e de emissão de NFSEs, e um dashboard consolidado.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.5 (Web, Data JPA, Validation, Security, Actuator) |
| Banco | PostgreSQL 16 + Flyway |
| Autenticação | JWT (JJWT 0.12.6) |
| Documentação | springdoc-openapi 2.8.3 |
| Relatórios | OpenPDF (PDF) + Apache POI (XLSX) |
| Métricas | Micrometer + Prometheus |
| Testes | JUnit 5, Mockito, H2, Testcontainers, JaCoCo |
| Build | Maven 3.9 · Docker / Docker Compose |

## Pré-requisitos

- **Docker** e **Docker Compose** — caminho recomendado, não exige nada instalado localmente
- **Java 21** e **PostgreSQL 16**, apenas para rodar via Maven sem container
- Docker também é necessário para a suíte de testes: parte dela sobe um PostgreSQL via Testcontainers

## Como rodar

```bash
git clone <url-do-repositorio>
cd carlessopilatesapi

# Configurar as variáveis de ambiente de desenvolvimento
cp .env.example .env.dev

# Subir banco e aplicação (perfil dev, com seed automático)
docker compose --env-file .env.dev up --build -d

# Acompanhar os logs
docker compose logs -f app
```

A API fica disponível em `http://localhost:8080`. Para derrubar: `docker compose down` (`-v` também remove os
dados do banco).

Execução em produção, uso via Maven e a lista completa de variáveis de ambiente estão em
[`docs/deploy.md`](docs/deploy.md).

## Documentação interativa (Swagger UI)

Com a aplicação rodando:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

> No perfil `prod` a documentação OpenAPI/Swagger fica **desabilitada** (`springdoc.*.enabled=false`); essas
> rotas retornam `404` em produção para não expor o mapa da API. O mapa de rotas versionado está em
> [`docs/api.md`](docs/api.md).

## Testes

```bash
mvn verify
```

O mesmo `verify` roda a suíte completa, checa formatação (Spotless), faz análise estática (SpotBugs) e aplica o
gate de cobertura (JaCoCo). Detalhes em [`docs/desenvolvimento.md`](docs/desenvolvimento.md).

## Documentação

| Arquivo | O que contém |
|---|---|
| [`docs/arquitetura.md`](docs/arquitetura.md) | Camadas, estrutura de pacotes e tratamento de erros |
| [`docs/api.md`](docs/api.md) | Mapa de rotas, paginação e contratos de relatório |
| [`docs/regras-de-negocio.md`](docs/regras-de-negocio.md) | Regras por domínio e processos automáticos |
| [`docs/desenvolvimento.md`](docs/desenvolvimento.md) | CI, testes, cobertura e CodeGraph |
| [`docs/deploy.md`](docs/deploy.md) | Docker Compose, Maven e variáveis de ambiente |
| [`docs/operacao.md`](docs/operacao.md) | Actuator, métricas, logging e compressão gzip |
| [`docs/banco-de-dados.md`](docs/banco-de-dados.md) | Migrations Flyway |
| [`docs/importacao-seufisio.md`](docs/importacao-seufisio.md) | Importação do seufisio e runbook de go-live |
| [`docs/simetrografo-virtual.md`](docs/simetrografo-virtual.md) | Especificação do simetrógrafo virtual |
| [`docs/context.md`](docs/context.md) | Modelo de dados e decisões de design |

Antes de documentar uma mudança, veja em [`docs/README.md`](docs/README.md) qual arquivo é o dono do assunto.

## Licença

Este projeto está licenciado sob a licença MIT. Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.
