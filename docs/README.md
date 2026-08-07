# Documentação — Carlesso Pilates API

| Arquivo | O que contém |
|---|---|
| [`arquitetura.md`](arquitetura.md) | Camadas, estrutura de pacotes e tratamento de erros |
| [`api.md`](api.md) | Mapa de rotas, paginação, contratos de relatório e regras de preenchimento |
| [`regras-de-negocio.md`](regras-de-negocio.md) | Regras por domínio e processos automáticos (scheduler) |
| [`desenvolvimento.md`](desenvolvimento.md) | CI, estratégia de testes, cobertura, Spotless/SpotBugs e CodeGraph |
| [`deploy.md`](deploy.md) | Docker Compose (dev/prod), execução via Maven e variáveis de ambiente |
| [`operacao.md`](operacao.md) | Actuator, métricas Prometheus, logging estruturado e compressão gzip |
| [`banco-de-dados.md`](banco-de-dados.md) | Migrations Flyway estruturais e de seed |
| [`importacao-seufisio.md`](importacao-seufisio.md) | Importação de pacientes/evoluções e runbook de go-live |
| [`simetrografo-virtual.md`](simetrografo-virtual.md) | Especificação funcional do simetrógrafo virtual |
| [`context.md`](context.md) | Modelo de dados, regras e histórico de decisões de design |

Para instalar e rodar o projeto, veja o [README](../README.md). O contrato REST completo (schemas de request e
response) é gerado pelo springdoc e fica no [Swagger UI](http://localhost:8080/swagger-ui.html) com a aplicação
de pé.

## Onde documentar cada mudança

O `README.md` já foi um arquivo de 1824 linhas porque toda feature nova acrescentava um bloco a ele. Para não
repetir isso, cada tipo de informação tem um dono:

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

Regra prática: se a informação não ajuda alguém a colocar o projeto no ar nos primeiros cinco minutos, ela não
pertence ao `README.md`.

Não mantenha à mão o que o repositório ou o OpenAPI já respondem: árvores de arquivos comentadas classe a
classe, corpos de request/response por rota e catálogos de exemplos `curl` nascem desatualizados e foram
removidos de propósito.
