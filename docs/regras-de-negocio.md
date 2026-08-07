# Regras de Negócio

## Pacientes
- Um paciente pode ter **apenas um plano ativo** por vez
- Pacientes **inativos** não recebem novas cobranças nem têm aulas geradas

## Profissionais
- Tipos de contrato: `CLT`, `PJ`, `AUTONOMO`
- O `percentualPagamentoAula` representa o percentual recebido por aula ministrada
- Profissionais inativos são mantidos no banco (soft delete)
- O relatório de pagamento considera aulas realizadas vinculadas ao profissional no período informado e ignora aulas de pacientes inativos
- O relatório de pagamento usa uma consulta consolidada com `JOIN` e `GROUP BY` para buscar os dados das aulas e a quantidade de aulas do pagamento sem round-trips adicionais
- O valor devido por aula é calculado por `valor do pagamento / quantidade de aulas do pagamento * percentualPagamentoAula / 100`
- O relatório de pagamento é limitado a períodos de até 366 dias e até 5.000 aulas para evitar exportações excessivas em memória
- O relatório também é exportável em PDF (OpenPDF) e Excel/XLSX (Apache POI), reaproveitando o mesmo cálculo do endpoint JSON

## Planos de Pagamento
- Tipos: `MENSAL`, `TRIMESTRAL`, `ANUAL`
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
- Não pode haver dois pagamentos para o mesmo plano no mesmo período
- Ao confirmar (`PAGO`), as aulas do período são geradas automaticamente
- A confirmação de pagamento recebe `dataPagamento` no corpo da requisição; se omitida, usa a data atual

## NFSE
- O relatório de NFSE considera apenas pagamentos `PAGO` com `dataPagamento` dentro da competência `MM/AAAA`
- Pacientes inativos são ignorados
- `Nome`, `CPF/CNPJ`, `ValorPago` e `DataPagamento` vêm do paciente e do pagamento confirmado
- `DescricaoServico` é gerada automaticamente como `Aulas de Pilates - Competência MM/AAAA`
- `NotaAnteriorEmitida` é baseada nas NFSEs emitidas persistidas: `true` quando há nota registrada para o paciente em competência anterior à consultada
- Registros sem nome, CPF/CNPJ, valor positivo ou data de pagamento retornam erro de regra de negócio (`422`)
- As NFSEs emitidas são persistidas por `(paciente, competência)` via `POST /api/nfse-emitidas`; o registro é idempotente (atualiza a nota existente da competência) e exige paciente ativo (`422` se inativo)

## Geração de Aulas
- Aulas geradas com base nos dias da semana do plano e no período do pagamento
- Sem duplicatas: se a aula do paciente naquela data já existir, ela é ignorada
- Requer paciente ativo e pagamento confirmado
- Consultas de aulas por ID, paciente, pagamento, período e relatório retornam apenas aulas associadas a pacientes ativos
- Ao marcar uma aula como realizada, `profissionalId` pode ser informado para alimentar o relatório de pagamento do profissional
- A listagem por período (`GET /aulas?inicio=&fim=`) exige período fechado de no máximo 92 dias e não é paginada

## Avaliações Fisioterapêuticas
- Um paciente pode ter múltiplas avaliações fisioterapêuticas para manter histórico clínico
- Criar avaliação para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `dataAvaliacao`, `queixaFuncional`, `escalaDor` e `diagnosticoFisioterapeutico`
- `escalaDor` aceita valores inteiros de 0 a 10
- Consultas e atualizações não filtram por paciente ativo: o histórico de ex-aluno continua acessível
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados

## Planos de Tratamento
- Um paciente pode ter múltiplos planos de tratamento para manter histórico clínico
- Criar plano para paciente inexistente retorna `404`; para paciente inativo retorna `422`
- Campos obrigatórios: `pacienteId`, `dataInicio` e `objetivosTratamento`
- `dataFimPrevista`, quando informada, não pode ser anterior a `dataInicio`
- `numeroSessoesPrevistas` aceita apenas valores positivos quando informado
- Consultas e atualizações filtram apenas planos ativos (soft delete do plano), independentemente de o paciente estar ativo
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados; `objetivosTratamento` não aceita strings em branco quando enviado
- Exclusão é lógica: `DELETE /planos-tratamento/{id}` marca o plano como inativo e preserva o histórico no banco

## Sessões de Pilates/Fisioterapia
- O `status` padrão é `AGENDADA`; mudanças de status usam `PATCH /sessoes/{id}/realizar` ou `PATCH /sessoes/{id}/cancelar`
- Transições permitidas: apenas `AGENDADA -> REALIZADA` e `AGENDADA -> CANCELADA`
- `PUT /sessoes/{id}` faz atualização parcial dos dados da sessão, mas não altera `status`
- A evolução clínica estruturada deve ser registrada em `/evolucoes-sessao`
- O campo legado `sessoes_pilates.evolucao` não faz parte do contrato REST de sessões
- Excluir uma sessão remove também a evolução vinculada, quando existir
- A listagem por período (`GET /sessoes?inicio=&fim=`) exige período fechado de no máximo 92 dias, não é paginada e não filtra por paciente ativo

## Evoluções de Sessão
- Cada sessão possui no máximo uma evolução clínica (regra de unicidade por `sessao_id`)
- Criar evolução para sessão inexistente retorna `404`; para sessão de paciente inativo retorna `422`
- Tentar criar segunda evolução para a mesma sessão retorna `409`
- Campos obrigatórios: `sessaoId` e `dataHoraRegistro`
- `dorAntes` e `dorDepois`, quando informados, aceitam apenas valores inteiros de 0 a 10
- Consultas e atualizações não filtram por paciente ativo
- Atualização parcial: apenas campos não-nulos do DTO de update são aplicados

## Histórico clínico de paciente inativo
- `ativo = false` marca um **ex-aluno**, não um registro apagado: todo o prontuário dele continua consultável pela API
- As consultas por paciente (`/sessoes`, `/anamneses`, `/avaliacoes-fisioterapeuticas`, `/planos-tratamento`, `/reavaliacoes`, `/evolucoes-sessao`, `/api/nfse-emitidas`) validam somente se o paciente existe; `404 Paciente não encontrado` passa a significar que o id realmente não existe
- Quando o paciente existe mas o registro não, a mensagem é específica do recurso (ex.: `Anamnese não encontrada para o paciente: {id}`)
- **Criar** registro clínico novo continua exigindo paciente ativo, com resposta uniforme nos oito endpoints de criação: `404` quando o paciente não existe e `422` quando existe mas está inativo (regra centralizada em `util/PacienteGuard`); **atualizar** registro existente de paciente inativo é permitido
- Consultas financeiras/operacionais (aulas, dashboard, relatório de pagamento e relatório de NFSE) continuam ignorando pacientes inativos

## Recuperação de senha (esqueci minha senha)
- `POST /auth/forgot-password` recebe `email` e sempre retorna `200` com resposta genérica, mesmo se o e-mail não existir ou pertencer a um usuário inativo, para evitar enumeração de usuários
- Rate limiting reaproveita o `LoginAttemptService` (5 solicitações por e-mail em 15 minutos); acima do limite retorna `429`. Chaves cujas tentativas já saíram da janela são removidas do mapa em memória tanto ao consultar o limite quanto periodicamente por `LoginAttemptCleanupScheduler` (a cada 15 min), evitando crescimento ilimitado via solicitações não autenticadas
- Ao gerar um novo token, qualquer token anterior ainda válido do mesmo usuário é invalidado — apenas o token da solicitação mais recente funciona
- Token de redefinição: gerado aleatoriamente (32 bytes, Base64 URL-safe), salvo em `password_reset_tokens` **apenas como hash SHA-256** (nunca em texto puro), com expiração configurável (`app.email.reset-password-token-ttl-minutos`, default 30 min — a mesma propriedade usada no texto do e-mail) e uso único
- O e-mail de redefinição é montado a partir do template Thymeleaf `password-reset.html` e enviado de forma assíncrona (`@Async`) via `EmailSender`, sem bloquear a resposta do `forgot-password`
- `POST /auth/reset-password` recebe `token`, `novaSenha` (mín. 8 caracteres) e `confirmacaoNovaSenha`; token inexistente, expirado ou já utilizado, assim como confirmação divergente, retornam `422` com mensagem genérica. A busca do token usa lock pessimista (`@Lock(PESSIMISTIC_WRITE)`) para impedir que duas requisições concorrentes redimam o mesmo token de uso único
- Ao redefinir com sucesso: a senha é armazenada com `BCryptPasswordEncoder` e o `token_version` do usuário é incrementado (invalidando JWTs emitidos antes da redefinição) via `UserService.aplicarNovaSenha`, reaproveitado também por `PUT /users/me/senha` e pelo CRUD administrativo; o token é marcado como usado
- Troca de provedor de e-mail (SMTP → SES, por exemplo) exige apenas uma nova implementação de `EmailSender` e ajuste em `EmailConfig`/`app.email.provider`, sem alterar `PasswordResetService`

## Scheduler (processos automáticos)
| Horário | Ação | Configuração |
|---|---|---|
| 06:00 todo dia (default) | Marca como `VENCIDO` pagamentos `PENDENTE` com data de vencimento passada | `app.cobranca.cron-vencidos` (env `APP_COBRANCA_CRON_VENCIDOS`) |
| 07:00 todo dia (default) | Gera cobranças futuras para planos ativos a partir de 7 dias antes do fim do período | `app.cobranca.cron-cobrancas-futuras` (env `APP_COBRANCA_CRON_COBRANCAS_FUTURAS`) |

O vencimento das cobranças geradas pelo scheduler é definido por `app.cobranca.vencimento-dias` (env `APP_COBRANCA_VENCIMENTO_DIAS`, default `10`), somado ao início do período. O tamanho padrão de página nas listagens paginadas é controlado por `spring.data.web.pageable.default-page-size`, alimentado pela env `APP_PAGINACAO_TAMANHO_PADRAO` (default `10`).


## Ver também

- [`api.md`](api.md) — contrato REST e comportamento por endpoint
- [`arquitetura.md`](arquitetura.md#tratamento-de-erros) — mapeamento de exceções para status HTTP
- [`context.md`](context.md) — modelo de dados e decisões de design
