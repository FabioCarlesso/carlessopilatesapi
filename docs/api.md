# API REST

Base URL: `http://localhost:8080`

Os schemas de request e response são gerados pelo **springdoc-openapi** e ficam disponíveis no
[Swagger UI](http://localhost:8080/swagger-ui.html) (`/api-docs` para o JSON). Este documento cobre o que o
OpenAPI não expressa: o mapa de rotas, os contratos de relatório e as regras de preenchimento que não estão
no schema.

> No perfil `prod` a documentação OpenAPI fica desabilitada (`springdoc.*.enabled=false`) e essas rotas
> retornam `404`, para não expor o mapa da API. Este arquivo é a referência nesse cenário.

## Autenticação

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/auth/login` | Público | Valida e-mail/senha e retorna JWT. Retorna `429` após 5 tentativas falhas em 15 min |
| `POST` | `/auth/forgot-password` | Público | Solicita redefinição de senha por e-mail. Sempre retorna `200` com mensagem genérica, mesmo se o e-mail não existir (evita enumeração de usuários). Retorna `429` após 5 solicitações em 15 min para o mesmo e-mail |
| `POST` | `/auth/reset-password` | Público | Redefine a senha a partir de `token`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`. Retorna `422` para token inválido, expirado, já utilizado ou confirmação divergente |
| `GET` | `/users/me` | Autenticado | Retorna dados seguros do usuário autenticado |
| `PUT` | `/users/me/senha` | Autenticado | Troca a própria senha informando `senhaAtual`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`. Retorna `422` para senha atual incorreta, confirmação divergente ou reuso da senha atual. Tokens emitidos antes da troca deixam de autorizar rotas protegidas |
| `GET` | `/users/me/preferencias` | Autenticado | Retorna as preferências do usuário autenticado (idioma, tema e notificações). Usuário sem preferências salvas recebe os valores padrão |
| `PUT` | `/users/me/preferencias` | Autenticado | Atualiza as preferências do usuário autenticado. Valida `idioma` (`PT_BR`, `EN_US`, `ES_ES`) e `tema` (`CLARO`, `ESCURO`); valores inválidos retornam `400` |
| `POST` | `/users` | `ADMIN` | Cria usuário com role `USER` ou `ADMIN` |
| `GET` | `/users` | `ADMIN` | Lista usuários cadastrados sem expor senha |
| `GET` | `/users/roles` | `ADMIN` | Lista as roles disponíveis (`value` e `label`) para uso em formulários administrativos |
| `GET` | `/users/{id}` | `ADMIN` | Busca usuário por ID |
| `PUT` | `/users/{id}` | `ADMIN` | Atualiza nome, e-mail, senha e perfil. Admin não pode alterar o próprio role nem rebaixar o último ADMIN ativo |
| `DELETE` | `/users/{id}` | `ADMIN` | Inativa usuário (soft delete). Admin não pode inativar a própria conta nem o último ADMIN ativo |
| `GET` | `/admin/health` | `ADMIN` | Endpoint inicial administrativo |

As demais rotas de negócio exigem `Authorization: Bearer <accessToken>`. Tokens ausentes, inválidos, expirados ou emitidos antes da última troca/redefinição de senha retornam `401 Unauthorized`; usuário sem role `ADMIN` em `/admin/**` e no CRUD de `/users` recebe `403 Forbidden`.

O cadastro de usuários é restrito a administradores (`POST /users`); não há registro público. Em desenvolvimento, use um dos [usuários de seed](banco-de-dados.md#usuários-de-seed-perfil-dev); em produção, o admin inicial criado a partir de `APP_INITIAL_ADMIN_*`.

## Pacientes

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/pacientes` | Cadastrar novo paciente |
| `GET` | `/pacientes` | Listar e filtrar pacientes (paginado) |
| `GET` | `/pacientes/{id}` | Buscar paciente por ID |
| `PUT` | `/pacientes/{id}` | Atualizar dados do paciente |
| `PATCH` | `/pacientes/{id}/ativar` | Reativar paciente |
| `PATCH` | `/pacientes/{id}/inativar` | Inativar paciente (soft delete) |

Apenas `nome` é obrigatório. `email` e `cpf` são opcionais — alguns sistemas externos não fornecem esses dados na [importação inicial](importacao-seufisio.md); quando informado, `email` precisa ter formato válido. O `PUT` é uma atualização parcial: apenas os campos enviados são alterados.

## Profissionais

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/profissionais` | Cadastrar novo profissional |
| `GET` | `/profissionais` | Listar e filtrar profissionais (paginado) |
| `GET` | `/profissionais/{id}` | Buscar profissional por ID |
| `PUT` | `/profissionais/{id}` | Atualizar dados do profissional |
| `PATCH` | `/profissionais/{id}/ativar` | Reativar profissional |
| `PATCH` | `/profissionais/{id}/inativar` | Inativar profissional (soft delete) |
| `GET` | `/profissionais/{id}/relatorio-pagamento` | Gerar relatório de pagamento por período (JSON) |
| `GET` | `/profissionais/{id}/relatorio-pagamento/pdf` | Exportar relatório de pagamento em PDF |
| `GET` | `/profissionais/{id}/relatorio-pagamento/xlsx` | Exportar relatório de pagamento em Excel (XLSX) |

Campos obrigatórios: `nome`, `email`, `cpf`, `tipoContrato`, `percentualPagamentoAula` e `dataInicio`.

`numeroRegistro` é opcional (máx. 30 caracteres), sem validação de formato — acomoda CREFITO, CREF e outros conselhos. No `PUT /profissionais/{id}`, enviá-lo como string vazia limpa o campo; omitir mantém o valor atual.

## Planos de Pagamento

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/planos` | Criar plano para paciente |
| `GET` | `/planos/{id}` | Buscar plano por ID |
| `GET` | `/planos/paciente/{id}` | Listar planos do paciente |
| `GET` | `/planos/paciente/{id}/ativo` | Buscar plano ativo do paciente |
| `DELETE` | `/planos/{id}` | Inativar plano |

## Pagamentos

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/pagamentos` | Criar pagamento (PENDENTE) |
| `GET` | `/pagamentos/{id}` | Buscar pagamento por ID |
| `GET` | `/pagamentos/paciente/{id}` | Listar pagamentos do paciente |
| `PATCH` | `/pagamentos/{id}/pagar` | Confirmar pagamento e gerar aulas; aceita `dataPagamento` opcional no corpo |

## Aulas

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/aulas` | Listar aulas por período (agenda do estúdio); filtros `profissionalId`, `pacienteId` e `realizada` |
| `GET` | `/aulas/{id}` | Buscar aula por ID |
| `GET` | `/aulas/paciente/{id}` | Listar aulas do paciente |
| `GET` | `/aulas/pagamento/{id}` | Listar aulas de um pagamento |
| `PATCH` | `/aulas/{id}/realizar` | Marcar aula como realizada, opcionalmente com `profissionalId` |
| `PATCH` | `/aulas/{id}/profissional` | Atribuir ou desvincular o profissional de uma aula ainda não realizada |

## Sessões de Pilates/Fisioterapia

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/sessoes` | Registrar sessão de Pilates ou Fisioterapia para um paciente |
| `GET` | `/sessoes` | Listar sessões por período (agenda do estúdio); filtros `profissionalId`, `pacienteId`, `tipo` e `status` |
| `GET` | `/sessoes/{id}` | Buscar sessão por ID |
| `GET` | `/sessoes/paciente/{pacienteId}` | Listar sessões do paciente |
| `PUT` | `/sessoes/{id}` | Atualizar sessão (parcial, exceto `status`) |
| `PATCH` | `/sessoes/{id}/realizar` | Marcar sessão como `REALIZADA` (apenas a partir de `AGENDADA`) |
| `PATCH` | `/sessoes/{id}/cancelar` | Cancelar sessão (apenas a partir de `AGENDADA`) |
| `DELETE` | `/sessoes/{id}` | Excluir sessão permanentemente |

### Listagem por período — agenda do estúdio

`GET /aulas` e `GET /sessoes` são as consultas que montam o calendário do estúdio e a agenda de um profissional.
Ambas exigem `inicio` e `fim` (`YYYY-MM-DD`, período fechado — inclusive nas duas pontas) e aceitam os filtros
opcionais acima, combináveis entre si:

```
GET /aulas?inicio=2025-02-01&fim=2025-02-28&profissionalId=7&realizada=false
```

- Período ausente, invertido (`inicio` posterior a `fim`) ou com amplitude acima de **92 dias** retorna `400`. O teto
  existe porque estas rotas não são paginadas: o recorte é o próprio período.
- Resultado acima de **5000 registros** também retorna `400` — o teto de dias limita a janela, não o volume, e um
  período curto de um estúdio movimentado ainda pode encher a resposta. Mesmo limite do relatório de pagamento do
  profissional. Reduza o intervalo ou aplique filtros.
- Período sem registros retorna `200` com lista vazia — nunca `404`.
- `GET /aulas` traz apenas aulas de **pacientes ativos**, mesma regra das demais consultas de aulas. `GET /sessoes`
  traz também as de pacientes inativos: sessão é registro clínico e o histórico do ex-aluno continua legível, o mesmo
  critério de `GET /sessoes/paciente/{id}`.
- Ordenação: aulas por `data` e `id`; sessões por `data`, `horario` e `id`, com as sessões sem `horario` no fim do
  respectivo dia.
- `AulaResponseDTO` expõe `profissionalId` e `profissionalNome` (ambos `null` enquanto a aula não tem profissional
  vinculado) — o calendário usa esses campos para colorir e filtrar por profissional.

## Avaliações Fisioterapêuticas

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/avaliacoes-fisioterapeuticas` | Criar avaliação fisioterapêutica para um paciente |
| `GET` | `/avaliacoes-fisioterapeuticas/{id}` | Buscar avaliação fisioterapêutica por ID |
| `GET` | `/avaliacoes-fisioterapeuticas/paciente/{pacienteId}` | Listar avaliações fisioterapêuticas do paciente |
| `PUT` | `/avaliacoes-fisioterapeuticas/{id}` | Atualizar dados da avaliação fisioterapêutica |

Campos obrigatórios: `pacienteId`, `dataAvaliacao`, `queixaFuncional`, `escalaDor` (0 a 10) e `diagnosticoFisioterapeutico`.

## Análises Posturais (simetrógrafo virtual)

Especificação funcional completa em [`simetrografo-virtual.md`](simetrografo-virtual.md).

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/avaliacoes-posturais` | Criar análise postural (status `RASCUNHO`) para uma avaliação e vista |
| `GET` | `/avaliacoes-posturais/{id}` | Buscar análise por ID (com métricas calculadas) |
| `GET` | `/avaliacoes-posturais/avaliacao-fisioterapeutica/{avaliacaoId}` | Listar análises ativas da avaliação |
| `PUT` | `/avaliacoes-posturais/{id}` | Atualizar landmarks, linha de prumo, calibração e observações (apenas `RASCUNHO`) |
| `PUT` | `/avaliacoes-posturais/{id}/foto` | Enviar/substituir a foto da análise (multipart, JPEG/PNG até 2 MB, apenas `RASCUNHO`) |
| `GET` | `/avaliacoes-posturais/{id}/foto` | Recuperar o binário da foto da análise |
| `PATCH` | `/avaliacoes-posturais/{id}/concluir` | Concluir análise (exige pontos obrigatórios completos e foto) |
| `PATCH` | `/avaliacoes-posturais/{id}/cancelar` | Cancelar análise (exclusão lógica) |

`vista` aceita `FRENTE`, `COSTAS`, `LADO_DIREITO` ou `LADO_ESQUERDO`. A análise nasce em `RASCUNHO` e cada
avaliação admite no máximo **uma análise ativa por vista** (duplicata retorna `409`).

### Landmarks

Coordenadas são **normalizadas (0 a 1)** relativas à imagem; valores fora do intervalo retornam `400`. Os códigos
válidos dependem da vista — código de outra vista retorna `422`:

| Vista | Códigos aceitos |
|---|---|
| `FRENTE` / `COSTAS` | `OLHO_ESQ`, `OLHO_DIR`, `OMBRO_ESQ`, `OMBRO_DIR`, `QUADRIL_ESQ`, `QUADRIL_DIR`, `JOELHO_ESQ`, `JOELHO_DIR`, `TORNOZELO_ESQ`, `TORNOZELO_DIR` |
| `LADO_DIREITO` / `LADO_ESQUERDO` | `ORELHA`, `OMBRO`, `QUADRIL`, `JOELHO`, `TORNOZELO` |

### Upload da foto

`PUT /avaliacoes-posturais/{id}/foto` recebe `multipart/form-data` com o campo `foto` (JPEG ou PNG, máx. **2 MB**;
o formato é validado pelos *magic bytes* do conteúdo, não pela extensão).

- Apenas análises em `RASCUNHO` aceitam foto; um novo envio **substitui** a anterior. Em análise `CONCLUIDA` retorna `422` (cancele a análise e crie outra).
- Arquivo que não é JPEG/PNG (mesmo renomeado) ou corrompido retorna `400`; acima de 2 MB retorna `413`; acima de **10000 px** por lado retorna `400` (proteção contra decompression bomb).
- Largura e altura em pixels são extraídas apenas do header da imagem (sem decodificar os pixels) e persistidas junto do binário.

O `GET /avaliacoes-posturais/{id}/foto` devolve o binário com `Content-Type` do upload e
`Content-Disposition: inline; filename="avaliacao-postural-{id}.jpg"` (`.png` quando PNG); análise sem foto retorna
`404`. O binário fica em tabela própria (`avaliacoes_posturais_fotos`), fora das listagens e buscas da análise.

### Métricas

O bloco `metricas` da resposta:

- É **somente leitura**: as métricas nunca são aceitas em requisições e são recalculadas a cada `PUT`.
- Métricas cujos pontos ainda não foram marcados vêm `null` (vistas laterais não têm pares, então saem sem desníveis).
- Os ângulos vêm da reta entre os dois pontos (`atan2` sobre as coordenadas normalizadas), no intervalo `(-90, 90]`; positivo indica o ponto da direita da imagem mais baixo, `0` indica pontos nivelados.
- `proporcaoImagem` (largura/altura) corrige a distorção dos eixos em fotos não quadradas; sem ela o cálculo assume imagem quadrada.
- `desvioPrumoNormalizado` é a distância horizontal entre a linha de prumo e a referência do tronco (ponto médio dos acrômios nas vistas frontais, acrômio nas laterais).
- `desvioPrumoCm` **só aparece quando há `calibracaoCmPorUnidade`**; sem calibração, a API responde apenas ângulos e o desvio normalizado.

Ciclo de vida: `RASCUNHO` aceita marcação parcial; `concluir` exige todos os pontos da vista e a foto enviada (senão
`422`); análise `CONCLUIDA` é imutável (novo `PUT`/`concluir` retorna `422`) e `cancelar` faz exclusão lógica,
liberando a vista para uma nova análise.

## Planos de Tratamento

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/planos-tratamento` | Criar plano de tratamento para um paciente |
| `GET` | `/planos-tratamento/{id}` | Buscar plano de tratamento por ID |
| `GET` | `/planos-tratamento/paciente/{pacienteId}` | Listar planos de tratamento do paciente |
| `PUT` | `/planos-tratamento/{id}` | Atualizar dados do plano de tratamento |
| `DELETE` | `/planos-tratamento/{id}` | Inativar plano de tratamento |

## Evoluções de Sessão

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/evolucoes-sessao` | Registrar evolução clínica de uma sessão |
| `GET` | `/evolucoes-sessao/{id}` | Buscar evolução por ID |
| `GET` | `/evolucoes-sessao/sessao/{sessaoId}` | Buscar evolução pela sessão vinculada |
| `GET` | `/evolucoes-sessao/paciente/{pacienteId}` | Listar evoluções do paciente, da sessão mais recente para a mais antiga |
| `PUT` | `/evolucoes-sessao/{id}` | Atualizar dados da evolução |

A resposta traz `profissionalId`, `profissionalNome` e `profissionalNumeroRegistro`: um **snapshot** do profissional
da sessão, congelado no momento do registro. Editar depois o cadastro do profissional não reescreve as evoluções já
gravadas, e o `PUT` acima preserva os três campos. Sessões sem profissional vinculado geram evolução com os três
campos `null`. Evoluções anteriores à migration `V32` têm `profissionalNumeroRegistro` `null`, já que o dado não
existia à época do registro.

## Relatórios

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/relatorios/nfse` | Gerar relatório de emissão de NFSEs por competência (JSON, CSV ou XLSX) |

## NFSE emitidas

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/nfse-emitidas` | Registrar ou atualizar a NFSE emitida de um paciente em uma competência |
| `GET` | `/api/nfse-emitidas/paciente/{pacienteId}` | Listar as NFSEs emitidas registradas para um paciente |

## Dashboard

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/dashboard/resumo` | Resumo consolidado para o painel inicial (pacientes, profissionais, pagamentos e aulas do mês) |

## Preferências do usuário

`PUT /users/me/preferencias` exige todos os campos: `idioma` (`PT_BR`, `EN_US`, `ES_ES`), `tema` (`CLARO`,
`ESCURO`), `notificacoesEmail` e `notificacoesPush`. Quando o usuário ainda não tem preferências salvas,
`GET /users/me/preferencias` retorna os valores padrão (`idioma=PT_BR`, `tema=CLARO`, `notificacoesEmail=true`,
`notificacoesPush=false`).

## Paginação

Os endpoints de listagem suportam os query params padrão do Spring:

```
GET /pacientes?page=0&size=10&sort=nome,asc
GET /profissionais?page=0&size=10&sort=nome,asc
```

O endpoint `GET /pacientes` também suporta filtros opcionais por `nome`, `email`, `cpf`, `telefone` e `ativo`. Quando `ativo` é omitido, retorna apenas pacientes ativos.

```
GET /pacientes?nome=maria&email=email.com&cpf=123&telefone=119&ativo=true&page=0&size=10&sort=nome,asc
GET /pacientes?ativo=false
```

O endpoint `GET /profissionais` também suporta filtros opcionais por `nome`, `email`, `tipoContrato`, `percentualPagamentoAula` e `ativo`. Quando `ativo` é omitido, retorna apenas profissionais ativos.

```
GET /profissionais?nome=paula&email=email.com&tipoContrato=PJ&percentualPagamentoAula=45.00&ativo=true&page=0&size=10&sort=nome,asc
GET /profissionais?ativo=false
GET /profissionais/1/relatorio-pagamento?inicio=2025-02-01&fim=2025-02-28
GET /profissionais/1/relatorio-pagamento/pdf?inicio=2025-02-01&fim=2025-02-28
GET /profissionais/1/relatorio-pagamento/xlsx?inicio=2025-02-01&fim=2025-02-28
GET /api/relatorios/nfse?competencia=04/2026
GET /api/relatorios/nfse?competencia=04/2026&notaAnteriorEmitida=false&formato=XLSX
```

O tamanho padrão de página é controlado por `APP_PAGINACAO_TAMANHO_PADRAO` (default `10`) — ver
[`deploy.md`](deploy.md#variáveis-de-ambiente).

## Relatório de pagamento — contrato JSON (Angular-friendly)

A resposta do endpoint `GET /profissionais/{id}/relatorio-pagamento` é estruturada em sub-objetos para facilitar o consumo direto no Angular sem mapeamentos adicionais:

```json
{
  "profissional": {
    "id": 1,
    "nome": "Paula Mendes",
    "cpf": "12345678900",
    "tipoContrato": "PJ",
    "percentualPagamentoAula": 45.00,
    "numeroRegistro": "350544-F"
  },
  "periodo": {
    "inicio": "2025-02-01",
    "fim": "2025-02-28"
  },
  "resumo": {
    "totalAulas": 8,
    "quantidadePagamentos": 2,
    "totalPagamentosBruto": 400.00,
    "totalProfissional": 90.00
  },
  "pagamentos": [
    {
      "pagamentoId": 5,
      "valorPagamento": 200.00,
      "quantidadeAulasPagamento": 8,
      "quantidadeAulasNoPeriodo": 4,
      "valorBaseAula": 25.00,
      "totalProfissional": 45.00
    }
  ],
  "aulas": [
    {
      "aulaId": 10,
      "data": "2025-02-03",
      "pacienteId": 2,
      "pacienteNome": "Ana",
      "pagamentoId": 5,
      "valorPagamento": 200.00,
      "quantidadeAulasPagamento": 8,
      "valorBaseAula": 25.00,
      "percentualPagamentoAula": 45.00,
      "valorProfissional": 11.25
    }
  ],
  "geradoEm": "2025-03-01T10:00:00"
}
```

### Exportação PDF/XLSX

Os endpoints `GET /profissionais/{id}/relatorio-pagamento/pdf` e `GET /profissionais/{id}/relatorio-pagamento/xlsx` retornam o relatório como anexo:

| Endpoint | `Content-Type` | `Content-Disposition` |
|---|---|---|
| `/relatorio-pagamento/pdf` | `application/pdf` | `attachment; filename="relatorio-pagamento-profissional-{id}-{inicio}-{fim}.pdf"` |
| `/relatorio-pagamento/xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `attachment; filename="relatorio-pagamento-profissional-{id}-{inicio}-{fim}.xlsx"` |

Os dois formatos trazem o mesmo cabeçalho de identificação do JSON — incluindo `Número de registro`, exibido como `-` quando o profissional não tem o campo preenchido.

O XLSX possui três abas: `Resumo`, `Pagamentos` e `Aulas`. O PDF apresenta as mesmas informações em layout único, com tabelas para pagamentos e aulas.

## Resumo do dashboard — contrato JSON

A resposta de `GET /dashboard/resumo` consolida contadores do banco em um único objeto para consumo direto pelo painel inicial:

```json
{
  "pacientes": {
    "totalAtivos": 10,
    "totalInativos": 2
  },
  "profissionais": {
    "totalAtivos": 3,
    "totalInativos": 1
  },
  "pagamentos": {
    "totalPendentes": 5,
    "totalPagos": 8,
    "totalVencidos": 2,
    "receitaMesAtual": 1600.00
  },
  "aulas": {
    "totalRealizadasMesAtual": 40,
    "totalAgendadasMesAtual": 20
  },
  "geradoEm": "2026-04-29T10:00:00"
}
```

- `receitaMesAtual` — soma dos pagamentos com status `PAGO` e `dataPagamento` dentro do mês corrente.
- `totalAgendadasMesAtual` — aulas com `realizada = false` e `data` dentro do mês corrente vinculadas a pacientes ativos.
- `totalRealizadasMesAtual` — aulas com `realizada = true` e `data` dentro do mês corrente vinculadas a pacientes ativos.

## Relatório de emissão de NFSEs

O endpoint `GET /api/relatorios/nfse` exige `competencia` no formato `MM/AAAA` e aceita os filtros opcionais `notaAnteriorEmitida` e `formato` (`JSON`, `CSV` ou `XLSX`). Ele retorna apenas pagamentos confirmados (`PAGO`) com `dataPagamento` dentro da competência informada e pacientes ativos.

Contrato JSON:

```json
[
  {
    "nome": "Ana Souza",
    "cpfCnpj": "11122233344",
    "valorPago": 250.00,
    "competencia": "04/2026",
    "descricaoServico": "Aulas de Pilates - Competência 04/2026",
    "notaAnteriorEmitida": false,
    "dataPagamento": "2026-04-10",
    "observacoes": ""
  }
]
```

O campo `notaAnteriorEmitida` é preenchido com base nas NFSEs efetivamente registradas: é `true` quando existe uma NFSE emitida para o paciente em uma competência anterior à consultada (ver seção abaixo). CSV e XLSX são retornados como anexo com nome `relatorio-nfse-{MM-AAAA}.{ext}`.

## NFSE emitidas — registro fiscal

Para que o relatório use dados reais em `notaAnteriorEmitida`, a última NFSE emitida de cada paciente é persistida por competência através de `POST /api/nfse-emitidas`. O registro é idempotente por `(paciente, competência)`: se já existir uma nota para o paciente na competência informada, ela é atualizada; caso contrário, é criada.

- `pacienteId` e `dataEmissao` são obrigatórios; o paciente precisa estar ativo (404 caso contrário). `dataEmissao` não pode ser futura (422 caso contrário).
- `competencia` é obrigatória no formato `MM/AAAA` (400 quando fora do formato).
- `numeroNota` é opcional e limitado a 60 caracteres (400 quando excedido); `valor` e `observacoes` são opcionais; quando informado, `valor` deve ser maior que zero (422 caso contrário).
- O registro é idempotente por `(paciente, competência)` inclusive sob concorrência: requisições simultâneas para o mesmo par resolvem para uma única nota (a colisão da constraint única é repetida automaticamente como atualização).

`GET /api/nfse-emitidas/paciente/{pacienteId}` retorna as notas registradas do paciente, da competência mais recente para a mais antiga.

## Ver também

- [`regras-de-negocio.md`](regras-de-negocio.md) — regras por domínio
- [`arquitetura.md`](arquitetura.md#tratamento-de-erros) — contrato de erro e mapeamento de exceções
- [`operacao.md`](operacao.md#compressão-de-respostas-gzip) — compressão das respostas
