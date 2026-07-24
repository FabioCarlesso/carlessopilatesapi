#!/usr/bin/env python3
"""Importa pacientes do seufisio.com.br para a API local carlessopilatesapi.

Pré-requisitos:
- API local rodando em LOCAL_API_URL (default http://localhost:8080).
- Token JWT válido do seufisio (header `authorization` capturado no DevTools).

Uso:
    # Recomendado: criar scripts/.env (ignorado pelo git) e carregar com
    #   set -a; source scripts/.env; set +a
    # para evitar deixar token/senha no histórico do shell.
    export SEUFISIO_TOKEN="eyJ..."           # JWT capturado no DevTools
    export SEUFISIO_CLINICA_ID="..."         # header `setfisio` (DevTools)
    export SEUFISIO_VERSION_APP="..."        # header `x-version-app` (DevTools)
    export LOCAL_API_URL="http://localhost:8080"
    export LOCAL_EMAIL="..."
    export LOCAL_PASSWORD="..."
    python3 scripts/import_seufisio.py [--dry-run]

--dry-run: imprime payloads mascarados (CPF/e-mail ofuscados) sem POSTar. Quando
LOCAL_EMAIL/LOCAL_PASSWORD estão disponíveis, o dry-run também carrega os
pacientes já cadastrados e mostra o delta real que seria importado.

Idempotência: antes de criar, busca todos os pacientes já cadastrados na API
local — ativos **e** inativos — e pula quem já existe por CPF ou por e-mail
(ambos têm unicidade parcial no banco). Re-rodar não cria duplicatas.

Segurança:
- Tokens lidos apenas de variáveis de ambiente; nunca commitar credenciais.
- Logs e dry-run mascaram PII (nome reduzido, CPF/e-mail ofuscados).
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime

DESCRIPTION = "Importa pacientes do seufisio.com.br para a API local."
SEUFISIO_BASE = "https://api.seufisio.com.br/api"
SITUACAO_ATIVO = 2
RATE_LIMIT_SECONDS = 0.15
LOCAL_PAGE_SIZE = 200
BROWSER_UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
)
DATE_FORMATS = ("%Y-%m-%d", "%d/%m/%Y", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S")


def http_json(method, url, headers=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    req.add_header("User-Agent", BROWSER_UA)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        return e.code, raw


def only_digits(value):
    if not value:
        return None
    return re.sub(r"\D", "", value) or None


def normalize_email(value):
    if not value:
        return None
    return value.strip().lower() or None


def mask_cpf(cpf):
    if not cpf:
        return None
    return f"***.***.***-{cpf[-2:]}" if len(cpf) >= 2 else "***"


def mask_email(email):
    if not email:
        return None
    if "@" not in email:
        return "***"
    local, domain = email.split("@", 1)
    return f"{local[:1]}***@{domain}"


def short_name(nome):
    if not nome:
        return "***"
    parts = nome.split()
    if len(parts) == 1:
        return parts[0][:1] + "***"
    return f"{parts[0]} {parts[-1][:1]}***"


def parse_data_nascimento(value):
    if value is None:
        return None, None
    if not isinstance(value, str):
        return None, f"data_nascimento tipo inesperado: {type(value).__name__}"
    txt = value.strip()
    if not txt:
        return None, None
    for fmt in DATE_FORMATS:
        try:
            return datetime.strptime(txt[:len(fmt) + 4], fmt).date().isoformat(), None
        except ValueError:
            continue
    return None, f"data_nascimento em formato não reconhecido: {txt!r}"


def seufisio_headers(token, clinica_id, version_app):
    return {
        "Authorization": f"Bearer {token}",
        "acesso": "web-mobile",
        "x-version-app": version_app,
        "setfisio": clinica_id,
        "Origin": "https://app.seufisio.com.br",
        "Referer": "https://app.seufisio.com.br/",
        "X-Requested-With": "XMLHttpRequest",
    }


def login_local(base_url, email, password):
    status, body = http_json(
        "POST", f"{base_url}/auth/login",
        body={"email": email, "password": password},
    )
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"Login local falhou ({status}): {body}")
    token = body.get("accessToken")
    if not token:
        raise SystemExit("Login local não retornou accessToken na resposta")
    return token


def list_seufisio(headers):
    status, body = http_json(
        "GET", f"{SEUFISIO_BASE}/cliente?per_page=500",
        headers=headers,
    )
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"Listagem seufisio falhou ({status}): {body}")
    return body.get("data") or []


def fetch_seufisio(headers, cid):
    status, body = http_json(
        "GET", f"{SEUFISIO_BASE}/cliente/{cid}",
        headers=headers,
    )
    if status != 200:
        return None, f"GET cliente/{cid} -> {status}"
    return body, None


def pagina_final(body, content):
    """Se esta é a última página, nos dois formatos de `Page` do Spring.

    O Spring Boot 3.4 serializa `Page` como `{content, page: {size, number,
    totalElements, totalPages}}` — **sem** o `last` do formato antigo. Lê-lo
    com default `True` encerraria o laço na primeira página e deduplicaria
    contra um set incompleto, que é exatamente a causa dos 409 da issue #76.
    """
    if isinstance(body.get("last"), bool):
        return body["last"]
    page = body.get("page")
    if isinstance(page, dict) and isinstance(page.get("totalPages"), int):
        return page.get("number", 0) >= page["totalPages"] - 1
    # Formato desconhecido: segue enquanto as páginas vierem cheias, em vez
    # de parar cedo e correr o risco de perder pacientes da deduplicação.
    return len(content) < LOCAL_PAGE_SIZE


def fetch_pacientes_locais(base_url, token):
    """Todos os pacientes da API local, em duas passadas: ativos e inativos.

    `GET /pacientes` sem `ativo` retorna apenas ativos, então os inativos
    precisam de uma segunda passada — sem isso o CPF de um paciente inativo
    fica fora do set de deduplicação e o POST volta 409 (issue #76).
    """
    pacientes = []
    for ativo in ("true", "false"):
        page = 0
        while True:
            url = f"{base_url}/pacientes?ativo={ativo}&page={page}&size={LOCAL_PAGE_SIZE}"
            status, body = http_json(
                "GET", url,
                headers={"Authorization": f"Bearer {token}"},
            )
            if status != 200 or not isinstance(body, dict):
                raise SystemExit(f"Falha ao listar pacientes locais ({status}): {body}")
            content = body.get("content") or []
            pacientes.extend(content)
            if not content or pagina_final(body, content):
                break
            page += 1
    return pacientes


def index_pacientes(pacientes):
    """Sets normalizados de CPFs e e-mails já cadastrados localmente.

    O e-mail é normalizado para minúsculas de propósito, ainda que o índice
    parcial da V23 (`ON pacientes (email) WHERE email IS NOT NULL`) não use
    `lower()`: o banco aceitaria `Ana@x.com` ao lado de `ana@x.com`, mas é a
    mesma pessoa e o script prefere pular a criar a duplicata.
    """
    cpfs = set()
    emails = set()
    for p in pacientes:
        cpf = only_digits(p.get("cpf"))
        if cpf:
            cpfs.add(cpf)
        email = normalize_email(p.get("email"))
        if email:
            emails.add(email)
    return cpfs, emails


def motivo_duplicado(payload, cpfs, emails):
    """Motivo pelo qual o paciente já existe localmente, ou None se é novo."""
    if payload.get("cpf") and payload["cpf"] in cpfs:
        return "cpf_ja_cadastrado"
    email = normalize_email(payload.get("email"))
    if email and email in emails:
        return "email_ja_cadastrado"
    return None


def map_to_paciente(c):
    nome = (c.get("nome") or "").strip() or None
    if not nome:
        return None, "sem nome"

    email = (c.get("email") or "").strip() or None
    cpf = only_digits(c.get("cpf"))
    telefone = (c.get("telefone") or "").strip() or None

    data_nascimento, data_err = parse_data_nascimento(c.get("data_nascimento"))
    if data_err:
        return None, data_err

    cep = only_digits(c.get("cep"))
    endereco_campos = {
        "logradouro": c.get("endereco"),
        "numero": c.get("endereco_numero"),
        "bairro": c.get("bairro"),
        "cidade": c.get("cidade"),
        "uf": c.get("uf"),
        "cep": cep,
    }
    endereco = endereco_campos if any(endereco_campos.values()) else None

    return {
        "nome": nome,
        "email": email,
        "cpf": cpf,
        "telefone": telefone,
        "dataNascimento": data_nascimento,
        "endereco": endereco,
    }, None


def masked_payload(payload):
    return {
        "nome": short_name(payload.get("nome")),
        "email": mask_email(payload.get("email")),
        "cpf": mask_cpf(payload.get("cpf")),
        "telefone": "***" if payload.get("telefone") else None,
        "dataNascimento": payload.get("dataNascimento"),
        "endereco": "(presente)" if payload.get("endereco") else None,
    }


def print_relatorio(total, ok, skips, fails, rotulo_ok="importados"):
    """Resumo final: total processado / criados / ignorados / falhas, por motivo."""
    print("\n=== Resumo ===")
    print(f"total processado : {total}")
    print(f"{rotulo_ok:<17}: {ok}")
    print(f"ignorados        : {sum(skips.values())}")
    for motivo, qtd in sorted(skips.items()):
        print(f"  - {motivo}: {qtd}")
    print(f"falhas           : {sum(fails.values())}")
    for motivo, qtd in sorted(fails.items()):
        print(f"  - {motivo}: {qtd}")


def post_paciente(base_url, token, payload):
    return http_json(
        "POST", f"{base_url}/pacientes",
        headers={"Authorization": f"Bearer {token}"},
        body=payload,
    )


def patch_inativar(base_url, token, paciente_id):
    return http_json(
        "PATCH", f"{base_url}/pacientes/{paciente_id}/inativar",
        headers={"Authorization": f"Bearer {token}"},
    )


def main():
    parser = argparse.ArgumentParser(description=DESCRIPTION)
    parser.add_argument("--dry-run", action="store_true",
                        help="Imprime payloads mascarados sem chamar a API local.")
    args = parser.parse_args()

    seufisio_token = os.environ.get("SEUFISIO_TOKEN")
    clinica_id = os.environ.get("SEUFISIO_CLINICA_ID")
    version_app = os.environ.get("SEUFISIO_VERSION_APP")
    base_url = os.environ.get("LOCAL_API_URL", "http://localhost:8080").rstrip("/")
    local_email = os.environ.get("LOCAL_EMAIL")
    local_password = os.environ.get("LOCAL_PASSWORD")

    if not seufisio_token:
        sys.exit("Variável SEUFISIO_TOKEN obrigatória")
    if not clinica_id or not version_app:
        sys.exit("SEUFISIO_CLINICA_ID e SEUFISIO_VERSION_APP obrigatórias "
                 "(headers `setfisio` e `x-version-app` capturados no DevTools)")
    if not args.dry_run and (not local_email or not local_password):
        sys.exit("LOCAL_EMAIL e LOCAL_PASSWORD obrigatórias (use --dry-run para testar mapeamento)")
    tem_credenciais_locais = bool(local_email and local_password)

    sf_headers = seufisio_headers(seufisio_token, clinica_id, version_app)

    print(f"[1/4] Listando clientes em {SEUFISIO_BASE}/cliente ...")
    clientes = list_seufisio(sf_headers)
    print(f"      {len(clientes)} clientes encontrados")

    total = len(clientes)
    skips = Counter()
    fails = Counter()

    print("[2/4] Buscando detalhes ...")
    detalhes = []
    for c in clientes:
        cid = c.get("id") if isinstance(c, dict) else None
        if cid is None:
            fails["listagem_sem_id"] += 1
            print("      [fail] item sem id na listagem")
            continue
        full, err = fetch_seufisio(sf_headers, cid)
        if err:
            fails["detalhe_indisponivel"] += 1
            print(f"      [fail seufisio_id={cid}] {err}")
            continue
        detalhes.append(full)
        time.sleep(RATE_LIMIT_SECONDS)

    local_token = None
    cpfs_existentes = set()
    emails_existentes = set()
    if not args.dry_run or tem_credenciais_locais:
        local_token = login_local(base_url, local_email, local_password)
        print("[3/4] Carregando pacientes já cadastrados (ativos + inativos) ...")
        locais = fetch_pacientes_locais(base_url, local_token)
        cpfs_existentes, emails_existentes = index_pacientes(locais)
        print(f"      {len(locais)} pacientes locais — "
              f"{len(cpfs_existentes)} CPFs e {len(emails_existentes)} e-mails para deduplicação")
    else:
        print("[3/4] Dry-run sem LOCAL_EMAIL/LOCAL_PASSWORD: duplicatas não serão detectadas")

    print(f"[4/4] {'Simulando' if args.dry_run else 'Enviando'} POST /pacientes ...")
    ok = 0
    for c in detalhes:
        seufisio_id = c.get("id") if isinstance(c, dict) else None
        payload, invalido = map_to_paciente(c)
        if invalido:
            skips["invalido"] += 1
            print(f"      [skip seufisio_id={seufisio_id}] {invalido}")
            continue

        duplicado = motivo_duplicado(payload, cpfs_existentes, emails_existentes)
        if duplicado:
            skips[duplicado] += 1
            print(f"      [skip seufisio_id={seufisio_id} cpf={mask_cpf(payload['cpf'])} "
                  f"email={mask_email(payload['email'])}] {duplicado}")
            continue

        if args.dry_run:
            print(json.dumps(masked_payload(payload), ensure_ascii=False))
            ok += 1
            continue

        status, body = post_paciente(base_url, local_token, payload)
        if status == 409:
            # Rede de segurança: unicidade que o dedup proativo não pegou.
            skips["conflito_409"] += 1
            print(f"      [skip seufisio_id={seufisio_id} cpf={mask_cpf(payload['cpf'])} "
                  f"email={mask_email(payload['email'])}] conflito_409")
            continue
        if status != 201 or not isinstance(body, dict):
            fails[f"post_status_{status}"] += 1
            print(f"      [fail seufisio_id={seufisio_id} cpf={mask_cpf(payload['cpf'])}] {status}")
            continue
        novo_id = body.get("id")
        if novo_id is None:
            fails["post_sem_id"] += 1
            print(f"      [fail seufisio_id={seufisio_id}] resposta sem id")
            continue
        ok += 1
        if payload["cpf"]:
            cpfs_existentes.add(payload["cpf"])
        email = normalize_email(payload["email"])
        if email:
            emails_existentes.add(email)

        if c.get("situacao") != SITUACAO_ATIVO:
            inat_status, _ = patch_inativar(base_url, local_token, novo_id)
            if inat_status != 204:
                print(f"      [warn inativar id={novo_id}] {inat_status}")

    print_relatorio(total, ok, skips, fails)
    if sum(fails.values()):
        sys.exit(1)


if __name__ == "__main__":
    main()
