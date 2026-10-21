# Regras de Negócio

Fonte única das regras de negócio da API. O `docs/context.md` guarda o modelo de dados e as decisões de
design; o comportamento por endpoint está em [`api.md`](api.md).

## Segurança

- Autenticação stateless com Spring Security e JWT
- Senhas são armazenadas com `BCryptPasswordEncoder`
- O segredo JWT vem de `JWT_SECRET`; não há segredo fixo no código
- JWT inclui claims `role`, `userId` e `tokenVersion`; a cada requisição o filtro valida se o usuário ainda existe, está ativo e se a versão do token corresponde à versão atual do usuário antes de reconstruir o contexto de segurança
- O access token é curto (`jwt.expiration-ms`, default 15 min); a sessão é mantida pelo **refresh token** (`jwt.refresh-expiration-ms`, default 7 dias), emitido junto no login. Um token vazado deixa de valer em minutos, sem obrigar o usuário a logar várias vezes ao dia
- O refresh token é opaco (32 bytes aleatórios, Base64 URL-safe) e persistido em `refresh_tokens` **apenas como hash SHA-256**, como em `password_reset_tokens`
- `POST /auth/refresh` **rotaciona**: o token apresentado é marcado como usado (`used_at`) e um novo par access + refresh é emitido. Cada login abre uma **família** (`family_id`) que agrupa a cadeia de rotações; o novo refresh token herda a família e ganha prazo cheio a partir da rotação, **limitado ao teto absoluto da sessão** (`jwt.refresh-session-max-ms`, default 30 dias desde o login, gravado em `family_expires_at`). Rotacionar não estende a sessão além do teto, então um token roubado não a mantém viva para sempre
- Reapresentar um refresh token já rotacionado **dentro da tolerância** (`jwt.refresh-reuse-grace-ms`, default 10 s após a rotação) é tratado como retentativa de boa-fé — resposta perdida, duas abas renovando juntas: retorna `401`, mas a sessão continua com o sucessor já emitido e nada é contado como reuso. A contrapartida é que, se quem rotacionou primeiro foi um atacante, o reuso do dono legítimo nessa janela não derruba a sessão roubada; por isso a janela é de segundos. `0` desliga a tolerância
- Fora da tolerância, reapresentar um refresh token já rotacionado é tratado como vazamento: a família inteira é revogada (`revoked_at`), a resposta é `401` e o contador `pilates.refresh.tokens.reusados` é incrementado. Tanto quem roubou quanto o dono legítimo precisam logar de novo; outras sessões do mesmo usuário (outros logins) não são afetadas. A revogação sobrevive à exceção (`noRollbackFor`) — sem isso o rollback desfaria a revogação. Sessão já encerrada (logout, troca de senha, inativação ou reuso anterior) é checada **antes** do reuso: um token antigo dela recebe `401` sem ser contado como vazamento, para a métrica não gerar falso positivo
- A busca do refresh token usa lock pessimista, então duas rotações simultâneas do mesmo token são serializadas e a segunda recebe `401` (dentro da tolerância, sem derrubar a sessão). O frontend deve, ainda assim, serializar o refresh (uma única chamada em voo por vez) e reler o token mais recente antes de tentar de novo
- O refresh token guarda o `token_version` do usuário na emissão: troca ou redefinição de senha invalida access **e** refresh tokens antigos. Usuário inativado também não renova a sessão. Nos dois casos a família é revogada
- `POST /auth/logout` revoga a **sessão inteira** (a família do refresh token informado), não só o token: se o cliente apresentar uma cópia já rotacionada, o sucessor dela também cai. É idempotente (`204` mesmo para token desconhecido ou já revogado, para não servir de oráculo). O access token em uso continua válido até expirar — por isso ele é curto
- Limpeza diária (`RefreshTokenCleanupScheduler`, `app.refresh-token.cron-limpeza`, default 03:30) remove de `refresh_tokens` as famílias que passaram do teto absoluto e as revogadas há mais de 1 dia — cada rotação grava uma linha nova, e sem a limpeza a tabela cresceria para sempre
- Rate limiting de `/auth/login`: 5 tentativas falhas por e-mail em janela de 15 minutos retorna `429 Too Many Requests`
- Rate limiting por origem (IP), somado ao por e-mail, em `/auth/login` e `/auth/forgot-password` (`LoginAttemptService`, baldes separados por endpoint): 20 tentativas em 15 minutos da mesma origem retornam `429`, qualquer que seja o e-mail — barra quem varia o e-mail a cada tentativa (credential stuffing). Limite e janela são configuráveis (`app.rate-limit.ip.max-tentativas`, `app.rate-limit.ip.janela-minutos`, ambos `> 0`; valor inválido impede a subida) e mais folgados que o por e-mail para não punir vários usuários atrás do mesmo NAT. A tentativa é **reservada** nos dois baldes antes do trabalho caro (bcrypt no login, consulta e envio de e-mail no `forgot-password`), checando e contando numa só operação atômica: uma rajada concorrente não passa inteira pela checagem enquanto o bcrypt roda. No login a reserva é devolvida no sucesso — contam só as falhas —, mas o sucesso **não** zera as falhas anteriores da origem (senão o atacante intercalaria logins na própria conta); no `forgot-password` toda solicitação conta. Uma requisição barrada por um dos baldes não consome a cota do outro
- A origem vem da conexão (`ClientIpResolver`). O `X-Forwarded-For` só é considerado quando a conexão chega de um proxy listado em `app.security.trusted-proxies` (IPs ou CIDRs); nesse caso vale o primeiro endereço não confiável lido da direita para a esquerda, e o que estiver à esquerda dele — que o cliente pode forjar — é descartado. Porta e colchetes que alguns balanceadores acrescentam (`203.0.113.10:51234`, `[2001:db8::1]:443`) são removidos; entrada que não é IP (ex.: `unknown`) cai no endereço da conexão. Endereços IPv6 são agrupados pelo prefixo `/64`, que um assinante costuma receber inteiro — contar por endereço completo deixaria rotacionar endereços para nunca atingir o limite. Sem proxies configurados (padrão), o cabeçalho é ignorado. Atrás de proxy sem essa configuração, todos os clientes compartilham o IP do proxy e o limite por IP vira um bloqueio global; para não passar despercebido, a primeira requisição com `X-Forwarded-For` vinda de rede interna sem proxy configurado gera um `WARN` no log. Nos logs, o IP aparece mascarado (`LogMasker.ip`: `203.0.113.***`), como o e-mail
- Os dois baldes vivem em memória: zeram a cada restart e valem **por instância**. Com mais de uma réplica, cada uma conta separadamente e o limite efetivo se multiplica pelo número de réplicas — escalar horizontalmente exige mover o estado para um armazenamento compartilhado (ex.: Redis)
- Não há cadastro público de usuários: `POST /auth/register` foi removido na #132 (criação só por ADMIN em `POST /users`), então não há rota de registro a limitar
- Admin não pode inativar a própria conta nem alterar o próprio perfil de acesso (`422 Unprocessable Entity`)
- O sistema deve manter ao menos um usuário `ADMIN` ativo: não é permitido inativar nem rebaixar para `USER` o último administrador ativo (`422 Unprocessable Entity`)
- Usuários com `ativo=false` não conseguem fazer login e tokens emitidos antes da inativação deixam de autorizar rotas protegidas
- Usuário autenticado pode trocar a própria senha via `PUT /users/me/senha`: precisa informar `senhaAtual`, `novaSenha` (mínimo 8 caracteres) e `confirmacaoNovaSenha`; senha atual incorreta, confirmação divergente ou reuso da senha atual retornam `422 Unprocessable Entity`; a nova senha é armazenada com `BCryptPasswordEncoder` e access e refresh tokens emitidos antes da troca passam a retornar `401 Unauthorized`
- CORS permite o frontend Angular configurado em `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`), sem `Access-Control-Allow-Credentials`: os tokens viajam no corpo e no header `Authorization`, nunca em cookie
- Token ausente, inválido ou expirado em rota protegida retorna `401`; usuário sem `ADMIN` em `/admin/**` retorna `403`
- A listagem global `GET /pagamentos` (e o `HEAD` equivalente) e as rotas `/pagamentos/{id}/cobrancas/**` são restritas a `ADMIN` (`403` para os demais): são da gestão de inadimplência. O `POST /pagamentos` e as demais rotas de `/pagamentos/**` seguem abertas a qualquer usuário autenticado
- `GET /dashboard/indicadores` e `GET /dashboard/aulas-por-profissional` são restritos a `ADMIN` (`403` para os demais): são gestão do negócio. `GET /dashboard/resumo` segue aberto a qualquer usuário autenticado

## Recuperação de senha (esqueci minha senha)

- `POST /auth/forgot-password` recebe `email` e sempre retorna `200` com resposta genérica, independentemente de o e-mail existir ou pertencer a um usuário ativo, para evitar enumeração de usuários
- Rate limiting reaproveitando `LoginAttemptService` (mesmo limite de login: 5 solicitações por e-mail em 15 minutos); acima do limite retorna `429 Too Many Requests`. Chaves cujas tentativas já saíram da janela são removidas do mapa em memória tanto ao consultar o limite quanto periodicamente por `LoginAttemptCleanupScheduler` (a cada 15 min, para os baldes por e-mail e por IP), evitando crescimento ilimitado de memória via solicitações não autenticadas
- Quando o e-mail pertence a um usuário ativo, qualquer token de redefinição anterior ainda válido é invalidado e um novo token aleatório (32 bytes, Base64 URL-safe) é gerado e salvo em `password_reset_tokens` **apenas como hash SHA-256**, com expiração configurável (`app.email.reset-password-token-ttl-minutos`, default 30 min — a mesma propriedade usada no texto do e-mail, evitando divergência entre o prazo real e o exibido ao usuário); o token em texto puro nunca é persistido, apenas enviado por e-mail. Apenas o token da solicitação mais recente é válido
- O e-mail é montado a partir do template Thymeleaf `password-reset.html` e enviado de forma assíncrona (`@Async`) via `EmailSender`, sem bloquear a resposta do `forgot-password`
- `POST /auth/reset-password` recebe `token`, `novaSenha` (mínimo 8 caracteres) e `confirmacaoNovaSenha`; token inexistente, expirado ou já utilizado retorna `422 Unprocessable Entity` com mensagem genérica ("Token inválido ou expirado"), assim como confirmação de senha divergente. A busca do token usa lock pessimista (`@Lock(PESSIMISTIC_WRITE)`, mesmo padrão de `UserRepository.findByEmailForUpdate`) para impedir que duas requisições concorrentes redimam o mesmo token de uso único
- Ao redefinir com sucesso, a senha é armazenada com `BCryptPasswordEncoder` e o `token_version` do usuário é incrementado (invalidando JWTs e refresh tokens emitidos antes da redefinição) através do mesmo `UserService.aplicarNovaSenha` reaproveitado por `PUT /users/me/senha` e pelo CRUD administrativo de usuários; o token de redefinição é marcado como usado (`used_at`), não podendo ser reutilizado
- A troca de provedor de e-mail (SMTP → SES, por exemplo) é feita apenas registrando uma nova implementação de `EmailSender` e ajustando `EmailConfig`/`app.email.provider`, sem alterar `PasswordResetService`

## Pacientes

- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes **inativos** não recebem novas cobranças nem têm aulas geradas
- Consultas de aulas não retornam registros associados a pacientes inativos — exceto os indicadores históricos do dashboard (ver [Indicadores do dashboard](#indicadores-do-dashboard))
- O cadastro grava `dataCadastro` com a data do dia. Inativar grava `dataInativacao` com a data do dia, e inativar quem já está inativo não muda essa data; reativar a limpa

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
- A listagem global (`GET /pagamentos`, #196) ordena por `dataVencimento` crescente por padrão — os mais atrasados primeiro — e sempre acrescenta `id` como último critério: as cobranças nascem em lote com o mesmo vencimento, e sem o desempate uma linha poderia trocar de página entre duas requisições. Os campos ordenáveis são uma lista fechada (`dataVencimento`, `valor`, `status`, `paciente.nome`); campo fora dela retorna `400` em vez de chegar ao Spring Data e virar `500`
- A faixa `vencimentoDe`/`vencimentoAte` é inclusiva nas duas pontas e pode ser aberta de um dos lados; invertida retorna `400`
- Dias em atraso não são calculados no backend: derivam de `dataVencimento`, e ordenar por atraso é ordenar por vencimento

### Contatos de cobrança

- Registram cada contato do estúdio com o paciente sobre um pagamento em aberto — ligação, mensagem, conversa presencial (#197). Antes disso o histórico ficava fora do sistema, e não dava para saber se um vencido já tinha sido cobrado
- É um **log de inclusão**: não há edição nem exclusão; uma correção entra como novo contato
- `dataContato` vem do cliente (o contato pode ter acontecido antes de ser registrado), mas não pode ser futura **no fuso do estúdio** (`app.fuso-horario`). `registradoPor` e `registradoEm` são do servidor — `registradoPor` é o e-mail do usuário autenticado, gravado como snapshot para que o histórico não mude se o usuário for renomeado ou desativado, e `registradoEm` é a hora do estúdio, não a da JVM: o frontend a exibe sem conversão, e um container em UTC mostraria a hora 3h adiantada
- Pagamento `PAGO` não aceita novos contatos (`422`): não há o que cobrar. Os contatos registrados enquanto ele estava em aberto continuam no histórico. O pagamento é lido com lock de escrita (`findByIdForUpdate`): sem ele, registrar um contato e confirmar o pagamento ao mesmo tempo leriam os dois `PENDENTE`, e o contato entraria num pagamento já pago
- O histórico sai do contato mais recente para o mais antigo (`dataContato`), com `registradoEm` e `id` desempatando contatos do mesmo dia

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
- Timestamps e autoria das NFSEs emitidas seguem a [auditoria de criação/alteração](#auditoria-de-criação-e-alteração)

## Geração de Aulas

- Aulas geradas com base nos dias da semana do plano e no período do pagamento, percorrendo dia a dia entre `periodoInicio` e `periodoFim`
- Sem duplicatas: ignora datas onde o paciente já tem aula registrada
- Cada aula gerada recebe o `horario` do plano — um único horário para todos os `diasSemana` (#106). Plano sem horário gera aulas sem horário, que continuam válidas e consultáveis; o horário entra depois, aula a aula, por `PATCH /aulas/{id}/remarcar`. Planos não têm edição, então mudar o horário de um aluno exige novo plano, e as aulas já geradas mantêm o horário antigo
- O paciente continua com no máximo **uma aula por dia**: a constraint `(paciente_id, data)` foi mantida com a chegada do horário, e com ela a regra de colisão da remarcação (#170)
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
- `horario` é opcional na remarcação: ausente, a aula mantém o horário atual. Remarcar só o horário no mesmo dia não consulta a colisão `(paciente_id, data)` — só a troca de dia pode colidir. Mesma data e mesmo horário é no-op
- **Capacidade por horário**: cada profissional atende até `app.agenda.capacidade-por-horario` aulas (padrão `3`) cujos inícios distem menos que `app.agenda.duracao-aula-minutos` (padrão `60`) do início da aula conferida. A regra roda ao atribuir profissional e ao remarcar aula que já tem profissional, e excedê-la retorna `409`. A decisão de negócio (#106) foi limitar por profissional, não exigir exclusividade: no pilates um profissional atende vários alunos na mesma turma
- A contagem é conservadora: duas aulas às 08:00 e 08:50 sobrepõem uma nova às 08:30 e contam ambas, mesmo sem sobrepor uma à outra. Só pesa em grades com horários quebrados; com turmas alinhadas à duração (08:00, 09:00…), a conta é exata
- Ficam fora da capacidade: aula sem horário ou sem profissional (não ocupa a agenda de ninguém), aula de paciente inativo (não vai acontecer) e `realizar` com `profissionalId` — a rota registra um fato, e recusá-lo obrigaria a lançar o profissional errado, corrompendo o repasse
- Atribuir o profissional que a aula **já tem** é no-op e não confere capacidade: não ocupa vaga nova. Sem isso, um horário que passou do limite por um `realizar` recusaria com `409` uma operação que não muda nada
- Nenhuma constraint do banco garante a capacidade, então a checagem trava o profissional (`SELECT ... FOR UPDATE`) antes de contar: duas atribuições simultâneas ao mesmo horário são serializadas em vez de passarem juntas do limite (`AulaCapacidadeConcurrencyIntegrationTest`)
- `Aula` tem locking otimista (`@Version`, coluna `version` da `V34`). As três rotas de escrita — `realizar`, `profissional` e `remarcar` — gravam campos diferentes da mesma linha, e sem versão duas recepcionistas simultâneas se sobrescrevem em silêncio. O caso caro é realizar e remarcar ao mesmo tempo: a aula fica realizada numa data em que não foi ministrada e o erro segue direto para o repasse, que agrupa por `data`. Quem perde a corrida recebe `409` e refaz sobre o estado novo
- `GET /aulas?inicio=&fim=` é a agenda do estúdio: período fechado e obrigatório (`400` se ausente, invertido, acima de 92 dias ou com mais de 5000 registros no resultado), sem paginação, ordenado por `data`, `horario` e `id` — aulas sem `horario` no fim do dia; filtros opcionais `profissionalId`, `pacienteId` e `realizada` são combináveis. A consulta projeta direto no DTO para não arrastar a cadeia EAGER `pagamento → plano → diasSemana`
- A geração **não** consulta bloqueios de agenda: feriado e manutenção são avisos, não impedimentos (ver *Bloqueios de Agenda*)

## Bloqueios de Agenda

- Registram períodos em que o estúdio não funciona: feriados, manutenção de equipamentos, eventos, férias coletivas
- **O bloqueio é informativo, nunca bloqueante.** Não impede a geração automática de aulas, o agendamento de sessões nem a remarcação para uma data bloqueada. A decisão de negócio (#171) foi que existem exceções legítimas — a turma que treina no feriado — e uma recusa automática obrigaria a recepção a apagar o bloqueio para registrar a exceção. O contrato é avisar, via `GET /bloqueios`, e deixar a decisão humana
- Consequência assumida: um feriado no meio do mês continua gerando aula, e cabe à recepção decidir entre manter e remarcar (`PATCH /aulas/{id}/remarcar`). O ganho da funcionalidade é enxergar o conflito na agenda em vez de descobri-lo no dia
- `dataInicio` e `dataFim` são obrigatórias e ambas inclusivas; um feriado de um dia só repete a mesma data nas duas pontas. `dataFim` anterior a `dataInicio` retorna `400`
- `horaInicio`/`horaFim` são opcionais e **só valem juntas**: as duas ausentes bloqueiam o dia inteiro (`diaInteiro = true` na resposta), as duas presentes bloqueiam a faixa em cada dia do período. Informar apenas uma retorna `400`, assim como `horaFim` não posterior a `horaInicio`
- Com o horário da aula (#106), a faixa do bloqueio passa a poder ser cruzada com a agenda: o calendário compara `horaInicio`/`horaFim` com o `horario` das aulas do dia. O cruzamento continua sendo do cliente — o backend não recusa nem marca a aula, coerente com o bloqueio ser informativo
- `motivo` é obrigatório, não pode ser vazio ou só espaços e tem no máximo 200 caracteres; espaços nas pontas são aparados antes de persistir
- Sobreposição é permitida: "feriado" e "manutenção" no mesmo dia são dois registros legítimos, não um erro
- `GET /bloqueios?inicio=&fim=` retorna os bloqueios que **intersectam** o período (`dataInicio <= fim AND dataFim >= inicio`), inclusive os que começam antes ou terminam depois dele — férias de 20/12 a 05/01 precisam aparecer na consulta de janeiro. Ordenado por `dataInicio` e `id`, reusando o `PeriodoGuard` de aulas e sessões (período obrigatório e fechado), porém com teto de **366 dias** em vez dos 92 da agenda: o cadastro de feriados é conferido para o ano inteiro de uma vez e rende poucos registros por ano, não milhares por trimestre
- `PUT /bloqueios/{id}` é substituição completa: omitir `horaInicio`/`horaFim` remove a faixa e devolve o bloqueio para dia inteiro
- `DELETE /bloqueios/{id}` é exclusão física — o bloqueio não é referenciado por nenhum outro registro, então apagar não deixa órfãos e não afeta aulas ou sessões do período
- Autorização: escrita (`POST`, `PUT`, `DELETE`) exige `ADMIN`; a leitura fica em `authenticated()` porque é ela que alimenta o aviso nas telas de agenda e sessão usadas pela recepção. Escolha consciente enquanto a modelagem de papéis da [#131](https://github.com/FabioCarlesso/carlessopilatesapi/issues/131) não chega

## Lista de Espera

- Registra quem quer entrar num dia da semana e faixa de horário já lotados, para que a recepção tenha a quem chamar quando um cancelamento (`PATCH /sessoes/{id}/cancelar`) abrir vaga. Antes disso o interesse ficava na memória de quem atendeu (#172)
- **A fila é só cronológica.** `dataEntrada` é gravada pelo servidor na criação e nunca aceita do corpo — é ela que define a posição, e um cliente capaz de escolhê-la conseguiria furar a fila. Não existe prioridade manual
- A lista é **consultiva, nunca bloqueante**, pelo mesmo critério dos bloqueios de agenda: não impede o cancelamento de uma sessão com interessados e não vira sessão sozinha. O frontend exibe o aviso e chama o `POST /sessoes` existente
- Inscrição para paciente inexistente retorna `404`; para paciente inativo retorna `422`, como as demais criações de registro
- Duplicidade é o mesmo paciente, no mesmo dia da semana e na **mesma faixa exata**, ainda ativa: retorna `409`. Faixas apenas sobrepostas (08:00–09:00 e 08:30–09:30) descrevem interesses diferentes e coexistem
- A garantia da unicidade é o índice parcial `lista_espera_ativa_uidx` (`V37`), não a checagem do service: entre o `SELECT` e o `INSERT` há uma janela que requisições simultâneas atravessam juntas — oito `POST` concorrentes criaram três entradas idênticas antes do índice existir. O service mantém a checagem prévia (mensagem melhor no caso comum) e converte a violação de constraint no mesmo `409`, como `AulaService.remarcar` faz com `(paciente_id, data)`. O índice é **parcial** (`WHERE ativo`) porque a saída da fila é lógica: sem o predicado, quem saísse não conseguiria reentrar na mesma faixa
- `GET /lista-espera` recusa com `400` acima de **5000 registros**, mesmo teto das agendas de aulas e sessões e pela mesma razão: resposta única, sem paginação. A fila ativa é pequena por natureza, então o limite é rede de segurança, não recorte esperado
- `GET /lista-espera/{id}` existe para que o `Location` do `201` aponte para algo recuperável; entrada já removida responde `404`, como na listagem
- `horaFim` precisa ser posterior a `horaInicio` — faixa invertida ou de duração zero retorna `400`
- A consulta por faixa casa por **interseção**, não por igualdade: quem se inscreveu para 07:30–09:30 aparece na busca por 08:00–09:00, senão a recepção só encontraria quem digitou exatamente a mesma faixa. As pontas são abertas (`horaFim > :horaInicio AND horaInicio < :horaFim`) porque uma inscrição que termina 08:00 não disputa a vaga que começa 08:00
- Os filtros do `GET` são todos opcionais, mas `horaInicio` e `horaFim` só valem juntas (`400` caso contrário): meia faixa não descreve um interesse consultável
- A listagem ignora entradas de **paciente inativo**, mesmo recorte das consultas de aula — ex-aluno não deve ser chamado para uma vaga. A busca por id do `DELETE` não aplica esse filtro, senão inativar o paciente deixaria a linha viva sem nenhuma rota capaz de encerrá-la
- `DELETE /lista-espera/{id}` é remoção **lógica** (`ativo = false`): o contrato HTTP é o que o frontend pediu, mas apagar a linha perderia o registro de quem foi chamado a partir da fila — a única memória de que a espera funcionou. Repetir o `DELETE` retorna `404`
- Notificar o paciente automaticamente quando abre vaga é fase 2 e depende dos lembretes da [#107](https://github.com/FabioCarlesso/carlessopilatesapi/issues/107); quando existirem, esta entidade é a fonte da notificação

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
- `dataCriacao`/`criadoPor` são registrados na criação e `dataAtualizacao`/`alteradoPor` em cada atualização ([auditoria](#auditoria-de-criação-e-alteração))

## Planos de Tratamento

- Um paciente pode possuir múltiplos planos de tratamento para manter histórico clínico
- Criar plano de tratamento para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId`, `dataInicio` e `objetivosTratamento`
- `dataFimPrevista`, quando informada, não pode ser anterior a `dataInicio`
- `numeroSessoesPrevistas` aceita apenas valores positivos quando informado
- Consultas por ID e por paciente filtram `plano.ativo = true` (soft delete do próprio plano), mas não por `paciente.ativo`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `objetivosTratamento` não aceita strings em branco quando enviado
- Exclusão é lógica: `DELETE /planos-tratamento/{id}` marca `ativo = false` e preserva o histórico clínico
- `dataCriacao`/`criadoPor` são registrados na criação e `dataAtualizacao`/`alteradoPor` em cada atualização ([auditoria](#auditoria-de-criação-e-alteração))

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
- `dataCriacao`/`criadoPor` são registrados na criação e `dataAtualizacao`/`alteradoPor` em cada atualização ([auditoria](#auditoria-de-criação-e-alteração))
- `GET /sessoes?inicio=&fim=` é a agenda do estúdio: período fechado e obrigatório (`400` se ausente, invertido, acima de 92 dias ou com mais de 5000 registros no resultado), sem paginação, ordenado por `data`, `horario` e `id` — sessões sem `horario` no fim do dia; filtros opcionais `profissionalId`, `pacienteId`, `tipo` e `status` são combináveis. Diferente das aulas, não filtra `paciente.ativo`: sessão é registro clínico e o histórico do ex-aluno continua visível

## Evoluções de Sessão

- Cada sessão possui no máximo uma evolução clínica (regra de unicidade por `sessao_id`)
- Criar evolução para sessão inexistente retorna `404`; para sessão de paciente inativo retorna `422`
- Tentar criar segunda evolução para a mesma sessão retorna `409`
- Campos obrigatórios: `sessaoId` e `dataHoraRegistro`
- `dorAntes` e `dorDepois`, quando informados, aceitam apenas valores inteiros de 0 a 10
- Consultas e atualizações de evolução não filtram por `sessao.paciente.ativo`
- `GET /evolucoes-sessao/paciente/{pacienteId}` devolve o histórico completo do paciente em uma única chamada, ordenado por `sessao.data DESC`, `sessao.horario DESC NULLS LAST`, `sessao.id DESC`. É a única listagem por paciente que ficou fora da paginação da #100 (motivo abaixo)
- Paciente existente sem nenhuma evolução recebe `200` com lista vazia; apenas paciente inexistente retorna `404`
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- Ao excluir uma sessão, a evolução vinculada é removida junto
- `dataCriacao`/`criadoPor` são registrados na criação e `dataAtualizacao`/`alteradoPor` em cada atualização ([auditoria](#auditoria-de-criação-e-alteração))

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
- Listagem por paciente é paginada e, por padrão, sai por `dataReavaliacao DESC, id DESC` — ver [Listagens por paciente](api.md#listagens-por-paciente)
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados
- `dataCriacao`/`criadoPor` são registrados na criação e `dataAtualizacao`/`alteradoPor` em cada atualização ([auditoria](#auditoria-de-criação-e-alteração))

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
- `dataCriacao`/`criadoPor` são registrados na criação e `dataAtualizacao`/`alteradoPor` em cada atualização ([auditoria](#auditoria-de-criação-e-alteração))

## Locking otimista

Dez entidades editáveis têm `@Version` (coluna `version` da `V40`): `Paciente`, `Profissional`, `Anamnese`,
`AvaliacaoFisioterapeutica`, `PlanoTratamento`, `SessaoPilates`, `EvolucaoSessao`, `Reavaliacao`, `User` e
`PreferenciasUsuario` — além de `Aula`, que já tinha desde a `V34`. Sem ela, duas recepcionistas editando o mesmo
paciente se sobrescreviam em silêncio e a alteração de uma se perdia sem aviso.

- O update compara a `version` enviada pelo cliente com a do registro (`VersaoGuard`) antes de alterar qualquer
  campo. Divergência é `409` `concurrent-modification` e nada é gravado.
- Mesmo com a versão certa, duas requisições simultâneas ainda podem passar pela checagem ao mesmo tempo; o
  `UPDATE ... WHERE version = ?` do Hibernate barra a segunda no flush, com o mesmo `409`.
- **Modo legado temporário:** update sem `version` não é checado contra o que o cliente viu — é o comportamento de
  antes, mantido para o frontend atual seguir funcionando até passar a enviar a versão. A proteção da janela
  leitura → flush continua valendo. Quando o frontend estiver enviando em todas as telas, o modo legado deve ser
  removido e a versão, tornada obrigatória.
- Toda escrita na linha incrementa a versão, inclusive as que não passam pelo `PUT`: ativar/inativar paciente ou
  profissional, realizar/cancelar sessão, troca de senha (que mexe em `users`). Um formulário aberto antes dessas
  ações recebe `409` ao salvar.
- `Pagamento` ficou de fora: suas transições já são protegidas por regras de status e constraints.

## Indicadores do dashboard

`GET /dashboard/indicadores` e `GET /dashboard/aulas-por-profissional` (#202) são restritos a `ADMIN`; o `GET /dashboard/resumo` segue aberto a qualquer autenticado.

- A janela de `indicadores` tem `meses` meses (padrão 12, de 1 a 24, fora disso `400`) e termina no mês atual, que entra na série. Todo mês da janela aparece nas três séries, com zero quando não houve movimento.
- **Receita:** soma de `valor` dos pagamentos `PAGO`, pelo mês de `dataPagamento` — o mesmo critério do `receitaMesAtual` do resumo.
- **Aulas:** `realizadas` conta `realizada = true` e `agendadas` conta `realizada = false`, pelo mês de `data` — o mesmo par do resumo. Ocupação = realizadas / (realizadas + agendadas).
- **Pacientes:** `novos` conta por `dataCadastro` e `inativados` por `dataInativacao`. Pacientes sem essas datas (anteriores à `V41`) não entram em nenhum mês; um paciente reativado deixa de contar como evasão. Pacientes importados pelo `scripts/import_seufisio.py` entram como novos no mês da importação.
- **Ranking por profissional:** aulas `realizada = true` entre `inicio` e `fim` (inclusive), do maior total para o menor e, no empate, por nome. Sem os dois parâmetros, usa o mês atual; com só um, período invertido ou acima de 366 dias, `400`. Profissional inativo aparece se teve aulas; aula sem profissional fica de fora.
- Diferente das demais consultas de aulas, os indicadores **não** excluem pacientes inativos: no histórico, a inativação de hoje apagaria aulas que aconteceram.

## Auditoria de criação e alteração

Autor e momento da criação e da última alteração são gravados automaticamente pelo Spring Data Auditing (#109), na
superclasse `EntidadeAuditavel`. Nenhum service preenche esses campos, e o que o cliente enviar neles é ignorado.

- **Entidades auditadas:** pacientes, profissionais, planos, pagamentos, aulas, lista de espera, bloqueios de agenda,
  NFS-e emitidas e os dados clínicos (anamnese, avaliação fisioterapêutica, análise postural, plano de tratamento,
  sessão, evolução de sessão e reavaliação).
- **Fora da auditoria:** registros técnicos (`users`, preferências, refresh e reset tokens), a foto da análise
  postural (a análise em si é auditada) e os contatos de cobrança, que já guardam `registradoPor`/`registradoEm` e
  nunca são alterados.
- **Autor:** o e-mail do usuário autenticado. Processos sem usuário — os schedulers de cobrança — gravam `system`.
- `dataCriacao`/`criadoPor` não mudam depois da criação. `dataAtualizacao`/`alteradoPor` ficam nulos até a primeira
  alteração e passam a refletir a última.
- Só alterações que mudam a linha contam: um `PUT` sem nenhum campo diferente não atualiza `dataAtualizacao`. A
  substituição da foto da análise postural conta como alteração da análise.
- Registros anteriores à `V43` ficam com autor nulo, e os das tabelas que não tinham timestamp também ficam sem
  `dataCriacao`: não há como saber quem os criou, nem quando.
- É só autoria: o que mudou em cada alteração não é guardado. Um histórico de valores (Hibernate Envers) seria um
  passo separado.

## Scheduler (processos automáticos)

| Cron (default) | Horário | Ação | Propriedade |
|---|---|---|---|
| `0 0 6 * * *` | 06:00 todo dia | Marca como `VENCIDO` pagamentos `PENDENTE` com `dataVencimento` passada | `app.cobranca.cron-vencidos` (env `APP_COBRANCA_CRON_VENCIDOS`) |
| `0 0 7 * * *` | 07:00 todo dia | Gera cobranças futuras para planos ativos quando faltam ≤ 7 dias para o fim do período atual | `app.cobranca.cron-cobrancas-futuras` (env `APP_COBRANCA_CRON_COBRANCAS_FUTURAS`) |

O vencimento das cobranças geradas pelo scheduler é definido por `app.cobranca.vencimento-dias` (env
`APP_COBRANCA_VENCIMENTO_DIAS`, default `10`), somado ao início do período. O tamanho padrão de página nas
listagens paginadas é controlado por `spring.data.web.pageable.default-page-size`, alimentado pela env
`APP_PAGINACAO_TAMANHO_PADRAO` (default `10`).

As alterações feitas pelos schedulers são gravadas com autor `system` ([auditoria](#auditoria-de-criação-e-alteração)).

## Ver também

- [`api.md`](api.md) — contrato REST e comportamento por endpoint
- [`arquitetura.md`](arquitetura.md#tratamento-de-erros) — mapeamento de exceções para status HTTP
- [`context.md`](context.md) — modelo de dados e decisões de design
