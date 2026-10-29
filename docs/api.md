# API REST

Base URL: `http://localhost:8080`

Os schemas de request e response são gerados pelo **springdoc-openapi** e ficam disponíveis no
[Swagger UI](http://localhost:8080/swagger-ui.html) (`/api-docs` para o JSON). Este documento cobre o que o
OpenAPI não expressa: o mapa de rotas, os contratos de relatório e as regras de preenchimento que não estão
no schema.

> No perfil `prod` a documentação OpenAPI fica desabilitada (`springdoc.*.enabled=false`) e essas rotas
> retornam `404`, para não expor o mapa da API. Este arquivo é a referência nesse cenário.

## Locking otimista (`version`)

Pacientes, profissionais, anamneses, avaliações fisioterapêuticas, planos de tratamento, sessões, evoluções de
sessão, reavaliações, usuários, preferências do usuário e faixas de disponibilidade dos profissionais trazem
`version` na resposta e aceitam `version` no corpo do update (`PUT /pacientes/{id}`, `PUT /profissionais/{id}`,
`PUT /disponibilidades/{id}`, `PUT /anamneses/{id}`,
`PUT /avaliacoes-fisioterapeuticas/{id}`, `PUT /planos-tratamento/{id}`, `PUT /sessoes/{id}`,
`PUT /evolucoes-sessao/{id}`, `PUT /reavaliacoes/{id}`, `PUT /users/{id}` e `PUT /users/me/preferencias`).

- O cliente devolve no update a `version` que recebeu no `GET`. Se o registro mudou nesse meio-tempo, a resposta
  é `409` com `type` `concurrent-modification`: recarregue o registro e reaplique a edição sobre o estado novo.
- A resposta de um update bem-sucedido já traz a `version` incrementada — use-a no próximo save, sem novo `GET`.
- **Modo legado:** update sem `version` (ou com `null`) segue sem a checagem, como antes da #101. É temporário,
  até o frontend passar a enviá-la; ver [`regras-de-negocio.md`](regras-de-negocio.md#locking-otimista).
- `GET /users/me/preferencias` de quem nunca salvou devolve os padrões com `version: null`; o primeiro save
  também vai sem versão.

## Autoria (`criadoPor` / `alteradoPor`)

As respostas dos dados clínicos — anamnese, avaliação fisioterapêutica, análise postural, plano de tratamento,
sessão, evolução de sessão e reavaliação — trazem, ao lado de `dataCriacao` e `dataAtualizacao`:

- `criadoPor`: e-mail de quem criou o registro, ou `system` para processos automáticos;
- `alteradoPor`: e-mail de quem fez a última alteração, `null` enquanto não houver alteração.

Os quatro campos são só de leitura: enviados no request, são ignorados. `dataCriacao` e `dataAtualizacao` mantêm os
nomes e o formato de antes da #109, porque o frontend depende deles. Registros anteriores à auditoria vêm com autor
`null`. As regras de preenchimento estão em [`regras-de-negocio.md`](regras-de-negocio.md#auditoria-de-criação-e-alteração).

## Autenticação

| Método | Endpoint | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/auth/login` | Público | Valida e-mail/senha e retorna `accessToken` (JWT, 15 min por padrão) e `refreshToken` (opaco, 7 dias por padrão). Retorna `429` após 5 tentativas falhas em 15 min para o mesmo e-mail ou 20 tentativas falhas em 15 min a partir do mesmo IP (com qualquer e-mail) |
| `POST` | `/auth/refresh` | Público | Recebe `refreshToken` e devolve um novo par `accessToken` + `refreshToken` (mesmo corpo do login); o token apresentado deixa de valer. Retorna `401` para token inválido, expirado, revogado, já utilizado (o reuso fora da tolerância de 10 s revoga toda a sessão daquele login), emitido há mais tempo que o teto da sessão (30 dias desde o login) ou emitido antes da última troca de senha |
| `POST` | `/auth/logout` | Público | Recebe `refreshToken` e encerra a sessão daquele login (todos os refresh tokens da cadeia). Sempre `204`, mesmo para token desconhecido ou já revogado. O `accessToken` em uso segue válido até expirar |
| `POST` | `/auth/forgot-password` | Público | Solicita redefinição de senha por e-mail. Sempre retorna `200` com mensagem genérica, mesmo se o e-mail não existir (evita enumeração de usuários). Retorna `429` após 5 solicitações em 15 min para o mesmo e-mail ou 20 solicitações em 15 min a partir do mesmo IP |
| `POST` | `/auth/reset-password` | Público | Redefine a senha a partir de `token`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`. Retorna `422` para token inválido, expirado, já utilizado ou confirmação divergente |
| `GET` | `/users/me` | Autenticado | Retorna dados seguros do usuário autenticado |
| `PUT` | `/users/me/senha` | Autenticado | Troca a própria senha informando `senhaAtual`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`. Retorna `422` para senha atual incorreta, confirmação divergente ou reuso da senha atual. Access e refresh tokens emitidos antes da troca deixam de valer |
| `GET` | `/users/me/preferencias` | Autenticado | Retorna as preferências do usuário autenticado (idioma, tema e notificações). Usuário sem preferências salvas recebe os valores padrão |
| `PUT` | `/users/me/preferencias` | Autenticado | Atualiza as preferências do usuário autenticado. Valida `idioma` (`PT_BR`, `EN_US`, `ES_ES`) e `tema` (`CLARO`, `ESCURO`); valores inválidos retornam `400` |
| `POST` | `/users` | `ADMIN` | Cria usuário com um ou mais papéis em `roles` (`ADMIN`, `PROFISSIONAL`, `RECEPCAO`, `FINANCEIRO`). Lista vazia retorna `400`; papel reservado (`PACIENTE`), `422` |
| `GET` | `/users` | `ADMIN` | Lista usuários cadastrados sem expor senha |
| `GET` | `/users/roles` | `ADMIN` | Lista as roles disponíveis (`value` e `label`) para uso em formulários administrativos. `PACIENTE`, reservada para o portal do paciente (#173), não aparece |
| `GET` | `/users/{id}` | `ADMIN` | Busca usuário por ID |
| `PUT` | `/users/{id}` | `ADMIN` | Atualiza nome, e-mail, senha e papéis. `roles`, quando enviado, substitui o conjunto inteiro. Admin não pode alterar os próprios papéis nem tirar `ADMIN` do último ADMIN ativo |
| `PATCH` | `/users/{id}/inativar` | `ADMIN` | Inativa usuário (soft delete) e responde `204`. Admin não pode inativar a própria conta nem o último ADMIN ativo (`422`); id inexistente, `404` |
| `PATCH` | `/users/{id}/ativar` | `ADMIN` | Reativa usuário e responde `204`, inclusive se ele já estiver ativo (idempotente); id inexistente, `404` |
| `DELETE` | `/users/{id}` | `ADMIN` | **Deprecated** (`deprecated: true` no OpenAPI): use `PATCH /users/{id}/inativar`, com as mesmas regras e respostas. Mantida por compatibilidade |
| `GET` | `/admin/health` | `ADMIN` | Endpoint inicial administrativo |

As demais rotas de negócio exigem `Authorization: Bearer <accessToken>`. Tokens ausentes, inválidos, expirados ou emitidos antes da última troca/redefinição de senha retornam `401 Unauthorized` — ao receber `401` por expiração, o cliente chama `POST /auth/refresh` e repete a requisição com o novo `accessToken`; usuário autenticado sem o papel da rota recebe `403 Forbidden` (matriz abaixo).

### Autorização por papel

`ADMIN` acessa todas as áreas. Os demais papéis acessam só as marcadas; fora delas a resposta é `403`. Um usuário
pode acumular papéis e acessa a soma das áreas deles — `RECEPCAO` + `FINANCEIRO` abre agenda e NFSe, mas não o
prontuário. As respostas de usuário (`/users`, `/users/me` e o `user` do login) trazem `roles` como lista, na ordem
da tabela abaixo. Rota que o
`SecurityConfig` não cita é negada a todos, inclusive ao `ADMIN` (`denyAll`).

| Rotas | `PROFISSIONAL` | `RECEPCAO` | `FINANCEIRO` |
|---|---|---|---|
| `/anamneses`, `/avaliacoes-fisioterapeuticas`, `/avaliacoes-posturais`, `/evolucoes-sessao`, `/reavaliacoes`, `/planos-tratamento` | ✓ | | |
| `/pacientes` | ✓ | ✓ | ✓ |
| `/aulas`, `/sessoes`, `/lista-espera` | ✓ | ✓ | |
| `GET /bloqueios` | ✓ | ✓ | ✓ |
| `/planos`, `/pagamentos` (exceto as rotas só de `ADMIN`) | | ✓ | ✓ |
| `/api/nfse-emitidas`, `/api/relatorios/nfse`, `/api/relatorios/financeiro` | | | ✓ |
| `GET /profissionais`, `GET /profissionais/{id}` | ✓ | ✓ | ✓ |
| `GET /profissionais/{id}/disponibilidades` (e `/verificar`) | ✓ | ✓ | ✓ |
| `/profissionais/{id}/relatorio-pagamento` (JSON e PDF) | | | ✓ |
| `/dashboard/resumo` | ✓ | ✓ | ✓ |
| `/users/me`, `/users/me/senha`, `/users/me/preferencias` | ✓ | ✓ | ✓ |

Só de `ADMIN`: `/users/**` (exceto `/users/me*`), `/admin/**`, `/actuator/**` além de health/info, escrita em
`/bloqueios`, `/profissionais` e `/disponibilidades` (inclusive `POST /profissionais/{id}/disponibilidades`), `GET /pagamentos`, `/pagamentos/{id}/cobrancas`, `/dashboard/indicadores` e
`/dashboard/aulas-por-profissional`. `PACIENTE` só acessa `/users/me*` até o portal do paciente existir.

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

### Disponibilidade semanal

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/profissionais/{id}/disponibilidades` | Listar as faixas do profissional, ordenadas por dia da semana (segunda a domingo) e `horaInicio` |
| `POST` | `/profissionais/{id}/disponibilidades` | Cadastrar faixa — exige `ADMIN`; `201` com `Location: /disponibilidades/{id}` |
| `GET` | `/profissionais/{id}/disponibilidades/verificar?data=AAAA-MM-DD&horario=HH:mm` | Responde `{ "dentroDaDisponibilidade": true\|false }` |
| `PUT` | `/disponibilidades/{id}` | Atualizar faixa (substituição completa) — exige `ADMIN` |
| `DELETE` | `/disponibilidades/{id}` | Excluir faixa permanentemente — exige `ADMIN`; `204` |

Corpo do `POST` e do `PUT`: `diaSemana` (nome do `DayOfWeek` em inglês maiúsculo: `MONDAY` … `SUNDAY`),
`horaInicio` e `horaFim` (`"HH:mm"`, aceita também `"HH:mm:ss"`), todos obrigatórios, e `version` (opcional, só
conferida no `PUT`). A resposta traz `id`, `profissionalId`, `diaSemana`, `horaInicio`, `horaFim` (serializadas como
`"HH:mm:ss"`), `version`, `dataCriacao` e `dataAtualizacao`. A disponibilidade **avisa, não impede**: nenhuma rota de
aula ou sessão a consulta.

- A faixa é semiaberta, `[horaInicio, horaFim)`: `verificar` às 12:00 numa faixa 07:00–12:00 responde `false`.
- `horaInicio` não anterior a `horaFim` responde `422` (`business-rule`). Campo ausente ou `diaSemana` desconhecido,
  `400`.
- Faixa que se sobrepõe a outra do mesmo profissional no mesmo dia responde `409` (`conflict`); faixas que só encostam
  (12:00–14:00 e 14:00–19:00) são aceitas. No `PUT`, a própria faixa não conta como conflito; `version` divergente
  responde `409` `concurrent-modification`.
- Profissional inexistente responde `404` em todas as rotas `/profissionais/{id}/disponibilidades*`; faixa inexistente,
  `404` no `PUT`/`DELETE`. Profissional inativo responde `422` no `POST` e no `PUT`; a leitura e o `DELETE` seguem
  liberados.
- `verificar` usa o dia da semana de `data` e o horário local do estúdio, sem conversão de fuso. Profissional sem
  nenhuma faixa cadastrada responde `true` — sem configuração, não há aviso. `data` ou `horario` ausentes ou
  malformados respondem `400`.

## Planos de Pagamento

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/planos` | Criar plano para paciente |
| `GET` | `/planos/{id}` | Buscar plano por ID |
| `GET` | `/planos/paciente/{id}` | Listar planos do paciente |
| `GET` | `/planos/paciente/{id}/ativo` | Buscar plano ativo do paciente |
| `DELETE` | `/planos/{id}` | Inativar plano |

`horario` é opcional no `POST /planos` (`"08:00:00"`) e vale para todos os `diasSemana`: as aulas geradas pelo
pagamento copiam esse horário. Sem ele, as aulas nascem sem horário e ficam fora da regra de capacidade.

## Pagamentos

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/pagamentos` | Criar pagamento (PENDENTE) |
| `GET` | `/pagamentos/{id}` | Buscar pagamento por ID |
| `GET` | `/pagamentos/paciente/{id}` | Listar pagamentos do paciente (paginado) |
| `PATCH` | `/pagamentos/{id}/pagar` | Confirmar pagamento e gerar aulas; aceita `dataPagamento` e `formaPagamento` opcionais no corpo |
| `GET` | `/pagamentos` | Listagem global paginada (gestão de inadimplência, `ADMIN`); filtros `status`, `vencimentoDe`, `vencimentoAte`, `pacienteId` e `formaPagamento` |
| `POST` | `/pagamentos/{id}/cobrancas` | Registrar contato de cobrança do pagamento (`ADMIN`) |
| `GET` | `/pagamentos/{id}/cobrancas` | Histórico de contatos de cobrança do pagamento, do mais recente para o mais antigo (`ADMIN`) |
| `GET` | `/pagamentos/{id}/cobrancas/{contatoId}` | Buscar contato de cobrança (é o alvo do `Location` devolvido no registro; `ADMIN`) |

### Confirmação e forma de pagamento

```
PATCH /pagamentos/42/pagar
{"dataPagamento": "2026-10-10", "formaPagamento": "PIX"}
```

- O corpo inteiro é opcional. `dataPagamento` ausente usa a data atual; `formaPagamento` ausente ou `null` confirma o
  pagamento sem forma registrada — é o contrato de quem ainda manda só `{ "dataPagamento" }`.
- `formaPagamento` aceita `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `DINHEIRO` e `TRANSFERENCIA`; outro valor retorna
  `400` (corpo malformado, sem `campos`).
- Toda resposta de pagamento (`GET /pagamentos/{id}`, as listagens e a própria confirmação) traz `formaPagamento`, que
  é `null` em pagamentos em aberto e nos confirmados antes da #215.
- A forma não é aceita no `POST /pagamentos`: a cobrança nasce sem ela.

### Listagem global e inadimplência

`GET /pagamentos` alimenta a tela de inadimplência do frontend
([carlessopilatesfe#127](https://github.com/FabioCarlesso/carlessopilatesfe/issues/127)).

```
GET /pagamentos?status=PENDENTE&status=VENCIDO&vencimentoAte=2026-10-12&page=0&size=20
```

- Todos os filtros são opcionais e combináveis. `status` pode ser repetido; valor fora de `PENDENTE`, `PAGO` e
  `VENCIDO` retorna `400`.
- `vencimentoDe` e `vencimentoAte` (`yyyy-MM-dd`) são inclusivos e podem ser usados sozinhos. `vencimentoDe` posterior
  a `vencimentoAte` retorna `400`.
- `formaPagamento` aceita um valor do enum (fora dele, `400`) e só encontra pagamentos confirmados com a forma
  registrada: os em aberto e os confirmados sem forma ficam de fora.
- A resposta é paginada no formato descrito em [Paginação](#paginação), com o mesmo item de `GET /pagamentos/{id}`.
  Dias em atraso não vêm na resposta: o frontend calcula a partir de `dataVencimento`.
- A ordenação padrão é `dataVencimento,asc` (os mais atrasados primeiro), sempre com `id` como desempate. `sort` aceita
  apenas `dataVencimento`, `valor`, `status` e `paciente.nome`; outro campo retorna `400`.

### Contatos de cobrança

- O corpo do `POST` tem `dataContato` (`yyyy-MM-dd`, obrigatória, não futura) e `observacao` (obrigatória, até 500
  caracteres). Fora disso, `400` com o mapa `campos`.
- `registradoPor` (e-mail do usuário autenticado) e `registradoEm` são do servidor: enviá-los no corpo não muda nada.
- Pagamento inexistente retorna `404`; pagamento `PAGO` não aceita novos contatos e retorna `422`.

## Aulas

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/aulas` | Listar aulas por período (agenda do estúdio); filtros `profissionalId`, `pacienteId` e `realizada` |
| `GET` | `/aulas/{id}` | Buscar aula por ID |
| `GET` | `/aulas/paciente/{id}` | Listar aulas do paciente (paginado) |
| `GET` | `/aulas/pagamento/{id}` | Listar aulas de um pagamento |
| `PATCH` | `/aulas/{id}/realizar` | Marcar aula como realizada, opcionalmente com `profissionalId` (query param) |
| `PATCH` | `/aulas/{id}/profissional` | Atribuir ou desvincular o profissional de uma aula ainda não realizada (corpo) |
| `PATCH` | `/aulas/{id}/remarcar` | Trocar a data e/ou o horário de uma aula ainda não realizada (corpo) |

### Remarcação

`{"data": "2025-02-20", "horario": "08:00"}` move a aula, preservando paciente e `pagamentoId`. `horario` é opcional:
ausente ou `null`, a aula mantém o horário que já tinha. A nova data **não** é confrontada com
`periodoInicio`/`periodoFim` do pagamento e pode estar no passado — a recepção precisa poder remarcar a falta da
última semana do mês e registrar reposições já ocorridas.

- `409` se a aula já foi realizada, se o paciente já tem aula na data de destino (mensagem cita a data), ou se o
  profissional atribuído não tem vaga no novo dia e horário (ver [Capacidade por horário](#capacidade-por-horário)).
- `400` se `data` for omitida ou vier `null`: o corpo traz `data` na lista `campos`, apontando o campo culpado.
- `400` também se `data` vier em formato não-ISO (`"20/02/2025"`), mas **sem** `campos` — aí o corpo é o genérico
  `detail` `"Corpo da requisição inválido ou malformado"` (`type` `bad-request`). A desserialização falha antes da Bean Validation, e esse
  é o contrato do handler global para corpo malformado em toda a API, não uma particularidade desta rota.
- Remarcar para a data e o horário atuais da aula é no-op e retorna `200`. Trocar só o horário no mesmo dia é
  permitido e não colide com a própria aula.

As três rotas de escrita de aula (`realizar`, `profissional` e `remarcar`) usam locking otimista. Se duas
requisições alterarem a mesma aula ao mesmo tempo, a perdedora recebe `409` com `type`
`concurrent-modification` (e não o `conflict` das demais regras) — o cliente deve
recarregar a aula e refazer a ação sobre o estado novo, em vez de repetir a requisição às cegas.

### Profissional da aula

As duas rotas acima mexem no mesmo vínculo e **passam `profissionalId` por caminhos diferentes**: `realizar` aceita
query param, `profissional` exige corpo. A divergência é deliberada — `realizar` é contrato publicado e em uso pelo
frontend, e o corpo só é obrigatório na rota nova porque lá `null` tem significado próprio:

- `{"profissionalId": 7}` vincula; `{"profissionalId": null}` desvincula.
- Omitir o campo (`{}`) ou mandar a requisição sem corpo retorna `400`. `{}` e `null` explícito chegam idênticos ao
  Java, então exigir o campo é o que impede um corpo montado pela metade de apagar o vínculo sem avisar.
- Em `realizar`, ausência de `profissionalId` **preserva** o profissional já atribuído; presença sobrescreve.

### Capacidade por horário

Um profissional atende até `APP_AGENDA_CAPACIDADE_POR_HORARIO` aulas (padrão `3`) ao mesmo tempo. Toda aula dura
`APP_AGENDA_DURACAO_AULA_MINUTOS` (padrão `60`), então duas aulas disputam o horário quando seus inícios distam
menos que isso. Atribuir o profissional (`PATCH /aulas/{id}/profissional`) ou remarcar uma aula que já tem
profissional para um horário lotado retorna `409` com `type` `conflict` e a ocupação na mensagem.

- Aula sem horário ou sem profissional não entra na conta e não é conferida.
- Reatribuir o profissional que a aula já tem retorna `200` sem conferir a capacidade.
- `PATCH /aulas/{id}/realizar` com `profissionalId` **não** confere a capacidade: a rota registra uma aula que já
  aconteceu, e recusá-la obrigaria a lançar o profissional errado, corrompendo o repasse.

## Bloqueios de agenda

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/bloqueios` | Cadastrar bloqueio (feriado, manutenção, evento) — exige `ADMIN` |
| `GET` | `/bloqueios` | Listar bloqueios que intersectam o período (`inicio`, `fim`) |
| `GET` | `/bloqueios/{id}` | Buscar bloqueio por ID |
| `PUT` | `/bloqueios/{id}` | Atualizar bloqueio (substituição completa) — exige `ADMIN` |
| `DELETE` | `/bloqueios/{id}` | Excluir bloqueio permanentemente — exige `ADMIN` |

O bloqueio **avisa, não impede**: nem a geração automática de aulas nem o agendamento de sessões consultam esta
tabela. `GET /bloqueios` é a consulta que permite ao frontend marcar as datas na agenda e sugerir que não se marque
nada ali — a decisão continua com quem opera, porque exceções (a turma que treina no feriado) são legítimas.

- `horaInicio`/`horaFim` ausentes bloqueiam o dia inteiro e a resposta traz `diaInteiro: true`; presentes, bloqueiam
  a faixa em cada dia do período. Mandar só uma das duas retorna `400`, assim como `horaFim` não posterior a
  `horaInicio` — nesses casos o corpo é um `bad-request`, sem `campos`, por serem regras entre campos.
- `motivo` ausente, em branco ou acima de 200 caracteres, e `dataInicio`/`dataFim` ausentes, retornam `400` com
  `campos` apontando o campo culpado.
- `dataFim` anterior a `dataInicio` retorna `400`; iguais é válido (feriado de um dia).
- `GET /bloqueios` exige período obrigatório e fechado, como `GET /aulas`, mas com teto de **366 dias** em vez dos 92
  da agenda: feriados são conferidos para o ano inteiro de uma vez, e o volume que justifica o teto curto da agenda
  não existe aqui. Retorna também os bloqueios que começam antes de `inicio` ou terminam depois de `fim` — férias
  coletivas de 20/12 a 05/01 aparecem na consulta de janeiro. Período sem bloqueios retorna `200` com lista vazia.
- `PUT` substitui o registro inteiro: omitir a faixa de horário devolve o bloqueio para dia inteiro.
- Escrita exige `ADMIN` (`403` caso contrário); a leitura é liberada a toda a equipe, para que as telas de agenda e
  de sessão da recepção consigam exibir o aviso.

## Sessões de Pilates/Fisioterapia

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/sessoes` | Registrar sessão de Pilates ou Fisioterapia para um paciente |
| `GET` | `/sessoes` | Listar sessões por período (agenda do estúdio); filtros `profissionalId`, `pacienteId`, `tipo` e `status` |
| `GET` | `/sessoes/{id}` | Buscar sessão por ID |
| `GET` | `/sessoes/paciente/{pacienteId}` | Listar sessões do paciente (paginado) |
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
- Ordenação: aulas e sessões por `data`, `horario` e `id`, com os registros sem `horario` no fim do respectivo dia.
- `AulaResponseDTO` expõe `profissionalId` e `profissionalNome` (ambos `null` enquanto a aula não tem profissional
  vinculado) — o calendário usa esses campos para colorir e filtrar por profissional.

## Lista de espera

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/lista-espera` | Inscrever paciente num dia da semana e faixa de horário |
| `GET` | `/lista-espera` | Listar a fila ativa em ordem de chegada; filtros `diaSemana`, `horaInicio`, `horaFim` e `pacienteId` |
| `GET` | `/lista-espera/{id}` | Buscar entrada por ID (é o alvo do `Location` devolvido na criação) |
| `DELETE` | `/lista-espera/{id}` | Remover a entrada da fila |

A fila responde a uma pergunta só: quem chamar quando `PATCH /sessoes/{id}/cancelar` abrir uma vaga. Ela não é
consultada por nenhuma outra rota — não bloqueia o cancelamento nem vira sessão sozinha; o frontend usa
`GET /lista-espera` para exibir o aviso e depois chama o `POST /sessoes` de sempre.

- `diaSemana` usa o nome em inglês maiúsculo do `DayOfWeek` (`MONDAY`…`SUNDAY`), na query e no corpo. Valor fora
  desse conjunto retorna `400`.
- `dataEntrada` é sempre do servidor: mandá-la no corpo não muda nada, e é ela que define a posição na fila. A
  listagem sai ordenada por `dataEntrada` crescente, com `id` desempatando.
- Todos os filtros do `GET` são opcionais, mas `horaInicio` e `horaFim` só valem juntas — meia faixa retorna `400`,
  assim como faixa invertida. A faixa casa por **interseção**: uma busca por 08:00–09:00 encontra quem se inscreveu
  para 07:30–09:30. As pontas são abertas, então 07:00–08:00 não entra na busca por 08:00–09:00.
- `pacienteId` ausente ou inexistente retorna `404`; paciente inativo retorna `422`, como nas demais criações de
  registro. Entradas de paciente inativado somem da listagem, mas continuam removíveis pelo `DELETE`.
- Inscrever o mesmo paciente no mesmo dia e na **mesma faixa exata** ainda ativa retorna `409`. Faixas apenas
  sobrepostas (08:00–09:00 e 08:30–09:30) são interesses distintos e convivem. O `409` vale inclusive sob
  concorrência: requisições simultâneas passam juntas pela checagem prévia, e quem perde esbarra no índice único
  parcial da `V37` — que o service converte no mesmo `409`, nunca num `500`.
- A listagem recusa com `400` acima de **5000 registros**, mesmo teto das agendas de aulas e sessões: a rota não é
  paginada e a resposta é única. Na prática a fila ativa é pequena, então o teto é rede de segurança.
- `DELETE` é remoção lógica: a entrada some da listagem e o `DELETE` repetido retorna `404`, mas a linha permanece
  para o histórico de quem foi chamado a partir da fila.
- Escrita e leitura exigem `ADMIN`, `PROFISSIONAL` ou `RECEPCAO`: quem preenche vaga aberta é a recepção.

## Avaliações Fisioterapêuticas

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/avaliacoes-fisioterapeuticas` | Criar avaliação fisioterapêutica para um paciente |
| `GET` | `/avaliacoes-fisioterapeuticas/{id}` | Buscar avaliação fisioterapêutica por ID |
| `GET` | `/avaliacoes-fisioterapeuticas/paciente/{pacienteId}` | Listar avaliações fisioterapêuticas do paciente (paginado) |
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
| `GET` | `/planos-tratamento/paciente/{pacienteId}` | Listar planos de tratamento do paciente (paginado) |
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
| `GET` | `/api/relatorios/financeiro?inicio=&fim=` | Relatório financeiro consolidado do estúdio: receita × repasses (JSON) |
| `GET` | `/api/relatorios/financeiro/pdf?inicio=&fim=` | Exportar o relatório financeiro em PDF |
| `GET` | `/api/relatorios/financeiro/xlsx?inicio=&fim=` | Exportar o relatório financeiro em Excel (XLSX) |

## NFSE emitidas

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/nfse-emitidas` | Registrar ou atualizar a NFSE emitida de um paciente em uma competência |
| `GET` | `/api/nfse-emitidas/paciente/{pacienteId}` | Listar as NFSEs emitidas registradas para um paciente (paginado) |

## Dashboard

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/dashboard/resumo` | Resumo consolidado para o painel inicial (pacientes, profissionais, pagamentos e aulas do mês) |
| `GET` | `/dashboard/indicadores?meses=12` | Séries mensais de receita, aulas e pacientes (novos/inativados). Só `ADMIN` |
| `GET` | `/dashboard/aulas-por-profissional?inicio=&fim=` | Ranking de aulas realizadas por profissional no período. Só `ADMIN` |

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

`sort` com um campo que o recurso não tem (ex.: `GET /users?sort=role`, campo que deixou de existir com a #131)
responde `400` `bad-request`, com o nome do campo no `detail`.

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

O endpoint `GET /pagamentos` (restrito a `ADMIN`) suporta filtros por `status` (repetível), `vencimentoDe`,
`vencimentoAte`, `pacienteId` e `formaPagamento` — ver [Listagem global e inadimplência](#listagem-global-e-inadimplência).

O tamanho padrão de página é controlado por `APP_PAGINACAO_TAMANHO_PADRAO` (default `10`) — ver
[`deploy.md`](deploy.md#variáveis-de-ambiente). O teto de `size` é `2000` (`spring.data.web.pageable.max-page-size`), fixado porque o frontend pede o
histórico de um paciente numa página desse tamanho.

### Listagens por paciente

As listagens de sub-recursos por paciente são paginadas (#100) e devolvem o mesmo formato de `/pacientes`
(`{content, page:{size, number, totalElements, totalPages}}`). Sem `sort`, saem do registro mais recente para o mais
antigo; `id` é sempre acrescentado como último critério, então a ordem é estável entre páginas. Registros sem
valor no campo ordenado (`horario`, `dataFimPrevista`, `valor` da NFS-e) ficam no fim, em qualquer direção. Um campo de `sort` fora
da lista abaixo é recusado com `400`.

| Rota | Ordenação padrão | Campos aceitos em `sort` |
|---|---|---|
| `GET /pagamentos/paciente/{id}` | `dataVencimento,desc` | `dataVencimento`, `valor`, `status` |
| `GET /aulas/paciente/{id}` | `data,desc` | `data`, `horario`, `realizada` |
| `GET /sessoes/paciente/{id}` | `data,desc` | `data`, `horario`, `status`, `tipo` |
| `GET /avaliacoes-fisioterapeuticas/paciente/{id}` | `dataAvaliacao,desc` | `dataAvaliacao` |
| `GET /reavaliacoes/paciente/{id}` | `dataReavaliacao,desc` | `dataReavaliacao` |
| `GET /planos-tratamento/paciente/{id}` | `dataInicio,desc` | `dataInicio`, `dataFimPrevista` |
| `GET /api/nfse-emitidas/paciente/{id}` | `competencia,desc` | `competencia`, `dataEmissao`, `valor` |

```
GET /aulas/paciente/1?page=0&size=20
GET /sessoes/paciente/1?page=1&size=10&sort=horario,asc
```

`GET /evolucoes-sessao/paciente/{id}` continua devolvendo o histórico completo em array, pela decisão registrada em
[`regras-de-negocio.md`](regras-de-negocio.md#por-que-a-listagem-não-é-paginada-decisão-da-158). Quem precisa do
período inteiro de aulas ou sessões para montar uma agenda usa `GET /aulas` e `GET /sessoes` com `pacienteId`.

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

- `receitaMesAtual` — soma dos pagamentos com status `PAGO` e `dataPagamento` dentro do mês corrente. Só vem preenchida para `ADMIN` e `FINANCEIRO`; para `PROFISSIONAL` e `RECEPCAO` é `null` (os contadores continuam iguais para toda a equipe).
- `totalAgendadasMesAtual` — aulas com `realizada = false` e `data` dentro do mês corrente vinculadas a pacientes ativos.
- `totalRealizadasMesAtual` — aulas com `realizada = true` e `data` dentro do mês corrente vinculadas a pacientes ativos.

## Indicadores do dashboard — contrato JSON

`GET /dashboard/indicadores?meses=12` (padrão 12, de 1 a 24) devolve três séries com um item por mês, do mais antigo ao atual, incluindo os meses zerados. `mes` é `AAAA-MM`:

```json
{
  "meses": 12,
  "receita": [{ "mes": "2025-11", "receita": 1600.00 }],
  "aulas": [{ "mes": "2025-11", "realizadas": 40, "agendadas": 5 }],
  "pacientes": [{ "mes": "2025-11", "novos": 3, "inativados": 1 }],
  "geradoEm": "2026-10-17T10:00:00"
}
```

`GET /dashboard/aulas-por-profissional?inicio=2026-01-01&fim=2026-06-30` (datas inclusivas, até 366 dias; sem os dois, o mês atual) devolve o ranking ordenado por `totalRealizadas` decrescente:

```json
[
  { "profissionalId": 2, "nome": "Paula Mendes", "totalRealizadas": 30 },
  { "profissionalId": 1, "nome": "Ana Lima", "totalRealizadas": 12 }
]
```

Os critérios de cada série e as limitações da série de pacientes estão em [`regras-de-negocio.md`](regras-de-negocio.md#indicadores-do-dashboard).

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

## Relatório financeiro — contrato JSON

`GET /api/relatorios/financeiro?inicio=2026-02-01&fim=2026-02-28` (datas inclusivas, até 366 dias) consolida o
fechamento do estúdio no período. Só `ADMIN` e `FINANCEIRO`. Período ausente, invertido ou acima do teto retorna `400`;
período sem movimento retorna tudo zerado.

```json
{
  "periodo": { "inicio": "2026-02-01", "fim": "2026-02-28" },
  "receita": { "quantidadePagamentos": 4, "total": 1000.00 },
  "repasses": {
    "totalAulas": 8,
    "total": 90.00,
    "profissionais": [
      {
        "profissionalId": 1,
        "nome": "Paula Mendes",
        "percentualPagamentoAula": 45.00,
        "totalAulas": 8,
        "quantidadePagamentos": 2,
        "totalPagamentosBruto": 400.00,
        "totalProfissional": 90.00
      }
    ]
  },
  "inadimplencia": { "quantidadePagamentos": 1, "total": 250.00 },
  "resultadoLiquido": 910.00,
  "geradoEm": "2026-03-01T10:00:00"
}
```

Cada item de `repasses.profissionais` é o `resumo` do [relatório de pagamento](#relatório-de-pagamento--contrato-json-angular-friendly)
do profissional no mesmo período. Os critérios de cada bloco estão em
[`regras-de-negocio.md`](regras-de-negocio.md#relatório-financeiro-consolidado).

As exportações `/pdf` e `/xlsx` usam o mesmo cálculo e voltam como anexo
(`Content-Disposition: attachment; filename="relatorio-financeiro-{inicio}-{fim}.{ext}"`). O XLSX tem as abas
`Resumo` e `Repasses`; o PDF traz as mesmas informações numa página.

## NFSE emitidas — registro fiscal

Para que o relatório use dados reais em `notaAnteriorEmitida`, a última NFSE emitida de cada paciente é persistida por competência através de `POST /api/nfse-emitidas`. O registro é idempotente por `(paciente, competência)`: se já existir uma nota para o paciente na competência informada, ela é atualizada; caso contrário, é criada.

- `pacienteId` e `dataEmissao` são obrigatórios; o paciente precisa estar ativo (404 caso contrário). `dataEmissao` não pode ser futura (422 caso contrário).
- `competencia` é obrigatória no formato `MM/AAAA` (400 quando fora do formato).
- `numeroNota` é opcional e limitado a 60 caracteres (400 quando excedido); `valor` e `observacoes` são opcionais; quando informado, `valor` deve ser maior que zero (422 caso contrário).
- O registro é idempotente por `(paciente, competência)` inclusive sob concorrência: requisições simultâneas para o mesmo par resolvem para uma única nota (a colisão da constraint única é repetida automaticamente como atualização).

`GET /api/nfse-emitidas/paciente/{pacienteId}` retorna uma página das notas registradas do paciente, da competência mais
recente para a mais antiga — ver [Listagens por paciente](#listagens-por-paciente).

## Ver também

- [`regras-de-negocio.md`](regras-de-negocio.md) — regras por domínio
- [`arquitetura.md`](arquitetura.md#tratamento-de-erros) — contrato de erro e mapeamento de exceções
- [`operacao.md`](operacao.md#compressão-de-respostas-gzip) — compressão das respostas
