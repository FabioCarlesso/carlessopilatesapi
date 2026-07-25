#!/usr/bin/env python3
"""Importa evoluções (prontuários) do seufisio.com.br para a API carlessopilatesapi.

Cada prontuário preenchido no seufisio vira, na API local, uma `SessaoPilates`
REALIZADA + a `EvolucaoSessao` correspondente (relação 1:1).

Pré-requisitos:
- API alvo rodando em LOCAL_API_URL (local ou produção).
- Pacientes já importados (`import_seufisio.py`) — a evolução precisa do paciente.
- Token JWT válido do seufisio (headers capturados no DevTools).

Uso:
    # Recomendado: criar scripts/.env (ignorado pelo git) e carregar com
    #   set -a; source scripts/.env; set +a
    export SEUFISIO_TOKEN="eyJ..."           # JWT capturado no DevTools
    export SEUFISIO_CLINICA_ID="..."         # header `setfisio` (DevTools)
    export SEUFISIO_VERSION_APP="..."        # header `x-version-app` (DevTools)
    export LOCAL_API_URL="http://localhost:8080"
    export LOCAL_EMAIL="..."
    export LOCAL_PASSWORD="..."
    python3 scripts/import_evolucoes_seufisio.py [--dry-run] [--desde 2026-01-01]

Fonte no seufisio: `GET /api/cliente/{id}/prontuarios?rowsPerPage=N&page=P`,
paginado (`last_page`). Campos usados de cada linha:
    data_atendimento ("YYYY-MM-DD"), hora_atendimento ("HH:MM:SS"),
    prontuario (HTML), prontuario_preenchido_em ("YYYY-MM-DD HH:MM:SS" ou null).

Idempotência: a chave é `paciente + data + horário`. Antes de criar, o script
carrega as sessões já existentes do paciente (`GET /sessoes/paciente/{id}`) e
casa cada atendimento com uma delas — quantos atendimentos compartilharem a
chave, tantas sessões são consumidas; o excedente vira sessão nova. Para a
sessão já existente o script confere se ela tem evolução
(`GET /evolucoes-sessao/sessao/{id}`) e cria só a que faltar, de modo que uma
execução interrompida no meio se conserta na próxima. Re-rodar não duplica — é
o modo de operação esperado enquanto o seufisio seguir em uso (carga delta
recorrente).

Pacientes inativos: `POST /sessoes` e `GET /sessoes/paciente/{id}` só aceitam
pacientes ativos, então o script reativa temporariamente e reinativa ao final
(inclusive em caso de erro no meio).

Segurança:
- Tokens lidos apenas de variáveis de ambiente; nunca commitar credenciais.
- Logs e dry-run mascaram PII (nome reduzido, CPF ofuscado, texto clínico nunca
  é impresso — apenas o seu tamanho).
"""

import argparse
import os
import re
import sys
import time
import unicodedata
from collections import Counter, defaultdict
from datetime import date
from html import unescape
from html.parser import HTMLParser

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from import_seufisio import (  # noqa: E402  (precisa do sys.path acima)
    RATE_LIMIT_SECONDS,
    SEUFISIO_BASE,
    fetch_pacientes_locais,
    http_json,
    list_seufisio,
    login_local,
    mask_cpf,
    only_digits,
    print_relatorio,
    seufisio_headers,
    short_name,
)

DESCRIPTION = "Importa evoluções (prontuários) do seufisio.com.br para a API carlessopilatesapi."
PRONTUARIOS_PAGE_SIZE = 200
TIPO_SESSAO_PADRAO = "PILATES"
TIPOS_SESSAO_VALIDOS = ("PILATES", "FISIOTERAPIA")  # enum TipoSessao da API
TAGS_QUEBRA_LINHA = {
    "br", "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6",
}


class _ExtratorTexto(HTMLParser):
    """Converte o HTML do prontuário em texto puro preservando as quebras."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.partes = []

    def handle_data(self, data):
        self.partes.append(data)

    def handle_starttag(self, tag, attrs):
        if tag == "br":
            self.partes.append("\n")

    def handle_endtag(self, tag):
        if tag in TAGS_QUEBRA_LINHA:
            self.partes.append("\n")


def html_para_texto(html):
    """Texto puro do prontuário; None quando não sobra conteúdo."""
    if not html:
        return None
    parser = _ExtratorTexto()
    parser.feed(html)
    parser.close()
    texto = unescape("".join(parser.partes)).replace("\xa0", " ")
    linhas = [linha.strip() for linha in texto.splitlines()]
    texto = re.sub(r"\n{3,}", "\n\n", "\n".join(linhas)).strip()
    return texto or None


def validar_tipo_sessao(tipo):
    """Mensagem de erro quando o tipo não existe no enum da API, ou None."""
    if tipo in TIPOS_SESSAO_VALIDOS:
        return None
    return (f"SEUFISIO_TIPO_SESSAO inválido: {tipo!r} — "
            f"use um de {', '.join(TIPOS_SESSAO_VALIDOS)}")


def normalizar_nome(nome):
    """Nome sem acentos, em minúsculas e com espaços colapsados."""
    if not nome:
        return None
    sem_acento = unicodedata.normalize("NFKD", nome)
    sem_acento = "".join(c for c in sem_acento if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", sem_acento).strip().lower() or None


def chave_sessao(data, horario):
    """Chave de idempotência com precisão de minuto.

    A API serializa `LocalTime` sem os segundos quando eles são zero
    ("18:00"), enquanto o seufisio sempre envia "18:00:00" — comparar os dois
    formatos crus geraria sessões duplicadas.
    """
    if not data:
        return None
    hhmm = (horario or "00:00")[:5]
    return f"{data}T{hhmm}"


def indexar_para_match(pacientes):
    """(por_cpf, por_nome) para resolver o cliente do seufisio no paciente local."""
    por_cpf = {}
    por_nome = defaultdict(list)
    for p in pacientes:
        cpf = only_digits(p.get("cpf"))
        if cpf:
            por_cpf[cpf] = p
        nome = normalizar_nome(p.get("nome"))
        if nome:
            por_nome[nome].append(p)
    return por_cpf, por_nome


def resolver_paciente(cliente, por_cpf, por_nome):
    """(paciente_local, motivo_skip). Casa por CPF; cai para nome exato único."""
    cpf = only_digits(cliente.get("cpf"))
    if cpf and cpf in por_cpf:
        return por_cpf[cpf], None
    candidatos = por_nome.get(normalizar_nome(cliente.get("nome")), [])
    if len(candidatos) == 1:
        return candidatos[0], None
    if len(candidatos) > 1:
        return None, "nome_ambiguo"
    return None, "paciente_nao_encontrado"


def fetch_prontuarios(headers, cliente_id):
    """(linhas, erro) — todas as páginas de GET /cliente/{id}/prontuarios."""
    linhas = []
    page = 1
    while True:
        url = (f"{SEUFISIO_BASE}/cliente/{cliente_id}/prontuarios"
               f"?rowsPerPage={PRONTUARIOS_PAGE_SIZE}&page={page}")
        status, body = http_json("GET", url, headers=headers)
        if status != 200 or not isinstance(body, dict):
            return None, f"GET cliente/{cliente_id}/prontuarios p{page} -> {status}"
        linhas.extend(body.get("data") or [])
        if page >= (body.get("last_page") or 1):
            return linhas, None
        page += 1
        time.sleep(RATE_LIMIT_SECONDS)


def map_to_evolucao(linha, desde=None):
    """(dados, motivo_skip) a partir de uma linha de prontuário do seufisio."""
    texto = html_para_texto(linha.get("prontuario"))
    if not texto:
        return None, "sem_prontuario"

    data = (linha.get("data_atendimento") or "").strip()
    if not data:
        return None, "sem_data_atendimento"
    if desde and data < desde:
        return None, "anterior_ao_corte"

    horario = (linha.get("hora_atendimento") or "").strip() or None
    preenchido_em = (linha.get("prontuario_preenchido_em") or "").strip()
    registro = preenchido_em or f"{data} {horario or '00:00:00'}"

    return {
        "atendimento_id": linha.get("atendimento_id"),
        "data": data,
        "horario": horario,
        "dataHoraRegistro": registro.replace(" ", "T"),
        "texto": texto,
    }, None


def sessoes_existentes(base_url, token, paciente_id):
    """(por_chave, erro) — sessões já cadastradas agrupadas por `data+horário`.

    Cada chave aponta para a *lista* de sessões com aquela data/horário, não
    para uma só: sem `hora_atendimento` todos os atendimentos do dia colapsam
    na mesma chave, e tratá-la como única faria o script pular os demais como
    se já tivessem sido importados.

    Sessões CANCELADA ficam de fora — não recebem evolução e, contadas aqui,
    bloqueariam a importação de um atendimento legítimo no mesmo horário.
    """
    status, body = http_json(
        "GET", f"{base_url}/sessoes/paciente/{paciente_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    if status != 200 or not isinstance(body, list):
        return None, f"GET /sessoes/paciente/{paciente_id} -> {status}"
    por_chave = defaultdict(list)
    for s in body:
        if s.get("status") == "CANCELADA":
            continue
        por_chave[chave_sessao(s.get("data"), s.get("horario"))].append(s)
    return dict(por_chave), None


def evolucao_existe(base_url, token, sessao_id):
    """(existe, erro) — se a sessão já tem evolução registrada (relação 1:1)."""
    status, _ = http_json(
        "GET", f"{base_url}/evolucoes-sessao/sessao/{sessao_id}",
        headers={"Authorization": f"Bearer {token}"},
    )
    if status == 200:
        return True, None
    if status == 404:
        return False, None
    return None, f"GET /evolucoes-sessao/sessao/{sessao_id} -> {status}"


def patch_paciente(base_url, token, paciente_id, acao):
    return http_json(
        "PATCH", f"{base_url}/pacientes/{paciente_id}/{acao}",
        headers={"Authorization": f"Bearer {token}"},
    )


def post_sessao(base_url, token, paciente_id, tipo, evolucao):
    return http_json(
        "POST", f"{base_url}/sessoes",
        headers={"Authorization": f"Bearer {token}"},
        body={
            "pacienteId": paciente_id,
            "tipo": tipo,
            "data": evolucao["data"],
            "horario": evolucao["horario"],
        },
    )


def patch_realizar(base_url, token, sessao_id):
    return http_json(
        "PATCH", f"{base_url}/sessoes/{sessao_id}/realizar",
        headers={"Authorization": f"Bearer {token}"},
    )


def post_evolucao(base_url, token, sessao_id, evolucao):
    return http_json(
        "POST", f"{base_url}/evolucoes-sessao",
        headers={"Authorization": f"Bearer {token}"},
        body={
            "sessaoId": sessao_id,
            "dataHoraRegistro": evolucao["dataHoraRegistro"],
            "observacoesFisioterapeuta": evolucao["texto"],
        },
    )


def importar_evolucao(base_url, token, paciente_id, tipo, evolucao, fails, rotulo):
    """Cria sessão REALIZADA + evolução. Retorna True se ambas foram criadas."""
    status, body = post_sessao(base_url, token, paciente_id, tipo, evolucao)
    if status != 201 or not isinstance(body, dict) or body.get("id") is None:
        fails[f"post_sessao_{status}"] += 1
        print(f"      [fail {rotulo} data={evolucao['data']}] POST /sessoes -> {status}")
        return False

    sessao_id = body["id"]
    status, _ = patch_realizar(base_url, token, sessao_id)
    if status != 200:
        # A sessão fica AGENDADA; a próxima execução a reconhece pela chave,
        # não duplica e completa o que faltou (ver `completar_evolucao`).
        fails[f"patch_realizar_{status}"] += 1
        print(f"      [fail {rotulo} sessao_id={sessao_id}] PATCH realizar -> {status}")
        return False

    status, _ = post_evolucao(base_url, token, sessao_id, evolucao)
    if status != 201:
        fails[f"post_evolucao_{status}"] += 1
        print(f"      [orfa {rotulo} sessao_id={sessao_id} data={evolucao['data']}] "
              f"POST /evolucoes-sessao -> {status} (sessão criada sem evolução)")
        return False
    return True


def completar_evolucao(base_url, token, sessao, evolucao, fails, rotulo):
    """Cria a evolução que falta numa sessão que já existe.

    Cobre a sessão órfã de uma execução interrompida (sessão criada, evolução
    não) e a sessão criada direto no sistema novo: em ambos os casos a chave
    bate, e sem este caminho a evolução nunca seria importada.
    """
    sessao_id = sessao["id"]
    if sessao.get("status") == "AGENDADA":
        # `PATCH realizar` só aceita AGENDADA; numa sessão já REALIZADA a
        # transição volta 400 e abortaria a criação da evolução.
        status, _ = patch_realizar(base_url, token, sessao_id)
        if status != 200:
            fails[f"patch_realizar_{status}"] += 1
            print(f"      [fail {rotulo} sessao_id={sessao_id}] PATCH realizar -> {status}")
            return False

    status, _ = post_evolucao(base_url, token, sessao_id, evolucao)
    if status != 201:
        fails[f"post_evolucao_{status}"] += 1
        print(f"      [fail {rotulo} sessao_id={sessao_id} data={evolucao['data']}] "
              f"POST /evolucoes-sessao -> {status} (sessão existente sem evolução)")
        return False
    return True


def processar_cliente(cfg, paciente, linhas, skips, fails):
    """Importa as evoluções de um cliente. Retorna quantas foram criadas."""
    base_url, token = cfg["base_url"], cfg["token"]
    paciente_id = paciente["id"]
    rotulo = f"paciente_id={paciente_id} nome={short_name(paciente.get('nome'))}"

    pendentes = []
    for linha in linhas:
        evolucao, motivo = map_to_evolucao(linha, cfg["desde"])
        if motivo:
            skips[motivo] += 1
            continue
        pendentes.append(evolucao)
    if not pendentes:
        return 0

    inativo = not paciente.get("ativo", True)
    reativado = False
    if inativo and not cfg["dry_run"]:
        status, _ = patch_paciente(base_url, token, paciente_id, "ativar")
        if status != 204:
            fails[f"patch_ativar_{status}"] += 1
            print(f"      [fail {rotulo}] PATCH ativar -> {status}")
            return 0
        reativado = True

    try:
        if inativo and cfg["dry_run"]:
            # `GET /sessoes/paciente/{id}` responde 404 para inativos; na carga
            # real o paciente é reativado antes, aqui não há o que consultar.
            existentes, erro = {}, None
            print(f"      [dry-run {rotulo}] paciente inativo: seria reativado "
                  f"temporariamente; sessões existentes não verificadas")
        else:
            existentes, erro = sessoes_existentes(base_url, token, paciente_id)
        if erro:
            fails["sessoes_existentes"] += 1
            print(f"      [fail {rotulo}] {erro}")
            return 0

        criadas = 0
        # Quantos atendimentos desta execução já consumiram cada chave: o
        # n-ésimo atendimento casa com a n-ésima sessão existente e, quando as
        # sessões acabam, vira uma nova — é o que faz dois atendimentos no
        # mesmo dia sem horário serem importados sem duplicar na reexecução.
        consumidas = Counter()
        for evolucao in pendentes:
            chave = chave_sessao(evolucao["data"], evolucao["horario"])
            candidatas = existentes.get(chave) or []
            indice = consumidas[chave]
            consumidas[chave] += 1

            if indice < len(candidatas):
                sessao = candidatas[indice]
                tem_evolucao, erro = evolucao_existe(base_url, token, sessao["id"])
                if erro:
                    fails["evolucao_existe"] += 1
                    print(f"      [fail {rotulo}] {erro}")
                    continue
                if tem_evolucao:
                    skips["sessao_ja_existe"] += 1
                    continue
                if cfg["dry_run"]:
                    print(f"      [dry-run {rotulo}] sessao_id={sessao['id']} "
                          f"{evolucao['data']} sem evolução: seria completada "
                          f"(texto={len(evolucao['texto'])} chars)")
                    criadas += 1
                    continue
                if completar_evolucao(base_url, token, sessao, evolucao, fails, rotulo):
                    criadas += 1
                time.sleep(RATE_LIMIT_SECONDS)
                continue

            if cfg["dry_run"]:
                print(f"      [dry-run {rotulo}] {evolucao['data']} "
                      f"{evolucao['horario'] or '--:--'} texto={len(evolucao['texto'])} chars")
                criadas += 1
                continue

            if importar_evolucao(base_url, token, paciente_id, cfg["tipo"],
                                 evolucao, fails, rotulo):
                criadas += 1
            time.sleep(RATE_LIMIT_SECONDS)
        return criadas
    finally:
        if reativado:
            status, _ = patch_paciente(base_url, token, paciente_id, "inativar")
            if status != 204:
                print(f"      [warn {rotulo}] PATCH inativar -> {status} "
                      f"— paciente ficou ATIVO, reverter manualmente")


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=DESCRIPTION)
    parser.add_argument("--dry-run", action="store_true",
                        help="Não grava nada: lista o que seria importado, com PII mascarada.")
    parser.add_argument("--desde", metavar="AAAA-MM-DD",
                        help="Importa apenas atendimentos a partir desta data (carga delta).")
    parser.add_argument("--cliente-id", type=int, action="append", dest="cliente_ids",
                        help="Restringe a estes ids de cliente do seufisio (repetível).")
    parser.add_argument("--limite-clientes", type=int,
                        help="Processa no máximo N clientes (útil para validar a carga).")
    args = parser.parse_args(argv)
    if args.desde:
        try:
            date.fromisoformat(args.desde)
        except ValueError:
            parser.error("--desde precisa estar no formato AAAA-MM-DD")
    return args


def main():
    args = parse_args()

    seufisio_token = os.environ.get("SEUFISIO_TOKEN")
    clinica_id = os.environ.get("SEUFISIO_CLINICA_ID")
    version_app = os.environ.get("SEUFISIO_VERSION_APP")
    base_url = os.environ.get("LOCAL_API_URL", "http://localhost:8080").rstrip("/")
    local_email = os.environ.get("LOCAL_EMAIL")
    local_password = os.environ.get("LOCAL_PASSWORD")
    tipo = os.environ.get("SEUFISIO_TIPO_SESSAO", TIPO_SESSAO_PADRAO)

    if not seufisio_token:
        sys.exit("Variável SEUFISIO_TOKEN obrigatória")
    if not clinica_id or not version_app:
        sys.exit("SEUFISIO_CLINICA_ID e SEUFISIO_VERSION_APP obrigatórias "
                 "(headers `setfisio` e `x-version-app` capturados no DevTools)")
    if not local_email or not local_password:
        sys.exit("LOCAL_EMAIL e LOCAL_PASSWORD obrigatórias — mesmo em --dry-run, "
                 "para comparar com as sessões já cadastradas")
    erro_tipo = validar_tipo_sessao(tipo)
    if erro_tipo:
        # Sem esta checagem o valor inválido só falharia no primeiro POST
        # /sessoes — depois de toda a listagem e paginação, e em 100% das linhas.
        sys.exit(erro_tipo)

    sf_headers = seufisio_headers(seufisio_token, clinica_id, version_app)

    print(f"[1/3] Autenticando em {base_url} e carregando pacientes locais ...")
    token = login_local(base_url, local_email, local_password)
    pacientes = fetch_pacientes_locais(base_url, token)
    por_cpf, por_nome = indexar_para_match(pacientes)
    print(f"      {len(pacientes)} pacientes locais ({len(por_cpf)} com CPF)")

    print(f"[2/3] Listando clientes em {SEUFISIO_BASE}/cliente ...")
    clientes = list_seufisio(sf_headers)
    if args.cliente_ids:
        alvo = set(args.cliente_ids)
        clientes = [c for c in clientes if c.get("id") in alvo]
    if args.limite_clientes:
        clientes = clientes[:args.limite_clientes]
    print(f"      {len(clientes)} clientes a processar")

    cfg = {
        "base_url": base_url,
        "token": token,
        "tipo": tipo,
        "desde": args.desde,
        "dry_run": args.dry_run,
    }

    print(f"[3/3] {'Simulando' if args.dry_run else 'Importando'} evoluções "
          f"(tipo de sessão: {tipo}) ...")
    ok = 0
    total_linhas = 0
    skips = Counter()
    fails = Counter()
    for cliente in clientes:
        cliente_id = cliente.get("id")
        if cliente_id is None:
            fails["listagem_sem_id"] += 1
            continue

        paciente, motivo = resolver_paciente(cliente, por_cpf, por_nome)
        if motivo:
            skips[motivo] += 1
            print(f"      [skip seufisio_id={cliente_id} nome={short_name(cliente.get('nome'))} "
                  f"cpf={mask_cpf(only_digits(cliente.get('cpf')))}] {motivo}")
            continue

        linhas, erro = fetch_prontuarios(sf_headers, cliente_id)
        if erro:
            fails["prontuarios_indisponivel"] += 1
            print(f"      [fail seufisio_id={cliente_id}] {erro}")
            continue

        total_linhas += len(linhas)
        ok += processar_cliente(cfg, paciente, linhas, skips, fails)
        time.sleep(RATE_LIMIT_SECONDS)

    print(f"\nclientes processados: {len(clientes)}")
    print_relatorio(total_linhas, ok, skips, fails, rotulo_ok="evoluções criadas")
    if sum(fails.values()):
        sys.exit(1)


if __name__ == "__main__":
    main()
