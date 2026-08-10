# Regras de Negócio

Fonte única das regras de negócio da API. O `docs/context.md` guarda o modelo de dados e as decisões de
design; o comportamento por endpoint está em [`api.md`](api.md).

## Segurança

- Autenticação stateless com Spring Security e JWT
- Senhas são armazenadas com `BCryptPasswordEncoder`
- O segredo JWT vem de `JWT_SECRET`; não há segredo fixo no código
- JWT inclui claims `role`, `userId` e `tokenVersion`; a cada requisição o filtro valida se o usuário ainda existe, está ativo e se a versão do token corresponde à versão atual do usuário antes de reconstruir o contexto de segurança
- Rate limiting de `/auth/login`: 5 tentativas falhas por e-mail em janela de 15 minutos retorna `429 Too Many Requests`
- Admin não pode inativar a própria conta nem alterar o próprio perfil de acesso (`422 Unprocessable Entity`)
- O sistema deve manter ao menos um usuário `ADMIN` ativo: não é permitido inativar nem rebaixar para `USER` o último administrador ativo (`422 Unprocessable Entity`)
- Usuários com `ativo=false` não conseguem fazer login e tokens emitidos antes da inativação deixam de autorizar rotas protegidas
- Usuário autenticado pode trocar a própria senha via `PUT /users/me/senha`: precisa informar `senhaAtual`, `novaSenha` (mínimo 8 caracteres) e `confirmacaoNovaSenha`; senha atual incorreta, confirmação divergente ou reuso da senha atual retornam `422 Unprocessable Entity`; a nova senha é armazenada com `BCryptPasswordEncoder` e tokens emitidos antes da troca passam a retornar `401 Unauthorized`
- CORS permite o frontend Angular configurado em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`)
- Token ausente, inválido ou expirado em rota protegida retorna `401`; usuário sem `ADMIN` em `/admin/**` retorna `403`

## Recuperação de senha (esqueci minha senha)

- `POST /auth/forgot-password` recebe `email` e sempre retorna `200` com resposta genérica, independentemente de o e-mail existir ou pertencer a um usuário ativo, para evitar enumeração de usuários
- Rate limiting reaproveitando `LoginAttemptService` (mesmo limite de login: 5 solicitações por e-mail em 15 minutos); acima do limite retorna `429 Too Many Requests`. Chaves cujas tentativas já saíram da janela são removidas do mapa em memória tanto ao consultar o limite quanto periodicamente por `LoginAttemptCleanupScheduler` (a cada 15 min), evitando crescimento ilimitado de memória via solicitações não autenticadas
- Quando o e-mail pertence a um usuário ativo, qualquer token de redefinição anterior ainda válido é invalidado e um novo token aleatório (32 bytes, Base64 URL-safe) é gerado e salvo em `password_reset_tokens` **apenas como hash SHA-256**, com expiração configurável (`app.email.reset-password-token-ttl-minutos`, default 30 min — a mesma propriedade usada no texto do e-mail, evitando divergência entre o prazo real e o exibido ao usuário); o token em texto puro nunca é persistido, apenas enviado por e-mail. Apenas o token da solicitação mais recente é válido
- O e-mail é montado a partir do template Thymeleaf `password-reset.html` e enviado de forma assíncrona (`@Async`) via `EmailSender`, sem bloquear a resposta do `forgot-password`
- `POST /auth/reset-password` recebe `token`, `novaSenha` (mínimo 8 caracteres) e `confirmacaoNovaSenha`; token inexistente, expirado ou já utilizado retorna `422 Unprocessable Entity` com mensagem genérica ("Token inválido ou expirado"), assim como confirmação de senha divergente. A busca do token usa lock pessimista (`@Lock(PESSIMISTIC_WRITE)`, mesmo padrão de `UserRepository.findByEmailForUpdate`) para impedir que duas requisições concorrentes redimam o mesmo token de uso único
- Ao redefinir com sucesso, a senha é armazenada com `BCryptPasswordEncoder` e o `token_version` do usuário é incrementado (invalidando JWTs emitidos antes da redefinição) através do mesmo `UserService.aplicarNovaSenha` reaproveitado por `PUT /users/me/senha` e pelo CRUD administrativo de usuários; o token de redefinição é marcado como usado (`used_at`), não podendo ser reutilizado
- A troca de provedor de e-mail (SMTP → SES, por exemplo) é feita apenas registrando uma nova implementação de `EmailSender` e ajustando `EmailConfig`/`app.email.provider`, sem alterar `PasswordResetService`

## Pacientes

- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes **inativos** não recebem novas cobranças nem têm aulas geradas
- Consultas de aulas não retornam registros associados a pacientes inativos

## Profissionais

- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- O `percentualPagamentoAula` representa o percentual recebido por aula ministrada
- Profissionais inativos são mantidos no banco (soft delete)
- O relatório de pagamento considera apenas aulas `realizada = true` vinculadas ao profissional, dentro do período informado e associadas a pacientes ativos
- A consulta do relatório de pagamento consolida dados da aula, paciente, pagamento e contagem de aulas por pagamento em um único `JOIN` com `GROUP BY`, sem round-trips adicionais
- Valor por aula no relatório: `valor do pagamento / quantidade de aulas do pagamento`
- Valor devido ao profissional por aula: `valor por aula * percentualPagamentoAula / 100`
- O relatório retorna um contrato Angular-friendly com sub-objetos `profissional`, `periodo`, `resumo`, `pagamentos`, `aulas` e `geradoEm`. O bloco `pagamentos` agrega aulas pelo `pagamentoId` para facilitar a exibição financeira em UIs
- O relatório de pagamento é limitado a períodos de até 366 dias e até 5.000 aulas para evitar exportações excessivas em memória
- A exportação em PDF e XLSX reusa o mesmo cálculo do endpoint JSON. PDF usa OpenPDF; XLSX usa Apache POI (abas `Resumo`, `Pagamentos`, `Aulas`). Os endpoints retornam `Content-Disposition: attachment` com nome `relatorio-pagamento-profissional-{id}-{inicio}-{fim}.{ext}`

## Planos de Pagamento

- Tipos e duração: `MENSAL` (1 mês), `TRIMESTRAL` (3 meses), `ANUAL` (12 meses)
- A quantidade de dias da semana selecionados deve corresponder exatamente à frequência contratada (1x, 2x ou 3x)
- Ao criar um novo plano, o plano ativo anterior é automaticamente inativado

## Frequência de Aulas

| Frequência | Vezes/semana | Aulas/mês (referência) |
|---|---|---|
| `UMA_VEZ` | 1 | 4 |
| `DUAS_VEZES` | 2 | 8 |
| `TRES_VEZES` | 3 | 12 |

## Pagamentos

- Status: `PENDENTE` → `PAGO` ou `VENCIDO`
- Valor não pode ser menor que o valor do plano
- Não pode haver dois pagamentos para o mesmo plano no mesmo período (`UNIQUE plano_id + periodo_inicio`)
- Ao confirmar (`PAGO`), as aulas do período são geradas automaticamente
- A confirmação de pagamento recebe `dataPagamento` no corpo da requisição; se omitida, usa a data atual

## NFSE

- O relatório de emissão de NFSEs considera apenas pagamentos `PAGO`, com `dataPagamento` dentro da competência informada e pacientes ativos
- `competencia` é obrigatória no formato `MM/AAAA`; mês deve estar entre `01` e `12`
- O relatório retorna `Nome`, `CPF/CNPJ`, `ValorPago`, `Competencia`, `DescricaoServico`, `NotaAnteriorEmitida`, `DataPagamento` e `Observacoes`; nome, CPF/CNPJ, valor pago e data de pagamento vêm do paciente e do pagamento confirmado
- `DescricaoServico` é gerada automaticamente como `Aulas de Pilates - Competência MM/AAAA`
- `NotaAnteriorEmitida` é baseada nas NFSEs emitidas persistidas (`notas_fiscais_emitidas`): `true` quando existe nota registrada para o paciente em competência anterior à consultada
- O filtro opcional `notaAnteriorEmitida` permite retornar apenas registros com ou sem nota anterior emitida
- `formato` aceita `JSON`, `CSV` e `XLSX`; CSV e XLSX retornam `Content-Disposition: attachment` com nome `relatorio-nfse-{MM-AAAA}.{ext}`
- Registros sem nome do paciente, CPF/CNPJ, valor positivo ou data de pagamento retornam `422 Unprocessable Entity`
- As NFSEs emitidas são registradas via `POST /api/nfse-emitidas` (upsert por `(paciente, competência)`): paciente inexistente retorna `404` e paciente inativo `422` (regra comum de `PacienteGuard`), `competencia` no formato `MM/AAAA` (`400`), `numeroNota` no máximo 60 caracteres (`400`), `dataEmissao` não futura (`422`) e `valor`, quando informado, maior que zero (`422`). O upsert é idempotente mesmo sob concorrência: a colisão da constraint única `(paciente, competência)` é repetida automaticamente como atualização
- Timestamps de auditoria (`dataCriacao`/`dataAtualizacao`) das NFSEs emitidas são preenchidos pelos callbacks `@PrePersist`/`@PreUpdate` da entidade, conforme a convenção do projeto

## Geração de Aulas

- Aulas geradas com base nos dias da semana do plano e no período do pagamento, percorrendo dia a dia entre `periodoInicio` e `periodoFim`
- Sem duplicatas: ignora datas onde o paciente já tem aula registrada
- Requer paciente ativo e pagamento `PAGO`
- Consultas por ID, paciente, pagamento, período e relatório filtram `paciente.ativo = true`
- Ao marcar uma aula como realizada, `profissionalId` pode ser informado para alimentar o relatório de pagamento do profissional
- `AulaResponseDTO` expõe `profissionalId` e `profissionalNome`, `null` enquanto a aula não tem profissional vinculado
- `PATCH /aulas/{id}/profissional` atribui o profissional **antes** da realização, viabilizando a agenda futura: aulas geradas pelo pagamento nascem sem profissional e, sem essa rota, o vínculo só existiria retroativamente. Profissional inexistente retorna `404`, inativo retorna `422` — mesmas regras de `realizar`
- `profissionalId` é obrigatório mas anulável nesse corpo: `null` explícito desvincula, e **omitir o campo retorna `400`**. A distinção existe porque `{}` e `{"profissionalId": null}` chegam iguais ao Java — sem exigir o campo, um corpo montado sem ele apagaria o vínculo em silêncio. Quem desvincula precisa dizer isso explicitamente
- Aula já realizada retorna `409` nessa rota: depois de realizada, o vínculo alimenta o repasse e só muda por `PATCH /aulas/{id}/realizar`
- Precedência entre as duas rotas: `realizar` **sem** `profissionalId` preserva o profissional previamente atribuído; **com** `profissionalId` sobrescreve o vínculo
- Atribuir profissional não altera o relatório de pagamento: ele continua contando só aulas `realizada = true`. Aula apenas atribuída é projeção de agenda, não valor a pagar
- `PATCH /aulas/{id}/remarcar` troca a data de uma aula **não realizada**, mantendo paciente e `pagamento_id`. Sem essa rota, falta ou pedido de troca de dia só tinha duas saídas ruins: perder a aula ou marcá-la como realizada em data errada, corrompendo o relatório de pagamento do profissional
- A nova data **não** precisa ficar dentro de `periodoInicio`/`periodoFim` do pagamento de origem, e pode estar no passado. A decisão de negócio (#170) foi priorizar a operação da recepção: restringir ao período impediria remarcar a falta da última semana do mês, e proibir data passada impediria registrar reposição já ocorrida. A contrapartida aceita é que o repasse do profissional pode contar a aula num mês diferente do que a pagou — o relatório agrupa por `a.data`, não pelo período do pagamento
- Aula já realizada retorna `409` ao ser remarcada: a partir daí ela alimenta o repasse e mudar a data reescreveria histórico financeiro
- Colisão com a constraint `(paciente_id, data)` retorna `409` com a data na mensagem. A checagem prévia por `existsByPacienteAndData` não fecha a janela até o `INSERT`, então a violação de constraint também é capturada no `saveAndFlush` e convertida — nunca escapa como erro de banco
- Remarcar para a data que a aula já tem é no-op: retorna `200` sem consultar colisão nem gravar, porque a própria aula ocuparia a data na checagem
- `Aula` tem locking otimista (`@Version`, coluna `version` da `V34`). As três rotas de escrita — `realizar`, `profissional` e `remarcar` — gravam campos diferentes da mesma linha, e sem versão duas recepcionistas simultâneas se sobrescrevem em silêncio. O caso caro é realizar e remarcar ao mesmo tempo: a aula fica realizada numa data em que não foi ministrada e o erro segue direto para o repasse, que agrupa por `data`. Quem perde a corrida recebe `409` e refaz sobre o estado novo
- `GET /aulas?inicio=&fim=` é a agenda do estúdio: período fechado e obrigatório (`400` se ausente, invertido, acima de 92 dias ou com mais de 5000 registros no resultado), sem paginação, ordenado por `data` e `id`; filtros opcionais `profissionalId`, `pacienteId` e `realizada` são combináveis. A consulta projeta direto no DTO para não arrastar a cadeia EAGER `pagamento → plano → diasSemana`
- A geração **não** consulta bloqueios de agenda: feriado e manutenção são avisos, não impedimentos (ver *Bloqueios de Agenda*)

## Bloqueios de Agenda

- Registram períodos em que o estúdio não funciona: feriados, manutenção de equipamentos, eventos, férias coletivas
- **O bloqueio é informativo, nunca bloqueante.** Não impede a geração automática de aulas, o agendamento de sessões nem a remarcação para uma data bloqueada. A decisão de negócio (#171) foi que existem exceções legítimas — a turma que treina no feriado — e uma recusa automática obrigaria a recepção a apagar o bloqueio para registrar a exceção. O contrato é avisar, via `GET /bloqueios`, e deixar a decisão humana
- Consequência assumida: um feriado no meio do mês continua gerando aula, e cabe à recepção decidir entre manter e remarcar (`PATCH /aulas/{id}/remarcar`). O ganho da funcionalidade é enxergar o conflito na agenda em vez de descobri-lo no dia
- `dataInicio` e `dataFim` são obrigatórias e ambas inclusivas; um feriado de um dia só repete a mesma data nas duas pontas. `dataFim` anterior a `dataInicio` retorna `400`
- `horaInicio`/`horaFim` são opcionais e **só valem juntas**: as duas ausentes bloqueiam o dia inteiro (`diaInteiro = true` na resposta), as duas presentes bloqueiam a faixa em cada dia do período. Informar apenas uma retorna `400`, assim como `horaFim` não posterior a `horaInicio`
- Bloqueio com faixa de horário não tem efeito prático enquanto a `Aula` não tiver horário ([#106](https://github.com/FabioCarlesso/carlessopilatesapi/issues/106)); os campos já existem no modelo para não exigir nova migration depois
- `motivo` é obrigatório, não pode ser vazio ou só espaços e tem no máximo 200 caracteres; espaços nas pontas são aparados antes de persistir
- Sobreposição é permitida: "feriado" e "manutenção" no mesmo dia são dois registros legítimos, não um erro
- `GET /bloqueios?inicio=&fim=` retorna os bloqueios que **intersectam** o período (`dataInicio <= fim AND dataFim >= inicio`), inclusive os que começam antes ou terminam depois dele — férias de 20/12 a 05/01 precisam aparecer na consulta de janeiro. Ordenado por `dataInicio` e `id`, reusando o `PeriodoGuard` de aulas e sessões (período obrigatório e fechado), porém com teto de **366 dias** em vez dos 92 da agenda: o cadastro de feriados é conferido para o ano inteiro de uma vez e rende poucos registros por ano, não milhares por trimestre
- `PUT /bloqueios/{id}` é substituição completa: omitir `horaInicio`/`horaFim` remove a faixa e devolve o bloqueio para dia inteiro
- `DELETE /bloqueios/{id}` é exclusão física — o bloqueio não é referenciado por nenhum outro registro, então apagar não deixa órfãos e não afeta aulas ou sessões do período
- Autorização: escrita (`POST`, `PUT`, `DELETE`) exige `ADMIN`; a leitura fica em `authenticated()` porque é ela que alimenta o aviso nas telas de agenda e sessão usadas pela recepção. Escolha consciente enquanto a modelagem de papéis da [#131](https://github.com/FabioCarlesso/carlessopilatesapi/issues/131) não chega

## Anamnese

- Cada paciente possui no máximo uma anamnese principal (regra de unicidade por `paciente_id`)
- Criar anamnese para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Tentar criar segunda anamnese para o mesmo paciente retorna `409`
- Campos obrigatórios: `queixaPrincipal` e `objetivos`
- Consultas e atualizações de anamnese não filtram por `paciente.ativo`: o histórico de paciente inativo continua legível e editável (ver *Histórico clínico de paciente inativo*)
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `queixaPrincipal` e `objetivos` não aceitam strings em branco quando enviados
- `dataAtualizacao` é registrada automaticamente em cada atualização

## Avaliações Fisioterapêuticas

- Um paciente pode possuir múltiplas avaliações fisioterapêuticas para manter histórico clínico
- Criar avaliação para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `dataAvaliacao`, `queixaFuncional`, `escalaDor` e `diagnosticoFisioterapeutico`
- `escalaDor` aceita apenas valores inteiros de 0 a 10
- Consultas e atualizações não filtram por `paciente.ativo`: o histórico de ex-aluno continua acessível
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; campos textuais obrigatórios não aceitam strings em branco quando enviados
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

## Planos de Tratamento

- Um paciente pode possuir múltiplos planos de tratamento para manter histórico clínico
- Criar plano de tratamento para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId`, `dataInicio` e `objetivosTratamento`
- `dataFimPrevista`, quando informada, não pode ser anterior a `dataInicio`
- `numeroSessoesPrevistas` aceita apenas valores positivos quando informado
- Consultas por ID e por paciente filtram `plano.ativo = true` (soft delete do próprio plano), mas não por `paciente.ativo`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `objetivosTratamento` não aceita strings em branco quando enviado
- Exclusão é lógica: `DELETE /planos-tratamento/{id}` marca `ativo = false` e preserva o histórico clínico
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

## Sessões de Pilates/Fisioterapia

- Um paciente pode possuir múltiplas sessões para manter histórico clínico
- Criar sessão para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId`, `tipo` e `data`
- `tipo` aceita `PILATES` ou `FISIOTERAPIA`
- `status` padrão é `AGENDADA`; mudanças de status devem usar `PATCH /sessoes/{id}/realizar` ou `PATCH /sessoes/{id}/cancelar`
- Transições de status permitidas: apenas `AGENDADA -> REALIZADA` e `AGENDADA -> CANCELADA`; sessões `REALIZADA` ou `CANCELADA` não podem mudar de status novamente
- `profissionalId` e `planoTratamentoId` são opcionais; quando informados, o recurso deve existir e estar ativo, e o plano de tratamento deve pertencer ao mesmo `pacienteId` da sessão
- `duracaoMinutos` aceita apenas valores positivos quando informado
- Atualização parcial: `PUT /sessoes/{id}` aplica apenas campos não-nulos do DTO de update; `status` não faz parte do payload
- A evolução clínica estruturada deve ser registrada em `/evolucoes-sessao`; o campo legado `sessoes_pilates.evolucao` não faz parte do contrato REST
- Exclusão é física (DELETE permanente — sem soft delete, pois sessões canceladas por engano devem poder ser removidas) e remove a evolução vinculada quando existir
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização
- `GET /sessoes?inicio=&fim=` é a agenda do estúdio: período fechado e obrigatório (`400` se ausente, invertido, acima de 92 dias ou com mais de 5000 registros no resultado), sem paginação, ordenado por `data`, `horario` e `id` — sessões sem `horario` no fim do dia; filtros opcionais `profissionalId`, `pacienteId`, `tipo` e `status` são combináveis. Diferente das aulas, não filtra `paciente.ativo`: sessão é registro clínico e o histórico do ex-aluno continua visível

## Evoluções de Sessão

- Cada sessão possui no máximo uma evolução clínica (regra de unicidade por `sessao_id`)
- Criar evolução para sessão inexistente retorna `404`; para sessão de paciente inativo retorna `422`
- Tentar criar segunda evolução para a mesma sessão retorna `409`
- Campos obrigatórios: `sessaoId` e `dataHoraRegistro`
- `dorAntes` e `dorDepois`, quando informados, aceitam apenas valores inteiros de 0 a 10
- Consultas e atualizações de evolução não filtram por `sessao.paciente.ativo`
- `GET /evolucoes-sessao/paciente/{pacienteId}` devolve o histórico completo do paciente em uma única chamada, ordenado por `sessao.data DESC`, `sessao.horario DESC NULLS LAST`, `sessao.id DESC`
- Paciente existente sem nenhuma evolução recebe `200` com lista vazia; apenas paciente inexistente retorna `404`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- Ao excluir uma sessão, a evolução vinculada é removida junto
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

### Por que a listagem não é paginada (decisão da #158)

- **A listagem não é paginada**, com base na medição da base real em 2026-08-05: 90 pacientes com evolução (de 112 cadastrados), 5.697 evoluções no total; apenas 2 pacientes acima de 300 e 76 abaixo de 100 (mediana 35, média 63, máximo 384). A maior resposta é de 332 KB crus, que caem para **50 KB sob gzip** (razão de 6,5× a 8×). Paginar quebraria o contrato e arrastaria junto endpoint de série e filtros server-side, para economizar dezenas de KB
- O custo dominante da tela é DOM, não rede: renderizar 384 evoluções custa ~1 s a mais que renderizar 4 (2,2 s contra 1,2 s até o DOM estabilizar, em `ng serve`; 4.658 elementos no `<main>`). A solução é virtual scroll no frontend, que não exige mudança de contrato
- **Gatilho para revisitar a paginação** — basta um destes: (1) algum paciente ultrapassar **1.000 evoluções** (no ritmo atual de ~90 evoluções/ano dos pacientes mais assíduos, são ~7 anos); (2) a maior resposta gzipada passar de **150 KB**; (3) a aplicação passar a atender **mais de uma clínica**. Enquanto nenhum ocorrer, a listagem completa em uma chamada é a opção de menor atrito
- A decisão pressupõe a [compressão gzip](operacao.md#compressão-de-respostas-gzip) ativa — habilitada na #157 (`server.compression.*`), com 329.192 → 41.316 bytes medidos; sem ela a maior resposta volta a trafegar 321 KB crus

## Reavaliações

- Um paciente pode possuir múltiplas reavaliações periódicas para comparação com avaliações anteriores e acompanhamento da evolução clínica
- Criar reavaliação para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId` e `dataReavaliacao`
- `avaliacaoFisioterapeuticaId` e `planoTratamentoId` são opcionais; quando informados, o recurso deve existir, estar ativo quando aplicável e pertencer ao mesmo `pacienteId` da reavaliação
- Consultas por ID e por paciente não filtram por `paciente.ativo`
- Listagem por paciente retorna reavaliações ordenadas por `dataReavaliacao DESC, id DESC`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

## Histórico clínico de paciente inativo

- `ativo = false` em paciente marca um **ex-aluno**, não um registro apagado: todo o prontuário dele continua consultável pela API
- As consultas por paciente (`/sessoes`, `/anamneses`, `/avaliacoes-fisioterapeuticas`, `/planos-tratamento`, `/reavaliacoes`, `/evolucoes-sessao`, `/api/nfse-emitidas`) validam apenas a **existência** do paciente (`existsById`); `404 Paciente não encontrado` significa que o id realmente não existe
- Registro ausente para paciente existente usa mensagem própria (ex.: `Anamnese não encontrada para o paciente: {id}`), nunca `Paciente não encontrado`
- **Criar** novo registro clínico continua exigindo paciente ativo, e os oito pontos de criação respondem igual: `404` quando o paciente não existe, `422` quando existe mas está inativo. A regra fica em `util/PacienteGuard.exigirAtivo`, chamada por sessão, anamnese, avaliação, plano de tratamento, reavaliação, NFSE, evolução e análise postural
- **Atualizar** registro já existente de paciente inativo é permitido — consequência direta de o registro voltar a ser visível
- Os filtros por `paciente.ativo` permanecem nas consultas **financeiras/operacionais** (aulas, dashboard, relatórios de pagamento e de NFSE), onde excluir ex-alunos é intencional

## Preferências do usuário

- Cada usuário possui no máximo um registro de preferências (constraint `UNIQUE user_id`)
- Defaults centralizados no `PreferenciasUsuarioService`: `idioma=PT_BR`, `tema=CLARO`, `notificacoesEmail=true`, `notificacoesPush=false`
- `GET /users/me/preferencias` retorna os defaults quando o usuário ainda não tem registro salvo, sem persistir nada (1 query única via `findByUserEmail`)
- `PUT /users/me/preferencias` cria o registro na primeira chamada e atualiza nas seguintes; protegido contra criação concorrente para o mesmo usuário por lock pessimista em `users` (`UserRepository.findByEmailForUpdate`)
- Campos obrigatórios no PUT: `idioma`, `tema`, `notificacoesEmail`, `notificacoesPush` (Bean Validation `@NotNull`); idioma/tema fora dos enums retornam `400`
- Identificação do dono é feita exclusivamente por `Authentication.getName()`; e-mail é normalizado para lowercase antes da consulta
- A FK `preferencias_usuario.user_id` usa `ON DELETE CASCADE` para que a remoção física de um usuário (cenário de teste e cleanup) leve junto suas preferências
- `dataCriacao` é registrada na criação e `dataAtualizacao` em cada atualização

## Scheduler (processos automáticos)

| Cron (default) | Horário | Ação | Propriedade |
|---|---|---|---|
| `0 0 6 * * *` | 06:00 todo dia | Marca como `VENCIDO` pagamentos `PENDENTE` com `dataVencimento` passada | `app.cobranca.cron-vencidos` (env `APP_COBRANCA_CRON_VENCIDOS`) |
| `0 0 7 * * *` | 07:00 todo dia | Gera cobranças futuras para planos ativos quando faltam ≤ 7 dias para o fim do período atual | `app.cobranca.cron-cobrancas-futuras` (env `APP_COBRANCA_CRON_COBRANCAS_FUTURAS`) |

O vencimento das cobranças geradas pelo scheduler é definido por `app.cobranca.vencimento-dias` (env
`APP_COBRANCA_VENCIMENTO_DIAS`, default `10`), somado ao início do período. O tamanho padrão de página nas
listagens paginadas é controlado por `spring.data.web.pageable.default-page-size`, alimentado pela env
`APP_PAGINACAO_TAMANHO_PADRAO` (default `10`).

## Ver também

- [`api.md`](api.md) — contrato REST e comportamento por endpoint
- [`arquitetura.md`](arquitetura.md#tratamento-de-erros) — mapeamento de exceções para status HTTP
- [`context.md`](context.md) — modelo de dados e decisões de design
